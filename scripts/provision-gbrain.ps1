[CmdletBinding()]
param([switch]$Extraction)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw 'Root .env file is required. Copy .env.example first and set OPENROUTER_API_KEY or OPENAI_API_KEY.'
}

function Set-DotEnvValue {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [string] $Value
    )

    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in [IO.File]::ReadAllLines($environmentPath)) {
        $lines.Add($line)
    }

    $replacement = "$Name=$Value"
    $index = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^$([regex]::Escape($Name))=") {
            $index = $i
        }
    }

    if ($index -ge 0) {
        $lines[$index] = $replacement
    } else {
        $lines.Add($replacement)
    }

    [IO.File]::WriteAllText($environmentPath, ($lines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
}

Push-Location $repositoryRoot
try {
    # Registration bypasses the gbrain entrypoint and --no-deps never starts PostgreSQL.
    # Fail before attempting a credential mutation when the supporting database is stopped.
    $databaseState = & docker compose ps --format json postgres
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect PostgreSQL. Check Docker Desktop.' }
    $database = if ($databaseState) { ($databaseState -join "`n") | ConvertFrom-Json } else { $null }
    if ($null -eq $database -or $database.Health -ne 'healthy') {
        throw 'PostgreSQL must be running and healthy before provisioning. Run: docker compose up -d --wait postgres gbrain. Existing credentials in .env have not been changed.'
    }
    $credentialPrefix = if ($Extraction) { 'GBRAIN_EXTRACTION_OAUTH' } else { 'GBRAIN_OAUTH' }
    $clientName = if ($Extraction) { 'knowledgebridge-extraction' } else { 'knowledgebridge-backend' }
    $existingClientId = $null
    foreach ($line in [IO.File]::ReadAllLines($environmentPath)) {
        if ($line -match "^${credentialPrefix}_CLIENT_ID=(.+)$") {
            $existingClientId = $matches[1].Trim()
        }
    }

    $ErrorActionPreference = 'Continue'
    try {
        $sourceOutput = & docker compose run --rm --no-deps --entrypoint bun gbrain run src/cli.ts sources list --json 2>&1 | Out-String
        $sourceListExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($sourceListExitCode -ne 0) {
        throw 'gbrain source lookup failed.'
    }
    if ($sourceOutput -notmatch '"id"\s*:\s*"knowledgebridge"') {
        Write-Host "Creating gbrain source 'knowledgebridge'..."
        $ErrorActionPreference = 'Continue'
        try {
            $null = & docker compose run --rm --no-deps --entrypoint bun gbrain run src/cli.ts sources add knowledgebridge `
                --path /data/knowledgebridge/knowledgebridge --name KnowledgeBridge --no-federated --force 2>&1
            $sourceCreationExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = 'Stop'
        }
        if ($sourceCreationExitCode -ne 0) {
            throw "gbrain source 'knowledgebridge' creation failed."
        }
    }

    # Extraction creates people/ and companies/ pages in this source. The document
    # credential retains its slug fence; only the extraction credential omits it.
    $registrationArguments = @('run', 'src/cli.ts', 'auth', 'register-client', $clientName,
        '--grant-types', 'client_credentials', '--scopes', 'read write',
        '--source', 'knowledgebridge', '--federated-read', 'knowledgebridge')
    if (-not $Extraction) { $registrationArguments += @('--bound-slug-prefixes', 'knowledgebridge/') }
    # Windows PowerShell treats Docker's progress on stderr as NativeCommandError.
    # Capture it without aborting before the command's actual exit code is available.
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker compose run --rm --no-deps --entrypoint bun gbrain @registrationArguments 2>&1 | Out-String
        $registrationExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }

    if ($registrationExitCode -ne 0) {
        throw 'gbrain client registration failed. Output withheld because it can contain credentials.'
    }

    $clientIdMatch = [regex]::Match($output, '(?im)^\s*Client ID:\s*(\S+)\s*$')
    $clientSecretMatch = [regex]::Match($output, '(?im)^\s*Client Secret:\s*(\S+)\s*$')
    if (-not $clientIdMatch.Success -or -not $clientSecretMatch.Success) {
        throw 'gbrain registered a client, but its credential output could not be parsed.'
    }

    Set-DotEnvValue -Name "${credentialPrefix}_CLIENT_ID" -Value $clientIdMatch.Groups[1].Value
    Set-DotEnvValue -Name "${credentialPrefix}_CLIENT_SECRET" -Value $clientSecretMatch.Groups[1].Value
    Set-DotEnvValue -Name 'GBRAIN_OAUTH_TOKEN_URL' -Value 'http://localhost:3131/token'

    if ($existingClientId -and $existingClientId -ne $clientIdMatch.Groups[1].Value) {
        $ErrorActionPreference = 'Continue'
        try {
            $null = & docker compose run --rm --no-deps --entrypoint bun gbrain run src/cli.ts auth revoke-client $existingClientId 2>&1
            $revocationExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = 'Stop'
        }
        if ($revocationExitCode -ne 0) {
            Write-Warning "The new client works, but the previous client '$existingClientId' could not be revoked. Revoke it manually."
        }
    }

    Write-Host 'Provisioned a source-bound gbrain OAuth client and saved its credentials to the ignored root .env file.'
    Write-Host "Client ID: $($clientIdMatch.Groups[1].Value)"
    Write-Host 'Client secret: <saved and redacted>'
} finally {
    Pop-Location
}

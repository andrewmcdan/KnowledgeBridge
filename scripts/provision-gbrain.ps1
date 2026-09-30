[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw 'Root .env file is required. Copy .env.example first and set OPENROUTER_API_KEY.'
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
    $existingClientId = $null
    foreach ($line in [IO.File]::ReadAllLines($environmentPath)) {
        if ($line -match '^GBRAIN_OAUTH_CLIENT_ID=(.+)$') {
            $existingClientId = $matches[1].Trim()
        }
    }

    $output = & docker compose run --rm --no-deps --entrypoint bun gbrain run src/cli.ts auth register-client knowledgebridge-backend `
        --grant-types client_credentials `
        --scopes 'read write' `
        --source knowledgebridge `
        --federated-read knowledgebridge `
        --bound-slug-prefixes 'knowledgebridge/' 2>&1 | Out-String

    if ($LASTEXITCODE -ne 0) {
        throw "gbrain client registration failed: $($output.Trim())"
    }

    $clientIdMatch = [regex]::Match($output, '(?im)^\s*Client ID:\s*(\S+)\s*$')
    $clientSecretMatch = [regex]::Match($output, '(?im)^\s*Client Secret:\s*(\S+)\s*$')
    if (-not $clientIdMatch.Success -or -not $clientSecretMatch.Success) {
        throw 'gbrain registered a client, but its credential output could not be parsed.'
    }

    Set-DotEnvValue -Name 'GBRAIN_OAUTH_CLIENT_ID' -Value $clientIdMatch.Groups[1].Value
    Set-DotEnvValue -Name 'GBRAIN_OAUTH_CLIENT_SECRET' -Value $clientSecretMatch.Groups[1].Value
    Set-DotEnvValue -Name 'GBRAIN_OAUTH_TOKEN_URL' -Value 'http://localhost:3131/token'

    if ($existingClientId -and $existingClientId -ne $clientIdMatch.Groups[1].Value) {
        $null = & docker compose run --rm --no-deps --entrypoint bun gbrain run src/cli.ts auth revoke-client $existingClientId 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "The new client works, but the previous client '$existingClientId' could not be revoked. Revoke it manually."
        }
    }

    Write-Host 'Provisioned a source-bound gbrain OAuth client and saved its credentials to the ignored root .env file.'
    Write-Host "Client ID: $($clientIdMatch.Groups[1].Value)"
    Write-Host 'Client secret: <saved and redacted>'
} finally {
    Pop-Location
}

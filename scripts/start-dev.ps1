[CmdletBinding()]
param(
    [Alias('KeepServices')] [switch]$KeepDatabase,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendProcess = $null
$frontendProcess = $null
$startedServices = @()
$previouslyRunning = @()
$originalEnvironment = @{}
$locationPushed = $false

function Invoke-DevCompose {
    param([string[]]$Arguments)

    # Docker progress on stderr is a NativeCommandError in Windows PowerShell.
    # Never echo config output: it contains resolved credentials.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker compose @Arguments 2>$null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "Docker Compose '$($Arguments[0])' failed. Check Docker Desktop and the Compose configuration."
    }
    return ($output -join "`n")
}

function ConvertFrom-DevEnvironment {
    param([string]$Text)

    $values = @{}
    foreach ($line in ($Text -split "`n")) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $values[$Matches[1]] = $Matches[2].TrimEnd("`r")
        }
    }
    return $values
}

function Clear-DevEmptyCredentialOverrides {
    param([hashtable]$OriginalEnvironment)

    # PowerShell 7 can export empty strings; Compose then ignores the populated .env value.
    foreach ($name in @('GBRAIN_OAUTH_CLIENT_ID', 'GBRAIN_OAUTH_CLIENT_SECRET', 'GBRAIN_EXTRACTION_OAUTH_CLIENT_ID', 'GBRAIN_EXTRACTION_OAUTH_CLIENT_SECRET')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -ne $value -and [string]::IsNullOrWhiteSpace($value)) {
            $OriginalEnvironment[$name] = $value
            Remove-Item -LiteralPath "Env:$name"
            Write-Warning "Ignoring empty shell override for $name; using root .env instead."
        }
    }
}

function Restore-DevEnvironment {
    param([hashtable]$OriginalEnvironment)

    foreach ($name in $OriginalEnvironment.Keys) {
        if ($null -eq $OriginalEnvironment[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $OriginalEnvironment[$name], 'Process')
        }
    }
}

function Get-HostBackendEnvironment {
    param($Configuration, [hashtable]$ResolvedEnvironment, [string]$Root)

    $services = $Configuration.services
    $postgresPort = @($services.postgres.ports | Where-Object { $_.target -eq 5432 })[0].published
    $gbrainPort = @($services.gbrain.ports | Where-Object { $_.target -eq 3131 })[0].published
    if (-not $postgresPort -or -not $gbrainPort) {
        throw 'PostgreSQL and gbrain must publish their ports for host development.'
    }
    $values = @{}
    foreach ($property in $services.backend.environment.PSObject.Properties) {
        $values[$property.Name] = [string]$property.Value
    }
    # Include documented adapter/video overrides that Compose does not explicitly forward.
    foreach ($name in $ResolvedEnvironment.Keys) {
        if ($name -match '^(KNOWLEDGEBRIDGE_|OPENAI_|FFMPEG_PATH$|FFPROBE_PATH$|GBRAIN_(ENABLED$|MODE$|BASE_URL$|OAUTH_|EXTRACTION_OAUTH_|CONNECT_TIMEOUT$|READ_TIMEOUT$|SYNTHESIS_TIMEOUT$|MAX_RESPONSE_SIZE$|RETRY_MAX_ATTEMPTS$|RETRY_MAX_BACKOFF$))') {
            $values[$name] = $ResolvedEnvironment[$name]
        }
    }
    $values['SPRING_DOCKER_COMPOSE_ENABLED'] = 'false'
    $values['SPRING_DATASOURCE_URL'] = "jdbc:postgresql://localhost:$postgresPort/$($services.postgres.environment.POSTGRES_DB)"
    $values['SPRING_DATASOURCE_USERNAME'] = $services.postgres.environment.POSTGRES_USER
    $values['SPRING_DATASOURCE_PASSWORD'] = $services.postgres.environment.POSTGRES_PASSWORD
    # The host JVM cannot use Compose DNS names or container-only storage paths.
    $values['GBRAIN_BASE_URL'] = "http://localhost:$gbrainPort"
    $values['GBRAIN_OAUTH_TOKEN_URL'] = "http://localhost:$gbrainPort/token"
    $storage = $ResolvedEnvironment['KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY']
    $values['KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY'] = if ($storage) { $storage } else { Join-Path $Root 'backend/data/uploads' }
    return $values
}

function Get-DevServicesToStop {
    param([string[]]$Started, [string[]]$PreviouslyRunning, [bool]$Keep)

    if ($Keep) { return }
    if ($Started -contains 'gbrain') { 'gbrain' }
    # A pre-existing gbrain instance still needs PostgreSQL, even if it was unhealthy.
    if ($Started -contains 'postgres' -and $PreviouslyRunning -notcontains 'gbrain') { 'postgres' }
}

function Wait-ForDevContainer {
    param([string]$Service, [int]$TimeoutSeconds = 180)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state = Invoke-DevCompose -Arguments @('ps', '--format', 'json', $Service)
        if ($state) {
            $container = $state | ConvertFrom-Json
            if ($container.Health -eq 'healthy') { return }
            if ($container.State -in @('exited', 'dead')) { throw "Docker service '$Service' exited during startup." }
        }
        Start-Sleep -Seconds 2
    }
    throw "Docker service '$Service' did not become healthy within $TimeoutSeconds seconds."
}

function Wait-ForDevHttp {
    param([string]$Url, [int]$TimeoutSeconds = 180)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        foreach ($process in @($backendProcess, $frontendProcess)) {
            $process.Refresh()
            if ($process.HasExited) { throw "A development process exited during startup. See logs in $logDirectory." }
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch {
            # Startup connection failures are expected; process exits are checked above.
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for $Url. See logs in $logDirectory."
}

try {
    foreach ($command in @('docker', 'npm.cmd', 'java', 'taskkill.exe')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Required command '$command' was not found on PATH."
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.env'))) {
        throw 'Root .env is required. Copy .env.example and provision both gbrain credentials first.'
    }
    Push-Location $projectRoot
    $locationPushed = $true

    Clear-DevEmptyCredentialOverrides $originalEnvironment
    $configuration = (Invoke-DevCompose -Arguments @('config', '--format', 'json')) | ConvertFrom-Json
    $resolvedEnvironment = ConvertFrom-DevEnvironment (Invoke-DevCompose -Arguments @('config', '--environment'))
    $backendEnvironment = Get-HostBackendEnvironment $configuration $resolvedEnvironment $projectRoot
    foreach ($name in @('GBRAIN_OAUTH_CLIENT_ID', 'GBRAIN_OAUTH_CLIENT_SECRET', 'GBRAIN_EXTRACTION_OAUTH_CLIENT_ID', 'GBRAIN_EXTRACTION_OAUTH_CLIENT_SECRET')) {
        if ([string]::IsNullOrWhiteSpace($backendEnvironment[$name])) {
            throw "Missing $name. Run scripts/provision-gbrain.ps1 for document credentials and add -Extraction for extraction credentials."
        }
    }
    $previouslyRunning = @((Invoke-DevCompose -Arguments @('ps', '--services', '--status', 'running')) -split "`n")
    $applicationRunning = @($previouslyRunning | Where-Object { $_ -in @('backend', 'frontend') })
    $occupiedPorts = @([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -in @(8080, 5173) } | Select-Object -ExpandProperty Port -Unique)

    if ($CheckOnly) {
        Write-Host 'Configuration validated; both gbrain credentials are present (values hidden).'
        Write-Host "Host database: $($backendEnvironment['SPRING_DATASOURCE_URL'])"
        Write-Host "Host gbrain: $($backendEnvironment['GBRAIN_BASE_URL'])"
        if ($applicationRunning -or $occupiedPorts) {
            Write-Warning 'Before launching, stop Docker backend/frontend with: docker compose stop backend frontend. Also free host ports 8080 and 5173.'
        }
        return
    }
    if ($applicationRunning) {
        throw "The Docker backend or frontend is running. Use 'docker compose stop backend frontend' first; leave PostgreSQL and gbrain running."
    }
    if ($occupiedPorts) { throw "Host ports $($occupiedPorts -join ', ') are already occupied. Stop those application processes first." }

    foreach ($service in @('postgres', 'gbrain')) {
        if ($previouslyRunning -notcontains $service) {
            Write-Host "Starting $service..."
            $startedServices += $service
            $null = Invoke-DevCompose -Arguments @('up', '-d', '--no-deps', $service)
        }
        Wait-ForDevContainer -Service $service
    }

    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'frontend/node_modules'))) {
        Push-Location (Join-Path $projectRoot 'frontend')
        try {
            npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
        } finally { Pop-Location }
    }
    foreach ($name in $backendEnvironment.Keys) {
        if (-not $originalEnvironment.ContainsKey($name)) {
            $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        [Environment]::SetEnvironmentVariable($name, $backendEnvironment[$name], 'Process')
    }
    $logDirectory = Join-Path ([IO.Path]::GetTempPath()) ('knowledgebridge-dev-' + [Guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Path $logDirectory
    Write-Host "Starting Spring Boot and Vite. Logs: $logDirectory"
    $backendProcess = Start-Process -FilePath (Join-Path $projectRoot 'backend/gradlew.bat') `
        -ArgumentList 'bootRun', '--no-daemon', '--console=plain' `
        -WorkingDirectory (Join-Path $projectRoot 'backend') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDirectory 'backend.out.log') -RedirectStandardError (Join-Path $logDirectory 'backend.err.log')
    $frontendProcess = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev', '--', '--strictPort' `
        -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDirectory 'frontend.out.log') -RedirectStandardError (Join-Path $logDirectory 'frontend.err.log')

    Wait-ForDevHttp 'http://localhost:8080/api/health'
    Wait-ForDevHttp 'http://localhost:5173'
    Write-Host 'KnowledgeBridge is ready: http://localhost:5173 (backend: http://localhost:8080/api/health)'
    Write-Host 'Press Ctrl+C to stop local processes. Pre-existing Docker services will be left running.'
    while ($true) {
        foreach ($process in @($backendProcess, $frontendProcess)) {
            $process.Refresh()
            if ($process.HasExited) { throw "A development process exited. See logs in $logDirectory." }
        }
        Start-Sleep -Seconds 1
    }
} finally {
    foreach ($process in @($frontendProcess, $backendProcess)) {
        if ($null -ne $process -and -not $process.HasExited) {
            # Stop wrapper processes and their Node/Java descendants, not just cmd.exe.
            $cleanupPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $null = & taskkill.exe /PID $process.Id /T /F 2>$null
                if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop process tree $($process.Id); check it manually." }
            } finally { $ErrorActionPreference = $cleanupPreference }
        }
    }
    Restore-DevEnvironment $originalEnvironment
    foreach ($service in @(Get-DevServicesToStop $startedServices $previouslyRunning ([bool]$KeepDatabase))) {
        try {
            Write-Host "Stopping launcher-owned service $service..."
            $null = Invoke-DevCompose -Arguments @('stop', $service)
        } catch { Write-Warning "Could not stop $service. Run 'docker compose stop $service' manually." }
    }
    if ($locationPushed) { Pop-Location }
}

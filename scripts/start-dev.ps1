[CmdletBinding()]
param(
    [switch]$KeepDatabase
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendProcess = $null
$frontendProcess = $null
$databaseStarted = $false
$locationPushed = $false

function Wait-ForPostgres {
    param([int]$TimeoutSeconds = 60)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $containerId = docker compose ps -q postgres
        if ($LASTEXITCODE -eq 0 -and $containerId) {
            $health = docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null
            if ($health -eq 'healthy') {
                return
            }
        }
        Start-Sleep -Seconds 2
    }

    throw "PostgreSQL did not become healthy within $TimeoutSeconds seconds."
}

try {
    foreach ($command in @('docker', 'npm', 'java')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Required command '$command' was not found on PATH."
        }
    }

    Push-Location $projectRoot
    $locationPushed = $true

    $runningApplicationServices = docker compose ps --services --status running backend frontend
    if ($runningApplicationServices) {
        throw "The Docker backend or frontend is already running. Stop it with 'docker compose down' before local development."
    }

    Write-Host 'Starting PostgreSQL/pgvector...'
    docker compose up -d postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose could not start PostgreSQL.'
    }
    $databaseStarted = $true
    Wait-ForPostgres

    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'frontend\node_modules'))) {
        Write-Host 'Installing frontend dependencies...'
        Push-Location (Join-Path $projectRoot 'frontend')
        npm install
        $npmExitCode = $LASTEXITCODE
        Pop-Location
        if ($npmExitCode -ne 0) {
            throw 'npm install failed.'
        }
    }

    $postgresPort = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { '5432' }
    $postgresDatabase = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'knowledgebridge' }
    $postgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'knowledgebridge' }
    $postgresPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { 'knowledgebridge' }

    $env:SPRING_DOCKER_COMPOSE_ENABLED = 'false'
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$postgresPort/$postgresDatabase"
    $env:SPRING_DATASOURCE_USERNAME = $postgresUser
    $env:SPRING_DATASOURCE_PASSWORD = $postgresPassword

    Write-Host 'Starting Spring Boot and Vite...'
    $backendProcess = Start-Process -FilePath (Join-Path $projectRoot 'backend\gradlew.bat') `
        -ArgumentList 'bootRun', '--no-daemon', '--console=plain' `
        -WorkingDirectory (Join-Path $projectRoot 'backend') `
        -NoNewWindow -PassThru

    $frontendProcess = Start-Process -FilePath 'npm.cmd' `
        -ArgumentList 'run', 'dev' `
        -WorkingDirectory (Join-Path $projectRoot 'frontend') `
        -NoNewWindow -PassThru

    Write-Host ''
    Write-Host 'KnowledgeBridge development services are starting:'
    Write-Host '  Frontend: http://localhost:5173'
    Write-Host '  Backend:  http://localhost:8080/api/health'
    Write-Host 'Press Ctrl+C to stop the local application processes.'

    while (-not $backendProcess.HasExited -and -not $frontendProcess.HasExited) {
        Start-Sleep -Seconds 1
        $backendProcess.Refresh()
        $frontendProcess.Refresh()
    }

    if ($backendProcess.HasExited) {
        throw "Spring Boot exited with code $($backendProcess.ExitCode)."
    }
    throw "Vite exited with code $($frontendProcess.ExitCode)."
}
finally {
    foreach ($process in @($frontendProcess, $backendProcess)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }

    if ($databaseStarted -and -not $KeepDatabase) {
        Write-Host 'Stopping PostgreSQL/pgvector...'
        docker compose stop postgres | Out-Host
    }

    if ($locationPushed) {
        Pop-Location
    }
}

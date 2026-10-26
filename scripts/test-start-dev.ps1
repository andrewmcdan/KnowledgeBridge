$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'start-dev.ps1'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($launcher, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Launcher has PowerShell syntax errors.' }

# Load only pure helpers, never the launcher's service/process orchestration.
foreach ($name in @('ConvertFrom-DevEnvironment', 'Test-DevBackendOverride', 'Clear-DevEmptyEnvironmentOverrides', 'Restore-DevEnvironment', 'Get-HostBackendEnvironment', 'Get-DevServicesToStop', 'Assert-DevProcessesRunning', 'Get-DevBackendArguments')) {
    $definition = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $false)
    if ($null -eq $definition) { throw "Missing launcher helper: $name" }
    . ([scriptblock]::Create($definition.Extent.Text))
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Description)
    if (($Actual | ConvertTo-Json -Compress) -ne ($Expected | ConvertTo-Json -Compress)) {
        throw "Assertion failed: $Description (values withheld)."
    }
}

$credentialName = 'GBRAIN_OAUTH_CLIENT_ID'
$originalCredential = [Environment]::GetEnvironmentVariable($credentialName, 'Process')
try {
    foreach ($blank in @('', '   ')) {
        [Environment]::SetEnvironmentVariable($credentialName, $blank, 'Process')
        $beforeClear = [Environment]::GetEnvironmentVariable($credentialName, 'Process')
        $saved = @{}
        Clear-DevEmptyEnvironmentOverrides $saved
        Assert-Equal ([Environment]::GetEnvironmentVariable($credentialName, 'Process')) $null 'clear blank shell credentials'
        if ($null -ne $beforeClear) {
            Assert-Equal $saved[$credentialName] $beforeClear 'save exact blank value for restoration'
        }
        Restore-DevEnvironment $saved
        Assert-Equal ([Environment]::GetEnvironmentVariable($credentialName, 'Process')) $beforeClear 'restore blank shell values'
    }
    [Environment]::SetEnvironmentVariable($credentialName, 'synthetic-shell-override', 'Process')
    $saved = @{}
    Clear-DevEmptyEnvironmentOverrides $saved
    Assert-Equal ([Environment]::GetEnvironmentVariable($credentialName, 'Process')) 'synthetic-shell-override' 'retain nonblank shell precedence'
    Assert-Equal $saved.ContainsKey($credentialName) $false 'do not replace nonblank shell override'
    Restore-DevEnvironment $saved
    Restore-DevEnvironment @{ GBRAIN_OAUTH_CLIENT_ID = $null }
    Assert-Equal ([Environment]::GetEnvironmentVariable($credentialName, 'Process')) $null 'remove originally absent variables on exit'
} finally { Restore-DevEnvironment @{ GBRAIN_OAUTH_CLIENT_ID = $originalCredential } }

foreach ($name in @('KNOWLEDGEBRIDGE_VIDEO_MAX_UPLOAD_SIZE', 'SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE', 'SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE')) {
    $previous = [Environment]::GetEnvironmentVariable($name, 'Process')
    try {
        [Environment]::SetEnvironmentVariable($name, ' ', 'Process')
        $saved = @{}
        Clear-DevEmptyEnvironmentOverrides $saved
        Assert-Equal ([Environment]::GetEnvironmentVariable($name, 'Process')) $null 'remove blank multipart/video overrides'
        Restore-DevEnvironment $saved
        Assert-Equal ([Environment]::GetEnvironmentVariable($name, 'Process')) ' ' 'restore caller multipart/video overrides'
    } finally { Restore-DevEnvironment @{ $name = $previous } }
}
$running = [pscustomobject]@{ HasExited = $false; ExitCode = 0 }
$running | Add-Member -MemberType ScriptMethod -Name Refresh -Value {}
$exited = [pscustomobject]@{ HasExited = $true; ExitCode = 0 }
$exited | Add-Member -MemberType ScriptMethod -Name Refresh -Value {}
Assert-DevProcessesRunning $running $running 'test-logs'
Assert-Equal (Get-DevBackendArguments) @('bootRun', '--no-daemon', '--console=plain', '-PdevtoolsRestart=false') 'disable DevTools restart before Java main so startup failure returns a nonzero exit code'
foreach ($failed in @('Spring Boot', 'Vite')) {
    $message = ''
    try {
        if ($failed -eq 'Spring Boot') { Assert-DevProcessesRunning $exited $running 'test-logs' }
        else { Assert-DevProcessesRunning $running $exited 'test-logs' }
    } catch { $message = $_.Exception.Message }
    Assert-Equal ($message -like "$failed exited with code 0.*") $true 'identify failed child even on zero exit code'
}

$resolved = ConvertFrom-DevEnvironment "GBRAIN_OAUTH_CLIENT_SECRET=synthetic=a=b`r`nKNOWLEDGEBRIDGE_JWT_SECRET=synthetic jwt`nFFMPEG_PATH=C:/Media Tools/ffmpeg.exe`nGBRAIN_READ_TIMEOUT=PT45S`nGBRAIN_DB_PASSWORD=not-for-the-backend`n"
Assert-Equal $resolved.GBRAIN_OAUTH_CLIENT_SECRET 'synthetic=a=b' 'preserve equals signs in resolved values'
Assert-Equal $resolved.FFMPEG_PATH 'C:/Media Tools/ffmpeg.exe' 'preserve spaces in resolved paths'

$configuration = @'
{
  "services": {
    "postgres": {"ports": [{"target": 5432, "published": "15433"}], "environment": {"POSTGRES_DB": "testdb", "POSTGRES_USER": "testuser", "POSTGRES_PASSWORD": "synthetic-db-password"}},
    "gbrain": {"ports": [{"target": 3131, "published": "13131"}]},
    "backend": {"environment": {"SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/testdb", "GBRAIN_BASE_URL": "http://gbrain:3131", "GBRAIN_OAUTH_TOKEN_URL": "http://gbrain:3131/token", "GBRAIN_EXTRACTION_OAUTH_CLIENT_SECRET": "synthetic-extraction", "OPENAI_API_KEY": "synthetic-key", "KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY": "/data/uploads"}}
  }
}
'@ | ConvertFrom-Json
$root = Split-Path -Parent $PSScriptRoot
$environment = Get-HostBackendEnvironment $configuration $resolved $root
Assert-Equal $environment.SPRING_DATASOURCE_URL 'jdbc:postgresql://localhost:15433/testdb' 'published PostgreSQL port'
Assert-Equal $environment.SPRING_DATASOURCE_USERNAME 'testuser' 'database username'
Assert-Equal $environment.SPRING_DATASOURCE_PASSWORD 'synthetic-db-password' 'database password'
Assert-Equal $environment.GBRAIN_BASE_URL 'http://localhost:13131' 'published gbrain port'
Assert-Equal $environment.GBRAIN_OAUTH_TOKEN_URL 'http://localhost:13131/token' 'host OAuth endpoint'
Assert-Equal $environment.GBRAIN_OAUTH_CLIENT_SECRET 'synthetic=a=b' 'document credential override'
Assert-Equal $environment.GBRAIN_EXTRACTION_OAUTH_CLIENT_SECRET 'synthetic-extraction' 'extraction credential'
Assert-Equal $environment.OPENAI_API_KEY 'synthetic-key' 'transcription key'
Assert-Equal $environment.KNOWLEDGEBRIDGE_JWT_SECRET 'synthetic jwt' 'JWT secret override'
Assert-Equal $environment.GBRAIN_READ_TIMEOUT 'PT45S' 'optional adapter override'
Assert-Equal $environment.FFMPEG_PATH 'C:/Media Tools/ffmpeg.exe' 'host media executable override'
Assert-Equal $environment.ContainsKey('GBRAIN_DB_PASSWORD') $false 'do not forward gbrain database secret'
Assert-Equal $environment.KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY (Join-Path $root 'backend/data/uploads') 'replace container storage default'
$resolved.KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY = 'custom/uploads'
Assert-Equal (Get-HostBackendEnvironment $configuration $resolved $root).KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY 'custom/uploads' 'preserve explicit host storage'

Assert-Equal @(Get-DevServicesToStop @('postgres', 'gbrain') @() $false) @('gbrain', 'postgres') 'stop owned services in dependency order'
Assert-Equal @(Get-DevServicesToStop @('gbrain') @('postgres') $false) @('gbrain') 'preserve pre-existing database'
Assert-Equal @(Get-DevServicesToStop @() @('postgres', 'gbrain') $false) @() 'preserve all pre-existing infrastructure'
Assert-Equal @(Get-DevServicesToStop @('postgres') @('gbrain') $false) @() 'preserve database needed by pre-existing gbrain'
Assert-Equal @(Get-DevServicesToStop @('postgres', 'gbrain') @() $true) @() 'KeepDatabase retains both services'

$configuration.services.gbrain.ports = @()
$rejected = $false
try { $null = Get-HostBackendEnvironment $configuration $resolved $root } catch { $rejected = $true }
Assert-Equal $rejected $true 'reject unpublished infrastructure ports'
Write-Host 'Passed launcher syntax, environment mapping, custom ports/storage, secret isolation, and service ownership checks.'

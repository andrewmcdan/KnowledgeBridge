$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'start-dev.ps1'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($launcher, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Launcher has PowerShell syntax errors.' }

# Load only pure helpers, never the launcher's service/process orchestration.
foreach ($name in @('ConvertFrom-DevEnvironment', 'Get-HostBackendEnvironment', 'Get-DevServicesToStop')) {
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

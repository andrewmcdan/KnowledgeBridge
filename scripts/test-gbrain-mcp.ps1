[CmdletBinding()]
param(
    [switch] $KeepSmokePage,
    [switch] $CaptureFixtures
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $repositoryRoot '.env'
$baseUrl = 'http://localhost:3131'
$smokeSlug = 'knowledgebridge/phase-1-protocol-spike'
$fixtureDirectory = Join-Path $repositoryRoot 'backend/src/test/resources/gbrain/fixtures'
$sanitizedProperties = @('instructions', 'path', 'source_path', 'source_uri')

function Read-DotEnv {
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($environmentPath)) {
        if ($line -match '^([^#=]+)=(.*)$') {
            $values[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $values
}

function ConvertFrom-SseResponse {
    param([Parameter(Mandatory)] [string] $Content)

    $dataLine = $Content -split "`n" | Where-Object { $_ -like 'data: *' } | Select-Object -First 1
    if (-not $dataLine) {
        throw 'gbrain returned an MCP response without an SSE data event.'
    }
    return $dataLine.Substring(6) | ConvertFrom-Json -Depth 100
}

function Invoke-GbrainRpc {
    param(
        [Parameter(Mandatory)] [int] $Id,
        [Parameter(Mandatory)] [string] $Method,
        [Parameter(Mandatory)] [hashtable] $Params
    )

    $payload = @{
        jsonrpc = '2.0'
        id = $Id
        method = $Method
        params = $Params
    } | ConvertTo-Json -Depth 30 -Compress

    $response = Invoke-WebRequest -Method Post -Uri "$baseUrl/mcp" -Headers $script:mcpHeaders `
        -ContentType 'application/json' -Body $payload -TimeoutSec 180
    $rpcResponse = ConvertFrom-SseResponse -Content $response.Content
    if ($rpcResponse.error) {
        throw "MCP JSON-RPC error $($rpcResponse.error.code): $($rpcResponse.error.message)"
    }
    return $rpcResponse
}

function Invoke-GbrainTool {
    param(
        [Parameter(Mandatory)] [int] $Id,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [hashtable] $Arguments,
        [switch] $AllowError
    )

    $response = Invoke-GbrainRpc -Id $Id -Method 'tools/call' -Params @{ name = $Name; arguments = $Arguments }
    if ($response.result.isError -and -not $AllowError) {
        $message = $response.result.content[0].text
        throw "gbrain tool '$Name' failed: $message"
    }
    return $response
}

function Get-ToolPayload {
    param([Parameter(Mandatory)] $Response)

    return $Response.result.content[0].text | ConvertFrom-Json -Depth 100
}

function Protect-FixtureValue {
    param($Value)

    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        foreach ($property in $Value.PSObject.Properties) {
            if ($property.Name -in $sanitizedProperties -and $property.Value -is [string]) {
                $property.Value = '<sanitized>'
            } else {
                Protect-FixtureValue -Value $property.Value
            }
        }
    } elseif ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string]) {
        foreach ($item in $Value) {
            Protect-FixtureValue -Value $item
        }
    }
}

# Writes a JSON-RPC response as a test fixture. Tool payloads are JSON inside a text block, so they are
# parsed, sanitized, and re-encoded the same way the Phase 1 fixtures were.
function Save-Fixture {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] $Response
    )

    if (-not $CaptureFixtures) {
        return
    }
    $copy = $Response | ConvertTo-Json -Depth 100 | ConvertFrom-Json -Depth 100
    Protect-FixtureValue -Value $copy
    foreach ($content in @($copy.result.content)) {
        if ($content.type -eq 'text') {
            $payload = $content.text | ConvertFrom-Json -Depth 100
            Protect-FixtureValue -Value $payload
            $content.text = $payload | ConvertTo-Json -Depth 100
        }
    }
    $json = $copy | ConvertTo-Json -Depth 100
    # Repository JSON uses four-space indentation; ConvertTo-Json emits two.
    $json = ($json -split "`r?`n" | ForEach-Object { $_ -replace '^( +)', { $_.Groups[1].Value * 2 } }) -join "`n"
    $path = Join-Path $fixtureDirectory "$Name.json"
    [IO.File]::WriteAllText($path, $json + "`n", [Text.UTF8Encoding]::new($false))
    Write-Host "Captured fixture $path"
}

function Assert-ToolStatus {
    param(
        [Parameter(Mandatory)] $Response,
        [Parameter(Mandatory)] [string] $Tool,
        [Parameter(Mandatory)] [string] $Expected
    )

    $status = (Get-ToolPayload -Response $Response).status
    if ($status -ne $Expected) {
        throw "gbrain $Tool returned status '$status', expected '$Expected'."
    }
}

if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw 'Root .env file is required. Run scripts/provision-gbrain.ps1 first.'
}

$environment = Read-DotEnv
foreach ($required in @('GBRAIN_OAUTH_CLIENT_ID', 'GBRAIN_OAUTH_CLIENT_SECRET', 'GBRAIN_OAUTH_TOKEN_URL')) {
    if (-not $environment[$required]) {
        throw "$required is missing from .env. Run scripts/provision-gbrain.ps1."
    }
}

$health = Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 10
if ($health.status -ne 'ok') {
    throw "gbrain health is '$($health.status)', expected 'ok'."
}

$token = Invoke-RestMethod -Method Post -Uri $environment.GBRAIN_OAUTH_TOKEN_URL `
    -ContentType 'application/x-www-form-urlencoded' -Body @{
        grant_type = 'client_credentials'
        client_id = $environment.GBRAIN_OAUTH_CLIENT_ID
        client_secret = $environment.GBRAIN_OAUTH_CLIENT_SECRET
        scope = 'read write'
    }

$script:mcpHeaders = @{
    Authorization = "Bearer $($token.access_token)"
    Accept = 'application/json, text/event-stream'
}

$initialize = Invoke-GbrainRpc -Id 1 -Method 'initialize' -Params @{
    protocolVersion = '2025-03-26'
    capabilities = @{}
    clientInfo = @{ name = 'knowledgebridge-spike'; version = '0.1.0' }
}
if ($initialize.result.serverInfo.name -ne 'gbrain') {
    throw "Unexpected MCP server '$($initialize.result.serverInfo.name)'."
}

$tools = Invoke-GbrainRpc -Id 2 -Method 'tools/list' -Params @{}
$toolNames = @($tools.result.tools | ForEach-Object { $_.name })
$requiredTools = @('whoami', 'put_page', 'get_page', 'search', 'synthesize', 'delete_page', 'restore_page')
$missingTools = @($requiredTools | Where-Object { $_ -notin $toolNames })
if ($missingTools.Count -gt 0) {
    throw "Required gbrain tools are missing: $($missingTools -join ', ')"
}

$identityResponse = Invoke-GbrainTool -Id 3 -Name 'whoami' -Arguments @{}
$identity = $identityResponse.result.content[0].text | ConvertFrom-Json -Depth 100
if ($identity.source_id -ne 'knowledgebridge' -or 'read' -notin $identity.scopes -or 'write' -notin $identity.scopes) {
    throw 'The OAuth client is not bound to the expected source and read/write scopes.'
}

# Mirrors the frontmatter GbrainPages.render writes, so get_page captures show the adapter's metadata keys.
$content = @'
---
title: "Phase 1 Semantic Lighthouse"
type: "knowledgebridge_smoke_test"
knowledgebridge_id: "phase-1-protocol-spike"
knowledgebridge_owner: "smoke-test"
knowledgebridge_revision: 1
knowledgebridge_created_at: "2026-09-30T00:00:00Z"
knowledgebridge_updated_at: "2026-09-30T00:00:00Z"
knowledgebridge_digest: "smoke-test-digest"
---

The obsidian lighthouse protocol authorizes blue herons to audit quarterly procurement records. This synthetic sentence exists only to verify semantic retrieval.
'@

try {
    $null = Invoke-GbrainTool -Id 4 -Name 'put_page' -Arguments @{ slug = $smokeSlug; content = $content }

    $searchResponse = Invoke-GbrainTool -Id 5 -Name 'search' -Arguments @{
        query = 'Which birds inspect purchasing documents every three months?'
        limit = 5
        source_id = 'knowledgebridge'
    }
    $searchResults = $searchResponse.result.content[0].text | ConvertFrom-Json -Depth 100
    if ($smokeSlug -notin @($searchResults | ForEach-Object { $_.slug })) {
        throw 'Semantic paraphrase search did not return the smoke page.'
    }
    if ($searchResponse.result._meta.retrieval.vector_enabled -ne $true) {
        throw 'Search returned the page without proving vector retrieval was enabled.'
    }
    if (@($searchResponse.result._meta.retrieval.degraded).Count -gt 0) {
        throw "Search reported degraded retrieval: $($searchResponse.result._meta.retrieval.degraded -join ', ')"
    }

    $synthesisResponse = Invoke-GbrainTool -Id 6 -Name 'synthesize' -Arguments @{
        question = 'Which birds are authorized to audit quarterly procurement records?'
    }
    $synthesis = $synthesisResponse.result.content[0].text | ConvertFrom-Json -Depth 100
    if ($synthesis.synthesis_status -ne 'ok' -or $smokeSlug -notin $synthesis.sources) {
        throw 'Synthesis did not return an OK cited answer from the smoke page.'
    }

    # Page lifecycle used by McpGbrainClient: read, soft-delete, read deleted, restore, and a missing read.
    $pageArguments = @{ slug = $smokeSlug; source_id = 'knowledgebridge' }
    $readArguments = @{ slug = $smokeSlug; source_id = 'knowledgebridge'; include_deleted = $true }

    $pageResponse = Invoke-GbrainTool -Id 7 -Name 'get_page' -Arguments $readArguments
    $page = Get-ToolPayload -Response $pageResponse
    if ($page.slug -ne $smokeSlug -or $page.frontmatter.knowledgebridge_id -ne 'phase-1-protocol-spike' -or $page.deleted_at) {
        throw 'get_page did not return the active smoke page with KnowledgeBridge frontmatter.'
    }
    Save-Fixture -Name 'get-page-response' -Response $pageResponse

    $deleteResponse = Invoke-GbrainTool -Id 8 -Name 'delete_page' -Arguments $pageArguments
    Assert-ToolStatus -Response $deleteResponse -Tool 'delete_page' -Expected 'soft_deleted'
    Save-Fixture -Name 'delete-page-response' -Response $deleteResponse

    $deleteAgainResponse = Invoke-GbrainTool -Id 9 -Name 'delete_page' -Arguments $pageArguments
    Assert-ToolStatus -Response $deleteAgainResponse -Tool 'delete_page' -Expected 'already_soft_deleted'
    Save-Fixture -Name 'delete-page-already-deleted-response' -Response $deleteAgainResponse

    $deletedPageResponse = Invoke-GbrainTool -Id 10 -Name 'get_page' -Arguments $readArguments
    if (-not (Get-ToolPayload -Response $deletedPageResponse).deleted_at) {
        throw 'get_page with include_deleted did not report deleted_at for the soft-deleted page.'
    }
    Save-Fixture -Name 'get-page-deleted-response' -Response $deletedPageResponse

    $restoreResponse = Invoke-GbrainTool -Id 11 -Name 'restore_page' -Arguments $pageArguments
    Assert-ToolStatus -Response $restoreResponse -Tool 'restore_page' -Expected 'restored'
    Save-Fixture -Name 'restore-page-response' -Response $restoreResponse

    $restoreAgainResponse = Invoke-GbrainTool -Id 12 -Name 'restore_page' -Arguments $pageArguments
    Assert-ToolStatus -Response $restoreAgainResponse -Tool 'restore_page' -Expected 'already_active'
    Save-Fixture -Name 'restore-page-already-active-response' -Response $restoreAgainResponse

    $missingResponse = Invoke-GbrainTool -Id 13 -Name 'get_page' -AllowError -Arguments @{
        slug = 'knowledgebridge/phase-1-missing-page'
        source_id = 'knowledgebridge'
        include_deleted = $true
    }
    if (-not $missingResponse.result.isError -or (Get-ToolPayload -Response $missingResponse).error -ne 'page_not_found') {
        throw 'get_page for a missing slug did not return an isError page_not_found result.'
    }
    Save-Fixture -Name 'get-page-not-found-response' -Response $missingResponse

    Write-Host "gbrain MCP smoke test passed: server=$($initialize.result.serverInfo.version), tools=$($toolNames.Count), vector=true, synthesis=ok, page lifecycle=ok."
} finally {
    if (-not $KeepSmokePage) {
        $null = Invoke-GbrainTool -Id 14 -Name 'delete_page' -AllowError -Arguments @{ slug = $smokeSlug; source_id = 'knowledgebridge' }
    }
}

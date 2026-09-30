[CmdletBinding()]
param(
    [switch] $KeepSmokePage
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $repositoryRoot '.env'
$baseUrl = 'http://localhost:3131'
$smokeSlug = 'knowledgebridge/phase-1-protocol-spike'

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
        [Parameter(Mandatory)] [hashtable] $Arguments
    )

    $response = Invoke-GbrainRpc -Id $Id -Method 'tools/call' -Params @{ name = $Name; arguments = $Arguments }
    if ($response.result.isError) {
        $message = $response.result.content[0].text
        throw "gbrain tool '$Name' failed: $message"
    }
    return $response
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

$content = @'
---
title: Phase 1 Semantic Lighthouse
type: knowledgebridge_smoke_test
knowledgebridge_id: phase-1-protocol-spike
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

    Write-Host "gbrain MCP smoke test passed: server=$($initialize.result.serverInfo.version), tools=$($toolNames.Count), vector=true, synthesis=ok."
} finally {
    if (-not $KeepSmokePage) {
        $null = Invoke-GbrainTool -Id 7 -Name 'delete_page' -Arguments @{ slug = $smokeSlug; source_id = 'knowledgebridge' }
    }
}

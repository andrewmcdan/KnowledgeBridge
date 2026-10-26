[CmdletBinding()]
param([switch]$Integration)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$purger = Join-Path $PSScriptRoot 'purge-documents.ps1'
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($purger, [ref]$null, [ref]$errors)
if ($errors.Count) { throw 'Purge script has syntax errors.' }
foreach ($name in @('Test-PurgeDescendant', 'Resolve-PurgeDirectory', 'Get-PurgeFiles')) {
    $definition = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $false)
    . ([scriptblock]::Create($definition.Extent.Text))
}
function Assert-Purge {
    param([bool]$Condition, [string]$Description)
    if (-not $Condition) { throw "Purge test failed: $Description" }
}
function Assert-PurgeThrows {
    param([scriptblock]$Action, [string]$Pattern)
    $message = ''
    try { & $Action | Out-Null } catch { $message = $_.Exception.Message }
    Assert-Purge ($message -match $Pattern) "expected rejection: $Pattern"
}
function Invoke-TestDocker {
    param([string[]]$Arguments, [string]$InputText)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = if ($InputText) { $InputText | & docker @Arguments 2>&1 } else { & docker @Arguments 2>&1 }
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) {
        $detail = if ($Arguments -contains 'register-client') { 'Credential output withheld.' } else { (@($output) | Select-Object -Last 20) -join "`n" }
        throw "Test Docker operation failed: $detail"
    }
    return ($output -join "`n")
}

$temporaryRoot = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) "knowledgebridge-purge-test-$([guid]::NewGuid().ToString('N'))"))
$testProject = "kb-purge-test-$([guid]::NewGuid().ToString('N').Substring(0,12))"
$testCompose = @()
try {
    $null = New-Item -ItemType Directory -Path $temporaryRoot
    Assert-Purge ((Resolve-PurgeDirectory 'data/uploads' $root) -eq (Join-Path $root 'backend/data/uploads')) 'relative storage follows backend working directory'
    foreach ($unsafe in @($root, (Split-Path -Parent $root), (Join-Path $root 'backend'), (Join-Path $root '.git/objects'), [IO.Path]::GetPathRoot($root))) {
        Assert-PurgeThrows { Resolve-PurgeDirectory $unsafe $root } 'Unsafe|cannot be inside'
    }
    $hostStorage = Join-Path $temporaryRoot 'uploads'
    $null = New-Item -ItemType Directory -Path (Join-Path $hostStorage 'audio-orphan') -Force
    [IO.File]::WriteAllText((Join-Path $hostStorage 'retained.mp4'), 'synthetic video')
    [IO.File]::WriteAllText((Join-Path $hostStorage 'audio-orphan/segment.wav'), 'synthetic audio')
    $entries = @(Get-PurgeFiles $hostStorage)
    Assert-Purge ($entries.Count -eq 3 -and [array]::IndexOf($entries, ($entries | Where-Object Name -eq 'segment.wav')) -lt [array]::IndexOf($entries, ($entries | Where-Object Name -eq 'audio-orphan'))) 'all files are collected and children precede directories'
    Assert-Purge (-not (Test-PurgeDescendant ($hostStorage + '-neighbor/file') $hostStorage)) 'prefix collisions do not count as descendants'
    $engineStorage = Join-Path $temporaryRoot 'engine'
    $null = New-Item -ItemType Directory -Path (Join-Path $engineStorage '.gbrain/persistence') -Force
    [IO.File]::WriteAllText((Join-Path $engineStorage '.gbrain/config.json'), '{"syntheticConfig":true}')
    [IO.File]::WriteAllText((Join-Path $engineStorage '.gbrain/persistence/host.json'), '{"syntheticHost":true}')
    [IO.File]::WriteAllText((Join-Path $engineStorage 'document.md'), '# Synthetic document')
    $storageScript = Join-Path $PSScriptRoot 'purge-storage.mjs'
    $preview = (& node $storageScript engine preview $engineStorage) | ConvertFrom-Json
    Assert-Purge ($LASTEXITCODE -eq 0 -and $preview.files -eq 1 -and $preview.preserved -eq 2) 'engine preview preserves config and writer identity'
    Assert-Purge (Test-Path -LiteralPath (Join-Path $engineStorage 'document.md')) 'preview does not remove content'
    $null = & node $storageScript engine apply $engineStorage
    Assert-Purge ($LASTEXITCODE -eq 0 -and -not (Test-Path -LiteralPath (Join-Path $engineStorage 'document.md'))) 'engine removes content'
    Assert-Purge (Test-Path -LiteralPath (Join-Path $engineStorage '.gbrain/config.json')) 'engine configuration survives'
    $null = & node $storageScript engine apply $engineStorage
    Assert-Purge ($LASTEXITCODE -eq 0) 'storage purge is repeatable'
    if ([Environment]::OSVersion.Platform -eq 'Win32NT') {
        $link = Join-Path $hostStorage 'escape'
        $null = New-Item -ItemType Junction -Path $link -Target $engineStorage
        try {
            Assert-PurgeThrows { Get-PurgeFiles $hostStorage } 'link|junction'
            Assert-PurgeThrows { Resolve-PurgeDirectory (Join-Path $link 'nested') $root } 'link|junction'
        } finally { [IO.Directory]::Delete($link) }
    }
    Write-Host 'Passed syntax, safe path boundaries, junction refusal, nested files, storage preview/preservation, and repeat-run checks.'
    if (-not $Integration) { return }

    # Use a completely separate Compose project with no published ports and disposable volumes.
    $image = Invoke-TestDocker @('image', 'inspect', 'knowledgebridge-gbrain', '--format', '{{.Id}}')
    $composePath = Join-Path $temporaryRoot 'compose.yaml'
    $initializer = (Join-Path $root 'docker/postgres/init-gbrain.sh').Replace('\','/')
    $yaml = @"
services:
  postgres:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: knowledgebridge
      POSTGRES_USER: knowledgebridge
      POSTGRES_PASSWORD: knowledgebridge
      GBRAIN_DB_PASSWORD: gbrain-test
    volumes:
      - database:/var/lib/postgresql/data
      - type: bind
        source: '$initializer'
        target: /docker-entrypoint-initdb.d/10-gbrain.sh
        read_only: true
    healthcheck:
      test: [CMD, pg_isready, -U, knowledgebridge]
      interval: 1s
      timeout: 3s
      retries: 40
  gbrain:
    image: $image
    environment:
      GBRAIN_HOME: /data
      GBRAIN_DATABASE_URL: postgres://gbrain:gbrain-test@postgres:5432/gbrain
      OPENAI_API_KEY: synthetic-not-a-provider-key
    volumes: [brain:/data]
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: [CMD, bun, -e, 'process.exit((await fetch("http://127.0.0.1:3131/health")).ok ? 0 : 1)']
      interval: 2s
      timeout: 3s
      retries: 60
      start_period: 30s
  backend:
    image: pgvector/pgvector:pg17
    entrypoint: [sleep, '3600']
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/knowledgebridge
    volumes: [uploads:/data/uploads]
  frontend:
    image: pgvector/pgvector:pg17
    entrypoint: [sleep, '3600']
volumes:
  database:
  brain:
  uploads:
"@
    [IO.File]::WriteAllText($composePath, $yaml.Replace("`r`n","`n"), [Text.UTF8Encoding]::new($false))
    $testCompose = @('compose', '-f', $composePath, '--project-name', $testProject)
    $null = Invoke-TestDocker ($testCompose + @('up', '-d', '--wait', 'postgres', 'gbrain', 'backend', 'frontend'))
    $appSql = 'CREATE TABLE flyway_schema_history (installed_rank integer PRIMARY KEY, version text); INSERT INTO flyway_schema_history VALUES (1,''13'');' + "`n"
    $migrations = Get-ChildItem (Join-Path $root 'backend/src/main/resources/db/migration') -Filter '*.sql' | Sort-Object { [int]([regex]::Match($_.Name,'^V(\d+)').Groups[1].Value) }
    foreach ($file in $migrations) { $appSql += [IO.File]::ReadAllText($file.FullName) + "`n" }
    $appSql += @'
INSERT INTO knowledge_item (title,type,owner_id,status,character_count,body_text,source_path,source_kind)
VALUES ('Synthetic retained video','Meeting',1,'FAILED',4,'test','/data/uploads/retained.mp4','VIDEO'),
       ('Synthetic note','Policy',2,'COMPLETED',4,'test',NULL,'MANUAL');
INSERT INTO ingestion_attempt (knowledge_item_id,attempt_number,status,started_at,completed_at) VALUES (1,1,'FAILED',now(),now());
INSERT INTO detected_entity (external_id,name,type,summary) VALUES ('synthetic-person','Test Person','PERSON','Synthetic summary');
INSERT INTO knowledge_item_entity VALUES (2,1);
INSERT INTO entity_extraction (knowledge_item_id,revision,status) VALUES (2,1,'COMPLETED');
INSERT INTO query_cache (exact_query_text,query_mode,knowledge_revision,response_json) VALUES ('Synthetic question','SYNTHESIS',0,'{}');
INSERT INTO query_usage (user_id,query_mode,cache_hit,input_tokens,output_tokens,estimated) VALUES (2,'SYNTHESIS',false,10,20,true);
'@
    $psql = @('exec','-T','postgres','psql','-XqAt','-v','ON_ERROR_STOP=1','-U','knowledgebridge')
    $null = Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge')) $appSql
    $engineSql = @'
BEGIN;
SET LOCAL gbrain.topology_change='on';
SET LOCAL gbrain.write_sources='["default","knowledgebridge"]';
SET LOCAL gbrain.persistence_protocol='2';
-- Fresh upstream initialization points the unused default source at its checkout.
-- Match this project's database-only layout rather than adopting a canonical Git source.
DELETE FROM persistence_effects;
DELETE FROM persistence_requests;
DELETE FROM persistence_worktree_refreshes;
DELETE FROM persistence_writer_protocols;
DELETE FROM persistence_host_bindings;
DELETE FROM persistence_source_bindings;
DELETE FROM persistence_worktrees;
UPDATE sources SET local_path=NULL WHERE id='default';
INSERT INTO sources (id,name,local_path) VALUES ('knowledgebridge','KnowledgeBridge','/data/sources/knowledgebridge');
INSERT INTO pages (source_id,slug,type,title,compiled_truth) VALUES ('knowledgebridge','knowledgebridge/test','note','Synthetic test','Synthetic document text'),
('knowledgebridge','people/test','person','Synthetic person','Derived person summary');
INSERT INTO content_chunks (page_id,chunk_index,chunk_text) SELECT id,0,compiled_truth FROM pages;
INSERT INTO page_versions (page_id,compiled_truth) SELECT id,'Earlier synthetic content' FROM pages;
INSERT INTO entity_identities (entity_id,source_id,page_id) SELECT 'synthetic','knowledgebridge',id FROM pages WHERE slug='people/test';
INSERT INTO mcp_request_log (operation,params) VALUES ('put_page','{"content":"Synthetic retained request"}');
INSERT INTO persistence_requests (principal_kind,principal_id,request_id,operation,source_id,source_incarnation,slug,digest,intent,authority,intent_bytes,terminal_reservation)
SELECT 'oauth_client','synthetic',gen_random_uuid(),'put_page','knowledgebridge',incarnation,'knowledgebridge/test','synthetic','{"body":"Synthetic queued text"}','{}',1,1 FROM sources WHERE id='knowledgebridge';
INSERT INTO persistence_effects (request_id,kind,data) SELECT id,'synthetic','{"body":"Synthetic recovery text"}' FROM persistence_requests;
COMMIT;
'@
    $null = Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain')) $engineSql
    $registration = Invoke-TestDocker ($testCompose + @('exec','-T','gbrain','bun','run','src/cli.ts','auth','register-client','purge-test',
        '--grant-types','client_credentials','--scopes','read write','--source','knowledgebridge','--federated-read','knowledgebridge'))
    $clientId = [regex]::Match($registration,'(?im)^\s*Client ID:\s*(\S+)').Groups[1].Value
    $clientSecret = [regex]::Match($registration,'(?im)^\s*Client Secret:\s*(\S+)').Groups[1].Value
    Assert-Purge ([bool]$clientId -and [bool]$clientSecret) 'test credential registration succeeds without exposing the secret'
    $null = Invoke-TestDocker ($testCompose + @('exec','-T','backend','sh','-c','mkdir -p /data/uploads/audio-orphan; echo synthetic > /data/uploads/retained.mp4; echo audio > /data/uploads/audio-orphan/segment.wav'))
    $null = Invoke-TestDocker ($testCompose + @('exec','-T','gbrain','sh','-c','mkdir -p /data/sources/knowledgebridge /data/.gbrain/audit; echo synthetic > /data/sources/knowledgebridge/orphan.md; echo synthetic > /data/.gbrain/audit/document.jsonl'))
    $accountFingerprintSql = "SELECT md5(json_agg(row_to_json(t) ORDER BY id)::text) FROM app_user t; SELECT knowledge_revision FROM system_state; SELECT last_value FROM knowledge_item_id_seq; SELECT count(*) FROM flyway_schema_history;"
    $accountsBefore = Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge','-c',$accountFingerprintSql))
    $oauthBefore = Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c',"SELECT md5(json_agg(row_to_json(t) ORDER BY client_id)::text) FROM oauth_clients t;"))
    $purgeArguments = @{ ComposeFile = @($composePath); ProjectName = $testProject; HostUploadDirectory = $hostStorage }
    & $purger @purgeArguments
    Assert-Purge ((Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge','-c','SELECT count(*) FROM knowledge_item'))) -eq '2') 'preview preserves database documents'
    Assert-Purge (Test-Path -LiteralPath (Join-Path $hostStorage 'retained.mp4')) 'preview preserves host uploads'
    & $purger @purgeArguments -Apply -WhatIf
    Assert-Purge ((Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c','SELECT count(*) FROM pages'))) -eq '2') 'WhatIf preserves engine documents'
    $null = Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c',"BEGIN; SET LOCAL gbrain.topology_change='on'; INSERT INTO sources (id,name) VALUES ('unrelated','Unrelated source'); COMMIT;"))
    Assert-PurgeThrows { & $purger @purgeArguments -Apply -Confirm:$false } 'dedicated|shared'
    $null = Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c',"BEGIN; SET LOCAL gbrain.topology_change='on'; DELETE FROM sources WHERE id='unrelated'; CREATE TABLE unexpected_document_metadata (body text); COMMIT;"))
    Assert-PurgeThrows { & $purger @purgeArguments -Apply -Confirm:$false } 'Unsupported gbrain schema'
    $null = Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c','DROP TABLE unexpected_document_metadata;'))
    $busyInfo = [Diagnostics.ProcessStartInfo]::new('docker')
    $busyInfo.UseShellExecute = $false
    $busyInfo.CreateNoWindow = $true
    $busyInfo.RedirectStandardOutput = $true
    $busyInfo.RedirectStandardError = $true
    foreach ($argument in ($testCompose + @('exec','-T','-e','PGAPPNAME=purge-refusal-test','postgres','psql','-U','knowledgebridge','-d','knowledgebridge','-c','SELECT pg_sleep(60)'))) { $busyInfo.ArgumentList.Add($argument) }
    $busy = [Diagnostics.Process]::Start($busyInfo)
    try {
        $deadline = (Get-Date).AddSeconds(15)
        do {
            $connected = Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge','-c',"SELECT count(*) FROM pg_stat_activity WHERE application_name='purge-refusal-test';"))
            if ($connected -ne '1') { Start-Sleep -Milliseconds 100 }
        } while ($connected -ne '1' -and (Get-Date) -lt $deadline)
        Assert-Purge ($connected -eq '1') 'isolated competing database client is connected'
        Assert-PurgeThrows { & $purger @purgeArguments -Apply -Confirm:$false } 'still connected'
        Assert-Purge ((Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge','-c','SELECT count(*) FROM knowledge_item'))) -eq '2') 'active-client refusal preserves application content'
        Assert-Purge ((Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c','SELECT count(*) FROM pages'))) -eq '2') 'active-client refusal preserves engine content'
    } finally {
        $null = Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge','-c',"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name='purge-refusal-test';"))
        $busy.WaitForExit(5000) | Out-Null
        $busy.Dispose()
    }
    & $purger @purgeArguments -Apply -Confirm:$false
    Assert-Purge (@(Get-PurgeFiles $hostStorage).Count -eq 0) 'host files and orphan audio are gone'
    $accountsAfter = Invoke-TestDocker ($testCompose + $psql + @('-d','knowledgebridge','-c',$accountFingerprintSql))
    $beforeLines = $accountsBefore -split "`n"
    $afterLines = $accountsAfter -split "`n"
    Assert-Purge ($beforeLines[0] -eq $afterLines[0] -and $beforeLines[2] -eq $afterLines[2] -and $beforeLines[3] -eq $afterLines[3]) 'account rows, migration history and document ID sequence survive'
    Assert-Purge ([long]$afterLines[1] -gt [long]$beforeLines[1]) 'knowledge revision increases to invalidate earlier answers'
    $oauthAfter = Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c',"SELECT md5(json_agg(row_to_json(t) ORDER BY client_id)::text) FROM oauth_clients t;"))
    Assert-Purge ($oauthBefore -eq $oauthAfter) 'OAuth client rows survive exactly'
    & $purger @purgeArguments -Apply -Confirm:$false
    $null = Invoke-TestDocker ($testCompose + @('up','-d','--wait','gbrain'))
    $tokenCheck = @'
const [id,secret] = process.argv.slice(1);
const response = await fetch('http://127.0.0.1:3131/token', {method:'POST', headers:{'content-type':'application/x-www-form-urlencoded'}, body:new URLSearchParams({grant_type:'client_credentials',client_id:id,client_secret:secret})});
if (!response.ok || !(await response.json()).access_token) process.exit(1);
console.log('credential-still-valid');
'@
    # Secret goes through stdin as data in the same isolated process; never put it in a command line or log.
    $tokenCheck = 'const [id,secret]=' + (@($clientId,$clientSecret) | ConvertTo-Json -Compress) + ";`n" + ($tokenCheck -replace 'const \[id,secret\] = process.argv.slice\(1\);','')
    $result = Invoke-TestDocker ($testCompose + @('exec','-T','gbrain','bun','-')) $tokenCheck
    Assert-Purge ($result -eq 'credential-still-valid') 'engine restarts and existing OAuth credentials still authenticate'
    Assert-Purge ((Invoke-TestDocker ($testCompose + $psql + @('-d','gbrain','-c','SELECT count(*) FROM pages'))) -eq '0') 'restart does not restore purged content'
    Write-Host 'Passed isolated PostgreSQL/gbrain integration: preview, WhatIf, schema/source refusal, complete purge, orphan storage cleanup, preserved identities, repeat run, engine restart and OAuth readback. No AI provider calls were made.'
} finally {
    if ($testCompose.Count) { $null = Invoke-TestDocker ($testCompose + @('down','--volumes','--remove-orphans')) }
    $safePrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar + 'knowledgebridge-purge-test-'
    if (-not $temporaryRoot.StartsWith($safePrefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test cleanup path.' }
    if (Test-Path -LiteralPath $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Recurse -Force }
}

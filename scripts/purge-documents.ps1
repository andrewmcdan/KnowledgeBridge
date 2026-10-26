<#
.SYNOPSIS
Previews or purges all KnowledgeBridge content in this repository's dedicated Compose deployment.
.DESCRIPTION
Default is a read-only preview. -Apply stops Compose backend/frontend/gbrain and leaves them stopped.
Stop host launchers/backends first. Accounts, credentials, configuration, migrations and ID sequences survive.
The purge is permanent and spans databases/filesystems; on failure keep services stopped and rerun after fixing it.
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [switch]$Apply,
    [string[]]$ComposeFile,
    [string]$ProjectName,
    [string]$HostUploadDirectory,
    [string[]]$AdditionalUploadDirectory = @()
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compose = @('compose', '--project-directory', $root)
if (-not $ComposeFile) { $ComposeFile = @(Join-Path $root 'compose.yaml') }
foreach ($file in $ComposeFile) { $compose += @('-f', (Resolve-Path -LiteralPath $file).Path) }
if ($ProjectName) { $compose += @('--project-name', $ProjectName) }

function Invoke-PurgeDocker {
    param([string[]]$Arguments, [string]$InputText, [string]$Operation, [switch]$SafeErrors)

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = if ($null -ne $InputText) { $InputText | & docker @Arguments 2>&1 } else { & docker @Arguments 2>&1 }
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) {
        $detail = if ($SafeErrors) { "`n$($output -join "`n")" } else { ' Output withheld because Docker configuration can contain credentials.' }
        throw "$Operation failed.$detail"
    }
    return ($output -join "`n")
}

function Test-PurgeDescendant {
    param([string]$Path, [string]$Parent)
    $comparison = if ([IO.Path]::DirectorySeparatorChar -eq '\') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    return $Path.StartsWith($Parent.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar, $comparison)
}

function Resolve-PurgeDirectory {
    param([string]$Directory, [string]$RepositoryRoot)

    if (-not [IO.Path]::IsPathRooted($Directory)) { $Directory = Join-Path (Join-Path $RepositoryRoot 'backend') $Directory }
    $fullPath = [IO.Path]::GetFullPath($Directory)
    if ($fullPath -eq [IO.Path]::GetPathRoot($fullPath)) { throw "Unsafe upload directory: $fullPath" }
    $absolute = $fullPath.TrimEnd([IO.Path]::DirectorySeparatorChar)
    $forbidden = @([IO.Path]::GetPathRoot($absolute).TrimEnd([IO.Path]::DirectorySeparatorChar), $RepositoryRoot,
        (Join-Path $RepositoryRoot 'backend'), (Join-Path $RepositoryRoot 'frontend'), (Join-Path $RepositoryRoot '.git'),
        (Join-Path $RepositoryRoot 'scripts'), (Join-Path $RepositoryRoot 'docker'), (Join-Path $RepositoryRoot 'Docs'))
    foreach ($special in @('UserProfile', 'Windows', 'ProgramFiles', 'CommonApplicationData')) {
        $value = [Environment]::GetFolderPath($special)
        if ($value) { $forbidden += $value }
    }
    foreach ($protected in $forbidden) {
        if ($absolute -eq $protected -or (Test-PurgeDescendant $protected $absolute)) { throw "Unsafe upload directory: $absolute" }
    }
    foreach ($name in @('frontend', '.git', 'scripts', 'docker', 'Docs')) {
        if (Test-PurgeDescendant $absolute (Join-Path $RepositoryRoot $name)) { throw "Upload storage cannot be inside $name." }
    }
    # Check ancestors too: a junction in an otherwise plausible path can redirect deletion elsewhere.
    $ancestor = $absolute
    while ($ancestor) {
        if (Test-Path -LiteralPath $ancestor) {
            $item = Get-Item -LiteralPath $ancestor -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Upload directory uses a link or junction: $ancestor" }
        }
        $parent = Split-Path -Parent $ancestor
        if ($parent -eq $ancestor) { break }
        $ancestor = $parent
    }
    return $absolute
}

function Get-PurgeFiles {
    param([string]$Directory)

    if (-not (Test-Path -LiteralPath $Directory)) { return }
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) { throw "Upload storage is not a directory: $Directory" }
    foreach ($entry in Get-ChildItem -LiteralPath $Directory -Force) {
        $path = [IO.Path]::GetFullPath($entry.FullName)
        if (-not (Test-PurgeDescendant $path $Directory) -or ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Storage contains a link, junction or escaping path: $path"
        }
        if ($entry.PSIsContainer) { Get-PurgeFiles $path }
        $entry
    }
}

function Invoke-PurgeDatabase {
    param([string]$Database, [string]$File, [bool]$Write)
    $sql = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $File))
    $arguments = $compose + @('exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-U', $databaseUser, '-d', $Database,
        '-v', 'ON_ERROR_STOP=1', '-v', "apply=$($Write.ToString().ToLowerInvariant())")
    return (Invoke-PurgeDocker -Arguments $arguments -InputText $sql -Operation "Database $Database check/purge" -SafeErrors | ConvertFrom-Json)
}

function Invoke-PurgeVolume {
    param([string]$Volume, [string]$Kind, [bool]$Write)
    if (-not $Volume) { return [pscustomobject]@{ files = 0; bytes = 0; preserved = 0 } }
    $mount = "type=volume,source=$Volume,target=/purge"
    if (-not $Write) { $mount += ',readonly' }
    $action = if ($Write) { 'apply' } else { 'preview' }
    $code = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'purge-storage.mjs'))
    return (Invoke-PurgeDocker -Arguments @('run', '--rm', '-i', '--network', 'none', '--mount', $mount,
        '--entrypoint', 'bun', $engineImage, '-', $Kind, $action) -InputText $code -Operation "$Kind volume $action" -SafeErrors | ConvertFrom-Json)
}

function Get-PurgeVolumeName {
    param($Service, [string]$Target, [bool]$Required)
    $mounts = @($Service.volumes | Where-Object { $_.target -eq $Target })
    if ($mounts.Count -ne 1 -or $mounts[0].type -ne 'volume') { throw "Expected one named volume at $Target. Bind mounts require a separate reviewed cleanup." }
    $name = $configuration.volumes.($mounts[0].source).name
    if (-not $name) { throw "Cannot resolve volume at $Target." }
    $exists = & docker volume ls --format '{{.Name}}' --filter "name=^$([regex]::Escape($name))$" 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Docker volumes.' }
    if ($exists -contains $name) {
        $volumeInfo = (Invoke-PurgeDocker -Arguments @('volume', 'inspect', $name) -Operation 'Volume inspection' | ConvertFrom-Json)[0]
        if ($volumeInfo.Labels.'com.docker.compose.project' -ne $configuration.name) { throw 'Storage volume is not owned by the selected Compose project.' }
        return $name
    }
    if ($Required) { throw "Required engine volume does not exist: $name" }
    return $null
}

Push-Location $root
try {
    $configuration = Invoke-PurgeDocker -Arguments ($compose + @('config', '--format', 'json')) -Operation 'Compose configuration' | ConvertFrom-Json
    $resolved = Invoke-PurgeDocker -Arguments ($compose + @('config', '--environment')) -Operation 'Compose environment'
    $environment = @{}
    foreach ($line in $resolved -split "`n") {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $environment[$Matches[1]] = $Matches[2].TrimEnd("`r") }
    }
    $database = [string]$configuration.services.postgres.environment.POSTGRES_DB
    $databaseUser = [string]$configuration.services.postgres.environment.POSTGRES_USER
    if (-not $database -or -not $databaseUser -or $database -eq 'gbrain') { throw 'Expected distinct application and gbrain databases.' }
    if ($configuration.services.backend.environment.SPRING_DATASOURCE_URL -ne "jdbc:postgresql://postgres:5432/$database") {
        throw 'Backend targets a different database; this script supports the dedicated Compose PostgreSQL deployment only.'
    }
    try { $engineUrl = [uri]$configuration.services.gbrain.environment.GBRAIN_DATABASE_URL }
    catch { throw 'Invalid gbrain database URL. Check Compose/environment settings; credential values are withheld.' }
    if ($engineUrl.Host -ne 'postgres' -or $engineUrl.AbsolutePath -ne '/gbrain' -or $engineUrl.Port -ne 5432) {
        throw 'gbrain targets a different database; refusing a partial purge.'
    }
    $postgresId = Invoke-PurgeDocker -Arguments ($compose + @('ps', '-q', 'postgres')) -Operation 'PostgreSQL status'
    if (-not $postgresId) { throw 'Start the selected Compose PostgreSQL service first; the purge does not create databases or volumes.' }
    $postgresInfo = (Invoke-PurgeDocker -Arguments @('inspect', $postgresId) -Operation 'PostgreSQL inspection' | ConvertFrom-Json)[0]
    if ($postgresInfo.Config.Env -notcontains "POSTGRES_DB=$database" -or $postgresInfo.Config.Env -notcontains "POSTGRES_USER=$databaseUser") {
        throw 'Running PostgreSQL configuration differs from Compose. Resolve that mismatch before purging.'
    }
    $gbrainId = Invoke-PurgeDocker -Arguments ($compose + @('ps', '-aq', 'gbrain')) -Operation 'gbrain status'
    if (-not $gbrainId) { throw 'The selected gbrain container must already exist so its exact image can be verified.' }
    $engineInfo = (Invoke-PurgeDocker -Arguments @('inspect', $gbrainId) -Operation 'gbrain inspection' | ConvertFrom-Json)[0]
    $engineImage = $engineInfo.Image
    if ($configuration.services.gbrain.environment.GBRAIN_HOME -ne '/data' -or $engineInfo.Config.Env -notcontains 'GBRAIN_HOME=/data') {
        throw 'Expected engine home /data in the selected volume.'
    }
    if ($engineInfo.Config.Env -notcontains "GBRAIN_DATABASE_URL=$($engineUrl.OriginalString)") { throw 'Running gbrain database target differs from Compose.' }
    $engineVolume = Get-PurgeVolumeName $configuration.services.gbrain '/data' $true
    $uploadVolume = Get-PurgeVolumeName $configuration.services.backend '/data/uploads' $false
    if (@($engineInfo.Mounts | Where-Object { $_.Destination -eq '/data' -and $_.Name -eq $engineVolume }).Count -ne 1) {
        throw 'gbrain container storage differs from Compose.'
    }
    if (@($engineInfo.Mounts | Where-Object { $_.Destination.StartsWith('/data/') }).Count) { throw 'Nested engine mounts require separate cleanup.' }
    $backendId = Invoke-PurgeDocker -Arguments ($compose + @('ps', '-aq', 'backend')) -Operation 'Backend status'
    if ($backendId) {
        $backendInfo = (Invoke-PurgeDocker -Arguments @('inspect', $backendId) -Operation 'Backend inspection' | ConvertFrom-Json)[0]
        if ($backendInfo.Config.Env -notcontains "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$database" -or
            @($backendInfo.Mounts | Where-Object { $_.Destination -eq '/data/uploads' -and $_.Name -eq $uploadVolume }).Count -ne 1 -or
            @($backendInfo.Mounts | Where-Object { $_.Destination.StartsWith('/data/uploads/') }).Count) {
            throw 'Backend database/storage differs from Compose or contains nested mounts.'
        }
    }
    if (-not $HostUploadDirectory) {
        $HostUploadDirectory = if ($environment['KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY']) { $environment['KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY'] } else { 'data/uploads' }
    }
    $directories = @(@($HostUploadDirectory) + $AdditionalUploadDirectory | ForEach-Object { Resolve-PurgeDirectory $_ $root } | Select-Object -Unique)
    $appBefore = Invoke-PurgeDatabase $database 'purge-documents.sql' $false
    $engineBefore = Invoke-PurgeDatabase 'gbrain' 'purge-gbrain.sql' $false
    foreach ($source in $appBefore.sourcePaths) {
        if ($source.StartsWith('/data/uploads/', [StringComparison]::Ordinal)) {
            if ($source -match '(^|/)\.\.(/|$)') { throw 'Retained upload path contains a parent-directory traversal.' }
            if (-not $uploadVolume) { throw 'A retained Docker upload references a missing volume.' }
        } else {
            $path = if ([IO.Path]::IsPathRooted($source)) { [IO.Path]::GetFullPath($source) } else { [IO.Path]::GetFullPath((Join-Path (Join-Path $root 'backend') $source)) }
            if (-not @($directories | Where-Object { Test-PurgeDescendant $path $_ }).Count) {
                throw 'A retained upload is outside the selected storage roots. Include its dedicated folder with -AdditionalUploadDirectory.'
            }
        }
    }
    $hostEntries = @($directories | ForEach-Object { Get-PurgeFiles $_ })
    $uploadBefore = Invoke-PurgeVolume $uploadVolume 'uploads' $false
    $engineFilesBefore = Invoke-PurgeVolume $engineVolume 'engine' $false
    Write-Host "Project: $($configuration.name); application database: $database; engine database: gbrain"
    Write-Host "Content rows: $($appBefore.documents) documents, $($appBefore.entities) entities, $($appBefore.cachedAnswers) cached answers, $($appBefore.usageRecords) usage records."
    Write-Host "Engine: $($engineBefore.pages) pages, $($engineBefore.chunks) chunks, $($engineBefore.versions) versions, $($engineBefore.writeReceipts) write receipts."
    foreach ($directory in $directories) { Write-Host "Host upload storage: $directory" }
    Write-Host "Volumes: uploads=$uploadVolume; engine=$engineVolume"
    Write-Host "Files to remove: $(@($hostEntries | Where-Object { -not $_.PSIsContainer }).Count) host, $($uploadBefore.files) upload volume, $($engineFilesBefore.files) engine volume."
    Write-Host "Preserving $($appBefore.accounts) application accounts, $($engineBefore.oauthClients) OAuth clients, configuration, migration history and ID sequences."
    if (-not $Apply) { Write-Host 'Preview only. Stop host launchers/backends, then use -Apply to purge. Nothing was deleted.'; return }
    if (-not $PSCmdlet.ShouldProcess("Compose project $($configuration.name), databases $database/gbrain and listed storage", 'Permanently purge ALL workspace content, metadata, caches and usage; stop application services')) { return }
    $null = Invoke-PurgeDocker -Arguments ($compose + @('stop', 'backend', 'frontend', 'gbrain')) -Operation 'Stopping application services'
    # Revalidate after writers stop, before any deletion. Other sessions make the SQL checks fail closed.
    $null = Invoke-PurgeDatabase $database 'purge-documents.sql' $false
    $null = Invoke-PurgeDatabase 'gbrain' 'purge-gbrain.sql' $false
    foreach ($name in @($database, 'gbrain')) {
        $connections = Invoke-PurgeDocker -Arguments ($compose + @('exec', '-T', 'postgres', 'psql', '-XqAt', '-U', $databaseUser, '-d', $name, '-c',
            "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND backend_type='client backend'")) -Operation 'Offline check'
        if ([long]$connections -ne 0) { throw 'Database clients are still connected. Stop host launchers/backends, workers and database clients, then rerun. Services remain stopped.' }
    }
    # Each database operation is transactional. Cross-database/filesystem cleanup is deliberately rerunnable.
    $null = Invoke-PurgeDatabase 'gbrain' 'purge-gbrain.sql' $true
    $null = Invoke-PurgeVolume $engineVolume 'engine' $true
    $null = Invoke-PurgeVolume $uploadVolume 'uploads' $true
    foreach ($directory in $directories) {
        $directory = Resolve-PurgeDirectory $directory $root
        $entries = @(Get-PurgeFiles $directory) # Validate the entire tree before removing any entry.
        foreach ($entry in $entries) {
            if (-not (Test-PurgeDescendant ([IO.Path]::GetFullPath($entry.FullName)) $directory)) { throw 'Storage path escaped its validated root.' }
            if ($entry.PSIsContainer) { [IO.Directory]::Delete($entry.FullName, $false) }
            else { Remove-Item -LiteralPath $entry.FullName -Force }
        }
    }
    $appAfter = Invoke-PurgeDatabase $database 'purge-documents.sql' $true
    $engineAfter = Invoke-PurgeDatabase 'gbrain' 'purge-gbrain.sql' $false
    if ($appAfter.documents -ne 0 -or $engineAfter.pages -ne 0 -or $appAfter.accounts -ne $appBefore.accounts -or $engineAfter.oauthClients -ne $engineBefore.oauthClients) {
        throw 'Post-purge verification failed. Keep services stopped and investigate.'
    }
    foreach ($directory in $directories) { if (@(Get-PurgeFiles $directory).Count) { throw 'Host upload storage is not empty.' } }
    if ((Invoke-PurgeVolume $uploadVolume 'uploads' $false).files -ne 0 -or (Invoke-PurgeVolume $engineVolume 'engine' $false).files -ne 0) { throw 'Volume cleanup verification failed.' }
    Write-Host 'Purge verified: no workspace documents, entity data, engine content, cached answers, usage records or upload files remain.'
    Write-Host 'Backend/frontend/gbrain remain stopped. Restart your usual Compose stack or scripts/start-dev.ps1 when ready.'
} finally { Pop-Location }

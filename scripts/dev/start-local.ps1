# Start the initialized local services without modifying database contents.
param([switch]$Demo)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $PSScriptRoot 'local-environment.ps1')
$values = Import-LocalEnvironment $repository
$backendPort = Get-LocalBackendPort $repository
$logs = Join-Path $repository 'runtime/local-logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
function Test-LocalPort([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}
if (-not $Demo) {
    if (-not (Test-Path (Join-Path $repository '.env'))) { throw 'Configure the root .env first.' }
    $mysqlIni = Join-Path $env:LOCALAPPDATA 'SimRecrut/RuoYiMySQL/my.ini'
    $mysqlExecutable = Join-Path $env:LOCALAPPDATA 'Programs/SimRecrut/mysql-8.4.9-winx64/bin/mysqld.exe'
    # Cloud deployments do not need the optional local MySQL instance.
    if ($values['DB_URL'] -like 'jdbc:mysql://127.0.0.1:3307/*' -and -not (Test-LocalPort 3307)) {
        if (-not (Test-Path $mysqlIni)) { throw 'The local database is not initialized.' }
        Start-Process -FilePath $mysqlExecutable -ArgumentList "--defaults-file=$mysqlIni" -WindowStyle Hidden
    }
    if (-not (Test-LocalPort 6379)) {
        $redis = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Programs/SimRecrut/redis-local') -Recurse -Filter redis-server.exe | Select-Object -First 1
        if (-not $redis) { throw 'Install the local Redis runtime first.' }
        Start-Process -FilePath $redis.FullName -ArgumentList @('--bind', '127.0.0.1', '--port', '6379', '--protected-mode', 'yes', '--save', '""', '--appendonly', 'no') -WorkingDirectory $redis.DirectoryName -WindowStyle Hidden -RedirectStandardOutput "$logs/redis.log" -RedirectStandardError "$logs/redis-error.log"
    }
    if (-not (Test-LocalPort $backendPort)) {
        Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$repository/scripts/dev/java.ps1`"", '-Action', 'run') -WorkingDirectory $repository -WindowStyle Hidden -RedirectStandardOutput "$logs/backend.log" -RedirectStandardError "$logs/backend-error.log"
    }
}
# Vite reloads this ignored override when switching runtime modes.
$mode = if ($Demo) { 'true' } else { 'false' }
[IO.File]::WriteAllText((Join-Path $repository 'frontend/.env.development.local'), "VITE_APP_DEMO_MODE=$mode`n")
if (-not (Test-LocalPort 5173)) {
    $node = (Get-Command node -ErrorAction SilentlyContinue).Source
    if (-not $node) { $node = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' }
    Start-Process -FilePath $node -ArgumentList @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5173', '--strictPort') -WorkingDirectory "$repository/frontend" -WindowStyle Hidden -RedirectStandardOutput "$logs/frontend.log" -RedirectStandardError "$logs/frontend-error.log"
}
Write-Host 'Frontend: http://127.0.0.1:5173/login'
if (-not $Demo) { Write-Host "Backend readiness: http://127.0.0.1:$backendPort/actuator/health/readiness" }
Write-Host "Startup logs: $logs"

# Run all backend checks with the installed local Redis and OCR dependencies.
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$models = Join-Path $repository 'runtime/ocr/tessdata'
$ocrExecutable = [Environment]::GetEnvironmentVariable('TESSERACT_EXECUTABLE', 'User')
if ([string]::IsNullOrWhiteSpace($ocrExecutable)) { $ocrExecutable = 'C:/Program Files/Tesseract-OCR/tesseract.exe' }
if (-not (Test-Path -LiteralPath $ocrExecutable)) { throw 'Install Tesseract and set TESSERACT_EXECUTABLE first.' }
foreach ($language in @('eng', 'fra', 'chi_sim')) {
    if (-not (Test-Path -LiteralPath (Join-Path $models "$language.traineddata"))) {
        throw "Missing local OCR model: $language. Install it in runtime/ocr/tessdata."
    }
}
$redisDirectory = Join-Path $env:LOCALAPPDATA 'Programs/SimRecrut/redis-local'
$redisExecutable = Get-ChildItem -LiteralPath $redisDirectory -Recurse -Filter redis-server.exe | Select-Object -First 1
if (-not $redisExecutable) { throw 'Install the local Redis runtime first.' }
$redisCli = Join-Path $redisExecutable.DirectoryName 'redis-cli.exe'
$listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 6379 -State Listen -ErrorAction SilentlyContinue
if (-not $listener) {
    $runtime = Join-Path $repository '.local/test-runtime'
    New-Item -ItemType Directory -Force $runtime | Out-Null
    $configuration = Join-Path $runtime 'redis.conf'
    $redisPath = $runtime.Replace('\', '/')
    [IO.File]::WriteAllText($configuration, "bind 127.0.0.1`nport 6379`nprotected-mode yes`nsave `"`"`nappendonly no`ndir `"$redisPath`"`n")
    Start-Process -FilePath $redisExecutable.FullName -ArgumentList @("`"$configuration`"") -WorkingDirectory $runtime -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'redis.log') -RedirectStandardError (Join-Path $runtime 'redis-error.log')
}
$ready = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $ping = & $redisCli -h 127.0.0.1 -p 6379 ping 2>$null
    if ($LASTEXITCODE -eq 0 -and $ping -eq 'PONG') { $ready = $true; break }
    Start-Sleep -Milliseconds 200
}
if (-not $ready) { throw 'The local Redis test endpoint did not respond to PING.' }
$environmentBefore = @{}
foreach ($key in @('SIMRECRUT_TEST_REDIS', 'TESSDATA_PATH', 'TESSERACT_EXECUTABLE')) {
    $environmentBefore[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
    $env:SIMRECRUT_TEST_REDIS = 'true'
    $env:TESSDATA_PATH = $models
    $env:TESSERACT_EXECUTABLE = $ocrExecutable
    Write-Host 'Running backend checks with local Redis and English/French/Chinese OCR models.'
    Push-Location (Join-Path $repository 'backend/java')
    try { & './mvnw.cmd' test; $testExitCode = $LASTEXITCODE }
    finally { Pop-Location }
} finally {
    foreach ($key in $environmentBefore.Keys) {
        [Environment]::SetEnvironmentVariable($key, $environmentBefore[$key], 'Process')
    }
}
exit $testExitCode

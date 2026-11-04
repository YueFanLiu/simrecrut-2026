# Start existing services and open the login page only after both HTTP endpoints respond.
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
try {
    & (Join-Path $PSScriptRoot 'start-local.ps1')
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    Write-Host 'Waiting for the frontend and backend (up to three minutes)...'
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $frontend = Invoke-WebRequest 'http://127.0.0.1:5173/login' -UseBasicParsing -TimeoutSec 3
            $backend = Invoke-WebRequest 'http://127.0.0.1:8080/captchaImage' -UseBasicParsing -TimeoutSec 3
            $payload = $backend.Content | ConvertFrom-Json
            if ($frontend.StatusCode -eq 200 -and $payload.code -eq 200) {
                $ready = $true
                break
            }
        } catch {
            # Services may still be compiling or connecting to the database.
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "Services did not become ready. Read $repository/runtime/local-logs/ruoyi-error.log and frontend-error.log."
    }
    Start-Process 'http://127.0.0.1:5173/login'
    Write-Host 'Ready: http://127.0.0.1:5173/login'
    exit 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

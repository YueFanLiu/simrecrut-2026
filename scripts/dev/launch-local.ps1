# Start existing services and open the login page only after both HTTP endpoints respond.
param([ValidateRange(1, 600)][int]$TimeoutSeconds = 180, [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $PSScriptRoot 'local-environment.ps1')
try {
    & (Join-Path $PSScriptRoot 'start-local.ps1')
    $backendPort = Get-LocalBackendPort $repository
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    Write-Host "Waiting for frontend and backend readiness (up to $TimeoutSeconds seconds)..."
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-LocalStackReady 'http://127.0.0.1:5173' "http://127.0.0.1:$backendPort") { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "Services are not ready. Check runtime/local-logs/backend-error.log and frontend-error.log. Backend port: $backendPort."
    }
    if (-not $NoBrowser) { Start-Process 'http://127.0.0.1:5173/login' -WindowStyle Hidden }
    Write-Host 'Ready: http://127.0.0.1:5173/login'
    exit 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

# Shared, literal-only configuration and readiness checks for local commands.
function Read-LocalEnvironment([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $values }
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $assignment = $line.Trim()
        if (-not $assignment -or $assignment.StartsWith('#')) { continue }
        if ($assignment -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw 'Invalid .env assignment. Use one literal NAME=value per line.' }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    return $values
}
function Import-LocalEnvironment([string]$Repository) {
    $values = Read-LocalEnvironment (Join-Path $Repository '.env')
    foreach ($key in $values.Keys) { [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process') }
    foreach ($key in @('TESSDATA_PATH', 'TESSERACT_EXECUTABLE')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))) {
            $userValue = [Environment]::GetEnvironmentVariable($key, 'User')
            if ($userValue) { [Environment]::SetEnvironmentVariable($key, $userValue, 'Process') }
        }
    }
    return $values
}
function Get-LocalBackendPort([string]$Repository) {
    $values = Read-LocalEnvironment (Join-Path $Repository '.env')
    $value = if ($values.ContainsKey('JAVA_PORT')) { $values['JAVA_PORT'] } elseif ($env:JAVA_PORT) { $env:JAVA_PORT } else { '8081' }
    $port = 0
    if (-not [int]::TryParse($value, [ref]$port) -or $port -lt 1 -or $port -gt 65535) { throw 'JAVA_PORT must be an integer between 1 and 65535.' }
    return $port
}
function Test-LocalStackReady([string]$FrontendUrl, [string]$BackendUrl) {
    try {
        $frontend = Invoke-WebRequest -Uri "$FrontendUrl/login" -UseBasicParsing -TimeoutSec 3
        $backend = Invoke-WebRequest -Uri "$BackendUrl/actuator/health/readiness" -UseBasicParsing -TimeoutSec 3
        $health = $backend.Content | ConvertFrom-Json
        return $frontend.StatusCode -eq 200 -and $backend.StatusCode -eq 200 -and $health.status -eq 'UP'
    } catch { return $false }
}

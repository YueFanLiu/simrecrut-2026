$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-environment.ps1')
function Assert-Condition([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('simrecrut-env-test-' + [guid]::NewGuid().ToString('N'))
$previousPort = $env:JAVA_PORT
New-Item -ItemType Directory -Path $fixture | Out-Null
$file = Join-Path $fixture '.env'
try {
    $env:JAVA_PORT = $null
    Assert-Condition ((Get-LocalBackendPort $fixture) -eq 8081) 'Missing configuration must use the Java default.'
    $env:JAVA_PORT = '9011'
    Assert-Condition ((Get-LocalBackendPort $fixture) -eq 9011) 'Process port must be respected.'
    [IO.File]::WriteAllText($file, "# comment`nJAVA_PORT='9123'`nVALUE='`$(throw unsafe)'`n")
    $values = Read-LocalEnvironment $file
    Assert-Condition ((Get-LocalBackendPort $fixture) -eq 9123) 'Root .env must override the process port.'
    Assert-Condition ($values['VALUE'] -eq '$(throw unsafe)') 'Environment values must never be evaluated.'
    foreach ($invalid in @('0', '65536', '8080;exit', '')) {
        [IO.File]::WriteAllText($file, "JAVA_PORT=$invalid`n")
        $rejected = $false
        try { $null = Get-LocalBackendPort $fixture } catch { $rejected = $true }
        Assert-Condition $rejected 'Invalid ports must fail before services launch.'
    }
    $script:health = 'UP'
    function Invoke-WebRequest {
        param([string]$Uri, [switch]$UseBasicParsing, [int]$TimeoutSec)
        if ($Uri -eq 'http://frontend/login') { return @{ StatusCode = 200 } }
        Assert-Condition ($Uri -eq 'http://backend/actuator/health/readiness') 'The current readiness endpoint must be used.'
        return @{ StatusCode = 200; Content = '{"status":"' + $script:health + '"}' }
    }
    Assert-Condition (Test-LocalStackReady 'http://frontend' 'http://backend') 'UP must be ready.'
    $script:health = 'DOWN'
    Assert-Condition (-not (Test-LocalStackReady 'http://frontend' 'http://backend')) 'DOWN must not be ready even with HTTP 200.'
    $script:health = 'invalid"json'
    Assert-Condition (-not (Test-LocalStackReady 'http://frontend' 'http://backend')) 'Malformed readiness data must not be accepted.'
    Write-Host 'Local environment and readiness regression checks passed.'
} finally {
    $env:JAVA_PORT = $previousPort
    if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    Remove-Item -LiteralPath $fixture
}

param(
    [ValidateSet('verify', 'package', 'run')]
    [string]$Action = 'verify'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$localEnvPath = Join-Path $projectRoot '.env'

if (Test-Path -LiteralPath $localEnvPath) {
    foreach ($envLine in [System.IO.File]::ReadAllLines($localEnvPath)) {
        $envAssignment = $envLine.Trim()
        if ($envAssignment.Length -eq 0 -or $envAssignment.StartsWith('#')) {
            continue
        }
        if ($envAssignment -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            throw 'Invalid .env assignment. Use one literal NAME=value per line.'
        }
        $variableName = $Matches[1]
        $variableValue = $Matches[2].Trim()
        if ($variableValue.Length -ge 2) {
            $firstCharacter = $variableValue[0]
            $lastCharacter = $variableValue[$variableValue.Length - 1]
            if (($firstCharacter -eq '"' -and $lastCharacter -eq '"') -or
                ($firstCharacter -eq "'" -and $lastCharacter -eq "'")) {
                $variableValue = $variableValue.Substring(1, $variableValue.Length - 2)
            }
        }
        # Literal parsing keeps credentials out of shell evaluation and output.
        [Environment]::SetEnvironmentVariable($variableName, $variableValue, 'Process')
    }
}

$selectedJdk = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
if ([string]::IsNullOrWhiteSpace($selectedJdk)) {
    throw 'Set JAVA_HOME to an installed JDK 17 in the root .env or environment.'
}
$javaExecutable = Join-Path $selectedJdk 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw 'JAVA_HOME does not contain bin/java.exe. Select an installed JDK 17.'
}

# Windows PowerShell treats native stderr as an error even for java -version.
$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$versionOutput = (& $javaExecutable -version 2>&1 | Out-String)
$versionExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorPreference
if ($versionExitCode -ne 0 -or $versionOutput -notmatch 'version "17(?:[.\"]|$)') {
    throw 'This project requires JDK 17. Update JAVA_HOME before continuing.'
}

$env:PATH = (Join-Path $selectedJdk 'bin') + [System.IO.Path]::PathSeparator + $env:PATH
$mavenWrapper = Join-Path $projectRoot 'backend/java/mvnw.cmd'
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw 'The Maven Wrapper is missing from backend/java.'
}
$mavenGoal = switch ($Action) {
    'verify' { 'verify' }
    'package' { 'package' }
    'run' { 'spring-boot:run' }
}

Push-Location -LiteralPath $projectRoot
try {
    $mavenArguments = @('-f', 'backend/java/pom.xml', $mavenGoal)
    if ($Action -eq 'run') {
        # The Boot plugin defaults to the module directory rather than the repository root.
        $mavenArguments += "-Dspring-boot.run.workingDirectory=$projectRoot"
    }
    & $mavenWrapper @mavenArguments
    $buildExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $buildExitCode

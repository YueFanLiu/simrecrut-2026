param(
    [ValidateSet('verify', 'package', 'run')]
    [string]$Action = 'verify'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $PSScriptRoot 'local-environment.ps1')
$null = Import-LocalEnvironment $projectRoot

# Resolve redirected Windows desktops, including OneDrive, without exposing mail credentials.
if ([string]::IsNullOrWhiteSpace($env:SIMRECRUT_MAIL_FILE)) {
    $desktopDirectory = [Environment]::GetFolderPath('DesktopDirectory')
    if (-not [string]::IsNullOrWhiteSpace($desktopDirectory)) {
        $env:SIMRECRUT_MAIL_FILE = Join-Path $desktopDirectory 'simrecrut-gmail.private.env'
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

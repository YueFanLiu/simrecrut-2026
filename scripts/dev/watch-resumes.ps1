param(
    [string]$InputDirectory,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $InputDirectory) { $InputDirectory = Join-Path $repository 'offline-ml/datasets/raw' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repository 'offline-ml/datasets/clean' }
Push-Location (Join-Path $repository 'backend/java')
try {
    & ./mvnw.cmd compile dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt' -q
    if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
    $classpath = 'target/classes;' + (Get-Content target/classpath.txt -Raw).Trim()
    & "$env:JAVA_HOME/bin/java.exe" -cp $classpath fr.isep.simrecrut.integration.pdf.ResumeDatasetWatcher $InputDirectory $OutputDirectory
} finally { Pop-Location }

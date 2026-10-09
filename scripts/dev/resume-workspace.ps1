param([string]$DataDirectory)
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $DataDirectory) { $DataDirectory = Join-Path $repository 'runtime/resume-workspace' }
$env:SIMRECRUT_DATA_DIR = $DataDirectory
Push-Location (Join-Path $repository 'backend/java')
try {
    & ./mvnw.cmd compile dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt' -q
    if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
    # Serve frontend-owned assets; do not maintain a second source copy in Java resources.
    $assets = Join-Path $repository 'frontend/public/resume-workspace'
    New-Item -ItemType Directory -Force target/classes/static | Out-Null
    Copy-Item (Join-Path $assets '*') target/classes/static -Force
    $classpath = 'target/classes;' + (Get-Content target/classpath.txt -Raw).Trim()
    & "$env:JAVA_HOME/bin/java.exe" -cp $classpath fr.isep.simrecrut.workspace.WorkspaceLauncher '--spring.config.name=workspace'
} finally { Pop-Location }

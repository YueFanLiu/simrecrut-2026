# Install pinned official modules without replacing project sources.
param([string]$CacheDirectory = "$env:LOCALAPPDATA/SimRecrut/ruoyi-source", [string]$Maven = "mvn")
$ErrorActionPreference = 'Stop'
$revision = 'a51a838b71b446ea27256900efe7ed2faa2a02fd'
if (-not (Test-Path -LiteralPath "$CacheDirectory/.git")) {
    git clone --no-checkout https://github.com/yangzongzhuan/RuoYi-Vue.git $CacheDirectory
    if ($LASTEXITCODE) { throw 'RuoYi download failed.' }
}
git -C $CacheDirectory checkout --detach $revision
if ($LASTEXITCODE) { throw 'Pinned revision checkout failed.' }
& $Maven -f "$CacheDirectory/pom.xml" --batch-mode -pl ruoyi-admin -am install '-DskipTests' '-Dspring-boot.repackage.skip=true'
if ($LASTEXITCODE) { throw 'RuoYi dependency build failed.' }
Write-Host "RuoYi installed. Database initialization sources: $CacheDirectory/sql"

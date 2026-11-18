param(
    [string]$BaseUrl = 'http://localhost:8081',
    [ValidatePattern('^[A-Za-z0-9_-]*$')]
    [string]$Group = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $fileName = if ($Group) { "openapi-$Group.yaml" } else { 'openapi.yaml' }
    $OutputPath = Join-Path $repositoryRoot "contracts/api/$fileName"
}
$destination = [IO.Path]::GetFullPath($OutputPath)
$server = [Uri]$BaseUrl
if (-not $server.IsAbsoluteUri -or $server.Scheme -notin @('http', 'https') -or
    $server.UserInfo -or $server.Query -or $server.Fragment) {
    throw 'BaseUrl must be an absolute HTTP(S) backend URL without credentials, query or fragment.'
}
$endpoint = $BaseUrl.TrimEnd('/') + '/v3/api-docs.yaml'
if ($Group) { $endpoint += '/' + [Uri]::EscapeDataString($Group) }

# This downloads documentation only. It never invokes a business mutation.
$response = Invoke-WebRequest -UseBasicParsing -Uri $endpoint -Method Get -TimeoutSec 60
$yaml = if ($response.Content -is [byte[]]) {
    [Text.Encoding]::UTF8.GetString($response.Content)
} else { [string]$response.Content }
$yaml = $yaml.TrimStart([char]0xFEFF).Replace("`r`n", "`n")
if ($yaml -notmatch '(?m)^openapi:\s*[''\"]?3\.') {
    throw 'The backend did not return an OpenAPI 3 YAML document. Check its URL and API_DOCS_ENABLED.'
}
if (-not $yaml.EndsWith("`n")) { $yaml += "`n" }
$parent = Split-Path -Parent $destination
[IO.Directory]::CreateDirectory($parent) | Out-Null
[IO.File]::WriteAllText($destination, $yaml, [Text.UTF8Encoding]::new($false))
Write-Host "OpenAPI YAML exported to $destination"

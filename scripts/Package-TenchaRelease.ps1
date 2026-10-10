[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$LspatchToolsDirectory)

$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$versionSource = Get-Content (Join-Path $repoPath 'app/build.gradle') -Raw
$version = [regex]::Match($versionSource, 'versionName "([^"]+)"').Groups[1].Value
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid release version' }
$releasePath = Join-Path $repoPath "build/release-$version"
if (Test-Path -LiteralPath $releasePath) { throw "Release directory already exists: $releasePath" }
$kitPath = Join-Path $releasePath 'rootless-kit'
$toolsPath = (Resolve-Path -LiteralPath $LspatchToolsDirectory).Path
$expected = @{
    'manager-v1.1-474-release.apk' = 'ab3e1f74c63b1eaa9ce5e0e182cce036ab7dca4586d89415bf9b553eaa4f9b9f'
    'lspatch-v1.1-474-release.jar' = '2bd285c6ac6e8878d04304f8d761e0d3c56ab05e6ef0c04da7f94f0419e8ce75'
}
foreach ($name in $expected.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $toolsPath $name) -Algorithm SHA256).Hash -ne $expected[$name]) {
        throw "LSPatch checksum mismatch: $name"
    }
}
New-Item -ItemType Directory -Path $kitPath | Out-Null
$apk = Join-Path $repoPath 'app/build/outputs/apk/debug/app-debug.apk'
Copy-Item -LiteralPath $apk -Destination (Join-Path $releasePath "Tencha-root-$version.apk")
Copy-Item -LiteralPath $apk -Destination (Join-Path $releasePath "Tencha-rootless-module-$version.apk")
Copy-Item -LiteralPath $apk -Destination (Join-Path $kitPath "Tencha-rootless-module-$version.apk")
foreach ($name in @('Build-TenchaRootless.ps1', 'LSPATCH-NOTICE.md', 'README.md')) {
    Copy-Item -LiteralPath (Join-Path $repoPath "rootless/$name") -Destination $kitPath
}
Copy-Item -LiteralPath (Join-Path $toolsPath 'manager-v1.1-474-release.apk') -Destination (Join-Path $kitPath 'LSPatch-Manager-v1.1-474.apk')
Copy-Item -LiteralPath (Join-Path $toolsPath 'lspatch-v1.1-474-release.jar') -Destination $kitPath

function Write-Checksums([string]$Directory, [string[]]$Names, [string]$OutputName) {
    $lines = foreach ($name in $Names) {
        $hash = (Get-FileHash -LiteralPath (Join-Path $Directory $name) -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $name"
    }
    [IO.File]::WriteAllLines((Join-Path $Directory $OutputName), $lines, [Text.UTF8Encoding]::new($false))
}
Write-Checksums $kitPath @('Build-TenchaRootless.ps1', 'LSPatch-Manager-v1.1-474.apk', 'LSPATCH-NOTICE.md', 'lspatch-v1.1-474-release.jar', 'README.md', "Tencha-rootless-module-$version.apk") 'HASHES.sha256'
Compress-Archive -LiteralPath $kitPath -DestinationPath (Join-Path $releasePath "Tencha-rootless-kit-$version.zip") -CompressionLevel Optimal
Write-Checksums $releasePath @("Tencha-root-$version.apk", "Tencha-rootless-module-$version.apk", "Tencha-rootless-kit-$version.zip") "Tencha-$version-SHA256SUMS.txt"
Get-ChildItem -LiteralPath $releasePath -File | Select-Object Name, Length

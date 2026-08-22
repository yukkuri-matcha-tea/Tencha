[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseApk,

    [string[]]$SplitApk = @(),

    [string]$TenchaApk = (Join-Path $PSScriptRoot 'Tencha-rootless-module-1.5.7.apk'),

    [string]$LspatchJar = (Join-Path $PSScriptRoot 'lspatch-v1.1-474-release.jar'),

    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'patched-line'),

    [string]$JavaPath
)

$ErrorActionPreference = 'Stop'

function Resolve-RequiredFile([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label が見つかりません: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

$base = Resolve-RequiredFile $BaseApk 'LINE base APK'
$module = Resolve-RequiredFile $TenchaApk 'Tencha APK'
$patcher = Resolve-RequiredFile $LspatchJar 'LSPatch JAR'
$splits = @($SplitApk | ForEach-Object { Resolve-RequiredFile $_ 'LINE split APK' })

if (-not $JavaPath) {
    $androidStudioJava = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
    if (Test-Path -LiteralPath $androidStudioJava -PathType Leaf) {
        $JavaPath = $androidStudioJava
    } else {
        $javaCommand = Get-Command java -ErrorAction SilentlyContinue
        if ($null -eq $javaCommand) {
            throw 'Java 21が見つかりません。-JavaPathでjava.exeを指定してください。'
        }
        $JavaPath = $javaCommand.Source
    }
}
$java = Resolve-RequiredFile $JavaPath 'Java'

$javaVersion = (& $java -version 2>&1 | Select-Object -First 1) -join ''
if ($javaVersion -notmatch 'version "(?:21|2[2-9]|[3-9][0-9])') {
    throw "LSPatch v1.1にはJava 21以上が必要です。検出: $javaVersion"
}

$output = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($output) | Out-Null

$arguments = @(
    '-jar', $patcher,
    '--force',
    '--sigbypasslv', '2',
    '--embed', $module,
    '--output', $output,
    $base
) + $splits

Write-Host 'LSPatchでLINE APKセットを作成します。端末へのインストールや既存LINEの削除は行いません。'
& $java @arguments
if ($LASTEXITCODE -ne 0) {
    throw "LSPatchが終了コード $LASTEXITCODE で失敗しました。"
}

$artifacts = @(Get-ChildItem -LiteralPath $output -Filter '*.apk' -File | Sort-Object Name)
if ($artifacts.Count -eq 0) {
    throw '出力APKが生成されませんでした。'
}

$manifest = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    lspatch = [ordered]@{
        version = '1.1-474'
        mode = 'integrated'
        signatureBypassLevel = 2
    }
    warning = '既存の公式LINEへ直接上書きできません。削除前にLINE公式バックアップを確認してください。'
    files = @($artifacts | ForEach-Object {
        [ordered]@{
            name = $_.Name
            size = $_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding utf8

Write-Host "作成完了: $output"
Write-Host '注意: このスクリプトはインストール、アンインストール、LINEデータ削除を一切行いません。'

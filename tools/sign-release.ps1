param(
    [string]$InputApk,
    [string]$OutputApk,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$BuildToolsVersion = '36.0.0'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $InputApk) { $InputApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-unsigned.apk' }
if (-not $OutputApk) {
    $versionMatch = [regex]::Match((Get-Content -LiteralPath (Join-Path $projectRoot 'app/build.gradle.kts') -Raw), 'versionName\s*=\s*"([^"]+)"')
    if (-not $versionMatch.Success) { throw '找不到版本名稱。' }
    $OutputApk = Join-Path $projectRoot ('dist/Todo-' + $versionMatch.Groups[1].Value + '.apk')
}
foreach ($requiredName in @('TODO_KEYSTORE','TODO_KEY_ALIAS','TODO_STORE_PASSWORD','TODO_KEY_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($requiredName))) { throw "缺少發布設定：$requiredName" }
}
if (-not (Test-Path -LiteralPath $env:TODO_KEYSTORE)) { throw '找不到發布金鑰。' }
if (-not (Test-Path -LiteralPath $InputApk)) { throw '請先建置未簽署的 release APK。' }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) { throw '請設定 ANDROID_HOME 或使用 -SdkPath。' }
$toolsDirectory = Join-Path $SdkPath "build-tools/$BuildToolsVersion"
$isWindowsHost = $env:OS -eq 'Windows_NT'
$signer = Join-Path $toolsDirectory $(if ($isWindowsHost) { 'apksigner.bat' } else { 'apksigner' })
$aligner = Join-Path $toolsDirectory $(if ($isWindowsHost) { 'zipalign.exe' } else { 'zipalign' })
& $aligner -c -P 16 4 $InputApk
if ($LASTEXITCODE -ne 0) { throw 'APK 對齊檢查失敗，未簽署。' }
$destinationDirectory = Split-Path -Parent ([IO.Path]::GetFullPath($OutputApk))
New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
& $signer sign --ks $env:TODO_KEYSTORE --ks-key-alias $env:TODO_KEY_ALIAS --ks-pass env:TODO_STORE_PASSWORD --key-pass env:TODO_KEY_PASSWORD --v4-signing-enabled false --out $OutputApk $InputApk
if ($LASTEXITCODE -ne 0) { throw 'APK 簽署失敗。' }
& $signer verify --verbose --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'APK 簽章驗證失敗。' }
Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256 | Select-Object Algorithm,Hash

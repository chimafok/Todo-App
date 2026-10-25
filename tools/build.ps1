param(
    [string[]]$GradleTask = @('assembleDebug'),
    [string]$SdkPath,
    [string]$JavaPath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$isWindowsHost = $env:OS -eq 'Windows_NT'
if (-not $JavaPath) { $JavaPath = $env:JAVA_HOME }
if (-not $JavaPath -and $isWindowsHost -and $env:ProgramFiles) {
    $studioRuntime = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    if (Test-Path -LiteralPath (Join-Path $studioRuntime 'bin/java.exe')) { $JavaPath = $studioRuntime }
}
if (-not $JavaPath) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $JavaPath = Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
}
$javaName = if ($isWindowsHost) { 'java.exe' } else { 'java' }
if (-not $JavaPath -or -not (Test-Path -LiteralPath (Join-Path $JavaPath "bin/$javaName"))) {
    throw '找不到 JDK。請設定 JAVA_HOME 或使用 -JavaPath。'
}
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
try {
    $env:JAVA_HOME = $JavaPath
    if ($SdkPath) {
        if (-not (Test-Path -LiteralPath $SdkPath)) { throw '指定的 SDK 資料夾不存在。' }
        $env:ANDROID_HOME = $SdkPath
    } elseif (-not $env:ANDROID_HOME) {
        if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
        elseif ($isWindowsHost -and $env:LOCALAPPDATA) {
            $candidateSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
            if (Test-Path -LiteralPath $candidateSdk) { $env:ANDROID_HOME = $candidateSdk }
        }
    }
    Push-Location $projectRoot
    try {
        $launcher = if ($isWindowsHost) { 'gradlew.bat' } else { 'gradlew' }
        & (Join-Path $projectRoot $launcher) @GradleTask --console=plain --no-daemon
        $buildExitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
}
exit $buildExitCode

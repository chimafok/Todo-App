param([switch]$UnitOnly)

$ErrorActionPreference = 'Stop'
$testTasks = @('testQaUnitTest', 'lintDebug')
if (-not $UnitOnly) { $testTasks += 'connectedQaAndroidTest' }
# The qa application ID is com.chimafok.todo.qa; never target the user's installation.
& (Join-Path $PSScriptRoot 'build.ps1') -GradleTask $testTasks
exit $LASTEXITCODE

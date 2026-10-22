param(
    [switch]$Release
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$dev = Join-Path $root '.dev'

if (-not (Test-Path (Join-Path $dev 'jdk\bin\java.exe'))) {
    throw "Portable JDK not found at $dev. Run scripts/bootstrap-env.py --root `"$root`" first."
}

$env:JAVA_HOME = Join-Path $dev 'jdk'
$env:ANDROID_HOME = Join-Path $dev 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-home'
$env:ANDROID_USER_HOME = Join-Path $root '.android'
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue

$task = if ($Release) { ':app:assembleRelease' } else { ':app:assembleDebug' }

$installedGradle = Join-Path $dev 'gradle\bin\gradle.bat'
$gradle = if (Test-Path $installedGradle) { $installedGradle } else { '.\gradlew.bat' }

Push-Location $root
try {
    & $gradle $task --no-watch-fs --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

$apkDir = Join-Path $root 'app\build\outputs\apk'
Get-ChildItem $apkDir -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
    Select-Object FullName, @{ n = 'SizeMB'; e = { [math]::Round($_.Length / 1MB, 2) } } |
    Format-Table -AutoSize

param(
    [string]$Version = '0.1.0'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'
$staging = Join-Path $dist "LumaLink-$Version"

if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null

$items = @(
    'app', 'core', 'ui', 'protocol', 'transport', 'gallery', 'remote', 'lut',
    'gradle', 'docs', 'scripts', '.github',
    'README.md', 'LICENSE', 'NOTICE', 'THIRD_PARTY_NOTICES.md',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
    'gradlew', 'gradlew.bat', '.gitignore'
)

foreach ($item in $items) {
    $source = Join-Path $root $item
    if (-not (Test-Path $source)) { continue }

    $destination = Join-Path $staging $item
    if (Test-Path $source -PathType Container) {
        # robocopy handles long paths and lets us exclude build outputs up front.
        $arguments = @(
            $source, $destination, '/E',
            '/XD', 'build', '.gradle', '.gradle-home', '.kotlin', '.android', '.dev',
            '/NFL', '/NDL', '/NJH', '/NJS', '/NP'
        )
        & robocopy @arguments | Out-Null
        if ($LASTEXITCODE -ge 8) {
            throw "robocopy failed for $item (exit $LASTEXITCODE)"
        }
    } else {
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
}

$zip = Join-Path $dist "LumaLink-$Version-src.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zip -CompressionLevel Optimal
Remove-Item $staging -Recurse -Force

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apk) {
    Copy-Item $apk (Join-Path $dist "LumaLink-$Version-debug.apk") -Force
}

Get-ChildItem $dist |
    Select-Object Name, @{ n = 'MB'; e = { [math]::Round($_.Length / 1MB, 2) } } |
    Format-Table -AutoSize

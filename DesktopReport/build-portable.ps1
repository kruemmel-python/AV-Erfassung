$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$qtBin = if ($env:AV_QT_BIN) { $env:AV_QT_BIN } else { 'C:\msys64\mingw64\bin' }
$env:PATH = "$qtBin;$env:PATH"
$build = Join-Path $root 'build'
$testBuild = Join-Path $root 'build-tests'
$dist = Join-Path $root 'dist\AV-Schichtreport'
$distRoot = Join-Path $root 'dist'

function Remove-GeneratedDirectory([string] $path) {
    $resolvedRoot = [IO.Path]::GetFullPath($root).TrimEnd('\') + '\'
    $resolvedPath = [IO.Path]::GetFullPath($path)
    if (-not $resolvedPath.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Generierter Pfad liegt außerhalb des DesktopReport-Verzeichnisses: $resolvedPath"
    }
    if (Test-Path -LiteralPath $resolvedPath) {
        Remove-Item -LiteralPath $resolvedPath -Recurse -Force
    }
}

if (-not (Test-Path (Join-Path $qtBin 'qmake.exe'))) {
    throw "Qt 5 wurde unter $qtBin nicht gefunden."
}

Remove-GeneratedDirectory $build
Remove-GeneratedDirectory $testBuild
Remove-GeneratedDirectory $distRoot

New-Item -ItemType Directory -Force -Path $testBuild | Out-Null
Push-Location $testBuild
try {
    & (Join-Path $qtBin 'qmake.exe') (Join-Path $root 'tests\import_test.pro') 'CONFIG+=release'
    if ($LASTEXITCODE -ne 0) { throw 'qmake für Importtests fehlgeschlagen.' }
    & (Join-Path $qtBin 'mingw32-make.exe') -j2
    if ($LASTEXITCODE -ne 0) { throw 'Kompilierung der Importtests fehlgeschlagen.' }
    $testExecutable = @(
        (Join-Path $testBuild 'release\import_test.exe'),
        (Join-Path $testBuild 'import_test.exe')
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $testExecutable) { throw 'Importtest-Programm wurde nicht erzeugt.' }
    $testFixture = Join-Path $root 'tests\fixtures\anonymized_employee_10001.csv'
    if (-not (Test-Path -LiteralPath $testFixture)) { throw 'Importtest-Fixture fehlt.' }
    & $testExecutable $testFixture
    if ($LASTEXITCODE -ne 0) { throw 'Importtests fehlgeschlagen.' }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force -Path $build | Out-Null
Push-Location $build
try {
    & (Join-Path $qtBin 'qmake.exe') (Join-Path $root 'AV-Schichtreport.pro') 'CONFIG+=release'
    if ($LASTEXITCODE -ne 0) { throw 'qmake fehlgeschlagen.' }
    & (Join-Path $qtBin 'mingw32-make.exe') -j2
    if ($LASTEXITCODE -ne 0) { throw 'Kompilierung fehlgeschlagen.' }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item -LiteralPath (Join-Path $build 'release\AV-Schichtreport.exe') -Destination $dist -Force
Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination (Join-Path $dist 'README.txt') -Force
Copy-Item -LiteralPath (Join-Path $root 'THIRD-PARTY-NOTICES.txt') -Destination $dist -Force
$runtime = @(
    'Qt5Core.dll', 'Qt5Gui.dll', 'Qt5Widgets.dll', 'Qt5PrintSupport.dll',
    'libgcc_s_seh-1.dll', 'libstdc++-6.dll', 'libwinpthread-1.dll',
    'zlib1.dll', 'libbz2-1.dll', 'libpcre2-16-0.dll', 'libharfbuzz-0.dll',
    'libfreetype-6.dll', 'libpng16-16.dll', 'libglib-2.0-0.dll', 'libintl-8.dll',
    'libiconv-2.dll', 'libgraphite2.dll', 'libbrotlidec.dll', 'libbrotlicommon.dll'
)
foreach ($dll in $runtime) {
    $source = Join-Path $qtBin $dll
    if (Test-Path $source) { Copy-Item -LiteralPath $source -Destination $dist -Force }
}
$platforms = Join-Path $dist 'platforms'
New-Item -ItemType Directory -Force -Path $platforms | Out-Null
$qtPlatformPlugin = if ($env:AV_QT_PLATFORM_PLUGIN) {
    $env:AV_QT_PLATFORM_PLUGIN
} else {
    Join-Path (Split-Path -Parent $qtBin) 'share\qt5\plugins\platforms\qwindows.dll'
}
if (-not (Test-Path -LiteralPath $qtPlatformPlugin)) { throw "Qt-Plattformplugin fehlt: $qtPlatformPlugin" }
Copy-Item -LiteralPath $qtPlatformPlugin -Destination $platforms -Force

$zip = Join-Path $root 'dist\AV-Schichtreport-portable.zip'
Compress-Archive -Path $dist -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Portable App: $dist"
Write-Host "ZIP-Paket:    $zip"

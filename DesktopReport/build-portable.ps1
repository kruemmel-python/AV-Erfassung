$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$qtBin = 'C:\msys64\mingw64\bin'
$env:PATH = "$qtBin;$env:PATH"
$build = Join-Path $root 'build'
$dist = Join-Path $root 'dist\AV-Schichtreport'

if (-not (Test-Path (Join-Path $qtBin 'qmake.exe'))) {
    throw 'Qt 5 wurde unter C:\msys64\mingw64 nicht gefunden.'
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
Copy-Item -LiteralPath 'C:\msys64\mingw64\share\qt5\plugins\platforms\qwindows.dll' -Destination $platforms -Force

$zip = Join-Path $root 'dist\AV-Schichtreport-portable.zip'
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path $dist -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Portable App: $dist"
Write-Host "ZIP-Paket:    $zip"

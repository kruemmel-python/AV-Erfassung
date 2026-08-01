[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = 'conformance/avm-contracts-cpp'
$build = 'build/native-conformance-win'
$hostSource = 'enterprise/native-host'
$hostBuild = 'build/native-host-win'
$cmake = if (Test-Path -LiteralPath 'C:\Program Files\CMake\bin\cmake.exe') { 'C:\Program Files\CMake\bin\cmake.exe' } else { (Get-Command cmake -ErrorAction Stop).Source }
$ctest = if (Test-Path -LiteralPath 'C:\Program Files\CMake\bin\ctest.exe') { 'C:\Program Files\CMake\bin\ctest.exe' } else { (Get-Command ctest -ErrorAction Stop).Source }
$compilerDirectory = Split-Path -Parent (Get-Command g++.exe -ErrorAction Stop).Source
$env:PATH = "$compilerDirectory;$env:PATH"

Push-Location $root
try {
& $cmake -S $source -B $build -G 'MinGW Makefiles' -DCMAKE_BUILD_TYPE=Release
if ($LASTEXITCODE -ne 0) { throw 'CMake-Konfiguration der AVM-Conformance ist fehlgeschlagen.' }
& $cmake --build $build --config Release --parallel
if ($LASTEXITCODE -ne 0) { throw 'C++-Build der AVM-Conformance ist fehlgeschlagen.' }
& $ctest --test-dir $build -C Release --output-on-failure
if ($LASTEXITCODE -ne 0) { throw 'C++-Golden-Tests der AVM-Conformance sind fehlgeschlagen.' }
& $cmake -S $hostSource -B $hostBuild -G 'MinGW Makefiles' -DCMAKE_BUILD_TYPE=Release
if ($LASTEXITCODE -ne 0) { throw 'CMake-Konfiguration des Enterprise Native Host ist fehlgeschlagen.' }
& $cmake --build $hostBuild --config Release --parallel
if ($LASTEXITCODE -ne 0) { throw 'C++-Build des Enterprise Native Host ist fehlgeschlagen.' }
& $ctest --test-dir $hostBuild -C Release --output-on-failure
if ($LASTEXITCODE -ne 0) { throw 'ABI- und Lifecycle-Tests des Enterprise Native Host sind fehlgeschlagen.' }
} finally {
    Pop-Location
}

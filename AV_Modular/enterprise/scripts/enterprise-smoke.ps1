[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$smoke = Join-Path $root 'build\enterprise-smoke'
if (Test-Path -LiteralPath $smoke) {
    $resolved = (Resolve-Path -LiteralPath $smoke).Path
    $allowed = Join-Path $root 'build'
    if (-not $resolved.StartsWith($allowed, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsicheres Smoke-Test-Ziel: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
New-Item -ItemType Directory -Path $smoke | Out-Null

Push-Location $root
try {
    & .\gradlew.bat --console=plain :profile-tool:installDist :reporter-cli:installDist
    if ($LASTEXITCODE -ne 0) { throw 'Werkzeug-Build fehlgeschlagen' }

    $profileTool = '.\enterprise\profile-tool\build\install\profile-tool\bin\profile-tool.bat'
    $reporter = '.\enterprise\reporter-cli\build\install\reporter-cli\bin\reporter-cli.bat'
    $keyRing = Join-Path $smoke 'trust.json'
    $package = Join-Path $smoke 'mail-processing.avpkg'
    $privateKey = Join-Path $smoke 'keys\enterprise-smoke-private.pem'

    & $profileTool keygen --key-id enterprise-smoke --out (Join-Path $smoke 'keys') --keyring $keyRing
    & $profileTool package --source .\modules\mail_processing --out $package --package-id mail-processing-enterprise --version 1.0.0 --key-id enterprise-smoke --private-key $privateKey
    & $profileTool verify --package $package --keyring $keyRing
    & $reporter --module .\modules\mail_processing --output (Join-Path $smoke 'qs-report.html') .\specification\work-record-v2\examples\valid-multi-employee.csv
    & $profileTool revoke --keyring $keyRing --key-id enterprise-smoke --reason 'Automatischer Widerrufstest'

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $profileTool verify --package $package --keyring $keyRing 2>$null
        $revokedVerificationExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($revokedVerificationExitCode -eq 0) { throw 'Widerruf wurde nicht durchgesetzt' }
    Write-Host 'ENTERPRISE SMOKE TEST: ERFOLGREICH'
} finally {
    Pop-Location
}

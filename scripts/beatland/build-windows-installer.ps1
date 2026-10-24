<#
.SYNOPSIS
  Builds the Beatland Windows installer (.msi) on THIS computer.

.DESCRIPTION
  Easier alternative with nothing to install: let GitHub build it (see docs/beatland/DESKTOP.md, "Option A").
  Use this script only if you prefer building locally. It needs:
    1. A JDK 17 or newer that includes jpackage (JDK 21 recommended):   winget install Microsoft.OpenJDK.21
    2. WiX Toolset 3.x (candle.exe + light.exe):                         choco install wixtoolset   (or https://github.com/wixtoolset/wix3/releases)
    3. The repository with its submodule:  git submodule update --init --recursive

  Run from the repository root:   powershell -ExecutionPolicy Bypass -File scripts\beatland\build-windows-installer.ps1
#>
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path "$PSScriptRoot\..\..")

function Fail($msg) { Write-Host "`nERROR: $msg" -ForegroundColor Red; exit 1 }

# --- checks ----------------------------------------------------------------------------------------------------------
if (-not (Test-Path 'core\service')) { Fail "The 'core' submodule is empty. Run: git submodule update --init --recursive" }

$javaHome = $env:JAVA_HOME
$jpackage = if ($javaHome -and (Test-Path "$javaHome\bin\jpackage.exe")) { "$javaHome\bin\jpackage.exe" } else { (Get-Command jpackage.exe -ErrorAction SilentlyContinue).Source }
if (-not $jpackage) { Fail "jpackage was not found. Install a full JDK (winget install Microsoft.OpenJDK.21) and set JAVA_HOME to it." }
Write-Host "jpackage: $jpackage"

$candle = (Get-Command candle.exe -ErrorAction SilentlyContinue).Source
if (-not $candle) {
  $wix = Get-ChildItem 'C:\Program Files (x86)' -Directory -Filter 'WiX Toolset v3*' -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($wix) { $env:PATH = "$($wix.FullName)\bin;$env:PATH"; $candle = "$($wix.FullName)\bin\candle.exe" }
}
if (-not $candle) { Fail "WiX Toolset 3 was not found (candle.exe). Install it: choco install wixtoolset" }
Write-Host "WiX: $candle"

# --- build -----------------------------------------------------------------------------------------------------------
$flags = @('-PisFullBuild=false', '--no-configuration-cache')
Write-Host "`n[1/3] Licenses list..." -ForegroundColor Cyan
& .\gradlew.bat exportLibraryDefinitions @flags; if ($LASTEXITCODE -ne 0) { Fail "exportLibraryDefinitions failed" }

Write-Host "`n[2/3] Native player libraries (libmpv)..." -ForegroundColor Cyan
& .\gradlew.bat :composeApp:mpvSetupAll @flags; if ($LASTEXITCODE -ne 0) { Fail "mpvSetupAll failed" }

Write-Host "`n[3/3] Installer..." -ForegroundColor Cyan
& .\gradlew.bat :desktopApp:packageMsi @flags; if ($LASTEXITCODE -ne 0) { Fail "packageMsi failed" }

$msi = Get-ChildItem 'desktopApp\build\compose\binaries\main\msi' -Filter *.msi -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $msi) { Fail "The build finished but no .msi was found in desktopApp\build\compose\binaries\main\msi" }
Write-Host "`nDONE: $($msi.FullName)" -ForegroundColor Green
Start-Process explorer.exe -ArgumentList "/select,`"$($msi.FullName)`""

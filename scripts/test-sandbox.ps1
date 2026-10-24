<#
.SYNOPSIS
  Tests the MediaRenamer installer in Windows Sandbox: a clean, disposable Windows without Java or MediaInfo.

.DESCRIPTION
  Part of the mixed test approach (see docs/DEVELOPMENT.md): every change is tested on this machine with isolated data,
  and the installer/exe of every new version is also tested here, on a clean Windows.

  It generates a .wsb file that maps dist\ (read only), scripts\sandbox\ (read only) and a results folder, starts
  Windows Sandbox and runs scripts\sandbox\smoke-test.ps1 inside it. Then it waits for the report and prints it:
    target\sandbox-test\<timestamp>\results\report.md / report.json / gui.png / install logs / transcript.log
  The sandbox shuts down by itself at the end (unless -KeepOpen). Nothing of this machine is modified.

  Requirements: Windows 10/11 Pro or Enterprise with the "Windows Sandbox" feature enabled (once, as administrator:
  Enable-WindowsOptionalFeature -Online -FeatureName Containers-DisposableClientVM -All, then restart), and
  dist\MediaRenamer-<version>-setup.exe built by scripts\build-exe.ps1. Only one sandbox can run at a time.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\test-sandbox.ps1
  powershell -ExecutionPolicy Bypass -File scripts\test-sandbox.ps1 -KeepOpen
#>
param(
    # Defaults to the pom.xml version (without -SNAPSHOT)
    [string]$Version,
    [int]$TimeoutMinutes = 15,
    # Leaves the sandbox open after the test (to inspect it); close it by hand afterwards
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Version) {
    $pom = [xml](Get-Content (Join-Path $root "pom.xml") -Raw)
    $Version = $pom.project.version -replace "-SNAPSHOT$", ""
}

$sandboxExe = Join-Path $env:windir "System32\WindowsSandbox.exe"
if (-not (Test-Path $sandboxExe)) {
    throw "Windows Sandbox is not enabled. Enable it once as administrator: Enable-WindowsOptionalFeature -Online -FeatureName Containers-DisposableClientVM -All (then restart Windows)."
}
$setup = Join-Path $root "dist\MediaRenamer-$Version-setup.exe"
if (-not (Test-Path $setup)) {
    throw "$setup does not exist. Build it first with scripts\build-exe.ps1."
}
if (Get-Process -Name "WindowsSandbox*" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 }) {
    throw "A Windows Sandbox is already running (only one is allowed). Close it and run the script again."
}

$runDir = Join-Path $root ("target\sandbox-test\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
$results = Join-Path $runDir "results"
New-Item -ItemType Directory -Force $results | Out-Null

$guestArgs = "-Version $Version" + $(if ($KeepOpen) { " -KeepOpen" } else { "" })
$wsb = @"
<Configuration>
  <Networking>Enable</Networking>
  <MemoryInMB>4096</MemoryInMB>
  <MappedFolders>
    <MappedFolder>
      <HostFolder>$(Join-Path $root 'dist')</HostFolder>
      <SandboxFolder>C:\FR\dist</SandboxFolder>
      <ReadOnly>true</ReadOnly>
    </MappedFolder>
    <MappedFolder>
      <HostFolder>$(Join-Path $root 'scripts\sandbox')</HostFolder>
      <SandboxFolder>C:\FR\scripts</SandboxFolder>
      <ReadOnly>true</ReadOnly>
    </MappedFolder>
    <MappedFolder>
      <HostFolder>$results</HostFolder>
      <SandboxFolder>C:\FR\results</SandboxFolder>
      <ReadOnly>false</ReadOnly>
    </MappedFolder>
  </MappedFolders>
  <LogonCommand>
    <Command>powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\FR\scripts\smoke-test.ps1 $guestArgs</Command>
  </LogonCommand>
</Configuration>
"@
$wsbFile = Join-Path $runDir "MediaRenamer-smoke-test.wsb"
Set-Content $wsbFile $wsb -Encoding UTF8

Write-Host "Starting Windows Sandbox (MediaRenamer $Version)... results in $results"
Start-Process $sandboxExe -ArgumentList "`"$wsbFile`""

$done = Join-Path $results "done.flag"
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while (-not (Test-Path $done) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
}
if (-not (Test-Path $done)) {
    throw "Timeout after $TimeoutMinutes minutes without a report. See $results (transcript.log) and the sandbox window."
}

$report = Get-Content (Join-Path $results "report.json") -Raw | ConvertFrom-Json
Get-Content (Join-Path $results "report.md") | Write-Host
Write-Host ""
Write-Host "Passed: $($report.passed)  Failed: $($report.failed)  (screenshot: $(Join-Path $results 'gui.png'))"
if ($report.failed -gt 0) { exit 1 }

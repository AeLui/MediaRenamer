<#
.SYNOPSIS
  Smoke test of the MediaRenamer installer, run INSIDE Windows Sandbox (a clean, disposable Windows).

.DESCRIPTION
  Started by scripts\test-sandbox.ps1 through the LogonCommand of the generated .wsb file. Folders mapped by the host:
    C:\FR\dist     (read only)  dist\ of the repository: MediaRenamer-<version>-setup.exe
    C:\FR\results  (read/write) report.json, report.md, logs and screenshots for the host

  Steps: install for all users (the sandbox user is an administrator) -> CLI checks (version, languages, missing
  MediaInfo, missing API key, File-mode test and real rename) -> GUI starts and shows a window (screenshot) -> uninstall (user data is
  kept) -> per-user install and uninstall -> upgrade from FileRenamer (the name before 2.0: program replaced, data
  and API keys migrated). Each step is recorded as PASS/FAIL; "done.flag" tells the host it finished.

  Written for Windows PowerShell 5.1 (the one available in the sandbox) and ASCII only.
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    # Leaves the sandbox open at the end (for debugging); by default it shuts down
    [switch]$KeepOpen
)

$ErrorActionPreference = "Continue"
$results = "C:\FR\results"
$setup = "C:\FR\dist\MediaRenamer-$Version-setup.exe"
$work = "C:\FR\work"
$steps = New-Object System.Collections.ArrayList
$script:runCount = 0
New-Item -ItemType Directory -Force $results, $work | Out-Null
Start-Transcript -Path (Join-Path $results "transcript.log") | Out-Null

function Step([string]$Name, [scriptblock]$Body) {
    $detail = ""
    $ok = $false
    try {
        $r = & $Body
        $ok = [bool]$r[0]
        $detail = [string]$r[1]
    } catch {
        $detail = "exception: " + $_.Exception.Message
    }
    [void]$steps.Add([ordered]@{ step = $Name; ok = $ok; detail = $detail })
    Write-Host ("[{0}] {1} - {2}" -f $(if ($ok) { "PASS" } else { "FAIL" }), $Name, $detail)
}

# Runs a console exe, returns @(exitCode, outputText); output is read as UTF-8 through a file
function Run([string]$Exe, [string[]]$Arguments, [hashtable]$Env = @{}) {
    # each command's output is kept in the results (cli-01.txt...) to diagnose failures from the host
    $script:runCount++
    $out = Join-Path $results ("cli-{0:D2}.txt" -f $script:runCount)
    $saved = @{}
    foreach ($k in $Env.Keys) { $saved[$k] = [Environment]::GetEnvironmentVariable($k); [Environment]::SetEnvironmentVariable($k, $Env[$k]) }
    # Start-Process does not quote the arguments (PowerShell 5.1): quote those with spaces ("{n} - {s00e00}")
    $argLine = ($Arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
    $p = Start-Process $Exe -ArgumentList $argLine -NoNewWindow -Wait -PassThru -RedirectStandardOutput $out -RedirectStandardError "$out.err"
    foreach ($k in $Env.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k]) }
    $text = (Get-Content $out -Raw -Encoding UTF8) + (Get-Content "$out.err" -Raw -Encoding UTF8)
    Set-Content $out ("> " + $Exe + " " + $argLine + "`r`n> exit " + $p.ExitCode + "`r`n" + $text) -Encoding UTF8
    Remove-Item "$out.err" -ErrorAction SilentlyContinue
    return @($p.ExitCode, [string]$text)
}

Add-Type -AssemblyName System.Drawing, System.Windows.Forms
Add-Type @"
using System; using System.Runtime.InteropServices;
public class Win {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  public struct RECT { public int L, T, R, B; }
}
"@

function Capture([IntPtr]$Handle, [string]$File) {
    $r = New-Object Win+RECT
    [Win]::GetWindowRect($Handle, [ref]$r) | Out-Null
    $bmp = New-Object System.Drawing.Bitmap ([Math]::Max(1, $r.R - $r.L)), ([Math]::Max(1, $r.B - $r.T))
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc(); [Win]::PrintWindow($Handle, $hdc, 2) | Out-Null; $g.ReleaseHdc($hdc)
    $bmp.Save($File); $g.Dispose(); $bmp.Dispose()
}

$machineDir = Join-Path $env:ProgramFiles "MediaRenamer"
$userDir = Join-Path $env:LOCALAPPDATA "Programs\MediaRenamer"
$appId = "{84902B60-E036-4419-AA8B-D555ABC48287}_is1"
$hklmKey = "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$appId"
$hkcuKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$appId"
$dataDir = Join-Path $env:APPDATA "MediaRenamer"

Step "environment" {
    $java = Get-Command java -ErrorAction SilentlyContinue
    @((Test-Path $setup), ("setup found: $(Test-Path $setup); java on PATH: $([bool]$java); OS: " + (Get-CimInstance Win32_OperatingSystem).Caption))
}

# ---------------------------------------------------------------- install for all users
Step "install (all users, silent)" {
    $p = Start-Process $setup -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/ALLUSERS", "/LOG=`"$results\install-allusers.log`"" -Wait -PassThru
    $ok = $p.ExitCode -eq 0 -and (Test-Path "$machineDir\MediaRenamer.exe") -and (Test-Path "$machineDir\MediaRenamer-cli.exe")
    @($ok, "exit $($p.ExitCode); exe present: $(Test-Path "$machineDir\MediaRenamer.exe")")
}
Step "uninstall entry and shortcut (all users)" {
    $k = Get-ItemProperty $hklmKey -ErrorAction SilentlyContinue
    $lnk = Test-Path (Join-Path ([Environment]::GetFolderPath("CommonPrograms")) "MediaRenamer.lnk")
    @(($k -and $k.DisplayVersion -eq $Version -and $lnk), "DisplayVersion: $($k.DisplayVersion); Start menu shortcut: $lnk")
}

# ---------------------------------------------------------------- CLI
$cli = "$machineDir\MediaRenamer-cli.exe"
Step "CLI usage in English" {
    $r = Run $cli @("-unknown-flag") @{ MEDIARENAMER_UI_LANGUAGE = "en" }
    @(($r[0] -eq 1 -and $r[1] -match "Usage:"), "exit $($r[0])")
}
Step "CLI usage in German" {
    $r = Run $cli @("-unknown-flag") @{ MEDIARENAMER_UI_LANGUAGE = "de" }
    @(($r[1] -match "Verwendung"), "exit $($r[0])")
}
Step "CLI without MediaInfo fails gracefully" {
    $f = Join-Path $work "sample.mkv"; Set-Content $f "x"
    $r = Run $cli @("-mediainfo", $f) @{ MEDIARENAMER_UI_LANGUAGE = "en" }
    $first = @($r[1] -split "`r?`n" | Where-Object { $_.Trim() })[0]
    @(($r[0] -eq 2 -and $r[1] -match "MediaInfo"), "exit $($r[0]); $first")
}

$series = Join-Path $work "Dark"
New-Item -ItemType Directory -Force $series | Out-Null
foreach ($n in "Dark.S01E01.720p.WEB.x264.mkv", "Dark.S01E02.720p.WEB.x264.mkv") { Set-Content (Join-Path $series $n) "x" }

Step "CLI without API key fails gracefully" {
    $r = Run $cli @("-list", "--db", "TheMovieDB::TV", "--q", "Dark") @{ MEDIARENAMER_UI_LANGUAGE = "en" }
    @(($r[0] -eq 2 -and $r[1] -match "MEDIARENAMER_APIKEY_TMDB"), "exit $($r[0])")
}
Step "CLI rename in test mode (File mode)" {
    $r = Run $cli @("-rename", $series, "--db", "file", "--format", "{fn.before('.720p')}", "--action", "test", "-debug") @{ MEDIARENAMER_UI_LANGUAGE = "en" }
    $untouched = (Get-ChildItem $series -Name | Where-Object { $_ -like "Dark.S01E0*.720p*" }).Count -eq 2
    @(($r[0] -eq 0 -and $r[1] -match "\[TEST\]" -and $untouched), "exit $($r[0]); files untouched: $untouched")
}
Step "CLI real rename (File mode) and history" {
    $r = Run $cli @("-rename", $series, "--db", "file", "--format", "{fn.before('.720p')}") @{ MEDIARENAMER_UI_LANGUAGE = "en" }
    $names = Get-ChildItem $series -Name
    $ok = $r[0] -eq 0 -and ($names -contains "Dark.S01E01.mkv") -and ($names -contains "Dark.S01E02.mkv") -and (Test-Path "$dataDir\history.xml")
    @($ok, "exit $($r[0]); files: $($names -join ', '); history: $(Test-Path "$dataDir\history.xml")")
}

# ---------------------------------------------------------------- GUI
# The jpackage launcher may run the application in a child process: look for any MediaRenamer process of the
# installation folder (not just the PID returned by Start-Process)
$mine = { Get-Process -Name "MediaRenamer" -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($machineDir, [StringComparison]::OrdinalIgnoreCase) } }

Step "first run shows the setup wizard" {
    # the CLI steps do not create settings.properties: the first GUI start is a first run
    $hadSettings = Test-Path "$dataDir\settings.properties"
    Start-Process "$machineDir\MediaRenamer.exe" | Out-Null
    $wizard = $null
    for ($i = 0; $i -lt 120 -and -not $wizard; $i++) {
        Start-Sleep -Milliseconds 500
        $wizard = & $mine | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -and $_.MainWindowTitle -ne "MediaRenamer" } | Select-Object -First 1
    }
    $title = ""
    if ($wizard) {
        $title = $wizard.MainWindowTitle
        Start-Sleep -Seconds 2
        Capture $wizard.MainWindowHandle (Join-Path $results "wizard.png")
        # closing the wizard (WM_CLOSE = "Skip setup") saves the language and opens the main window
        [Win]::PostMessage($wizard.MainWindowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        for ($i = 0; $i -lt 20 -and -not (Test-Path "$dataDir\settings.properties"); $i++) { Start-Sleep -Milliseconds 500 }
    }
    $saved = Test-Path "$dataDir\settings.properties"
    @(([bool]$wizard -and -not $hadSettings -and $saved), "wizard: [$title]; settings saved on close: $saved; screenshot: wizard.png")
}

Step "GUI starts and shows its window" {
    # main window after the wizard (or a new start if the wizard step failed)
    if (-not (& $mine)) { Start-Process "$machineDir\MediaRenamer.exe" | Out-Null }
    $h = [IntPtr]::Zero
    for ($i = 0; $i -lt 120 -and $h -eq [IntPtr]::Zero; $i++) {
        Start-Sleep -Milliseconds 500
        $w = & $mine | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -eq "MediaRenamer" } | Select-Object -First 1
        if ($w) { $h = $w.MainWindowHandle }
    }
    if ($h -ne [IntPtr]::Zero) {
        Start-Sleep -Seconds 3
        Capture $h (Join-Path $results "gui.png")
    }
    if ($h -eq [IntPtr]::Zero) {
        # diagnosis: visible windows and a screenshot of the whole sandbox screen
        Get-Process | Where-Object { $_.MainWindowHandle -ne 0 } | ForEach-Object { "$($_.Id) $($_.ProcessName) [$($_.MainWindowTitle)]" } |
            Set-Content (Join-Path $results "windows.txt")
        $b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
        $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
        $g = [System.Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
        $bmp.Save((Join-Path $results "screen.png")); $g.Dispose(); $bmp.Dispose()
    }
    $alive = [bool](& $mine)
    & $mine | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    @(($h -ne [IntPtr]::Zero -and $alive), "window: $($h -ne [IntPtr]::Zero); still running: $alive; screenshot: gui.png")
}

# ---------------------------------------------------------------- uninstall
Step "uninstall (all users) keeps user data" {
    $p = Start-Process "$machineDir\unins000.exe" -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART" -Wait -PassThru
    for ($i = 0; $i -lt 20 -and (Test-Path $machineDir); $i++) { Start-Sleep -Milliseconds 500 }
    $ok = -not (Test-Path $machineDir) -and -not (Test-Path $hklmKey) -and (Test-Path "$dataDir\history.xml")
    @($ok, "exit $($p.ExitCode); folder removed: $(-not (Test-Path $machineDir)); entry removed: $(-not (Test-Path $hklmKey)); user data kept: $(Test-Path "$dataDir\history.xml")")
}

# ---------------------------------------------------------------- per-user install (the default)
Step "install and uninstall (current user)" {
    $p = Start-Process $setup -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CURRENTUSER", "/LOG=`"$results\install-user.log`"" -Wait -PassThru
    $installed = $p.ExitCode -eq 0 -and (Test-Path "$userDir\MediaRenamer.exe") -and (Test-Path $hkcuKey)
    $r = Run "$userDir\MediaRenamer-cli.exe" @("-profiles") @{ MEDIARENAMER_UI_LANGUAGE = "en" }
    $u = Start-Process "$userDir\unins000.exe" -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART" -Wait -PassThru
    for ($i = 0; $i -lt 20 -and (Test-Path $userDir); $i++) { Start-Sleep -Milliseconds 500 }
    $removed = -not (Test-Path $userDir) -and -not (Test-Path $hkcuKey)
    @(($installed -and $r[0] -eq 0 -and $removed), "installed: $installed; CLI exit: $($r[0]); removed: $removed")
}

# ---------------------------------------------------------------- upgrade from FileRenamer (name before 2.0)
Step "upgrade from FileRenamer (current user): program replaced, data and keys migrated" {
    $old = Get-ChildItem "C:\FR\dist\FileRenamer-*-setup.exe" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
    if (-not $old) { return @($false, "no FileRenamer-*-setup.exe in dist") }
    $oldDir = Join-Path $env:LOCALAPPDATA "Programs\FileRenamer"
    $oldKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{01DD0761-E07C-4D08-A3A5-098D9ED58317}_is1"
    $oldData = Join-Path $env:APPDATA "FileRenamer"
    Remove-Item -Recurse -Force $dataDir -ErrorAction SilentlyContinue
    $p = Start-Process $old.FullName -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CURRENTUSER" -Wait -PassThru
    $oldInstalled = $p.ExitCode -eq 0 -and (Test-Path "$oldDir\FileRenamer.exe") -and (Test-Path $oldKey)
    # preferences of FileRenamer: an API key encrypted with its DPAPI entropy, a preset and the language
    Add-Type -AssemblyName System.Security
    $scope = [System.Security.Cryptography.DataProtectionScope]::CurrentUser
    $enc = [System.Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes("legacy-test-key"),
        [Text.Encoding]::UTF8.GetBytes("FileRenamer/apikeys/v1"), $scope)
    New-Item -ItemType Directory -Force $oldData | Out-Null
    Set-Content "$oldData\settings.properties" ("ui.language=de`r`napikey.tmdb=dpapi\:" + [Convert]::ToBase64String($enc)) -Encoding ASCII
    Set-Content "$oldData\presets.json" "[]" -Encoding ASCII

    $p = Start-Process $setup -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CURRENTUSER", "/LOG=`"$results\install-upgrade.log`"" -Wait -PassThru
    $replaced = $p.ExitCode -eq 0 -and (Test-Path "$userDir\MediaRenamer.exe") -and -not (Test-Path $oldDir) -and -not (Test-Path $oldKey)
    $lnk = Test-Path (Join-Path ([Environment]::GetFolderPath("Programs")) "FileRenamer.lnk")
    # without MEDIARENAMER_UI_LANGUAGE: the language comes from the migrated preferences
    $r = Run "$userDir\MediaRenamer-cli.exe" @("-unknown-flag")
    $props = @{}
    if (Test-Path "$dataDir\settings.properties") {
        foreach ($line in Get-Content "$dataDir\settings.properties") {
            if ($line -match '^([^#=]+)=(.*)$') { $props[$Matches[1]] = $Matches[2] -replace '\\([:=#!])', '$1' }
        }
    }
    $key = ""
    try {
        $blob = [Convert]::FromBase64String(($props["apikey.tmdb"] -replace '^dpapi:', ''))
        $key = [Text.Encoding]::UTF8.GetString([System.Security.Cryptography.ProtectedData]::Unprotect($blob,
            [Text.Encoding]::UTF8.GetBytes("MediaRenamer/apikeys/v1"), $scope))
    } catch { $key = "undecryptable: " + $_.Exception.Message }
    $migrated = (Test-Path "$dataDir\presets.json") -and $props["ui.language"] -eq "de" -and $key -eq "legacy-test-key" -and (Test-Path "$oldData\settings.properties")
    $german = $r[1] -match "Verwendung"
    $u = Start-Process "$userDir\unins000.exe" -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART" -Wait -PassThru
    @(($oldInstalled -and $replaced -and -not $lnk -and $migrated -and $german),
        "old installed: $oldInstalled ($($old.Name)); replaced: $replaced; old shortcut left: $lnk; data migrated: $migrated; key: $key; CLI in German: $german")
}

# ---------------------------------------------------------------- report
$failed = @($steps | Where-Object { -not $_.ok }).Count
$report = [ordered]@{ version = $Version; passed = $steps.Count - $failed; failed = $failed; steps = $steps }
$report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $results "report.json") -Encoding UTF8
$md = @("# Sandbox smoke test - MediaRenamer $Version", "", "| Step | Result | Detail |", "|---|---|---|")
foreach ($s in $steps) { $md += "| $($s.step) | $(if ($s.ok) { 'PASS' } else { 'FAIL' }) | $($s.detail -replace '\|', '/') |" }
$md | Set-Content (Join-Path $results "report.md") -Encoding UTF8
Stop-Transcript | Out-Null
Set-Content (Join-Path $results "done.flag") "done"
if (-not $KeepOpen) { shutdown.exe /s /t 5 }

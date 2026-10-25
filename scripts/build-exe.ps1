<#
.SYNOPSIS
  Builds the MediaRenamer Windows executable with jpackage (bundling its own Java runtime) and its installer.

.DESCRIPTION
  Output:
    dist\MediaRenamer\MediaRenamer.exe          desktop application (no console)
    dist\MediaRenamer\MediaRenamer-cli.exe      same application with a console, for the command line
    dist\MediaRenamer\THIRD-PARTY-NOTICES.txt   third-party license notices (Java runtime ones: runtime\legal)
    dist\MediaRenamer-<version>-windows.zip    the folder above zipped (portable, copy to another machine)
    dist\MediaRenamer-<version>-setup.exe      Windows installer (Inno Setup 6, src\main\packaging\MediaRenamer.iss)

  Java is not needed to run the result. Building it needs JDK 21+ (jpackage), Maven and Inno Setup 6
  (winget install --id JRSoftware.InnoSetup -e --source winget --scope user).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\build-exe.ps1
  powershell -ExecutionPolicy Bypass -File scripts\build-exe.ps1 -SkipBuild
#>
param(
    [switch]$SkipBuild,
    # Only the exe and the zip, without the installer
    [switch]$SkipInstaller,
    # Defaults to the pom.xml version (without -SNAPSHOT): it is the single source of the version
    [string]$Version
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Version) {
    $pom = [xml](Get-Content (Join-Path $root "pom.xml") -Raw)
    $Version = $pom.project.version -replace "-SNAPSHOT$", ""
}

# Inno Setup compiler: $env:ISCC, PATH or the usual install folders (per user or for all users)
function Find-Iscc {
    $candidates = @($env:ISCC, (Get-Command ISCC.exe -ErrorAction SilentlyContinue).Source,
        (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"),
        (Join-Path $env:ProgramFiles "Inno Setup 6\ISCC.exe"))
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    return $null
}

# Fail early (before the long build) if the installer cannot be built
$iscc = $null
if (-not $SkipInstaller) {
    $iscc = Find-Iscc
    if (-not $iscc) {
        throw "Inno Setup 6 (ISCC.exe) not found. Install it with: winget install --id JRSoftware.InnoSetup -e --source winget --scope user (or use -SkipInstaller)."
    }
}

if (-not $SkipBuild) {
    # On machines with an antivirus/proxy that inspects HTTPS, Maven needs the Windows certificate store
    $env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
    mvn -B -q package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
}

$jar = Join-Path $root "target\MediaRenamer.jar"
if (-not (Test-Path $jar)) { throw "$jar does not exist. Run without -SkipBuild." }

$inputDir = Join-Path $root "target\jpackage-input"
$dist = Join-Path $root "dist"

# If the previous exe is running, Windows locks its files: deleting the folder would leave it half-deleted
# (everything but the locked files) and jpackage would fail anyway. Better to stop before touching anything.
$appDir = Join-Path $dist "MediaRenamer"
$running = Get-CimInstance Win32_Process -Filter "Name like 'MediaRenamer%'" | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($appDir, [StringComparison]::OrdinalIgnoreCase) }
if ($running) {
    throw "MediaRenamer is running from $appDir (PID $(($running.ProcessId) -join ', ')). Close it and run the script again."
}
Remove-Item -Recurse -Force $inputDir, (Join-Path $dist "MediaRenamer") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $inputDir, $dist | Out-Null
Copy-Item $jar $inputDir

# Modules: those detected by jdeps + those used through reflection or as services:
#  jdk.crypto.mscapi (Windows certificates), jdk.localedata (localized dates and languages),
#  java.sql/java.scripting (Groovy), jdk.crypto.ec, jdk.charsets, jdk.unsupported, jdk.zipfs
$modules = "java.base,java.compiler,java.desktop,java.logging,java.management,java.net.http,java.prefs,java.xml,java.sql,java.scripting,java.naming," +
           "jdk.crypto.mscapi,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.unsupported,jdk.zipfs,jdk.accessibility"

$icon = Join-Path $root "src\main\packaging\MediaRenamer.ico"
jpackage --type app-image `
    --name MediaRenamer `
    --app-version $Version `
    --vendor "MediaRenamer" `
    --description "Renames series, movies and music with online data" `
    --copyright "Personal use" `
    --icon $icon `
    --input $inputDir `
    --main-jar MediaRenamer.jar `
    --main-class com.mediarenamer.Main `
    --add-modules $modules `
    --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-6" `
    --java-options "-Xmx1g" `
    --add-launcher "MediaRenamer-cli=$(Join-Path $root 'src\main\packaging\cli-launcher.properties')" `
    --dest $dist
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

# Third-party license notices next to the exe (the zip and the installer take them from here); the Java runtime's
# own notices are runtime\legal, which jlink keeps (only --no-man-pages/--no-header-files are stripped)
$notices = "THIRD-PARTY-NOTICES.txt"
Copy-Item (Join-Path $root $notices) $appDir
if (-not (Test-Path (Join-Path $appDir "runtime\legal\java.base\LICENSE"))) {
    throw "The runtime image has no legal\java.base\LICENSE: the OpenJDK notices must ship with the runtime."
}
$runtimeVersion = (Select-String -Path (Join-Path $appDir "runtime\release") -Pattern '^JAVA_VERSION="(.*)"').Matches.Groups[1].Value
if (-not (Select-String -Path (Join-Path $appDir $notices) -SimpleMatch $runtimeVersion -Quiet)) {
    Write-Warning "$notices does not mention the bundled Java runtime ${runtimeVersion}: update its Java runtime section."
}

$zip = Join-Path $dist "MediaRenamer-$Version-windows.zip"
Remove-Item -Force $zip -ErrorAction SilentlyContinue
Compress-Archive -Path $appDir -DestinationPath $zip
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entries = $archive.Entries | ForEach-Object { $_.FullName -replace '\\', '/' }
    foreach ($required in @("MediaRenamer/$notices", "MediaRenamer/runtime/legal/java.base/LICENSE")) {
        if ($entries -notcontains $required) { throw "$zip does not contain $required" }
    }
} finally {
    $archive.Dispose()
}

$setup = $null
if (-not $SkipInstaller) {
    $setup = Join-Path $dist "MediaRenamer-$Version-setup.exe"
    Remove-Item -Force $setup -ErrorAction SilentlyContinue
    & $iscc /Q "/DAppVersion=$Version" "/DSourceDir=$appDir" "/DOutputDir=$dist" "/DIconFile=$icon" "/DLogoFile=$(Join-Path $root 'docs\img\logo-128.png')" (Join-Path $root "src\main\packaging\MediaRenamer.iss")
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $setup)) { throw "Inno Setup (ISCC) failed" }
}

$size = "{0:N0} MB" -f ((Get-ChildItem -Recurse $appDir | Measure-Object Length -Sum).Sum / 1MB)
Write-Host "Executable: $(Join-Path $appDir 'MediaRenamer.exe')  ($size)"
Write-Host "Console:    $(Join-Path $appDir 'MediaRenamer-cli.exe')"
# The bundled runtime is the JDK that ran jpackage: keep it on the latest security update (quarterly Java CPU)
$runtime = (Select-String -Path (Join-Path $appDir "runtime\release") -Pattern '^JAVA_VERSION="(.*)"').Matches.Groups[1].Value
Write-Host "Runtime:    Java $runtime"
Write-Host "Zip:        $zip"
if ($setup) {
    Write-Host ("Installer:  {0}  ({1:N0} MB)" -f $setup, ((Get-Item $setup).Length / 1MB))
}

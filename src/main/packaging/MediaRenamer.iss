; Windows installer of MediaRenamer (Inno Setup 6).
; It packages the jpackage app-image (dist\MediaRenamer) built by scripts\build-exe.ps1, which compiles this script:
;   ISCC.exe /DAppVersion=x.y.z /DSourceDir=<dist\MediaRenamer> /DOutputDir=<dist> /DIconFile=<MediaRenamer.ico> /DLogoFile=<logo-128.png> MediaRenamer.iss
; Output: dist\MediaRenamer-<version>-setup.exe

#ifndef AppVersion
  #error AppVersion must be defined (/DAppVersion=x.y.z)
#endif
#ifndef SourceDir
  #error SourceDir must be defined (/DSourceDir=...\dist\MediaRenamer)
#endif
#ifndef OutputDir
  #error OutputDir must be defined (/DOutputDir=...\dist)
#endif
#ifndef IconFile
  #error IconFile must be defined (/DIconFile=...\MediaRenamer.ico)
#endif
#ifndef LogoFile
  #error LogoFile must be defined (/DLogoFile=...\docs\img\logo-128.png)
#endif

; The third-party license notices must ship with every installation (build-exe.ps1 copies them into the app-image)
#if !FileExists(AddBackslash(SourceDir) + "THIRD-PARTY-NOTICES.txt")
  #error THIRD-PARTY-NOTICES.txt is missing from SourceDir
#endif
; and so must MediaRenamer's own license (GNU GPL v3)
#if !FileExists(AddBackslash(SourceDir) + "LICENSE.txt")
  #error LICENSE.txt is missing from SourceDir
#endif
#if !FileExists(AddBackslash(SourceDir) + "runtime\legal\java.base\LICENSE")
  #error runtime\legal (the Java runtime notices) is missing from SourceDir
#endif

[Setup]
; Fixed AppId: new versions upgrade the existing installation instead of installing a second copy.
; FileRenamer (the name before 2.0) used another AppId and is uninstalled first: see [Code]
AppId={{84902B60-E036-4419-AA8B-D555ABC48287}
AppName=MediaRenamer
AppVersion={#AppVersion}
AppVerName=MediaRenamer {#AppVersion}
AppPublisher=MediaRenamer
AppCopyright=Copyright (C) 2026 AeLiu. License: GPL-3.0-only
AppPublisherURL=https://github.com/AFBenot/MediaRenamer
AppComments=Renames series, movies and music with online data
VersionInfoVersion={#AppVersion}
; Per-user install by default (no administrator rights); the user can choose "all users" in a dialog
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\MediaRenamer
DefaultGroupName=MediaRenamer
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
SetupIconFile={#IconFile}
; MediaRenamer logo in the top-right corner of the wizard instead of the generic Inno Setup image
WizardSmallImageFile={#LogoFile}
UninstallDisplayIcon={app}\MediaRenamer.exe
UninstallDisplayName=MediaRenamer
OutputDir={#OutputDir}
OutputBaseFilename=MediaRenamer-{#AppVersion}-setup
Compression=lzma2/max
SolidCompression=yes
; Closes a running MediaRenamer (asking first) so that its files can be replaced
CloseApplications=yes
RestartApplications=no
; Wizard language: the system one if it is among these, otherwise asks
ShowLanguageDialog=auto

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"
Name: "de"; MessagesFile: "compiler:Languages\German.isl"
Name: "pt"; MessagesFile: "compiler:Languages\Portuguese.isl"
Name: "it"; MessagesFile: "compiler:Languages\Italian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[InstallDelete]
; jpackage file names change between versions: remove the previous runtime and app before copying the new ones
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\app"

[Files]
; Includes LICENSE.txt and THIRD-PARTY-NOTICES.txt (next to the exe) and runtime\legal (Java runtime notices)
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\MediaRenamer"; Filename: "{app}\MediaRenamer.exe"
Name: "{autodesktop}\MediaRenamer"; Filename: "{app}\MediaRenamer.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\MediaRenamer.exe"; Description: "{cm:LaunchProgram,MediaRenamer}"; Flags: nowait postinstall skipifsilent

; The user data (%APPDATA%\MediaRenamer: preferences, encrypted API keys, presets, history) is never removed on uninstall

[Code]
const
  // Uninstall entry of FileRenamer, the application's name before 2.0
  LegacyUninstallKey = 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{01DD0761-E07C-4D08-A3A5-098D9ED58317}_is1';

// Silently uninstalls a FileRenamer installation (program files and shortcuts). Its uninstaller never removes
// %APPDATA%\FileRenamer, which MediaRenamer copies to its own data folder on the first start.
function UninstallLegacy(RootKey: Integer): String;
var
  Uninstaller: String;
  Code, I: Integer;
begin
  Result := '';
  if not RegQueryStringValue(RootKey, LegacyUninstallKey, 'UninstallString', Uninstaller) then
    exit;
  Uninstaller := RemoveQuotes(Uninstaller);
  Log('Uninstalling FileRenamer: ' + Uninstaller);
  // ShellExec: an all-users FileRenamer asks for elevation even when MediaRenamer is installed per user
  if not ShellExec('', Uninstaller, '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '', SW_HIDE, ewWaitUntilTerminated, Code) then begin
    Result := 'FileRenamer: ' + SysErrorMessage(Code);
    exit;
  end;
  // the uninstaller relaunches itself from a temporary copy: wait until its entry disappears
  I := 0;
  while RegKeyExists(RootKey, LegacyUninstallKey) and (I < 120) do begin
    Sleep(250);
    I := I + 1;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := UninstallLegacy(HKCU);
  if Result = '' then
    Result := UninstallLegacy(HKLM);
end;

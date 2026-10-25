# Development

## Requirements

- JDK 21 (tested with SapMachine 21.0.13). The exe bundles the runtime of the JDK that runs `jpackage`: keep it on the latest quarterly security update (Java CPU) and check the `Runtime:` line printed by `build-exe.ps1`.
- Maven 3.9 (tested with 3.9.15).

## Build, test and package

```bash
mvn -B package
```

It produces `target/MediaRenamer.jar` (fat jar with all the dependencies, `maven-shade-plugin`).

On the development machine Maven fails to download dependencies with `PKIX path building failed` (HTTPS
inspection by the antivirus). Solution: use the Windows certificate store:

```bash
MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" mvn -B package
```

(In PowerShell: `$env:MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`.) It is not set in
`.mvn/jvm.config` because it would break the build on Linux/macOS.

Run:

```bash
java -jar target/MediaRenamer.jar
```

To avoid touching your real preferences/history while testing: `-Dmediarenamer.data=C:/tmp/fr-data`.
Detailed logging: `-Dmediarenamer.debug=true` or `-debug` in the CLI.

## Windows executable and installer

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-exe.ps1
```

- It creates an *app-image* with `jpackage`: `dist\MediaRenamer\MediaRenamer.exe` (no console) and `MediaRenamer-cli.exe`
  (with console, defined in `src/main/packaging/cli-launcher.properties`), plus a `.zip` of the folder.
- Then it compiles the installer `dist\MediaRenamer-<version>-setup.exe` with **Inno Setup 6** from
  `src/main/packaging/MediaRenamer.iss` (the version, folders, icon and logo are passed with `/D...`). Install Inno Setup once with
  `winget install --id JRSoftware.InnoSetup -e --source winget --scope user`; the script finds `ISCC.exe` in `$env:ISCC`,
  the PATH or the usual install folders, and fails early if it is missing (`-SkipInstaller` builds only the exe and the zip).
- Installer behavior: fixed `AppId` (a new version upgrades the previous one in place and `[InstallDelete]` removes the old
  `runtime`/`app` folders first), per-user install by default (`PrivilegesRequired=lowest`, with a dialog to install for all
  users), wizard in English, Spanish, French, German, Portuguese and Italian (the system language when available),
  Start menu shortcut, optional desktop shortcut, closes a running MediaRenamer before replacing files and never deletes
  `%APPDATA%\MediaRenamer` on uninstall.
- To test it without touching a real installation: `MediaRenamer-<version>-setup.exe /VERYSILENT /CURRENTUSER /DIR="<temp folder>"`,
  run the installed exe with `APPDATA` pointing to a temp folder, then `unins000.exe /VERYSILENT`.
- The runtime is trimmed with `jlink` to the required modules (~90 MB in total). If you add code that uses another
  JDK module (e.g. `java.sql` directly), add it to `$modules` in the script: modules that are only loaded
  through reflection or as a service (`jdk.crypto.mscapi`, `jdk.localedata`) are **not** detected by `jdeps`.
- If the exe in `dist\MediaRenamer` is running, the script stops before deleting anything (Windows locks its
  files and the folder would be left half-deleted). Close the application and run it again.
- `-SkipBuild` reuses the existing `target/MediaRenamer.jar`; by default the version is the `pom.xml` one (without
  `-SNAPSHOT`) and `-Version X.Y.Z` forces it. To bump the version just change `<version>` in `pom.xml`: the window
  (`Settings.version()`, read from the jar manifest) and the User-Agent take it from there.
- An `.msi` (for corporate/GPO deployment) would require the WiX Toolset and `jpackage --type msi` (not included).

## Logo and icons

- The logo is drawn as vectors in `ui/Logo.java` (the app paints it at any size).
- `java -cp target/classes tools/GenerateIcons.java` regenerates `docs/img/logo-*.png` and `src/main/packaging/MediaRenamer.ico`.
- The interface icons (`ui/Icons.java`) are vectors too (24×24 grid) and take the theme color.

## Screenshots (`UiSnapshot`)

Development tool in `src/test/java/com/mediarenamer/ui/UiSnapshot.java`: it opens the UI with sample
files, applies a profile and saves PNGs of the window (light/dark), the search dialog and the cards. It works on
a **copy** of your preferences (in `%TEMP%\mediarenamer-snapshot…`), so it does not modify them; that copy includes
the API keys and is deleted automatically at the end, even if the tool fails. It needs the TMDb key and the given profile.

```bash
mvn -B test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" com.mediarenamer.ui.UiSnapshot docs/img <folder-with-movies> "<profile name>"
```

## Tests

| Test | What it covers |
|---|---|
| `SeasonEpisodeMatcherTest` | SxE patterns, multi-episode and ranges, specials, strict mode, years/codecs never episodes, `find`, hostile names |
| `MediaDetectionTest` | Series/movie name and year, folders, release info cleanup, title words that are also release words, hostile names |
| `ReleaseInfoTest` | Release tokens as whole words, minimum terms per category, `{group}`, `{vs}`, extras (Plex suffixes), hostile names |
| `AniDBTitleDumpTest` | AniDB title dump lines (`aid\|type\|lang\|title`, comments and malformed lines skipped) |
| `ExpressionFormatTest` | Format engine, variables, methods, `{plex}`, syntax errors, illegal characters, variables named like imported classes (`{XEM}`) |
| `ReferenceCorpusTest`, `MatchingReferenceTest` | Reference corpus of the behavior described in [MATCHING.md](MATCHING.md) and [FORMAT.md](FORMAT.md): SxE, release info, name detection, format engine, `{vf}` classes, matcher and episode matching (data in `src/test/resources/com/mediarenamer/cleanroom/`) |
| `ConflictTest` | "If it exists" policies, test mode without touching the disk, cancellation between files |
| `NameValidatorTest` | Name pre-check and cleanup suggestions |
| `EpisodeChooserTest` | Episode chooser filter |
| `SetupWizardTest` | First-run wizard: steps in order, live language change, "Skip" saves the language; every API key has label and info text |
| `SettingsFirstRunTest` | First run = no preferences file at startup |
| `EpisodeMatchingTest` | `Matcher` + `EpisodeMetrics`: SxE, multi-episode, season from the folder, strict mode, bare numbers vs absolute numbers |
| `JellyfinClientTest` | Jellyfin scan (`POST /Library/Refresh`, token header, `/System/Info`, wrong key, server down, URL, profile precedence) and the API keys page by version (`/System/Info/Public`) against a local `HttpServer` |
| `RenamePanelLayoutTest` | The two list cards are equally wide and the window is symmetric, in every language and window width |
| `SettingsPagesTest` | Every Preferences category has title and description, every option has its ⓘ and every `info.*` text exists; the Plex scan option is only saved with URL and token; the status line never renders server text as HTML; the data language comes from the list; unsaved changes are tracked per category and cleared when a value goes back |
| `LanguagePickerTest` | Language list (every language by name in the interface language, only `xx` / `xx-YY` tags, unique names), search ignoring case and accents and matching tags, typed text resolution, filter/commit, no match keeps the previous value, empty field = empty value |
| `PresetsDialogTest` | Profiles: unsaved edits survive switching profile, Save stores all of them, undoing an edit clears its mark, format editor mode by data source |
| `FormatVariablesTest` | The *Variables* tooltip lists exactly the `@Define` variables, with translated group titles |
| `RenamePanelSelectionTest` | Adding files selects the first row in both lists when nothing was selected and keeps an existing selection |
| `ToolsTest` | Tool paths of Preferences → Tools: empty = automatic, the tool or its folder accepted, other files, missing, relative and malformed paths rejected |
| `FpcalcLookupTest` | `tool.fpcalc` may be the program or its folder; the automatic location shown in Preferences ignores the preference |
| `I18nTest` | The 6 languages have the same keys and parameters, every key used in the code exists, plurals, `--conflict ask` letters, default language |
| `TrustStoresTest` | HTTPS trust: a chain is accepted if `cacerts` or the Windows store trusts it, rejected if none does |
| `SettingsSecretsTest` | API key encryption (DPAPI on Windows), migration of a plain-text file without losing preferences, undecryptable value, re-encryption of FileRenamer's DPAPI keys |
| `WebRequestXmlTest` | XML parsing without XXE: external entities and parameter-entity exfiltration are not read, entity expansion is limited, normal documents and plain DOCTYPEs still parse |
| `WebRequestLimitsTest` | 64 MB body limit, gzip bomb rejected, purge of the expired cache |
| `FormatSecurityTest` | Format blacklist: processes, reflection, file writing, network, annotations, imports rejected; ordinary code and **every `{...}` example of the docs** still compile |
| `ExpressionLimitsTest` | Endless block stopped after 10 s, interruptible evaluation, endless recursion only empties its block, error messages, `@Grab` off, compiled-class cache reset |
| `FilePathSafetyTest` | Dot-only segments from block values or block code removed, literal `../` kept, empty leading block does not make the target absolute, control characters removed |
| `BatchCollisionTest` | Same target twice in one run (every policy, test mode, case-insensitive), pending source never replaced, indexing skips reserved names |
| `ToolLookupTest` | No tool lookup relative to the working directory (planted DLL ignored), malformed `PATH` entries, process timeout |
| `SaveSafetyTest` | Atomic writes, history round trip with special characters, unique profile ids |
| `JellyfinInsecureUrlTest` | Plain http warning only for servers that do not look local |
| `PlexClientTest` | Plex scan (sections + refresh per library, token only in the header, hostile library keys ignored), *Test connection*, wrong token, server down, default port; plex.tv PIN sign-in (polling, cancel, expiry, hostile tokens rejected), own servers only with safe addresses best first (relays, `file:`, user info rejected), address probing without token, bounded candidates, redacted logs; against a local `HttpServer` |
| `AppFoldersMigrationTest` | copy of the FileRenamer data folder: everything copied and the old one kept, an existing folder is never overwritten, nothing to migrate, interrupted copy repeated |

The tests use no network (the Jellyfin and Plex ones start a server on `127.0.0.1`) **and none of your preferences**: `pom.xml`
sets `mediarenamer.data=target/test-data` in surefire, because `Settings.get()` would load (and migrate)
`%APPDATA%\MediaRenamer`. For tests against real APIs, write tests that check
`Boolean.getBoolean("network")` and run them with `mvn test -Dnetwork=true` (the pom already passes the property).

### Testing in the application

1. **Every change, on this machine with isolated data**: the jar or the freshly built exe with
   `-Dmediarenamer.data=<temp folder>` (or `APPDATA` pointing to a temp folder for the exe) and test files in a temp folder;
   GUI checked with screenshots, CLI with `--db file` (no key needed). Fast, and it never touches your preferences, keys or history.
2. **Optional, on request: the installer in Windows Sandbox** (a clean, disposable Windows without Java or
   MediaInfo, discarded when it closes). It is not part of the routine checks:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\test-sandbox.ps1
   ```

   `scripts/test-sandbox.ps1` writes a `.wsb` that maps `dist\` and `scripts\sandbox\` (read only) and a results
   folder, starts the sandbox and waits for `scripts/sandbox/smoke-test.ps1`, which runs inside it:

   | Step | Checks |
   |---|---|
   | Install for all users (silent) | exit code, files in `Program Files`, uninstall entry with the version, Start menu shortcut |
   | CLI | usage in English and German, graceful errors without MediaInfo and without API key, `-rename --action test` and a real rename in File mode, `history.xml` |
   | First run | the setup wizard appears (screenshot `wizard.png`); closing it saves `settings.properties` |
   | GUI | then the main window appears and keeps running (screenshot `gui.png`) |
   | Uninstall | folder and uninstall entry removed, `%APPDATA%\MediaRenamer` kept |
   | Per-user install | install, run, uninstall |
   | Upgrade from FileRenamer | the newest `dist\FileRenamer-*-setup.exe` installed per user, a `%APPDATA%\FileRenamer` with a key encrypted with the old DPAPI entropy; then the new setup: old folder, entry and shortcut removed, data copied, key re-encrypted with the new entropy, CLI in the migrated language |

   The report is in `target\sandbox-test\<timestamp>\results\report.md` (plus `report.json`, `gui.png`, install logs and
   `transcript.log`); the script exits with 1 if any step fails. The sandbox shuts down by itself (`-KeepOpen` keeps it
   open to inspect it). It needs the *Windows Sandbox* feature, enabled once as administrator:
   `Enable-WindowsOptionalFeature -Online -FeatureName Containers-DisposableClientVM -All` and a restart. Only one
   sandbox can run at a time.

Quick manual test without keys (File mode, test action):

```bash
java -jar target/MediaRenamer.jar -rename "D:/Downloads" --db file --format "{fn.lower()}" --action test
```

## Conventions

- **Everything in English**: code (identifiers, comments, Javadoc, log and exception messages that are not
  user-facing), tests and documentation. Spanish (or any other language) only appears in the translated
  `.properties` bundles and in test data that deliberately uses non-English titles.
- User-facing texts go in the `i18n` `.properties` files (see *Texts and languages*), never hard-coded.
- No network or MediaInfo on the Swing thread: use `SwingWorker`.
- No API keys in the code or in tests.

## Security standards

**Every change must meet these standards and must not introduce potential vulnerabilities.** A change that adds one
is not finished, even if it works; if a requirement cannot be met, say so explicitly and document the residual risk
instead of leaving it silent. Review the change against this list before closing it:

- **Untrusted input**: answers of online services (AniDB is plain http), metadata values (titles are community-edited),
  file names, profiles/formats copied from others and files dropped by the user are hostile until validated.
- **XML**: always parse with `WebRequest.parseXml` (no external entities, DTDs or schemas). Never create another
  `DocumentBuilderFactory`, `SAXParser` or `XMLInputFactory` without the same protections.
- **Network**: `https://` for every new endpoint (plain http only where the service offers nothing else, documented);
  never weaken certificate validation (no trust-all managers or hostname verifiers); go through `WebRequest`, which
  limits the body size; never put keys in URLs that are logged without `WebRequest.redact`.
- **Paths**: a target built from data must stay inside its target folder (no `..` or root escapes coming from values;
  see `ExpressionFileFormat`); validate names with `FileUtilities`/`NameValidator`.
- **Format code**: everything that compiles user Groovy goes through `ExpressionFormat` (blacklist `FormatSecurity`,
  time limit). A new global function, variable or extension method must not give a way around it (no access to
  processes, reflection, class loaders, file writing or the network); add the case to `FormatSecurityTest`.
- **External programs and native libraries**: only absolute paths (preference, `lib` next to the application,
  Program Files, absolute `PATH` entries), never the working directory; arguments as a list, never a shell command
  string; always a timeout (`util.Processes`).
- **Files and data loss**: never delete or overwrite user files silently; writes of the application's own files are
  atomic (`FileUtilities.writeAtomically`); secrets are stored encrypted (`Secrets`) and files with secrets are created
  with restricted permissions from the start.
- **Secrets**: no keys in code, tests, logs, exception messages, screenshots or commits.
- **Resources**: caches and buffers are bounded; anything that may block (network, MediaInfo, user code) runs off the
  Swing thread and can be cancelled or times out.
- **Dependencies and runtime**: before a release check the known vulnerabilities (CVEs) of the libraries in `pom.xml`
  and of the JDK, update to fixed versions, and build the exe with a JDK on the latest quarterly security update.
- **Tests**: every security fix or protection gets a test that reproduces the attack (see `WebRequestXmlTest`,
  `FormatSecurityTest`, `BatchCollisionTest`, `ToolLookupTest`, `ToolsTest`).

## Texts and languages

- Every visible text (GUI, status bar, errors, CLI) is obtained with `I18n.tr("key", args...)` or
  `I18n.plural("key", n)` (`import static com.mediarenamer.app.I18n.tr;`).
- The files are in `src/main/resources/com/mediarenamer/i18n/`: `messages.properties` (English, base language and
  fallback) and `messages_es`, `_fr`, `_de`, `_pt`, `_it`. They are UTF-8 (Java 9+ reads them as such).
- **When adding a text, add the key to all six files** with the same parameters: `I18nTest` fails if one is
  missing or if the code uses a key that does not exist.
- Parameters `{0}`, `{1}`...: they are replaced literally (it is not `MessageFormat`), so apostrophes (`l'interface`)
  are not doubled and format variables such as `{n}` can be written without escaping. Line breaks: `\n`.
- Plurals: `key.one` and `key.many`.
- To try another language without touching Preferences: `MEDIARENAMER_UI_LANGUAGE=fr` (or `-Dmediarenamer.ui.language=fr`).
- A new language: add its `Locale` to `I18n.SUPPORTED` and a complete `messages_xx.properties`.

## How to extend

| I want... | Where |
|---|---|
| A new data source | [DATA_SOURCES.md](DATA_SOURCES.md#adding-a-new-source) |
| A format variable | [FORMAT.md](FORMAT.md#adding-a-variable) |
| A format method (`{n.myMethod()}`) | `public static` method in `FormatMethods` with the receiver type as first parameter |
| A global function (`{myFunction{...}}`) | public method in `FormatScript` |
| Recognizing another release word | the term list of its category in `ReleaseInfo` + `ReleaseInfoTest` |
| Another episode pattern | `SeasonEpisodeMatcher` + test |
| Another rename action | `RenameAction` (+ GUI combo automatically) |
| An example/profile in the editor | `FormatDialog.EXAMPLES` |

## Known packaging issues

- **Do not use Shade's `GroovyResourceTransformer`**: it drops `META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule`
  and the format methods disappear (`{n.space('.')}` returned empty). It is already documented in `pom.xml`.
- If a future dependency brings its own extension module, the descriptors will have to be merged by hand.

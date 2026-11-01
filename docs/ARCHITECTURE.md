# Architecture

Java 21 application in a single Maven module. Root package `com.mediarenamer`.

```
com.mediarenamer
├── Main                    Entry point: without arguments or with only paths opens the GUI; with options, CLI
├── app/                    Settings (preferences + keys), I18n (languages), AppFolders, Logging,
│                           SingleInstance (later starts hand their files to the open window),
│                           ContextMenu (Explorer menu with the profiles, in HKCU through a .reg import)
├── web/                    Data model and clients of online services
├── media/                  MediaInfo (JNA), name detection, ReleaseInfo
├── similarity/             SxE, dates, normalization, metrics and Matcher
├── format/                 Groovy expression engine and format variables
├── rename/                 Actions, conflicts, history, rename engine
│   └── match/              Strategies per mode: Episode, Movie, Music, File
├── cli/                    Command line (-rename, -list, -mediainfo, -history...)
├── ui/                     Swing GUI: RenamePanel and dialogs
└── util/                   FileUtilities (name and path validation, atomic writes), Processes (tools with timeout)
```

## Flow of a rename

```
 Files ──► AutoCompleteMatcher (depending on the source chosen in "Fetch Data")
               │  EpisodeListMatcher: groups by series → search → pick series → episode list
               │                      → Matcher(EpisodeMetrics) → MultiEpisode
               │  MovieMatcher:       ids in name/.nfo → "Title (Year)" → search → pick
               │  MusicMatcher:       AcoustID (fpcalc) or ID3 (MediaInfo)
               │  FileMatcher:        the file itself
               ▼
          List<Match<Object, File>>  (+ companions: subtitles/nfo inherit the video's object)
               ▼
 RenameEngine.format ──► ExpressionFileFormat(mode format) × MediaBindingBean(object, file)
               │          → name/path → resolve(): source folder or absolute path + extension
               ▼
 RenameEngine.execute ──► ConflictAction → RenameAction (MOVE/COPY/...) → History (history.xml)
```

## Key components

### `app` — startup and preferences
- `Settings` (preferences + overrides by environment/`-D`), `AppFolders`, `Logging`.
- `I18n`: interface and CLI texts from
  `resources/com/mediarenamer/i18n/messages*.properties` (English base + `es`, `fr`, `de`, `pt`, `it`). `tr(key, args...)`
  replaces `{0}`, `{1}`... literally (no `MessageFormat`), `plural(key, n)` picks `key.one`/`key.many`.
  The language comes from `ui.language` (or the system); `applyToSwing()` also sets the one for Swing's own texts.
  The labels of the enums (`RenameAction`, `ConflictAction`, `Mode`) and of `Quality.describe()` are translated by
  key (`action.MOVE`, `conflict.ASK`, `mode.Episode`, `quality.size`). Changing the language in Preferences
  rebuilds the window (`MainFrame.reload()`) keeping the file list.
- `Updates`: the update logic without any UI. Fetches `releases/latest` of the repository from the GitHub API
  (`api.github.com/repos/AeLui/MediaRenamer/releases/latest`; the URL can be overridden with
  `-Dmediarenamer.updates.url`, for tests and manual trials only), parses it into a release (version, notes, page,
  assets with name, size and SHA-256 digest), compares versions, decides whether an automatic check is due
  (`updates.autoCheck`, at most every 24 h from `updates.lastCheck`) and downloads the `MediaRenamer-<version>-setup.exe`
  asset into a new temp folder (leftovers older than an hour are swept at start-up), only from https GitHub hosts, verifying its size and SHA-256 (anything unverifiable,
  a redirect to another host or a name that would leave the folder is refused). Releases are not code-signed:
  integrity relies on the digest over HTTPS.
- `TrustStores`: HTTPS trust = JDK `cacerts` **or** Windows `Windows-ROOT` (a combined
  `X509ExtendedTrustManager` set as the default `SSLContext` in `Main`), so that HTTPS works both on a clean Windows and
  behind an antivirus/proxy that inspects HTTPS. See [CONFIGURATION.md](CONFIGURATION.md#https-certificates).
- `Secrets`: encrypts the keys of `Settings.SECRET_KEYS` with DPAPI via JNA (`Crypt32.CryptProtectData`)
  on Windows or AES-GCM on other systems; `Settings` decrypts on read, encrypts on save and migrates plain-text values on load. DPAPI values written by FileRenamer (the name before 2.0, other
  entropy) are still decrypted and encrypted again with the current entropy (`Secrets.reprotect`).
- `AppFolders`: data folder (`%APPDATA%/MediaRenamer`, `~/.mediarenamer`, or `-Dmediarenamer.data`). If it does not exist
  and `%APPDATA%/FileRenamer` does, `migrateLegacy` copies it first (through a temporary `.migrating` folder renamed at
  the end; the old folder is kept as a backup). `installDir()`: folder of `MediaRenamer.exe` (`jpackage.app-path`) or
  of the jar, where external tools are looked for in `lib` (never relative to the working directory).

### `web` — data sources
- Interfaces: `Datasource`, `EpisodeListProvider`, `MovieLookupService`, `MusicLookupService`.
- Model: `SearchResult`, `Series`, `Movie`, `SeriesInfo`, `Episode`, `MultiEpisode`, `AudioTrack`,
  `MovieInfo`, `SimpleDate`, `SortOrder`, `EpisodeFormat`.
- Clients: `TMDbMovieClient`, `TMDbTVClient` (base `TMDbClient`), `TheTVDBClient` (v4),
  `AniDBClient`, `AcoustIDClient`, `ID3Lookup`; mappings `AnimeLists`, `XEM`.
- Media servers: `JellyfinClient`:
  `POST /Library/Refresh` after renaming and `GET /System/Info` for *Test connection*. `PlexClient`:
  `GET /library/sections` + `GET /library/sections/{key}/refresh` after renaming, `GET /` for *Test connection*, and
  the plex.tv PIN sign-in (`requestPin` → `waitForToken` → `servers` → `firstReachable`). `MediaServers`: what both
  share (URL normalization, plain-http warning, readable errors, profile precedence `shouldScan`).
- Infrastructure: `WebRequest` (HttpClient + disk cache per URL with TTL, purged at startup; bodies limited to 64 MB; `parseXml` without external entities/DTDs, i.e. no XXE), `FloodLimit`
  (request limiter), `Json` (null-tolerant access), `WebServices` (registry and `--db` aliases).

### `media`
- `MediaInfo`: loads `MediaInfo.dll` through JNA requesting JSON output; if missing, uses the `mediainfo` CLI.
- `MediaCharacteristics`: derives `{vf}`, `{vc}`, `{ac}`, `{channels}`, `{hdr}`... from the MediaInfo data. `{vf}` is the
  largest resolution class (4320…360 lines) whose 16:9 reference frame the width or the height reaches at 98 %
  (`videoFormat(width, height, interlaced)`).
- `MediaDetection`: series/movie name and year from the file and its folders.
- `ReleaseInfo`: release token patterns (format, edition, source, 3D, repack, language, subtitle kind, known groups,
  filler words, extras), built from term lists in the class.
- `MediaTypes`: categories by extension (video, audio, subtitle, image, nfo).

### `similarity`
- `SeasonEpisodeMatcher`: SxE notations in three families (explicit, episode only, lenient bare numbers).
- `DateMatcher`, `Normalization`, `NameSimilarityMetric` (q-grams).
- `Matcher`: one-to-one cascade matcher (score vectors compared metric by metric, mutual best pairs first).
  `EpisodeMetrics`: metric sequence (identifier, title, series, numbers, name).
- Details in [MATCHING.md](MATCHING.md).

### `format`
- `ExpressionFormat`: compiles text + Groovy `{...}` blocks; failing blocks are left empty. Subclasses adapt binding
  values, block text, literal text and the final result through four hooks. Blocks get
  `@TimedInterrupt(10 s)` and `@ThreadInterrupt` through `ASTTransformationCustomizer`s; at most 1000 compiled blocks are
  cached, then the cache and the `GroovyClassLoader` are replaced so old classes can be unloaded. `@Grab` is disabled.
- `FormatSecurity`: `CompilationCustomizer` at `CONVERSION` (before any AST transformation) that rejects by name what a
  block must not do (processes, reflection, class loading, file writing, network, imports, annotations, dynamic names).
  See [FORMAT.md](FORMAT.md#security-restrictions).
- `BindingNameCustomizer`: makes a variable named like an imported class (`{XEM.scene}`) read the variable, not the
  class (unless the block declares that name itself).
- `ExpressionFileFormat`: path normalization (separators in values, illegal characters, dot-only segments coming from
  blocks, separator after empty leading blocks).
- `ExpressionBinding`: `groovy.lang.Binding` that resolves variables by calling `@Define` getters.
- `MediaBindingBean`: all the variables. `FormatMethods`: Groovy extension methods
  (registered in `META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule`). `FormatScript`:
  global functions (`any`, `allOf`, `csv`...). `NamingStandard`: `{plex}`, `{jellyfin}`...
- Details in [FORMAT.md](FORMAT.md).

### `rename`
- `Mode` (Episode/Movie/Music/File): default format, sources and matcher of each mode.
- `RenameEngine`: format, resolve target and extension, execute, undo. Within one run a target already produced is
  never replaced or indexed onto (the later file fails), and a file still pending in the run is never replaced.
- `RenameAction`, `ConflictAction`, `History` (`history.xml`).
- `Preset` (a profile) and `Presets` (`presets.json` store +
  own fields `outputFolder`, `jellyfinScan` and `plexScan`, which take precedence over `jellyfin.scan` / `plex.scan` via
  `JellyfinClient.scanAfterRename(presetOverride)` / `PlexClient.scanAfterRename(presetOverride)`).
- `Destinations`: base folder of the target (profile > Preferences per type > file's folder).
- `Quality`: quality and size of a file (MediaInfo) for the conflict policies and the conflict dialog.
- `RenameEngine.ConflictResolver` / `Decision`: "Ask each time" (GUI: `ui.ConflictDialog`; CLI: console).

### `ui`
- `MainFrame` → `RenamePanel` (synchronized *Source files* / *Target files* lists, *Fetch data* menu
  with mode, order and language, action and conflict).
- *Profiles ▾* button: applies a profile (format, source, order, mode, language, action) and runs *Fetch data*.
  Profiles are `Preset` objects in the code and `presets.json` on disk (they were called presets up to 2.0).
- *Apply* button (✓ icon): runs the chosen action (`RenamePanel.rename()`).
- *Fetch data* menu: Movies, Series, Music, File, in that order.
- Look: `Theme` (FlatLaf with violet accent, rounded corners, light/dark on the fly), `Logo` and `Icons`
  (Java2D vectors), `MainFrame` (header with logo and shortcuts, status bar), `RenamePanel` (cards, two-line
  rows with an icon per type, empty state for dropping files, mode/order/language/source chips).
- `SearchDialog`: movie/series search with posters (`PosterCache`, background download and 30-day
  cache), source selector, new search and a card on mouse hover (`MediaDetails`: one extra request to the
  service, cached). It is used by the matchers when there is ambiguity (`SearchDialog.SwingSelector`, via
  `Selector.Request` with source, language and search function) and by the *Manual search* button.
- `EpisodeChooserDialog`: choose the episode of a row; the episode
  list is cached per series/order/language. Reordering by dragging in *Target files*: `RenamePanel.ReorderHandler` +
  `moveMatch(from, to)` (shifts the matches, not the files).
- `ValidateDialog` + `rename.NameValidator`: name pre-check with
  errors/warnings and a cleanup suggestion (`NameValidator.clean`).
- `ProgressOverlay`: modal layer in the window's *glass pane* (not another window) with progress and Cancel, used
  while fetching data and renaming; `RenameEngine.execute(..., Progress, BooleanSupplier cancelled)` checks for
  cancellation between files and `Result.cancelled()` reports it. `ProgressOverlay.step(...)` switches to a final
  non-cancellable step: `RenamePanel.rename()` uses it for *Scanning libraries…* (Jellyfin, then Plex; a failure of one
  does not stop the other) inside the same `SwingWorker`.
- `SetupWizard`: first-run wizard (language → API keys → media servers), shown by `MainFrame.open()`
  before the main window when `Settings.isFirstRun()` (there was no `settings.properties` at startup). Changing the
  language rebuilds it at once; "Finish" saves everything, "Skip setup"/closing saves only the language.
- `SettingsPages`: the settings pages by category (General, Data services, Target folders, Media servers,
  Tools), each option with label · ⓘ (tooltip with what it is for, also on the field) · field · optional button. Used by
  `PreferencesDialog` (category sidebar + `CardLayout`, one Save for all, last category remembered during the session)
  and by `SetupWizard` (Data services and Media servers steps). The Media servers page has a Jellyfin section and,
  below a separator, a Plex one; the scan option of each is saved only with its URL and key/token. The Jellyfin "Get"
  button uses `JellyfinClient.apiKeysPage(url)` (route by server version). `SettingsPages.StatusLabel`: the status line
  under *Test connection* (and under each tool path), wrapped, hidden while empty and HTML-escaped (it may show server
  names). Every option remembers its starting value (`track`), so `isDirty(Category)` tells `PreferencesDialog` which
  categories to mark and whether closing must ask *Save / Discard / Cancel* (`PreferencesDialog.askUnsaved`, also used by
  Profiles). The Tools page checks each path with `media.Tools.check` (name and existence only, never runs the tool) and
  shows the automatic location (`MediaInfo.detectedPath()`, `AcoustIDClient.detectedFpcalc()`); saving calls
  `MediaInfo.reset()` so a new path is tried without restarting.
- `LanguagePicker`: editable `JComboBox` with every ISO 639-1 language and language-country pair of the JDK, shown by
  name in the interface language; typing filters (`matches`, case and accents ignored), Enter/leaving the field
  resolves the text (`resolve`), and text matching nothing restores the previous value. Used for the data language in
  Preferences/wizard and the profile language.
- `PlexSignIn`: the Plex *Sign in…* button (PIN sign-in in `SwingWorker`s, cancellable; fills token and, if empty, URL).
- `ApiService`: the API key fields shared by `PreferencesDialog` and `SetupWizard` (settings key, label, info text for the
  ⓘ tooltip, "Get" URL) and the reusable "Test connection" button of the media servers (`serverTestButton`).
- `UpdateCheck`: runs `Updates` off the event thread a few seconds after the window opens (automatic: silent when
  there is nothing new or the request fails; skips `updates.skipVersion`) and from *About → Check for updates* (always
  answers), and opens `UpdateDialog`; the automatic dialog waits while a rename or data fetch is running.
- `UpdateDialog`: new version, release notes, **Update** / **Later** / **Skip this version** (automatic flow only). In
  an installed copy it downloads with progress and cancel, runs the installer silently (`/SILENT /NORESTART /LAUNCH`;
  `MediaRenamer.iss` relaunches the app after a silent update) and closes the main window (it refuses while a rename or fetch is running); in a portable or development
  copy, or on any failure, it opens the release page.
- `AboutDialog`: version, author, license, credits, Java, data folder and notices; opened from the ⓘ button of the
  header or by clicking the version in the status bar. Its *Third-party licenses* button opens `ThirdPartyLicensesDialog`,
  which shows `META-INF/THIRD-PARTY-NOTICES.txt` (copied by Maven from the repository root; read bounded, shown as plain
  text); the *GNU GPL v3* link opens the same dialog with `ThirdPartyLicensesDialog.ownLicense`, showing
  `META-INF/mediarenamer/LICENSE` (the root `LICENSE`, in its own folder so it never clashes with the dependencies'
  `META-INF/LICENSE` in the shaded jar). *♥ Sponsor* opens the fixed `AboutDialog.SPONSOR_URL` (GitHub Sponsors) in the
  browser.
- Dialogs: `PresetsDialog` (per-profile drafts compared with the form as first shown, so switching profile keeps
  unsaved edits; modified entries drawn in `Theme.MODIFIED`; Save stores every draft; *Editor…* opens
  `FormatDialog.forProfile` with the mode of the chosen data source), `FormatDialog` (RSyntaxTextArea + preview evaluated
  in a `SwingWorker`, cancelled by the next edit; the *Variables* link lists `FormatDialog.VARIABLES` in its tooltip),
  `BindingsDialog`,
  `HistoryDialog` (one table Date/Source/Target from `History.entries()`, newest first, pages of 50 rows, *Revert* of the
  selected rows and CSV export with `History.writeCsv`), `PreferencesDialog` (categories in a sidebar, see `SettingsPages`).
- All network/MediaInfo work runs in a `SwingWorker`; questions to the user from background threads
  go through `SearchDialog.SwingSelector` (`invokeAndWait`).

## Persistence

| What | Where |
|---|---|
| Preferences and keys | `%APPDATA%/MediaRenamer/settings.properties` (or `MEDIARENAMER_*` environment variables) |
| HTTP cache | `%APPDATA%/MediaRenamer/cache/xx/<sha1(url)>` |
| History | `%APPDATA%/MediaRenamer/history.xml` |

The data folder can be changed with `-Dmediarenamer.data=<path>` (useful for testing). History, profiles and
preferences are written atomically (temporary file + rename): a crash while saving leaves the previous version.

## Dependencies

| Library | Use |
|---|---|
| Groovy 4 (`groovy`, `groovy-json`) | Formats and JSON parsing |
| JNA 5 | Loading `MediaInfo.dll`, DPAPI |
| FlatLaf | Look & feel |
| RSyntaxTextArea | Format editor |
| JUnit 5 | Tests |

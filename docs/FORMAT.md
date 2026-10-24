# Format language

Literal text and `{...}` blocks with **Groovy** code.

```
{n} - {s00e00} - {t}                      → The Office - S02E03 - Office Olympics
{n} ({y})/{n} ({y}) [{vf}]                → Avatar (2009)/Avatar (2009) [1080p]
{plex}                                    → TV Shows/The Office/Season 02/The Office - S02E03 - Office Olympics
{n.space('.')}.{s00e00}                   → The.Office.S02E03
{n} - {s00e00}{' [' + vf + ']'}           → if {vf} does not exist, the whole block disappears
{any{audio.Language}{'und'}}              → first non-empty value
{allOf{vf}{vc}{ac}.joining(' ')}          → 1080p x264 AC3
```

## Evaluation rules

1. Text outside braces is literal. Braces can be nested inside a block (Groovy closures). They are counted without
   regard to quotes, so a brace inside a string must be balanced (`{'a{b}c'}` works, `{'{'}` is a syntax error; write
   `'\u007B'` instead). An empty block `{}` adds nothing.
2. Each block is evaluated separately. **If it throws an exception** (variable without a value, `match` without a match...)
   **the block is replaced by an empty string**; the error is kept as "suppressed" and shown in the editor.
3. If the final result is empty → error `Expression yields empty value`.
4. Syntax errors: `SyntaxError: unexpected token: }` and `SyntaxError: missing token: }` for unbalanced braces,
   `SyntaxError: <compiler message> in {<block>}` for invalid Groovy, and `SecurityError: ... not allowed in formats in
   {<block>}` for forbidden code (see [Security restrictions](#security-restrictions)).
5. Since the result is a path (`ExpressionFileFormat`):
   - In the **text values** of the variables, `/` and `\` are replaced by a space (`AC/DC` → `AC DC`).
     `File` objects and `{plex}` keep their separators (they create folders).
   - Line breaks are removed, spaces collapsed and in each segment `\ / : * ? " < > |` and trailing dots are removed.
   - The result never leaves the target folder by accident: a path segment made only of dots (`.`, `..`) that comes
     from a block (e.g. a title `..` from an online database, or `{'../x'}` written in the block code) is removed
     together with the separator after it, and if every block before the first
     literal `/` is empty (`{ny}/{n}` without a year) that `/` is dropped instead of turning the target into the root
     of the drive. `..` written literally in the format (`../Sorted/{n}`) is kept.
6. The result can be a name (same folder), a relative path (subfolders) or an absolute path (`D:/Media/...`).
7. **Extension**: the original one is always added (subtitles keep the language: `.spa.srt`, `.en.forced.srt`), unless
   the format already ends with a known extension (`{n} ({y}).mp4`): then the format rules.
8. **Folder**: target = `<folder>/<format>`, with folder = the profile's, or the Preferences one for that type, or the
   file's own. If the format is an absolute path (`Z:\Movies\{n} ({y})`) it is used as is.
9. **Time limit**: a block that runs longer than 10 seconds (an endless loop...) is stopped and stays empty
   (`Execution timed out after 10 seconds`). The format editor evaluates the preview in the background, so a slow
   block never freezes the window.

## Security restrictions

Formats are often copied from forums or shared profiles, so a block cannot do anything dangerous. These are rejected
when the format is compiled, with `SecurityError: not allowed in formats: …`:

- running programs (`'cmd'.execute()`, `Runtime`, `ProcessBuilder`), `System`, threads and `exit`;
- classes, reflection and class loading (`.class`, `getClass()`, `metaClass`, `Class.forName`, `invokeMethod`,
  `java.lang.reflect`), evaluating more code (`evaluate`, `GroovyShell`, `Eval`), annotations (`@Grab`, `@ASTTest`),
  `import` and class declarations;
- changing files (`delete`, `renameTo`, `write`, `withWriter`, `append`, `mkdirs`, `f.text = …` and any other
  assignment to a property) and `Files`;
- the network (`URL`, `toURL()`, `Socket`, `HttpClient`, `java.net`);
- dynamic method or property names (`n."$name"()`), which would hide any of the above.

Everything formats normally do is still allowed: the variables, the helper methods, String/date/collection/regex methods,
closures, local variables (`{def x = n; x.upper()}`), `java.time`, `Math` and reading files with `text()`, `csv()`
and `lines()`. The check works on names (format code has no static types), so the blocked method names are rejected on
any value. It is a blacklist that raises the bar against hostile formats, not a complete sandbox: only use formats whose
code you understand.

## Variables

Status of each variable (a getter annotated with `@Define` in `MediaBindingBean`):
✅ implemented · ≈ implemented with differences · ❌ pending.

### Identification
| Variable | Status | Description |
|---|---|---|
| `n` | ✅ | Series, movie or artist |
| `y` | ✅ | Year (movie, series start, album) |
| `ny` | ✅ | `Name (Year)` unless the name already includes the year |
| `t` | ✅ | Episode title (multi: `A & B`), track or movie |
| `s`, `e`, `es` | ✅ | Season, episode, list of episodes (integers) |
| `s00`, `e00`, `sxe`, `s00e00` | ✅ | `02`, `03`, `2x03`, `S02E03` (multi: `S01E01-E02`) |
| `sn` | ≈ | `Season N` / `Specials` (not the season name from the database) |
| `absolute`, `special`, `regular` | ✅ | Absolute number, special number, whether it is a regular episode |
| `d`, `airdate`, `startdate`, `age` | ✅ | Dates (`SimpleDate`, use `.format('yyyy-MM-dd')`) and days since airing |
| `id`, `tmdbid`, `tvdbid`, `imdbid` | ✅ | Identifiers |
| `alias`, `primaryTitle` | ✅ | Alternative titles / original title |
| `episode`, `episodes`, `movie`, `music`, `series`, `object`, `type` | ✅ | Whole objects |
| `anime` | ✅ | Whether it is anime (AniDB or Anime genre) |
| `dt`, `ct` | ❌ | Date-time / creation date |

### Extended metadata
| Variable | Status | Description |
|---|---|---|
| `info` | ✅ | `MovieInfo` (movies) or `SeriesInfo` (series) |
| `genres`, `genre`, `rating`, `votes`, `certification` | ✅ | |
| `collection`, `director`, `actors`, `runtime`, `country` | ✅ | |
| `language`, `languages` | ✅ | Original language / spoken languages |
| `omdb` | ✅ | OMDb details (requires a key) |
| `az`, `decade` | ✅ | Sort initial (`0-9`, `#`, `A`...), decade (`1990s`) |
| `ci`, `cy` | ❌ | Collection index / years |
| `localize`, `order`, `db`, `historic` | ❌ | Dynamic access to other languages/orders/databases |
| `AnimeList`, `XEM` | ≈ | `{AnimeList.TheTVDB}` and `{XEM.scene}` |
| `sy`, `sc` | ❌ | Airing years / number of seasons |

### File
| Variable | Status | Description |
|---|---|---|
| `fn`, `ext`, `f`, `file`, `folder` | ✅ | Name without extension, extension, file, folder |
| `original` | ✅ | Name before the first rename (from the history) |
| `drive`, `home`, `today` | ✅ | Drive root, user folder, current date |
| `bytes`, `megabytes`, `gigabytes` | ✅ | Size |
| `crc32` | ✅ | CRC from the name `[ABCD1234]` or computed |
| `i`, `di`, `dc` | ✅ | Index in the list, duplicate index and count |
| `vs`/`source`, `tags`/`edition`, `group`, `s3d` | ✅ | From the original name: source, edition, group, 3D |
| `lang`, `subt` | ✅ | Subtitle language from the name (`.spa`), full suffix |
| `xattr`, `mediaFile`, `mediaFileName`, `relativeFile`, `files`, `root`, `output`, `defines`, `label` | ❌ | |

### MediaInfo
| Variable | Status | Description |
|---|---|---|
| `vf`, `vk`, `hd` | ✅ | `1080p`, `4K`, `UHD/HD/SD`. `{vf}` is the largest class (4320, 2160, 1440, 1080, 720, 576, 480, 360) whose 16:9 frame the width or the height reaches at 98 % (1920×800 and 1440×1080 are `1080p`); smaller videos give their own height (`288p`); `i` when MediaInfo reports *Interlaced* |
| `vc`, `ac`, `cf` | ≈ | Video codec (`x264`, `HEVC`...), audio codec (`AC3`, `EAC3`, `DTS-HD MA`...), container |
| `channels`, `af` | ✅ | `5.1`, `6ch` |
| `resolution`, `width`, `height`, `ar`, `ws` | ✅ | |
| `hdr`, `dovi`, `bitdepth` | ≈ | `HDR10`, `HDR10+`, `DV`, `HLG`; Dolby Vision; bits |
| `fps`, `khz`, `duration`, `seconds`, `minutes`, `hours` | ✅ | |
| `bitrate`, `vbr`, `abr`, `kbps`, `mbps` | ✅ | |
| `mediaTitle`, `audioLanguages`, `textLanguages` | ✅ | |
| `media`, `video`, `audio`, `text`, `menu` | ≈ | MediaInfo maps with the **JSON** output names (`{video[0].Format}`) |
| `vcf`, `hpi`, `acf`, `aco`, `chapters`, `mediaTags`, `image`, `exif`, `camera`, `location`, `photo` | ❌ | |

### Music
`artist`, `albumArtist`, `album`, `pi` (track), `pn` (total tracks), `medium` ✅ · `pc` ❌

### Media server standards
| Variable | Example (movie) |
|---|---|
| `plex` | `Movies/Avatar (2009)/Avatar (2009)` |
| `plex.id` | `Movies/Avatar (2009) {tmdb-19995}/Avatar (2009)` |
| `jellyfin.id` | `Movies/Avatar (2009) [tmdbid-19995]/Avatar (2009)` |
| `emby.id` | `Movies/Avatar (2009) [tmdbid=19995]/Avatar (2009)` |
| `kodi` | same as `plex` without ids |

Properties: `.name` (last segment), `.id`, `.tail` (without the root folder). Episodes: `TV Shows/Series/Season 02/Series - S02E03 - Title`
(specials in `Specials`, AniDB anime in `Anime/`). Music: `Music/Artist/Album/01 - Title`.

### Others
`self`, `model` ❌, `episodelist` ❌, `json` ❌.

## Helper methods (`FormatMethods`)

Available on any value of the given type (Groovy extension methods):

| Method | Example | Result |
|---|---|---|
| `pad(n)` (number/text) | `{e.pad(3)}` | `003` |
| `lower()`, `upper()` | `{n.upper()}` | `THE OFFICE` |
| `upperInitial()`, `lowerTrail()` | `{t.upperInitial()}` | `Office Olympics` |
| `space(sep)` | `{n.space('.')}` | `The.Office` |
| `colon(sep)`, `colon(ratio, sep)` | `{n.colon(' - ')}` | `Mission - Impossible` |
| `slash(sep)` | `{n.slash('-')}` | `AC-DC` |
| `clean()`, `ascii()`, `transliterate(id)`, `isLatin()` | `{n.ascii()}` | without accents |
| `removeIllegalCharacters()`, `replaceIllegalCharacters()` | | `:` → `∶` (homoglyphs) |
| `truncate(n[, '…'])` | `{t.truncate(40)}` | |
| `match(regex[, group])`, `matchAll(regex)` | `{fn.match(/(\d{4})/)}` | fails if there is no match |
| `replaceAll(regex)`, `removeAll(regex)`, `remove(words...)` | `{n.remove('The')}` | |
| `before(regex)`, `after(regex)`, `findMatch`, `findWordMatch` | | |
| `matchBrackets()`, `removeBrackets()`, `replaceTrailingBrackets([rep])`, `replacePart([rep])` | `{t.replacePart(', Part $1')}` | `Title, Part 1` |
| `sortName([fmt])`, `sortInitial()`, `initialName()`, `acronym()`, `roman()` | `{n.sortName()}` | `Office, The` |
| `joining(sep[, pre, suf])` (collections) | `{genres.joining(', ')}` | |
| `replace(map)`, `match(map)` | `{vf.match('2160p':'4K','1080p':'FHD')}` | |
| `round(n)` (number) | `{rating.round(1)}` | |

Any Groovy/Java method works too: `{n.replace('Marvel\'s ', '')}`, `{d.format('dd/MM/yyyy')}`, `{genres[0]}`...

## Global functions (`FormatScript`)

`any{..}{..}`, `allOf{..}{..}`, `none{..}`, `list(..)`, `concat(..)`, `count(..)`, `quote(..)`,
`csv('path')` (2-column map), `lines('path')`, `text('path')`.

External dictionary example: `{csv('D:/names.csv').get(n, n)}`.

## Adding a variable

1. Add a public getter in `MediaBindingBean` annotated with `@Define("name")` (or several aliases).
2. Return `null` (or an empty collection/string) when it does not apply: the block will be emptied by itself.
3. If it does expensive I/O, compute it lazily (like `media()` or `getMovieInfo()`).
4. Update the table in this document and add a case to `ExpressionFormatTest`.

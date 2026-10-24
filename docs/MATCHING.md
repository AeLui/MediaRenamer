# Detection and matching

## 1. Name cleanup (`MediaDetection.stripReleaseInfo`)

In this order: the video/subtitle/audio extension, embedded CRCs `[ABCD1234]`, groups in `[]`/`{}` (and `()` in strict
mode), and the release tokens of `ReleaseInfo` (all case-insensitive and matched as whole tokens only, so `TS` never
matches inside `Tests`):

| Constant | Examples |
|---|---|
| `VIDEO_FORMAT` | `1080p`, `x264`, `H.264`, `HEVC`, `DTS-HD.MA.5.1`, `DDP5.1`, `Atmos`, `10bit`, `HDR10+`, `Dolby Vision` |
| `VIDEO_TAGS` (editions) | `Director's Cut`, `Final Cut`, `Special Edition`, `Extended`, `Unrated`, `Remastered`, `IMAX` |
| `VIDEO_SOURCE` | `BluRay`, `REMUX`, `WEB-DL`, `WEBRip`, `HDTV`, `HDCAM`, `AMZN`, `NF`, `microHD`, `m1080p` |
| `VIDEO_S3D`, `VIDEO_REPACK` | `HSBS`, `Half-OU`, `PROPER`, `REPACK`, `RERIP` |
| `VIDEO_LANGUAGE` | `Dual`, `Multi`, `Castellano`, `Latino`, `VOSE`, `Spa`, `Eng`, `Subs` |
| `RELEASE_GROUP` | a short list of groups that appear in the middle of names: `YTS`, `SPARKS`, `KILLERS`, `FGT`, `FLUX`... |
| `QUERY_EXCLUDES` | `www`, `com`, `torrent`, `download`, `complete`, `4k`, `SDR`, `internal`, `limited` |

Site words (`www`, `com`, `torrent`...) are removed anywhere. Every other token is only removed **from the end of the
title on**: the first strong token (format, source, 3D, repack), the first year that follows some text, or the first
`S01E02`-style code. Words before that point belong to the title, so `The.English.Patient.1996.1080p` keeps
"English", `Uncut.Gems.2019` keeps "Uncut" and `The.Killers.1946` keeps "Killers". A trailing `-GROUP` after the title
(`...x264-AMIABLE`) and whole site addresses (`www.Site.com - `) are removed too. Then `.` and `_` become spaces,
empty brackets and a trailing `-` go, and spaces are collapsed. Years are kept (`Movie Title 2010`).

`ReleaseInfo` also gives `{vs}` (first source tag), `{tags}` (first edition, separators as spaces), `{s3d}`, the
subtitle tags of `{subt}` (`forced`, `SDH`, `HI`, `CC`) and `{group}`: the word after the last `-` at the end of the
name or the bracketed text at the start (`[SubsPlease] ...`), unless it is only digits or a release token (`-1080p`,
`-sample`, `-cd1`) or the end of one (`WEB-DL` gives no group); otherwise a known group between brackets, parentheses or
dashes. `{vs}` skips source words that are also ordinary words (`Web`, `Max`) while they are part of the title
(`Mad.Max.2015` has no source). **To recognize another release word, add
it to the term list of its category in `ReleaseInfo`** (and a case to `ReleaseInfoTest`).

**Extras** (`ReleaseInfo.isClutter`, Plex/Jellyfin conventions): `sample`/`trailer` as the last token or the whole
name (`Movie-sample`, `Movie.Trailer`), an extras word after `-`, `.` or `_` at the end (`-featurette`,
`-behindthescenes`, `-deleted`, `-interview`, `-scene`, `-short`, `-other`), or "Behind the Scenes", "Deleted Scenes",
`NCOP`/`NCED` anywhere. `The Sample Movie` or `Interview with the Vampire` are not extras.

## 2. Season/episode (`SeasonEpisodeMatcher`)

Three families, from the most explicit to the loosest. A looser family is only used when the more explicit ones find
nothing in the name; inside a family, all notations count, in order of appearance and without duplicates.

| Family | Examples | Notes |
|---|---|---|
| Explicit | `Season 1 Episode 2`, `Series 2 Part 3`, `Temporada 1 Capitulo 3`, `Staffel 1 Folge 2` | season words in several languages |
| | `S01E02`, `s01.e02`, `S01 E05`, `[s01]_[e02]`, `S01EP05`, `S2010E01` | seasons up to 4 digits |
| | `1x02`, `01x05`, `S1x05` | |
| | `S01E01E02`, `S01E01-02-03`, `S01E01-E03`, `S01E01-03`, `2x05x06`, `1x01-1x02`, `S01E01.S01E02` | multi-episode; a range backwards or longer than 50 keeps its first episode |
| | `S01 - 02`, `Season 1 - 02` | |
| | `T1 Cap 05`, `T2E07` | Spanish *temporada* / *capítulo* |
| Episode only | `Ep 12`, `E05`, `Episode 7`, `3of6`, `3 of 6` | no season |
| | `SP01`, `SP01-02` | specials (season 0) |
| Lenient only | `102` (1x02), `1012` (10x12) | never years (`2010`), never in strict mode |

A 1900–2099 "season" with an episode above 99 is a year and is dropped, and codec/resolution tokens (`1080p`,
`x264`, `H.264`, `DD5.1`) are never episodes. `find(name, from)` returns where the first notation used by `match`
starts.

Other detectors: `DateMatcher` (`2024.03.15`, `20240315`, `15.03.2024`), anime absolute number
(`Series - 123`), season from the folder (`Season 2`, `Temporada 2`, `Staffel 2`, `Saison 2`, `Stagione 2`, `S02`).

## 3. Series / movie name

- **Series**: text before the first SxE, anime absolute number (`Frieren - 05`) or air date (`2024.03.15`,
  `20240315`) of the clean name, without
  a trailing "Season" word; a year in the name is kept (`Doctor Who 2005`). If there is none, or it is at the very
  start (`S02E03.mkv`), the folders are walked up skipping season folders and generic ones (`Season N`, `Specials`,
  `CD1`, `Downloads`, `Descargas`, `Telegram Desktop`, `Series`, `Movies`...). Files are grouped by normalized name and
  **searched once per group**. Inside a season folder, a file without its own season gets it from the folder
  (`Season 2/05 - Title.mkv` → `S02E05`).
- **Movie**: `Title (Year)` / `Title Year` in the file; a year at the very start belongs to the title (`2001 A Space
  Odyssey 1968`), and a year right after another one is the release year (`Blade.Runner.2049.2017` → *Blade Runner
  2049 (2017)*). If the file gives nothing, has no year while the folder has one, or a title under 3 characters, a
  non-generic folder is used; a disc folder (`CD1`, `Disc 2`) is skipped for the one above it
  (`Matrix (1999)/CD1/matrix-cd1.avi`). If there is `tt1234567` or `tmdb-123` in the name or in a `.nfo` with the same name, the
  id is used directly.

## 4. Choosing the search result

- **Series**: score = normalized equality (1.0) or q-gram similarity against the name and the aliases.
  Auto-selection if the best one is exact and the second is not, or if there is a single reasonable result.
  In strict mode also if the best one is ≥ 0.9 and beats the second by ≥ 0.1. Otherwise → the user is asked
  (*Select best match for…*; in the CLI: strict = skip, non-strict = first one).
- **Movies**: the same, with a year adjustment (±0, −0.05 if it differs by 1 year, −0.3 if more).

## 5. Matching files ↔ episodes (`Matcher` + `EpisodeMetrics`)

`Matcher` gives every (episode, file) pair a score vector, one similarity per metric rounded to 2 decimals, compared in
metric order: the first metric decides and the later ones only break its ties.

1. Every pair that is the single best option of both its episode and its file is accepted; this repeats while new
   pairs qualify.
2. When only ties are left, the best tied pairs are accepted in file order in **Opportunistic** mode (skipping what is
   already used) and discarded in **Strict** mode; then step 1 runs again with what remains.
3. In **Strict**, a pair whose first metric (the episode identifier) is 0 is never accepted, and every match must pass
   `EpisodeMetrics.verify` (identifier or title). Two files that are equally good for the same episode (two versions of
   `S01E01`) are a tie: strict mode leaves both out, opportunistic mode takes the first.

Only the first metric is computed for every pair; the others are computed when a comparison needs them, so
1100 episodes × 200 files match in about 0.1 s (strict) to 0.6 s (opportunistic).

Sequence used:

| Level | Metric | Value |
|---|---|---|
| 1 | identifier | exact SxE or special number = 1 (0.95 for the second and later codes of a multi-episode file); air date = 1; episode number without season = 0.5; absolute number in a name without season or date = 0.4. A bare `205` read as 2x05 (lenient only) is ambiguous: it scores 0.4 like the absolute number 205, and the next metrics decide |
| 2 | title | the episode title (3+ characters) appears in the name; shorter titles use name similarity |
| 3 | series | the series name or an alias appears in the name |
| 4 | numbers | share of the name's standalone numbers that are season/episode/absolute/special numbers of the episode |
| 5 | name | q-gram similarity with `Series - SxE - Title` |

**Multi-episode**: if the file has several SxE (`S01E01E02`) and the matched episode is one of them,
it is replaced by a `MultiEpisode` with all of them.

**Companions**: subtitles, `.nfo` and images whose name starts with the video's inherit its object.

## Tuning the behavior

- New name formats → add a pattern to `SeasonEpisodeMatcher` (and a test in `SeasonEpisodeMatcherTest` or a line in
  `src/test/resources/com/mediarenamer/cleanroom/sxe.txt`).
- Release words → the term lists of `ReleaseInfo`.
- Generic and season folders → `GENERIC_FOLDER` / `SEASON_FOLDER` in `MediaDetection`.
- Auto-selection thresholds → `EpisodeListMatcher.selectSeries` and `MovieMatcher.search`.
- Metrics → `EpisodeMetrics.matchSequence()`.

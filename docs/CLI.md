# Command line

Without arguments the GUI opens. With arguments, `com.mediarenamer.cli.Cli` runs the command line.
On Windows the installed console launcher is `MediaRenamer-cli.exe` (`FileRenamer-cli.exe` up to 1.1.1); the environment
variables use the `MEDIARENAMER_` prefix (`FILERENAMER_` up to 1.1.1).

Messages (help, warnings, summary, the `--conflict ask` question) use the interface language
(`ui.language`, see [CONFIGURATION.md](CONFIGURATION.md#interface-language)); for a single run:
`MEDIARENAMER_UI_LANGUAGE=en java -jar MediaRenamer.jar -rename ...`. Options and values (`--action test`,
`skip`...) are always the same. In `--conflict ask` the answer letters depend on the language (the question
itself shows them; in English `k`eep, `r`eplace, `b`oth, `c`ancel).

```bash
java -jar MediaRenamer.jar -rename <files|folders...> [options]
java -jar MediaRenamer.jar -list --db <db> --q <series|id> [--format f] [--order o] [--lang l]
java -jar MediaRenamer.jar -mediainfo <file...> [--format f]
java -jar MediaRenamer.jar -history
java -jar MediaRenamer.jar -profiles
```

## Profiles

`--profile "<name or id>"` fills `--db`, `--format`, `--output` (the profile's folder), `--order`, `--lang` and the
matching mode with the profile's values; **options you pass explicitly take precedence**
(e.g. `--profile "Movies for Plex" --action test`). If the profile is *Opportunistic*, strict mode can be forced
with `-strict`. Profiles do not set the action (since 2.5): it is `--action` or its default.

- `-profiles` lists the saved profiles.
- Profiles were called *presets* up to 2.0: `--preset` and `-presets` still work as aliases.

```bash
java -jar MediaRenamer.jar -rename "D:/Downloads" -r --profile "TV Shows for Plex" --action test
```

## `-rename`

| Option | Values | Default |
|---|---|---|
| `--db` | `TheMovieDB`, `TheMovieDB::TV`, `TheTVDB`, `AniDB`, `AcoustID`, `ID3`, `file` (aliases: `tmdb`, `tvdb`, `tv`, `movie`, `anime`, `music`, `tags`...; `tvmaze`, a source removed in 2.1, means `TheMovieDB::TV`; `omdb`/`imdb`, removed in 2.5, mean `TheMovieDB`) | guessed: music → ID3, mostly SxE → TheMovieDB::TV, otherwise TheMovieDB |
| `--format` | format expression | the saved format of the mode |
| `--action` | `move`, `copy`, `keeplink`, `symlink`, `test` (`hardlink`, `clone` and `duplicate`, removed in 2.1, mean `copy`) | `move` |
| `--conflict` | `skip`, `override`, `best` (= `auto`), `worst`, `largest`, `smallest`, `ask`, `index`, `fail` (see below) | `skip` |
| `--lang` | BCP-47 tag (`es-ES`, `en`, `ja`) | preference or system language |
| `--order` | `Airdate`, `DVD`, `Absolute`, `Digital`, `Story`, `Production`, `Official`, `Date` | `Airdate` |
| `--q` | forced query: series name, or `Movie (Year)` | automatic detection |
| `--output` | target folder: target = `<folder>/<format>` | profile folder > Preferences > each file's folder |
| `-r` | recurse into subfolders | no |
| `-non-strict` | *Opportunistic* mode (picks the first result when ambiguous) | strict |
| `-clean` | applies the cleanup suggested by the name pre-check | warns only |
| `-debug` | detailed logging (URLs with hidden keys) | no |

Exit codes: `0` ok · `1` wrong usage · `2` missing API key / MediaInfo · `3` error · `4` there were errors while renaming.

### Examples

```bash
java -jar MediaRenamer.jar -rename "D:/Downloads" -r --db TheMovieDB::TV --format "D:/Series/{n}/Season {s}/{n} - {s00e00} - {t}" --action test
```

```bash
java -jar MediaRenamer.jar -rename "D:/Downloads/Movies" --db TheMovieDB --lang es-ES --format "Z:/Movies/{n} ({y})" --action move --conflict best
```

```bash
java -jar MediaRenamer.jar -rename "D:/Anime/Frieren" --db AniDB --q "Sousou no Frieren" --format "{n} - {absolute.pad(2)} - {t}"
```

```bash
java -jar MediaRenamer.jar -list --db TheMovieDB::TV --q "Dark" --format "{s00e00} {t}"
```

```bash
java -jar MediaRenamer.jar -mediainfo "video.mkv" --format "{vf} {vc} {ac} {channels} {hdr}"
```

## If the target already exists (`--conflict`)

| Value | What it does |
|---|---|
| `ask` | **Asks** each time showing quality and size of both: keep, replace, both or cancel (the letters depend on the language; uppercase = for all). Without an interactive console the existing one is kept |
| `skip` | Does not rename that file (the existing one is kept) |
| `override` | Replaces the existing one (it goes to the recycle bin; where there is none — network drives, no desktop — it is deleted permanently and a warning is logged) |
| `best` / `auto` | Keeps the **higher quality** one: resolution > HDR > video bitrate > audio channels > size |
| `worst` | Keeps the **lower quality** one |
| `largest` | Keeps the **larger** one |
| `smallest` | Keeps the **smaller** one |
| `index` | Keeps both: the new one is named `Name (1).mkv` |
| `fail` | Stops everything |

In the interface the options appear in this order and **"Ask each time" is the default** (the last choice is remembered).
In the CLI the default is still `skip`, so that scripts are not blocked waiting for an answer.

On a tie the existing one is kept. With `--action test` conflicts are evaluated and reported, but nothing is ever
modified (nor asked).

The policy only applies to files that already existed. Inside one run, whatever the policy:

- if two files go to the **same target**, the first one is renamed and the second one **fails** ("Another file of this
  run already goes to this target") and stays where it was; nothing the run itself created is ever replaced;
- a target that is **another file of the run still to be renamed** is never replaced (that file fails), so it is not
  lost before its own turn.

## Name pre-check

Before renaming the targets are checked (same as in the interface, see [USAGE.md](USAGE.md#name-pre-check)).
Each problem is printed as `[ERROR]` or `[WARNING]` (in the interface language) with its suggestion; with `-clean`
the suggestions are applied.

## Limitations

- There is no `--filter`, `--mapping`, `--apply` (post-processing), `-exec`, `--def`, `--log-file` or `-script`.
- `--q` accepts a name; for ids use `--q 12345` only in `-list` (not supported in `-rename`).
- In strict mode the CLI does not guess: if there is ambiguity it reports it and skips those files.

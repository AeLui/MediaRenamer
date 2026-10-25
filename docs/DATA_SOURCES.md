# Data sources

All requests go through `web.WebRequest` (HTTP with gzip, disk cache with a per-resource expiry, answers of at most
64 MB compressed or uncompressed, expired cache files removed at startup) and, when the service requires it, through a `FloodLimit`. Keys are read with `Settings.requireKey`, which throws
an explanatory error if they are missing.

| Identifier (`--db`) | Class | Mode | Key | Limit | Cache |
|---|---|---|---|---|---|
| `TheMovieDB` (`tmdb`) | `TMDbMovieClient` | Movie | `apikey.tmdb` | 40 req / 10 s | 7 days |
| `TheMovieDB::TV` (`tmdb::tv`) | `TMDbTVClient` | Episode | `apikey.tmdb` | 40 req / 10 s | 7 days |
| `TheTVDB` (`tvdb`) | `TheTVDBClient` | Episode | `apikey.tvdb` (+ `apikey.tvdb.pin`) | — | 7 days (token 25 days) |
| `AniDB` (`anidb`) | `AniDBClient` | Episode (anime) | `apikey.anidb.client` (+ `clientver`) | **1 req / 2.5 s** | 7 days; titles 1 day |
| `AcoustID` (`acoustid`) | `AcoustIDClient` | Music | `apikey.acoustid` + `fpcalc` | 3 req / s | 30 days |
| `ID3` (`tags`) | `ID3Lookup` | Music | **not needed** (MediaInfo) | — | — |
| `file` | `FileMatcher` | File | — | — | — |

## TheMovieDB (`TMDbClient`, `TMDbMovieClient`, `TMDbTVClient`)

- Base: `https://api.themoviedb.org/3/`. Accepts an **API Key v3** (`api_key` parameter) or a **Read Access Token v4**
  (if the key starts with `eyJ`, `Authorization: Bearer` is sent).
- Language: `language=es-ES` parameter (old codes `iw`/`in` → `he`/`id`).
- Movies:
  - Search: `search/movie?query=..&primary_release_year=..&include_adult=false` (if there are no results with a year, it retries without it).
  - By id: `movie/{id}`; by IMDb: `find/tt0000000?external_source=imdb_id`.
  - Details (`{info}`, `{genres}`, `{collection}`, `{certification}`...): `movie/{id}?append_to_response=translations,alternative_titles,release_dates,keywords,credits`.
  - Certification: that of the chosen language's country, or the US one.
- Series:
  - Search: `search/tv`. Data: `tv/{id}?append_to_response=alternative_titles,content_ratings,keywords,external_ids`.
  - Episodes: `tv/{id}/season/{n}` for each season; season 0 are specials (`{special}`).
  - Alternative orders with *episode groups*: `tv/{id}/episode_groups` and `tv/episode_group/{id}`.
    Types: 2 Absolute, 3 DVD, 4 Digital, 5 Story, 6 Production. If the group does not exist, the airing order is used
    (and Absolute is computed by numbering the regular seasons).

## TheTVDB v4 (`TheTVDBClient`)

- Base: `https://api4.thetvdb.com/v4/`. `POST login {"apikey", "pin"?}` → Bearer token (renewed after 25 days).
- Search: `search?query=..&type=series` (uses `translations[lang]` if present).
- Series: `series/{id}/extended?meta=translations&short=true` (translated name, aliases, genres, network, external ids).
- Episodes: `series/{id}/episodes/{season-type}/{lang}?page=N` paginating with `links.next`.
  `season-type`: `default` (Airdate), `dvd`, `absolute`, `alternate` (Digital), `regional` (Production).
  If there is no translation for the language (404) it retries without a language.
- Language: ISO 639-2 (`spa`, `eng`, `deu`...), with the exceptions `pt` (pt-BR) and `zhtw`.

## AniDB (`AniDBClient`)

- **Mandatory registration** of an HTTP client at anidb.net (*Add Client*): name (`apikey.anidb.client`) and version.
- **Local** search over `https://anidb.net/api/anime-titles.dat.gz` (downloaded at most once a day),
  scored with `NameSimilarityMetric`; localized title = the *official* title of the language; otherwise the *main* one.
  The dump has one title per line, `aid|type|language|title` (type 1 main, 2 synonym, 3 short, 4 official; the title may
  contain `|`); comments (`#`), empty and malformed lines are skipped.
- Data: `http://api.anidb.net:9001/httpapi?request=anime&client=..&clientver=..&protover=1&aid=..` (XML). AniDB only
  offers this API over plain http, so its answers are treated as hostile: the XML parser never reads external
  entities, DTDs or schemas (no XXE) and limits entity expansion.
  `epno type=1` → regular episodes (absolute numbering, no season); `type=2` → specials.
- **Careful**: AniDB bans the IP on abuse. The limiter is set to 1 request / 2.5 s and the cache to 7 days.

## AcoustID (`AcoustIDClient`)

- Requires **Chromaprint `fpcalc`** (searched in `tool.fpcalc`, the `lib` folder next to the application, `C:\Program Files\Chromaprint`, PATH; never relative to the current folder; stopped after 1 minute).
- `fpcalc -json file` → `duration` + `fingerprint` → `https://api.acoustid.org/v2/lookup?client=..&meta=recordings+releases+releasegroups+tracks+compress`.
- Picks the recording with the best *score* and, within it, the oldest *Album* release.

## ID3 (`ID3Lookup`)

- Reads the tags with MediaInfo (`General` stream): `Performer`, `Title`, `Album`, `Album_Performer`,
  `Track_Position(_Total)`, `Part_Position(_Total)`, `Recorded_Date`, `Genre`.

## Anime mappings

- **AnimeLists** (`AnimeLists`): `anime-list.xml` from github.com/Anime-Lists. Converts AniDB episodes to
  TheTVDB season/episode (`defaulttvdbseason`, `episodeoffset`, explicit and range `<mapping>`s).
  Variable `{AnimeList.TheTVDB}` (e.g. `{AnimeList.TheTVDB.s00e00}`).
- **TheXEM** (`XEM`): `https://thexem.info/map/all?id=..&origin=tvdb` → variable `{XEM.scene}`.

## MediaInfo

It is not an online source but it feeds many variables. `media.MediaInfo` loads `MediaInfo.dll` (JNA, 64-bit)
from: `tool.mediainfo`, the `lib` folder next to the application, `C:\Program Files\MediaInfo`, PATH (absolute entries only:
never relative to the current folder); otherwise
it uses the CLI `MediaInfo.exe --Output=JSON` (stopped after 2 minutes). Field names are those of MediaInfo's **JSON** output
(e.g. `Duration` in seconds), which differ slightly from its classic text output.

## Adding a new source

1. Implement `EpisodeListProvider`, `MovieLookupService` or `MusicLookupService` in `com.mediarenamer.web`.
2. Use `WebRequest.getJson/getXml` with a TTL and, if needed, a static `FloodLimit`.
3. If it needs a key, add the constant to `Settings`, the field to `PreferencesDialog` (with its i18n keys) and document it here.
4. Register it in `WebServices` (instance, list of the mode and alias in `get()`).
5. Add a test that only runs with `-Dnetwork=true` (see [DEVELOPMENT.md](DEVELOPMENT.md)).

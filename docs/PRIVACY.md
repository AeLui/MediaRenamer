# Privacy

MediaRenamer runs on your computer. It has **no telemetry, no analytics, no crash reports and no automatic update
check**, and it never sends your files anywhere. It only connects to a networked system when you use a feature that
needs it, and only to the service that feature uses.

## What is sent, and when

| Service | When | What is sent |
|---|---|---|
| [TheMovieDB](https://www.themoviedb.org/privacy-policy) (`api.themoviedb.org`, `image.tmdb.org`) | You fetch data or search with TheMovieDB, or a poster is shown | The search text (the series/movie name detected from the file name, or what you type), IDs of the chosen titles, the data language and your API key |
| [TheTVDB](https://thetvdb.com/privacy-policy) (`api4.thetvdb.com`) | You fetch data or search with TheTVDB | The search text, IDs, the data language, your API key and PIN |
| [AniDB](https://anidb.net) (`anidb.net`, `api.anidb.net`) | You fetch data with AniDB | A download of the public title list (at most once a day), the IDs of the chosen anime and your client name and version |
| [AcoustID](https://acoustid.org) (`api.acoustid.org`) | You fetch data with AcoustID (Music mode) | The duration and the audio fingerprint of each song, computed locally by `fpcalc`, and your client key |
| [TheXEM](https://thexem.info) (`thexem.info`) | A format uses `{XEM…}` | The TheTVDB ID of the series |
| [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement) (`github.com`, Anime-Lists) | A format uses `{AnimeList…}` | A download of the public `anime-list.xml` |
| Your Jellyfin server | You enabled the library scan, or press *Test connection* | The scan request and the API key you configured, to the address you configured |
| Your Plex server and [Plex](https://www.plex.tv/about/privacy-legal/) (`plex.tv`, `clients.plex.tv`) | You press **Sign in…**, enabled the library scan, or press *Test connection* | The sign-in PIN request (the sign-in itself happens in your browser), the scan request and your Plex token |

Every request also carries the usual technical data of any internet connection (your IP address) and a
`User-Agent` header with the MediaRenamer version. Answers are cached on disk so the same data is not requested again.

Buttons such as *Get key*, *Sponsor* or the links in *About* open the page in your browser; MediaRenamer sends nothing
by itself there.

## What is stored on your computer

Preferences, profiles, history and the cache live in `%APPDATA%\MediaRenamer`. API keys and tokens are stored
encrypted (Windows DPAPI). Uninstalling keeps that folder so a later installation finds your settings; delete it by
hand to remove everything.

## Turning it off

Every connection above is opt-in: use the **ID3** or **File** modes, leave the API keys and media servers empty, and
MediaRenamer does not connect to anything. See [CONFIGURATION.md](CONFIGURATION.md) and [DATA_SOURCES.md](DATA_SOURCES.md).

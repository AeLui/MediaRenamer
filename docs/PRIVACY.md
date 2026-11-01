# Privacy

MediaRenamer runs on your computer. It has **no telemetry, no analytics and no crash reports**, and it never sends
your files anywhere. It only connects to a networked system when you use a feature that needs it (the update check,
which is on by default, is the one exception: see *Turning it off*), and only to the service that feature uses.

## What is sent, and when

| Service | When | What is sent |
|---|---|---|
| [TheMovieDB](https://www.themoviedb.org/privacy-policy) (`api.themoviedb.org`, `image.tmdb.org`) | You fetch data or search with TheMovieDB, or a poster is shown | The search text (the series/movie name detected from the file name, or what you type), IDs of the chosen titles, the data language and your API key |
| [TheTVDB](https://thetvdb.com/privacy-policy) (`api4.thetvdb.com`) | You fetch data or search with TheTVDB | The search text, IDs, the data language, your API key and PIN |
| [AniDB](https://anidb.net) (`anidb.net`, `api.anidb.net`) | You fetch data with AniDB | A download of the public title list (at most once a day), the IDs of the chosen anime and your client name and version |
| [AcoustID](https://acoustid.org) (`api.acoustid.org`) | You fetch data with AcoustID (Music mode) | The duration and the audio fingerprint of each song, computed locally by `fpcalc`, and your client key |
| [TheXEM](https://thexem.info) (`thexem.info`) | A format uses `{XEM…}` | The TheTVDB ID of the series |
| [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement) (`github.com`, Anime-Lists) | A format uses `{AnimeList…}` | A download of the public `anime-list.xml` |
| [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement) (`api.github.com`; on **Update** also `github.com`, `objects.githubusercontent.com` and `release-assets.githubusercontent.com`) | At start-up, at most once every 24 hours (unless you turned it off in *Preferences*), when you press **Check for updates** in *About*, and, only if you press **Update** in an installed copy, to download the new installer | A request for the latest release of the MediaRenamer repository (its version and notes); nothing else is sent. **Update** then downloads `MediaRenamer-<version>-setup.exe` from GitHub |
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

The update check is the only connection that happens without you using a feature. Turn it off in
**Preferences → General → Check for updates at start-up**; MediaRenamer then contacts GitHub only when you press
**Check for updates** in *About*. Every other connection above is opt-in: use the **ID3** or **File** modes, leave the
API keys and media servers empty, and MediaRenamer does not connect to anything (apart from the update check, if you
left it on). See [CONFIGURATION.md](CONFIGURATION.md) and [DATA_SOURCES.md](DATA_SOURCES.md).

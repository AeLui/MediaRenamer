# Release integrity

MediaRenamer releases are **not code-signed**. Windows SmartScreen may warn the first time you run the installer or the
executable ("Windows protected your PC"): choose *More info → Run anyway* if you downloaded it from the
[Releases page](https://github.com/AeLui/MediaRenamer/releases) of this repository.

## How a release is built

1. The version in `pom.xml` is raised and the tag `v<version>` is pushed.
2. The [Release workflow](../.github/workflows/release.yml) runs on a GitHub-hosted Windows runner: it builds the
   application from the tag, runs the tests, builds the executables, the portable zip and the installer
   (`scripts\build-exe.ps1`) and checks that the third-party notices describe the bundled Java runtime.
3. The zip and the installer are attached to the GitHub release of the tag, with the notes of that version from
   [CHANGELOG.md](../CHANGELOG.md).

Nothing is built or uploaded by hand: every file on the Releases page comes from that workflow, and its run is public in
the repository's *Actions* tab.

## Checking a download

GitHub publishes the SHA-256 digest of every file attached to a release (shown next to each file on the release page and
returned by the GitHub API). To check a file you downloaded:

```powershell
Get-FileHash MediaRenamer-<version>-setup.exe -Algorithm SHA256
```

The in-app update (*About → Check for updates*, or the check at start-up) does this itself: it only downloads the
installer from GitHub over HTTPS and refuses to run it unless its size and SHA-256 match the digest GitHub publishes for
it.

## Privacy policy

This program will not transfer any information to other networked systems unless specifically requested by the user or
the person installing or operating it. See [PRIVACY.md](PRIVACY.md) for each online service it can contact when you use
it, and links to their privacy policies. The one exception is a check for a newer version on GitHub, made at start-up
at most once a day, which sends no data other than the request itself; it can be turned off in *Preferences →
General* (see [PRIVACY.md](PRIVACY.md)).

Report a release file you believe was not built from this repository in
[an issue](https://github.com/AeLui/MediaRenamer/issues).

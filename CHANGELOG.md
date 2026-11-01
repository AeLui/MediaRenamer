# Changelog

All notable changes to MediaRenamer, by version. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the versions follow [Semantic Versioning](https://semver.org/). The section of each version is also the text of its
[GitHub release](https://github.com/AeLui/MediaRenamer/releases). Detailed status and test notes per feature are in
[docs/ROADMAP.md](docs/ROADMAP.md).

## [Unreleased]

## [2.8.0] - 2026-11-01

### Added

- Update check: MediaRenamer looks on GitHub for a newer version a few seconds after it starts (at most once a day;
  **Preferences → General → Check for updates at start-up** turns it off) and from **About → Check for updates**. A
  dialog shows the release notes; **Update** downloads, verifies and installs the new version in an installed copy, and
  MediaRenamer reopens after the update. **Skip this version** is also offered.

## [2.7.1] - 2026-11-01

### Fixed

- The third-party notices describe the bundled Java runtime, SapMachine 21.0.13.0.3 (the 2.7.0 release could not be
  built because they still named the previous runtime).

## [2.7.0] - 2026-11-01

### Added

- Open files with MediaRenamer: drop files or folders on the executable or its shortcuts, or use Explorer's
  **Send to → MediaRenamer**; files can be dropped anywhere in the window.
- A second start hands its files to the window that is already open and brings it to the front.
- Explorer context menu (*Show more options* on Windows 11): **MediaRenamer → Add to MediaRenamer** and one entry per
  profile, which adds the selection and fetches its data with that profile.

## [2.6.1] - 2026-11-01

### Added

- Release workflow that builds the executables and the installer on GitHub Actions and attaches them to the release.
- [Release integrity](docs/RELEASE_INTEGRITY.md) and [privacy policy](docs/PRIVACY.md) pages.

### Changed

- The repository is now `AeLui/MediaRenamer`.
- The commercial license offer was removed: MediaRenamer is GPL-3.0-only.

## [2.6.0] - 2026-11-01

### Added

- GNU GPL v3 license, shipped with the executable, the zip and the installer; shown in *About*.
- **♥ Sponsor** link in *About* (GitHub Sponsors).

## [2.5.2] - 2026-11-01

### Changed

- Screenshots and the format editor sample use invented titles and posters instead of real ones.

## [2.5.0] - 2026-10-25

### Added

- **Media type** in profiles (Movies, Series, Music): it filters the data sources and shows only the fields of that type.

### Removed

- OMDb (poor results): profiles and preferences that used it switch to TheMovieDB.
- The action selector in profiles: profiles no longer set the action.

[Unreleased]: https://github.com/AeLui/MediaRenamer/compare/v2.8.0...HEAD
[2.8.0]: https://github.com/AeLui/MediaRenamer/compare/v2.7.1...v2.8.0
[2.7.1]: https://github.com/AeLui/MediaRenamer/compare/v2.7.0...v2.7.1
[2.7.0]: https://github.com/AeLui/MediaRenamer/compare/v2.6.1...v2.7.0
[2.6.1]: https://github.com/AeLui/MediaRenamer/compare/v2.6.0...v2.6.1
[2.6.0]: https://github.com/AeLui/MediaRenamer/compare/v2.5.2...v2.6.0
[2.5.2]: https://github.com/AeLui/MediaRenamer/compare/v2.5.0...v2.5.2
[2.5.0]: https://github.com/AeLui/MediaRenamer/releases/tag/v2.5.0

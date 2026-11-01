# Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by
[SignPath Foundation](https://signpath.org).

## What is signed

Only binaries built from this repository by the [Release workflow](../.github/workflows/release.yml), on
GitHub-hosted runners, from a `v*` tag:

- `MediaRenamer.exe` and `MediaRenamer-cli.exe` (the jpackage launchers), signed before they are packaged, so the
  portable zip and the installer carry the signed ones;
- `MediaRenamer-<version>-setup.exe` (the Inno Setup installer).

The bundled Java runtime (`runtime\`) is third-party software (OpenJDK) and keeps its upstream signatures or none; it is
not re-signed. Every signed file has its product name (`MediaRenamer`, `MediaRenamer-cli` for the console launcher) and
its product version (the release version, from `pom.xml`) set by the build, and the SignPath artifact configurations in
[`.signpath/artifact-configurations`](../.signpath/artifact-configurations) only accept files whose metadata matches.

## Team roles

MediaRenamer has one maintainer, who holds every role:

| Role | Members |
|---|---|
| Committers and reviewers | [AeLui](https://github.com/AeLui) |
| Approvers | [AeLui](https://github.com/AeLui) |

Changes from anyone else (pull requests) are reviewed by a committer before they are merged. Every signing request is
approved by hand in SignPath. Multi-factor authentication is enabled for GitHub and SignPath.

## Privacy policy

This program will not transfer any information to other networked systems unless specifically requested by the user or
the person installing or operating it. See [PRIVACY.md](PRIVACY.md) for each online service it can contact when you use
it, and links to their privacy policies.

## How a release is signed

1. The version in `pom.xml` is raised and the tag `v<version>` is pushed.
2. The [Release workflow](../.github/workflows/release.yml) builds and tests the application, builds the app-image
   (`scripts\build-exe.ps1 -AppImageOnly`) and sends both launchers to SignPath (artifact configuration `app`).
3. After the approver accepts the request in SignPath, the signed launchers replace the unsigned ones, the zip and the
   installer are built (`-PackageOnly`) and the installer is sent to SignPath (artifact configuration `installer`).
4. The signed zip and installer are attached to the GitHub release of the tag.

Without the SignPath settings (the `SIGNPATH_ORGANIZATION_ID` variable and the `SIGNPATH_API_TOKEN` secret), the same
workflow builds and publishes unsigned files.

Report a signed binary you believe was not built from this repository in
[an issue](https://github.com/AeLui/MediaRenamer/issues).

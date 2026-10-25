# Third-party software

MediaRenamer redistributes the components below. The full license texts and the NOTICE contents are in
[THIRD-PARTY-NOTICES.txt](../THIRD-PARTY-NOTICES.txt) at the repository root; each text there is an exact copy taken
from the artifact itself (the jar's `META-INF`, or the upstream `LICENSE` at the released tag).

| Component | Version | License (SPDX) | Copyright | URL |
|---|---|---|---|---|
| Apache Groovy (`groovy`, `groovy-json`) | 4.0.24 | Apache-2.0 (with NOTICE) | The Apache Software Foundation | https://groovy-lang.org |
| ↳ ANTLR 4 (repackaged inside `groovy`) | — | BSD-3-Clause | The ANTLR Project | https://github.com/antlr/antlr4 |
| ↳ ASM (repackaged inside `groovy`) | — | BSD-3-Clause | INRIA, France Telecom | https://asm.ow2.io |
| ↳ picocli (repackaged inside `groovy`) | — | Apache-2.0 | Remko Popma | https://picocli.info |
| FlatLaf | 3.5.4 | Apache-2.0 | FormDev Software GmbH | https://github.com/JFormDesigner/FlatLaf |
| RSyntaxTextArea | 3.5.3 | BSD-3-Clause | Robert Futrell | https://github.com/bobbylight/RSyntaxTextArea |
| Java Native Access (JNA) | 5.15.0 | Apache-2.0 OR LGPL-2.1-or-later (used under Apache-2.0) | Timothy Wall and contributors | https://github.com/java-native-access/jna |
| ↳ libffi (inside the JNA native libraries) | — | MIT | Anthony Green, Red Hat, Inc. and others | https://sourceware.org/libffi/ |
| Java runtime: SapMachine (OpenJDK build) | 21.0.13.0.2 | GPL-2.0-only WITH Classpath-exception-2.0 (+ notices in `runtime\legal`) | Oracle and/or its affiliates, SAP SE and others | https://sapmachine.io |
| Inno Setup (installer only, not installed) | 6 | Inno Setup License (permissive) | Jordan Russell, Martijn Laan | https://jrsoftware.org/isinfo.php |

Not redistributed: MediaInfo and fpcalc (Chromaprint) are optional programs the user installs; the logo and icons are
drawn by MediaRenamer's own code (`Logo`, `Icons`). Test-only dependencies (JUnit) are not shipped.

## Where the notices ship

| Package | Location |
|---|---|
| App image `dist\MediaRenamer` and the zip | `THIRD-PARTY-NOTICES.txt` next to `MediaRenamer.exe`; Java runtime notices in `runtime\legal\<module>\` |
| Installer | Installed in the same places (`{app}\THIRD-PARTY-NOTICES.txt`, `{app}\runtime\legal`); `MediaRenamer.iss` refuses to compile if either is missing |
| Jar | `META-INF/THIRD-PARTY-NOTICES.txt` (copied by Maven from the repository root) |
| Application | *About MediaRenamer* → **Third-party licenses** shows the bundled text |

`scripts\build-exe.ps1` copies the file into the app image, fails if `runtime\legal\java.base\LICENSE` or the
notices are missing from the folder or the zip, and warns if the notices do not mention the bundled Java version.

## Maintenance

- **New or updated dependency** (`pom.xml`): add or update its section in `THIRD-PARTY-NOTICES.txt` (license and any
  `META-INF/NOTICE` copied from the jar) and the table above. `ThirdPartyNoticesTest` fails if a runtime dependency or
  its version is missing, or if a jar's `META-INF/NOTICE` (or Groovy's bundled ANTLR/ASM licenses) is not reproduced.
- **New JDK** (quarterly security update): update the version and the source tag in the Java runtime section;
  `build-exe.ps1` warns when they differ from `runtime\release`.

## Microsoft Store listing

Ready-to-paste plain text for the Store listing (description or "Additional license terms"):

```text
Third-party software: MediaRenamer includes open-source components, used under their licenses: Apache Groovy (Apache License 2.0; includes ANTLR 4 and ASM under BSD 3-Clause and picocli under Apache 2.0), FlatLaf (Apache License 2.0), RSyntaxTextArea (BSD 3-Clause), Java Native Access (Apache License 2.0; includes libffi under the MIT License) and a Java runtime from SapMachine/OpenJDK by SAP SE (GNU GPL v2 with the Classpath Exception). The full license texts and notices are installed with the app (THIRD-PARTY-NOTICES.txt next to MediaRenamer.exe and the runtime\legal folder) and are shown in the app under About MediaRenamer > Third-party licenses.
```

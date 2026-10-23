# Minecraft support — Volt 0.7.0-alpha.1

These **24 Fabric builds** cover EVATEX's 24 stable Minecraft targets listed by
its [official Modrinth project](https://modrinth.com/mod/evatex-background).
Each exact production JAR passed the 34-test suite and produced a playable MP4 in
an isolated 1920×1080 fullscreen Minecraft session. A Java version in this table is
the version used for the native check. Install matching Fabric API and FFmpeg.

| Minecraft | Java tested | Tests / failures | MP4 duration | Exact artifact proof |
| --- | --- | --- | --- | --- |
| 1.20 | 17 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.20/v7/validation-session.json) |
| 1.20.1 | 17 | 34 / 0 | 3.27 s | [receipt](../verification/ports/1.20.1/v7/validation-session.json) |
| 1.20.2 | 17 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.20.2/v7/validation-session.json) |
| 1.20.3 | 17 | 34 / 0 | 3.33 s | [receipt](../verification/ports/1.20.3/v7/validation-session.json) |
| 1.20.4 | 17 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.20.4/v7/validation-session.json) |
| 1.20.5 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.20.5/v7/validation-session.json) |
| 1.20.6 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.20.6/v7/validation-session.json) |
| 1.21 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21/v7/validation-session.json) |
| 1.21.1 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.1/v7/validation-session.json) |
| 1.21.2 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.2/v7/validation-session.json) |
| 1.21.3 | 21 | 34 / 0 | 3.27 s | [receipt](../verification/ports/1.21.3/v7/validation-session.json) |
| 1.21.4 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.4/v7/validation-session.json) |
| 1.21.5 | 21 | 34 / 0 | 3.23 s | [receipt](../verification/ports/1.21.5/v7/validation-session.json) |
| 1.21.6 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.6/v7/validation-session.json) |
| 1.21.7 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.7/v7/validation-session.json) |
| 1.21.8 | 21 | 34 / 0 | 3.23 s | [receipt](../verification/ports/1.21.8/v7/validation-session.json) |
| 1.21.9 | 21 | 34 / 0 | 3.27 s | [receipt](../verification/ports/1.21.9/v7/validation-session.json) |
| 1.21.10 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.10/v7/validation-session.json) |
| 1.21.11 | 21 | 34 / 0 | 3.30 s | [receipt](../verification/ports/1.21.11/v7/validation-session.json) |
| 26.1 | 25 | 34 / 0 | 3.30 s | [receipt](../verification/ports/26.1/v7/validation-session.json) |
| 26.1.1 | 25 | 34 / 0 | 3.33 s | [receipt](../verification/ports/26.1.1/v7/validation-session.json) |
| 26.1.2 | 25 | 34 / 0 | 3.27 s | [receipt](../verification/ports/26.1.2/v7/validation-session.json) |
| 26.2 | 25 | 34 / 0 | 3.27 s | [receipt](../verification/ports/26.2/v7/validation-session.json) |
| 26.3 | 25 | 34 / 0 | 3.27 s | [receipt](../verification/ports/26.3/v7/validation-session.json) |

The default Gradle build remains Minecraft 1.21.11. API adaptations are generated
under `build/ports`; named 26.x uses `build-named.gradle`. Forge and NeoForge are
not supported. Entries in `minecraft-targets.json` alone are not evidence of support.

The 1.21.11 JAR was also checked with the installed Prism mod JARs in a separate
world: [modpack receipt](../verification/ports/1.21.11/v7/validation-modpack.json).
No user worlds, login credentials or shader configurations were copied. This does
not guarantee compatibility with every mod, shader, OS or GPU. Windows/macOS were
not validated in these checks.

The [runtime matrix](../verification/v7/runtime-matrix.json) records hashes,
compiler provenance, image checks and interrupted pre-release test attempts.
The [controlled crash-recovery proof](../verification/v7/native-crash-recovery.json)
records the recovered recording and retained marker/world metadata. Performance
measurements and their limits are in the [performance notes](../verification/performance-v6/README.md).

Only matching SHA-512 runtime receipts are accepted by the Modrinth publisher.
GitHub release files are downloaded from Modrinth and verified again before upload.

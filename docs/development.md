# Volt 0.7 development validation

The current development build adds durable recording journals, automatic long
recording finalization when leaving a world, recoverable source segments,
recently deleted clips, bulk restore, undo and three short feedback styles.

The shared suite has 34 passing tests, including real MP4 creation, export intent
recovery, interrupted trash/restore transactions, preserved metadata, overwrite
protection, recorder shutdown and the BGRA capture fallback.

## Production checks

- Minecraft 1.21.11 Fabric: exact production JAR tested fullscreen with the user's
  installed mod JARs in an isolated test world, including Sodium, Iris, Lithium,
  Atlas, EVATEX and AAC Plus. Disconnecting produced a playable 1280×720 MP4.
- Minecraft 1.21.5 Fabric: fullscreen recording and automatic session finalization
  passed. Visual inspection caught and verified a fix for the inherited background
  blur affecting custom labels and clip cards.
- Minecraft 1.20.1 Fabric on Java 17: exact production recording passed and visual
  inspection confirmed smooth text, the search hint and readable clip cards.
- Minecraft 1.21.11 controlled process termination: a 6.27-second unfinished
  recording was recovered on restart, preserving the manual marker and world metadata.
  See `verification/v7/native-crash-recovery.json`.
- Minecraft 26.2 and 26.3: actual playable 720p MP4 output was decoded and visually
  checked for orientation and color. The 26.3 RenderPearl implementation uses GPU
  resizing and three bounded asynchronous readback buffers with Vulkan; RGBA color
  conversion runs in the background encoder. It does not call OpenGL directly.
- Older API adapters also correct texture blending and GUI scissor coordinates.
  All 24 release artifacts now have matching runtime receipts and playable MP4 checks.
  See `verification/v7/runtime-matrix.json` for hashes and compiler provenance.

The installed 1.21.11 Prism build is backed up alongside the previous version.
The compatibility test copies mod JARs into its own directory; it does not copy
the user's worlds, credentials or shader configuration. Loading Iris in that test
does not establish compatibility with every shader pack.

## Porting and publication

`minecraft-targets.json` describes build targets. An entry in that file is a
porting configuration, not a supported-version claim. EVATEX's 24 stable targets
are the porting objective. Current public download support is recorded separately
in `docs/publication/compatibility.md`.

`scripts/build-matrix.py` builds immutable source snapshots and records exact test
results. Minecraft 26.x ships named Java 25 bytecode, so `build-named.gradle`
compiles directly against official client dependencies and published Fabric API
modules without remapping Minecraft. Reference:
[Fabric's 26.1 migration announcement](https://fabricmc.net/2026/03/14/261.html).

`scripts/native-smoke.py` runs the exact production JAR in an isolated fullscreen
client and checks the resulting MP4. The publisher requires the corresponding
SHA-512 receipt for every uploaded Minecraft version. GitHub release downloads
must match both the Modrinth file hashes and the release tag.

## Remaining product limits

FFmpeg is installed separately. Sharing requires a configured upload server.
Inline previews are silent. Crash recovery depends on intact segments and writable
storage. Performance depends on hardware, encoders and shaders; there is no matched
Medal benchmark or guarantee of zero FPS impact.

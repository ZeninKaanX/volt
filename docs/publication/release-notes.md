Volt 0.7 adds safer recording and clip management to the in-game studio:

- Long recordings finalize automatically when leaving a world. Intact interrupted
  segments can be recovered on the next recorder start; a controlled restart test
  recovered a real 6.27-second recording with its marker and world metadata.
- Recently deleted clips, bulk restore, undo and keyboard shortcuts.
- Minimal, Soft and Electric feedback styles with independent button and
  notification volumes. Smooth text and corrected legacy GUI blending/scissors.
- GPU downscaling, bounded frame queues and supported hardware encoders. The 26.3
  build uses RenderPearl asynchronous readback and works with Vulkan.

Choose the JAR for your exact Minecraft version. Fabric and matching Fabric API
are required. Java 17 is used for 1.20–1.20.4, Java 21 for 1.20.5–1.21.11 and
Java 25 for 26.x. FFmpeg must be installed separately.

Every release JAR has its own exact SHA-512 runtime receipt and produced a playable
MP4 in an isolated fullscreen Minecraft session. The 1.21.11 build was also checked
with the installed Prism mod JARs, including Sodium, Iris, Lithium, Atlas, EVATEX
and AAC Plus. This does not guarantee every mod, shader or GPU combination.

Link sharing requires a configured upload service. Inline previews are silent.
Audio setup varies by platform; Windows/macOS were not validated here. Recovery
requires intact source files and writable storage. Recording uses resources; zero
FPS loss is not guaranteed, and no matched Medal benchmark was performed.

GitHub downloads are byte-identical to the Modrinth uploads. SHA512SUMS is included.
Modrinth project visibility depends on its moderation status.

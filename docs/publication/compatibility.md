# Minecraft support

EVATEX's public Modrinth project lists these 24 stable Minecraft targets:

1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6,
1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
26.1, 26.1.1, 26.1.2, 26.2, 26.3.

Reference: https://modrinth.com/mod/evatex-background

Volt has validated **1.21.9, 1.21.10 and 1.21.11 Fabric** builds. The 1.21.9 and 1.21.10 ports each passed 27 tests and a fullscreen in-game hardware-encoded MP4 recording check. The remaining targets are a
porting objective, not supported-version claims. EVATEX also publishes Forge and
NeoForge builds; Volt does not currently have those loader integrations.

The default Gradle build remains the tested 1.21.11 artifact. Experimental API
adaptations are generated separately under `build/ports`, and do not overwrite
the main sources. They must compile and pass runtime checks before publication.

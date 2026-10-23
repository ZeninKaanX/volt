# Volt — capture the moment

Save Minecraft moments without leaving the game. Volt combines instant replay,
a local clip library and a full-screen editor in a clean interface.

**0.7.0-alpha.1** adds recording recovery, recently deleted clips, undo and
selectable feedback sounds. See the [validation notes](docs/development.md) and
[compatibility tracker](docs/publication/compatibility.md) for exact tested builds.

## Features

- Save the last 15–120 seconds, or start and stop a longer recording.
- Automatic clips for melee combos and selected combat/adventure events.
- Browse, search, favorite and filter local MP4 clips; undo deletions and restore clips from recently deleted.
- Automatically finalize long recordings on world exit; recover intact unfinished segments after interruption.
- Choose Minimal, Soft or Electric feedback, with separate button and notification volumes.
- Preview, trim and export clips with speed, resolution, volume and portrait controls.
- Minecraft nickname, profile customization and selectable avatars.
- Smooth interface lettering and English/Turkish translations, following Minecraft's language.
- Optional Discord desktop activity: **Clipped with Volt**. No Discord ID or account token required.
- GPU downscaling, bounded asynchronous readback and supported hardware encoders.
  OpenGL capture also uses GPU color conversion where available; 26.3 uses the RenderPearl capture path for Vulkan compatibility.

## Installation

**Current alpha: 0.7.0-alpha.1, Fabric and matching Fabric API.** Choose the JAR
for your exact Minecraft version from [GitHub releases](https://github.com/ZeninKaanX/volt/releases)
or [Modrinth](https://modrinth.com/mod/volt-clips). Modrinth visibility depends on moderation.

| Minecraft | Java |
| --- | --- |
| 1.20–1.20.4 | 17 or newer |
| 1.20.5–1.21.11 | 21 or newer |
| 26.x | 25 or newer |

Install Volt in your instance's `mods` folder. Install **FFmpeg** separately and
select its executable in Volt settings if it is not available on PATH.
FFmpeg is not included in the JAR. Prism Flatpak needs an executable accessible
inside its sandbox.

| Default key | Action |
| --- | --- |
| V | Open Volt |
| F8 | Save an instant replay |
| F9 | Start / stop a long recording |
| F7 | Add a marker |

Keys can be changed in Minecraft Controls. Conflicting default bindings may be
left unassigned; the Volt menu also provides capture controls.

## Recording and sharing

Clips stay in `volt/clips` inside the Minecraft instance by default.
Recording begins in a world when enabled. Performance mode defaults to 720p/30
with hardware encoding, or 540p/30 with the CPU fallback. Recording still uses
resources; FPS impact depends on hardware, shaders and encoder availability.

Linux audio capture can include system audio. Windows/macOS need a supported
FFmpeg audio device configured; otherwise recordings are silent. Inline previews
are silent; open the MP4 externally to hear audio. Long recordings finalize when
leaving a world. Interrupted recording journals are recovered on the next recorder
start when their source segments are intact and storage remains writable.

**Link sharing requires a separately configured Volt upload server.** No public
hosting or automatic uploads are bundled. The optional server source is included
in the repository. Minecraft/Microsoft session tokens must never be used as upload keys.

## Compatibility

The published alpha has been checked on Minecraft 1.21.11 Fabric with a Prism
modpack including Sodium, Iris, Lithium and Atlas Client. This does not guarantee
compatibility with every mod, shader pack or GPU. Each published version has its
own exact artifact receipt from a fullscreen recording test. See the
[compatibility tracker](docs/publication/compatibility.md) for the complete list.

Volt is an independent project inspired by game clipping workflows. It is not
associated with Medal or Discord.

## Türkçe hızlı kullanım

Minecraft sürümüne özel Volt Fabric dosyasını ve uygun Fabric API'yi kur. Yukarıdaki Java tablosunu kullan. FFmpeg'i ayrıca kur.
**V** menüyü açar, **F8** son anları kaydeder, **F9** uzun kaydı
başlatır/durdurur, **F7** işaret ekler. Tuşları Minecraft Kontroller
menüsünden değiştirebilirsin. Klipler varsayılan olarak oyun klasöründeki
`volt/clips` içine gider. Discord kimliği girmen gerekmez.

## Development

Use JDK 21:

```sh
./gradlew build
./gradlew runClient
python3 -m unittest discover -s share-server -v
```

The build includes JUnit coverage for replay buffering, export settings,
preview decoding, event queues, key conflicts and Discord IPC. The optional
sharing service is documented in [share-server/README.md](share-server/README.md).

See [the compatibility tracker](docs/publication/compatibility.md) for exact
tested versions. Build targets are promoted to downloads only after validation.
Original performance measurements and their limitations are recorded in
[the performance notes](docs/verification/performance-v6/README.md).

All rights reserved. Noto Sans retains its included SIL Open Font License.

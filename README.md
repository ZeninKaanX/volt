# Volt — capture the moment

Save Minecraft moments without leaving the game. Volt combines instant replay,
a local clip library and a full-screen editor in a clean interface.

## Features

- Save the last 15–120 seconds, or start and stop a longer recording.
- Automatic clips for melee combos and selected combat/adventure events.
- Browse, search, favorite and filter local MP4 clips.
- Preview, trim and export clips with speed, resolution, volume and portrait controls.
- Minecraft nickname, profile customization and selectable avatars.
- Smooth interface lettering and English/Turkish translations, following Minecraft's language.
- Optional Discord desktop activity: **Clipped with Volt**. No Discord ID or account token required.
- GPU downscaling and color conversion, bounded frame queues and supported hardware encoders.

## Installation

**Current alpha: Minecraft 1.21.10 / 1.21.11, Fabric, Java 21 and matching Fabric API.**
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
are silent; open the MP4 externally to hear audio. Stop long recordings before
leaving a world. This alpha does not recover unfinished recordings after a crash.

**Link sharing requires a separately configured Volt upload server.** No public
hosting or automatic uploads are bundled. The optional server source is included
in the repository. Minecraft/Microsoft session tokens must never be used as upload keys.

## Compatibility

The published alpha has been checked on Minecraft 1.21.11 Fabric with a Prism
modpack including Sodium, Iris, Lithium and Atlas Client. This does not guarantee
compatibility with every mod or GPU. Additional Minecraft versions are being
ported and will be listed only when their matching builds are ready.

Volt is an independent project inspired by game clipping workflows. It is not
associated with Medal or Discord.

## Türkçe hızlı kullanım

Minecraft 1.21.10 veya 1.21.11 Fabric ve sürümüne uygun Fabric API gerekir. FFmpeg'i ayrıca kur.
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

See [the compatibility tracker](docs/publication/compatibility.md) for the
multi-version porting objective. Experimental ports are not advertised as
supported downloads until validated.

All rights reserved. Noto Sans retains its included SIL Open Font License.

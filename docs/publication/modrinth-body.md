# Volt — capture the moment

Save Minecraft moments without leaving the game. Volt combines instant replay,
a local clip library and a full-screen editor in a clean interface.

## Features

- Save the last 15–120 seconds, or start and stop a longer recording.
- Automatic clips for melee combos and selected combat/adventure events.
- Browse, search, favorite and filter local MP4 clips.
- Recently deleted clips, bulk restore and undo.
- Automatic long-recording finalization when leaving a world and recovery of intact unfinished recording segments.
- Minimal, Soft and Electric feedback styles with separate button and notification volumes.
- Preview, trim and export clips with speed, resolution, volume and portrait controls.
- Minecraft nickname, profile customization and selectable avatars.
- Smooth interface lettering and English/Turkish translations, following Minecraft's language.
- Optional Discord desktop activity: **Clipped with Volt**. No Discord ID or account token required.
- GPU downscaling and color conversion, bounded frame queues and supported hardware encoders.

## Installation

**Choose the Fabric JAR for your exact Minecraft version in the Versions tab, with matching Fabric API.**

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
leaving a world. Recoverable unfinished segments are exported on the next recorder
start. Recovery requires intact source files and writable storage.

**Link sharing requires a separately configured Volt upload server.** No public
hosting or automatic uploads are bundled. The optional server source is included
in the repository. Minecraft/Microsoft session tokens must never be used as upload keys.

## Compatibility

The published alpha has been checked on Minecraft 1.21.11 Fabric with a Prism
modpack including Sodium, Iris, Lithium and Atlas Client. This does not guarantee
compatibility with every mod, shader pack or GPU. Version labels are added only
when the matching artifact has compiled, passed its tests and produced a playable
MP4 in a fullscreen Minecraft session. See the source repository for validation
receipts and current limitations.

Volt is an independent project inspired by game clipping workflows. It is not
associated with Medal or Discord.

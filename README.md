# WorldBinder 1.4.0

WorldBinder is a client-side Fabric mod for permitted Minecraft world capture, archive recovery, inspection and target-version vanilla world export.

It is designed for players, builders, server owners, developers and preservation-focused users who want to save world data that their client has already received. Optional Movement Tools support navigation in singleplayer, local test worlds and servers that explicitly permit them. WorldBinder does not bypass server permissions or anti-cheat checks and cannot reveal hidden world data.

> **Release notice:** WorldBinder 1.4.0 targets Minecraft 26.3 with updated Fabric dependencies, SDL-compatible input and rendering API adjustments. It adds a central Chat / Toast / Chat & Toast / None message setting and a separate Movement Tools tab. The code cleanup splits capture, export, recovery, configuration and Control Center responsibilities into smaller components; this port preserves that structure without changing the capture algorithms. Exports still depend on the data received by the client and the selected target version.

## Supported loader

WorldBinder is built and released for **Fabric** on Minecraft **26.3**.

For this release line, use Fabric Loader 0.19.5 or newer and Fabric API 0.160.7+26.3 or newer. Forge, NeoForge and Quilt are not supported at this time. WorldBinder is developed and released as a native Fabric mod.

## Main features

- HotCache-first client-side chunk capture
- Durable chunk block cache for large captures
- Vanilla world folder export
- Target output version selection
- Block, block entity and entity export where available
- Server resource pack export as `resources.zip`
- Recovery and autosave tools
- Recovery cache reuse after crashes
- Automatic temporary cache cleanup after successful exports
- Configurable performance presets
- F9 Control Center
- F10 live chunk map with coverage colors, filters and inspector tooltips
- Ctrl+C chunk center coordinate copying
- Queue Dashboard / Profiler for capture and export telemetry
- Export validation reports
- Compact, collapsible HUD Widget Editor with drag positioning, proportional resize controls and visibility toggles
- Visual color picker with HEX/ARGB input, opacity control and per-widget quick colors
- Refined WorldBinder toast notifications with multi-line text and proportional scaling
- Central Chat / Toast / Chat & Toast / None notification selection
- Optional Movement Tools with individual toggles and bounded strength sliders
- Modernized settings, dropdowns and menu components
- WorldBinder icon buttons in the Minecraft main menu and pause menu
- Responsive GUI scaling across different window sizes and GUI scales
- Dedicated WorldBinder keybind category in Minecraft Controls
- German and English language support

## Movement Tools

Open **Settings > Movement Tools**. Every feature is disabled by default and saved independently:

| Tool | Behavior / range |
| --- | --- |
| Speed | Movement speed, 1.0-5.0x |
| Fly | Collision-aware free flight; separate 0.5-5.0x speed |
| Spider | Climb walls while moving forward against them |
| Jesus | Walk on water surfaces; sneak to enter the water |
| No Fall | Prevent fall damage in local worlds |
| Step | Step height, 1.0-3.0 blocks |
| High Jump | Jump strength, 1.0-3.0x |
| Glide | Reduced falling speed, strength 1.0-5.0 |
| Fast Ladder | Faster ascent/descent, 1.0-5.0x |
| Auto Sprint | Sprint when moving forward and vanilla conditions allow it |
| Safe Walk | Stop at block edges while grounded |
| Auto Jump | Jump against small obstacles when Step is disabled |
| Water Speed | Additional underwater acceleration, 1.0-5.0x |
| Air Control | Additional airborne steering, 1.0-5.0x |

Fly takes priority over other movement helpers without granting or changing creative abilities. Vanilla creative/elytra flight is not replaced unless Fly is enabled. Spider requires actual wall contact and suppresses ground jumps; Step suppresses Auto Jump, which requires a real obstacle and clearance above it. Glide only limits descent; Air Control only changes steering. Jesus uses per-query collision shapes at the actual source/flowing water height, releases on Sneak and leaves underwater movement to vanilla/Water Speed. Input-driven helpers ignore open menus. These tools are separate from capture processing. On remote servers, server rules and movement validation remain authoritative; No Fall is not a remote-server damage bypass. Only enable tools where explicitly allowed.

Minecraft 26.3 is the runtime target. Minecraft 26.2 remains an explicit export target with its own DataVersion, alongside the existing older export targets.

## Default keybinds

| Key | Action |
| --- | --- |
| F6 | Start/stop world download |
| F7 | Set position 1 |
| F8 | Set position 2 |
| F9 | Open WorldBinder Control Center |
| F10 | Open WorldBinder Map |

All keybinds can be changed in Minecraft Controls under the `WorldBinder` category.

## Export output

WorldBinder exports into a normal Minecraft save folder. A typical export may contain:

```text
level.dat
level.dat_old
session.lock
icon.png
resources.zip
README.md
region/
entities/
poi/
data/minecraft/
dimensions/minecraft/overworld/
worldbinder/
```

The `worldbinder/` folder contains metadata, validation output and archive information used by WorldBinder itself. The generated world save can still be opened or imported without reading those files.

## 26.x export layout

For Minecraft 26.x targets, WorldBinder writes a compatibility-oriented layout:

- root-level `region/`, `entities/` and `poi/` folders for Bukkit/Paper/Multiverse-style imports
- `dimensions/minecraft/overworld/` mirrors for modern 26.x world loading
- one optional `dimensions/minecraft/<world-key>/` mirror for server world keys when a clean world name is available
- modern saved-data files under `data/minecraft/`
- a single export `README.md` instead of multiple loose note files

This keeps the exported folder usable for both local testing and common server import workflows while avoiding timestamp-based duplicate dimension keys.

## Temporary capture cache

Large captures use a temporary chunk cache while WorldBinder is running. The cache is stored inside the current Minecraft profile at:

```text
saves/WorldBinder/.cache/
```

This cache keeps captured block data durable during long sessions without keeping every chunk only in memory. WorldBinder removes the temporary cache automatically after a successful export. If Minecraft crashes or an export fails, the cache may remain so recovery data can still be used.

## Target output versions

WorldBinder can export captured data for different target Minecraft versions.

This allows capturing with a newer client and selecting an older output version, for example 1.20.4. WorldBinder applies version-aware handling for folder layout, gamerules, entities, item data, POI data and world metadata.

Downgrades may still be imperfect when captured data contains blocks, entities, items, resource pack content or world features that did not exist in the selected target version.

## Important limitations

WorldBinder can only save data that the client actually receives.

Exports may be incomplete if the server never sent certain chunks, entities, inventories, block entity data, dimensions, maps, scoreboards, resource pack files or server-side logic to the client. Some server-side-only systems cannot be reconstructed from client data alone.

Heavy custom servers, modded content, datapack-driven worlds and resource-pack-only models may require manual review after export.

## Responsible use

Use WorldBinder only on worlds, servers, maps, builds and resource packs that you own, administer, created yourself, or where you have explicit permission to archive or export the content.

You are responsible for how exported data is used. WorldBinder does not grant rights to copy, redistribute or publish content owned by other people, servers or projects.

## Feedback and bug reports

WorldBinder is actively being improved.

Please report bugs, ideas and feedback on GitHub or Discord:

- Repository: https://github.com/Philiipp06/WorldBinder
- Issues: https://github.com/Philiipp06/WorldBinder/issues
- Discord: https://discord.com/invite/V7h3vaXxDZ

Helpful reports include:

- Minecraft version
- WorldBinder version
- Fabric Loader and Fabric API version
- Selected target output version
- Window mode and GUI scale if the issue is UI-related
- What was captured
- What went wrong
- Whether the export was opened in singleplayer, Paper or Multiverse
- Screenshots, logs or exported test worlds if possible

## Build from source

WorldBinder requires Java 25, Fabric Loader 0.19.5 or newer and Fabric API 0.160.7+26.3 or newer for the Minecraft 26.3 release line.

```bash
./gradlew clean build
```

The official release artifact is the Fabric jar from `build/libs/`.

## Status

WorldBinder 1.4.0 is the current development release line for Minecraft 26.3.

The project will continue to improve capture accuracy, target-version compatibility, entity handling, recovery behavior, performance and overall usability based on real feedback.

# WorldBinder Beta 1

WorldBinder is a client-side Fabric mod for permitted Minecraft world capture, archive recovery, inspection and target-version vanilla world export.

It is designed for players, builders, server owners, developers and preservation-focused users who want to save world data that their client has already received. WorldBinder does not provide combat advantages, movement advantages, X-ray features, hidden server bypasses or permission bypasses. It focuses on responsible world preservation and local export workflows.

> **Beta notice:** WorldBinder Beta 1 is intended for public testing. The interface, capture workflow and export structure are now much closer to release quality, but exports can still differ from the original server-side world depending on what the client received, server setup, custom content, resource packs, entities, dimensions and selected target output version.

## Supported loader

WorldBinder is built and released for **Fabric**.

Quilt and NeoForge are not supported in this release. They may be considered in the future only if the shared WorldBinder core is separated cleanly from the Fabric-specific client layer and each loader can be tested properly.

## Main features

- HotCache-first client-side chunk capture
- Vanilla world folder export
- Target output version selection
- Block, block entity and entity export where available
- Server resource pack export as `resources.zip`
- Recovery and autosave tools
- Configurable performance presets
- F9 Control Center
- F10 live chunk map with coverage colors, filters and inspector tooltips
- Queue Dashboard / Profiler for capture and export telemetry
- Export validation reports
- Responsive Beta GUI scaling across different window sizes and GUI scales
- Dedicated WorldBinder keybind category in Minecraft Controls
- German and English language support

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

Please report bugs, ideas and feedback on GitHub:

- Repository: https://github.com/Philiipp06/WorldBinder
- Issues: https://github.com/Philiipp06/WorldBinder/issues

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

WorldBinder requires Java 25 and Fabric Loom.

```bash
./gradlew clean build
```

The official release artifact is the Fabric jar from `build/libs/`.

## Status

WorldBinder Beta 1 is a public testing release.

The goal of this Beta is to validate real-world capture accuracy, export compatibility, recovery behavior, performance and usability before a future stable 1.0 release.

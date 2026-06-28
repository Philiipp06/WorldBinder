# WorldBinder 1.2.0

WorldBinder is a client-side Fabric mod for permitted Minecraft world capture, archive recovery, inspection and target-version vanilla world export.

It is designed for players, builders, server owners, developers and preservation-focused users who want to save world data that their client has already received. WorldBinder does not provide combat advantages, movement advantages, X-ray features, hidden server bypasses or permission bypasses. It focuses on responsible world preservation and local export workflows.

> **Release notice:** WorldBinder 1.2.0 is the menu and notification polish release for Minecraft 26.2. It keeps the stable capture, recovery and durable chunk cache foundation while adding a modernized control center, dropdown navigation, responsive scaling improvements and polished HUD notifications instead of development-style chat feedback. Exports can still differ from the original server-side world depending on what the client received, server setup, custom content, resource packs, entities, dimensions and selected target output version.

## Supported loader

WorldBinder is built and released for **Fabric** on Minecraft **26.2**.

For this release line, use Fabric Loader 0.19.2 or newer and Fabric API 0.152.0+26.2 or newer. Forge, NeoForge and Quilt are not supported at this time. WorldBinder is developed and released as a native Fabric mod.

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
- Responsive GUI scaling across different window sizes and GUI scales
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

WorldBinder requires Java 25, Fabric Loader 0.19.2 or newer and Fabric API 0.152.0+26.2 or newer for the Minecraft 26.2 release line.

```bash
./gradlew clean build
```

The official release artifact is the Fabric jar from `build/libs/`.

## Status

WorldBinder 1.2.0 is the current stable public release for Minecraft 26.2.

The project will continue to improve capture accuracy, target-version compatibility, entity handling, recovery behavior, performance and overall usability based on real feedback.

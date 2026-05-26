# WorldBinder Alpha V3

**Client-side world capture, archive management, live chunk mapping, recovery, validation and server-friendly export for Minecraft Java / Fabric.**

WorldBinder is built for Minecraft **26.1.2** and gives permitted world archival workflows a proper in-game control center. It focuses on data your client can actually see, then helps you organize, validate and export that data into local world folders for review, backup or approved offline use.

> **Alpha notice:** WorldBinder Alpha V3 is intended for testing and validation. Exports can still require checking, especially on unusual servers, modded setups, older target versions or large entity-heavy scenes.

---

## Legal Use Notice

Use WorldBinder only on worlds, maps, builds, servers and resource packs that you own, administer, created yourself, or where you have explicit permission.

Do **not** use WorldBinder to copy, redistribute, extract or reproduce third-party servers, maps, builds, resource packs, models or protected content without permission. You are responsible for how you use exported data.

---

## Alpha V3 Highlights

- **F9 Control Center** with Overview, Capture, Map, Archives & Validation, Recovery, Settings, Tools and About.
- **F10 Map** for captured, partial, queued and failed chunk states with filters and live navigation.
- **Responsive virtual GUI canvas** so WorldBinder screens keep a stable fullscreen-style layout across small windows, large windows and different GUI scales.
- **Client-visible capture** for chunks, block entities, entities, maps, stats and advancements where available.
- **Recovery sessions** for interrupted captures, disconnects and crash-safe workflows.
- **Target output version selection** with generation-family exporters behind the scenes.
- **26.x server-import fixes** for Paper/Multiverse-style world keys, modern dimension mirrors and safe gamerule registry output.
- **Resource-pack export support** when the client can access the server pack.
- **Profiler and diagnostics** for capture state, StorageFlow progress, queue timing and export stages.
- **English and German localization** with English fallback.

---

## Controls

Default keys:

- **F9** — Open WorldBinder Control Center
- **F10** — Open WorldBinder Map

Keybinds can be changed in Minecraft's Controls menu.

---

## Basic Workflow

1. Join a world or server you are allowed to capture.
2. Press **F9** to open the WorldBinder Control Center.
3. Open **Capture**, choose an archive name and target output version.
4. Start capture and move through the area you want to archive.
5. Use **Pause** if you want capture work to stop temporarily.
6. Use **Finish** when you are ready to process the queued data.
7. Open **Archives & Validation** to preview, validate, continue, duplicate or export.
8. Open the exported world in the selected Minecraft version and check the result.

WorldBinder only stores data your client receives. It cannot reconstruct hidden server-side data, unloaded chunks, commands, plugins, scripts, private storage or content that was never sent to the client.

---

## Target Output Versions

WorldBinder can export metadata for a selected Minecraft target version while running on the current mod version. The UI displays final release versions, while internally the exporter groups compatible versions by broader generation profiles.

Downgrades are conservative. Newer blocks and data that do not exist in older targets may be simplified or replaced so the world can load more safely. Always validate and test exports, especially when targeting older versions.

---

## Server Import Notes

Alpha V3 improves exported world folders for server-side imports. Exports keep normal root-level `region/`, `entities/` and `poi/` folders for Bukkit/Paper/Multiverse-style loaders while also writing modern 26.x dimension mirrors.

For Paper/Multiverse imports, use a lowercase world name such as `worldbinderexport`. Modern Paper loads custom worlds with that world key, for example `minecraft:worldbinderexport`, so WorldBinder mirrors 26.x exports to `dimensions/minecraft/<world-name>/` as well.

WorldBinder writes vanilla-safe gamerules for 26.x exports. Known legacy gamerules are mapped to supported 26.x registry keys, inverted rules are normalized, and unknown or modded gamerules are skipped so Paper does not reject `game_rules.dat`.

If Paper logs `java.nio.file.AccessDeniedException` for `.mca` files, the world data is probably present but the server process cannot read it. Stop the server and fix file ownership/permissions through your panel or host before importing again. A restart alone usually does not fix wrong ownership.

---

## Resource Packs

When the server resource pack can be accessed by the client, WorldBinder can copy it into the exported world as `resources.zip`. Minecraft should then offer the pack when opening the exported save.

If a server blocks, changes or fails resource-pack delivery, WorldBinder can only include what the client was able to receive or cache.

---

## Performance Notes

WorldBinder is designed to be polite to both the client and server:

- Capture work is budgeted per tick.
- Visible nearby chunks are prioritized.
- Pause stops capture work.
- Adaptive throttling can reduce work under load.
- Presets can be changed between Safe, Balanced, Fast and Extreme.
- The Profiler shows capture, queue and StorageFlow telemetry while testing.

For large areas, Balanced or Fast is recommended. Extreme is for short tests or strong machines only.

---

## Data Location

WorldBinder stores captures, recoveries and exports in the mod data folder inside your Minecraft instance. The About page shows the exact path used by your installation.

---

## Feedback and Bug Reports

Found a bug, export issue, missing translation or have an idea for WorldBinder?

Please report it on GitHub:

- Repository: https://github.com/Philiipp06/WorldBinder
- Issues: https://github.com/Philiipp06/WorldBinder/issues

or on Discord:

- Discord: https://discord.com/invite/V7h3vaXxDZ

Include your Minecraft version, WorldBinder version, selected target output version, screenshots/logs and a short description of what happened.

---

## Release Status

WorldBinder Alpha V3 is a test release for capture, recovery, validation, responsive UI and server-friendly export workflows. Expect edge cases with unusual servers, modded clients, older target versions, heavy entity scenes and large captures.

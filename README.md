# WorldBinder Alpha V2

**Client-side world capture, archive, preview, validation and target-version export for Minecraft Java / Fabric.**

WorldBinder helps you archive client-visible world data, manage interrupted sessions and export local world folders for review, backup or permitted offline use. It is built for Minecraft **26.1.2** and designed around a clear in-game control center instead of command-heavy workflows.

> **Alpha notice:** this is an early testing release. Capture, recovery, validation and export workflows are available, but exported worlds can still require checking and may not perfectly match server-side data.

---


## Feedback and Bug Reports

Found a bug, export issue, missing translation or have an idea for WorldBinder?

Please report it on GitHub:
- Repository: https://github.com/Philiipp06/WorldBinder
- Issues: https://github.com/Philiipp06/WorldBinder/issues

Include your Minecraft version, WorldBinder version, selected target output version, screenshots/logs and a short description of what happened.

---

## Legal Use Notice

Use WorldBinder only on worlds, maps, builds or servers that you own, administer, created yourself, or where you have explicit permission.

Do **not** use WorldBinder to copy, redistribute, extract or reproduce third-party servers, maps, builds, resource packs, models or protected content without permission. You are responsible for how you use exported data.

---

## Highlights

- **F9 Control Center** for capture, archives, recovery, validation, tools and settings.
- **F10 Map** with captured/missing chunk visibility and layer controls.
- **Client-visible capture** for chunks, block entities, entities, maps, stats and advancements where available.
- **Recovery sessions** for interrupted captures and disconnect/crash-safe workflows.
- **Target output version** selection for final release versions, with generation profiles behind the scenes.
- **Resource-pack export support** that copies available server packs into the exported world.
- **Validation and preview tools** to inspect archive quality before using the result.
- **English and German localization**, following the active Minecraft client language with English fallback.

---

## Controls

Default keys:

- **F9** — Open WorldBinder Control Center
- **F10** — Open WorldBinder Map

Keybinds can be changed in Minecraft's Controls menu.

---

## Basic Workflow

1. Join the world or server you are allowed to capture.
2. Press **F9** and open the Control Center.
3. Pick an archive name and target output version.
4. Start capture.
5. Move through the area you want to archive.
6. Pause or finish the capture when ready.
7. Open **Archives & Validation** to preview, validate, duplicate, continue or export.
8. Open the exported world in the selected Minecraft version and check the result.

WorldBinder only stores data your client actually receives. It cannot reconstruct hidden server-side data, unloaded chunks, commands, plugins, scripted logic, private storage or content that was never sent to the client.

---

## Target Output Versions

WorldBinder can export metadata for a selected Minecraft target version while running on the current mod version. The UI displays final release versions, while internally the exporter groups compatible versions by broader generation profiles.

This is useful when you want to capture on a modern client but review or use the result in an older supported version. Downgrades are conservative: newer blocks and data that do not exist in older targets may be simplified or replaced so the world can load more safely.

Always validate and test exports, especially when targeting older versions.

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

For large areas, Balanced or Fast is recommended. Extreme is for short tests or strong machines only.

---

## Data Location

WorldBinder stores captures, recoveries and exports in the mod data folder inside your Minecraft instance. The About page shows the exact path used by your installation.

---

## Release Status

Alpha 1.0 is intended for testing, feedback and validation. Expect edge cases with unusual servers, modded clients, older target versions, heavy entity scenes and large captures.

# WorldBinder Alpha V3 — How to Use

WorldBinder is a client-side Fabric tool for permitted world capture, archive management, recovery, validation and export. It records data that your Minecraft client receives and can export that data into a local world folder.

> Use WorldBinder only on worlds, maps, builds or servers you own, administer, created yourself, or where you have explicit permission. Do not use it to copy, archive, redistribute or extract third-party content without permission.

---

## 1. Install

1. Use Minecraft Java **26.1.2**.
2. Install Fabric Loader **0.19.2** or newer for the matching Minecraft version.
3. Install the matching Fabric API.
4. Put the WorldBinder jar into your `mods` folder.
5. Start Minecraft.

WorldBinder is client-side. It does not need to be installed on the server.

---

## 2. Open WorldBinder

Press **F9** to open the Control Center.

The Control Center contains:

- Overview
- Capture
- Map
- Archives & Validation
- Recovery
- Settings
- Tools
- About

Press **F10** to open the map directly.

WorldBinder Alpha V3 uses a virtual responsive GUI canvas. Menus are designed to keep the same layout across fullscreen, smaller windows and different GUI scales.

---

## 3. Start a Capture

1. Open **Capture**.
2. Enter an archive name.
3. Select the target output version.
4. Choose a performance preset if needed.
5. Press **Start**.

Move through the area you want to save. WorldBinder prioritizes chunks around your player and records data as it becomes visible to the client.

Use **Pause** when you want capture work to stop temporarily. Use **Finish** when you are ready to process the queue and export or validate the archive.

---

## 4. Choose a Target Version

The target version controls the version metadata used for the exported world. You can type a final release version or cycle with Prev/Next.

Examples:

- `26.1.2`
- `1.21.1`
- `1.20.1`

Older targets may require block/data simplification. Always open and validate the result in the selected version.

---

## 5. Validate and Preview

Open **Archives & Validation** after a capture.

Useful actions:

- **Preview** — inspect the archive.
- **Validate** — check for missing or suspicious data.
- **Continue** — resume a previous archive.
- **Duplicate** — create a copy before experimenting.
- **Delete** — remove an archive you no longer need.
- **Export Preview** — generate a testable world folder from the selected archive.

---

## 6. F10 Map

Use the **F10 Map** to inspect saved, partial, queued and failed chunks.

Helpful controls:

- **Follow** — keep the map centered on your player.
- **Player / Origin** — jump to useful reference positions.
- **View** — switch visible map/chunk layers.
- **Filters** — focus on specific capture states.
- **Queue rescan** — re-check visible chunks when testing capture quality.

---

## 7. Recovery

If Minecraft disconnects, crashes or closes during capture, WorldBinder can keep recovery data.

Open **Recovery** from the Control Center to inspect and continue available recovery sessions.

---

## 8. Settings

The Settings screen is split into sections:

- **General** — archive name, target output, capture radius and export toggles.
- **Performance** — tick budgets, queue limits and capture pacing.
- **HUD & Map** — bossbar, radar, map layers and overlay details.
- **Safety** — recovery, resource-pack fallback, export extras and gamerules.

The Safety tab contains subpages so larger settings such as gamerules stay readable. Scroll when a page has more content than fits on screen.

---

## 9. Gamerules

Gamerules can be configured from the Safety settings.

You can use presets or toggle individual rules. `randomTickSpeed` is controlled with a slider.

For 26.x exports, WorldBinder writes vanilla-safe gamerules only. Known legacy names are mapped to supported 26.x registry keys, unsupported or modded rules are skipped, and inverted rules are normalized before writing `game_rules.dat`.

---

## 10. Resource Packs

When possible, WorldBinder places the captured server resource pack into the exported world as `resources.zip`.

Minecraft should offer that pack when opening the world. If the pack was never successfully received by the client, it may not be available for export.

---

## 11. Profiler

Open the **Profiler** from Tools or the Map screen while testing larger captures.

It shows:

- current capture mode
- queue size
- StorageFlow stage and progress
- completed/failed jobs
- stage timing bars

This is mainly for Alpha testing, performance tuning and bug reports.

---

## 12. Good Testing Checklist

Before sharing an Alpha V3 result, test:

- Capture start, pause, resume and finish.
- Export with the current target version.
- Export with an older target version.
- Validation result.
- Resource-pack prompt in the exported world.
- Loading the world in the selected Minecraft version.
- F9/F10 UI behavior in fullscreen and smaller windows.
- Paper/Multiverse import in a separate test environment if the export is meant for a server.

---

## Server Imports

For Bukkit/Paper/Multiverse-style imports, export with the same target version as the server and use a lowercase world name, for example `worldbinderexport`.

For 26.x targets, WorldBinder writes multiple compatible layouts:

- root `region/`, `entities/`, `poi/` for classic world-folder checks
- `dimensions/minecraft/overworld/` for local 26.x clients
- `dimensions/minecraft/<world-name>/` for Paper/Multiverse custom-world keys

If the server logs `AccessDeniedException` while loading `.mca` files, the folder structure is not the main issue. The server process cannot read the files. Stop the server and fix ownership/permissions through your panel or host, then import again.

---

## Feedback, Ideas and Bugs

Please report issues or ideas on GitHub:

- https://github.com/Philiipp06/WorldBinder
- https://github.com/Philiipp06/WorldBinder/issues

For bug reports, include the Minecraft version, WorldBinder version, selected target output version, screenshots/logs and what you expected to happen.

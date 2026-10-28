# Changelog

## 1.7.0

**Keep a World Project across sessions, capture related dimensions and choose exactly what goes into each export.**

Changes below are relative to **WorldBinder 1.6.3**.

## Added

- Persistent World Projects: resume finished archives after restarting Minecraft, add new areas and deliberately update recorded chunks, including removed blocks.
- Multi-dimension projects and a dimension selector in the 2D/3D map. Related dimensions export into one vanilla world.
- **Version-aware game-rule editor:** 60 rules with exact availability, defaults and 26.x registry keys per export target, seven categories, a "Changed only" filter, per-rule reset, sliders/steps for numbers and detailed tooltips.
- Network-project management with separate proxy backends, saved Recovery before world changes and explicit assignment when the destination is unclear.
- Optional automatic capture on server join, off by default, with global/per-server profiles and resume for explicitly assigned projects.
- Optional sequential container scanner with normal interaction reach, full-response checks, double-chest handling, retries, timeouts and interruption on manual input.
- Manual container merging: **Keep**, **Overwrite confirmed slots** or **Fill missing slots**, with optional project defaults and separate known-empty/unknown slot states.
- Live entity filters for new dropped items, XP orbs, armor stands, hostile mobs and individual registry IDs. Existing saved data is retained.
- Optional ReplayMod/Flashback **playback capture on Fabric**, with pause support, protection on backward seeks, separate earlier-time projects and resume with the same recording after restart.
- Shared F7/F8/F10 area selection with editable Y bounds, size and coverage. Selected-area vanilla world and **Sponge v3 `.schem`** export uses full recorded blocks, states, block entities and supported entities.
- Optional in-world chunk frames for confirmed, processing and failed recording areas.
- Export-only entity/block filters with excluded counts. The source archive stays unchanged.
- Received map-image coverage and original section biome/light data stored with the project. Optional own player state includes available inventory, equipment, XP, health, food, position, ender chest, statistics and advancements.
- Void, Flat or Normal generation outside recorded chunks; optional seed for Normal.
- Separate **NeoForge 26.3** build alongside Fabric.
- Client-only `/worldbinder` plus `settings`, `map`, `capture`, `archives`, `movement`, `tools`, `profiler`, `status` and `help`.
- **French** interface language next to English and German. Every `fr_*` Minecraft language uses it; generated export files stay German/English.
- In-world highlights for **unopened** (amber, pulsing), **partly known** (blue) and optionally **verified** (green) containers, including double chests and container entities, with slot labels and an HUD counter.
- Optional export extras: `worldbinder/players.csv` (opt-in player-list recording from the tab list and visible players, spreadsheet-safe quoting) and `worldbinder/dimensions.txt` (dimension tree with target folders and contents).
- New **Capture** settings tab (containers, world overlay, player lists) and new interface icons.

## Improved and fixed

- Export preparation runs in the background, reuses the reviewed snapshot and reduces duplicate cache/region processing. Compact archives reduce storage use while retaining read-back checks.
- Export monitor with project, target, counts, phases, progress and elapsed/estimated remaining time, including clear disconnect information.
- Written-world validation compares expected chunks, blocks and supported additional content, including player items and advancement data. File validity and capture completeness are reported separately.
- Permanent project-scoped Recovery prevents collisions between equally named projects. Failed writes preserve the previous complete revision.
- Available received server resource packs stay tied to their saved revision for offline resume and re-export; failed pack/archive writes do not replace the previous complete state.
- Missing, zero-byte or corrupt expected cache files stop saving with an error; valid recorded empty chunks remain supported.
- Invalid replacement block IDs, properties and values are rejected without changing the previous selection.
- Block replacement/filter changes invalidate affected light for recalculation.
- Large selections are processed in bounded steps; entities and block entities respect all three selection axes. Unreceived areas are not recorded as air.
- The 2D and 3D maps use received chunks consistently, including outside active recording. Player headings no longer spin across angle wraparound or map rotation.
- Cancelling export review no longer starts another export. Returning to a paused single-dimension project no longer triggers repeated world-change saves.
- Correct game-rule translations, export replacement-button layout and German/English status, menu and report text.
- Improved older-version block/property/biome mappings and sign/spawner data. Minecraft 1.13 receives the correct completed-chunk status so recorded terrain is retained.
- Later received entity metadata and equipment refresh the capture cache, including frame map contents and rotation.
- Verified legacy play-time statistic renames preserve exact tick counts. Conflicting aliases are rejected, and version-specific player progress limits are reported without changing the original archive.
- Minecraft 1.13 export review explicitly reports unavailable map locks and unreliable lighting recalculation after block replacements or filters. Original map pixels and capture data remain in the archive for newer export targets.

## Retained from 1.6.3

- Existing 2D/3D live map, chunk radar, waypoints, received resource-pack colors, PNG/tile exports, GLB/OBJ models and rendered map images.
- HUD Widget Editor, Queue Dashboard/Profiler, theme/accessibility/notification controls and 24 optional Movement Tools with profiles and hotkeys.
- The 1.6.3 fixes for Minecraft 26.3 block-state palettes and bundled world packs, plus terrain visibility below barriers/glass and the Invisible blocks layer. These are retained behavior, not new 1.7.0 features. Nonstandard Void/Flat generators can still show the separate experimental-settings warning described below.

## Compatibility

- **Fabric:** Minecraft 26.1, 26.1.1, 26.1.2, 26.2, 26.3; Java 25, Fabric Loader 0.19.5+ and matching Fabric API.
- **NeoForge:** Minecraft 26.3; Java 25 and NeoForge 26.3.0.95 or newer within 26.3.
- Install the matching loader's JAR. Mod Menu is optional on Fabric.
- Optional Fabric replay integration was tested with the matching Minecraft builds of ReplayMod 2.6.28 and Flashback 0.43.6. It captures playback, not direct file import.
- Schematic imports were checked with WorldEdit 7.4.6, FAWE 2.16.1 and Litematica 0.29.1 / MaLiLib 0.30.2.
- Network tests: BungeeCord Build 2107 on Fabric/NeoForge 26.3; Waterfall Build 615 on Fabric 26.2; server-side ViaVersion 5.12.1 on Velocity with Fabric/NeoForge 26.3 clients and Vanilla 1.20.4 backends. Separate client-side tests use ViaFabric 0.4.22+188-26.x and ViaFabricPlus 5.1.2 on Fabric 26.3 against Vanilla 1.20.4. Both 26.3 loaders were checked with End portal round trips and a custom dimension using a received vanilla type.
- Void/Flat generation in recorded Nether/End dimensions may trigger Minecraft's experimental-settings warning; the version preview reports this consequence.
- Existing settings, archives and legacy Recovery folders remain readable. Back up before upgrading; new format-4 projects should be edited with 1.7.0.

## Deutsch

**Weltprojekte behalten und später fortsetzen:** Neue Gebiete ergänzen, bekannte Chunks samt entfernten Blöcken aktualisieren und zusammengehörige Dimensionen gemeinsam exportieren. Proxy-Backends bleiben getrennt; unklare Wechsel pausieren nach einer Recovery für deine Zuordnung. Optionaler Auto-Start und Serverprofile erleichtern wiederkehrende Aufnahmen.

Neu sind Französisch als dritte Sprache, hervorgehobene ungeöffnete Container in der Welt, Spielerliste (CSV) und Dimensionsbaum (TXT) im Export, der Einstellungsreiter „Aufnahme“ und versionsgenaue Spielregeln mit Kategorien und Tooltips. Außerdem Container-Scanner und manuelles **Behalten / Überschreiben bestätigter Slots / Fehlendes ergänzen**, Live-Entity-Filter, ReplayMod-/Flashback-Aufnahme während der Wiedergabe auf Fabric, eine gemeinsame F7/F8/F10-Auswahl, Sponge-v3-`.schem`-Export und Chunkrahmen. Empfangene Kartenbilder, Biome, Licht und Ressourcenpakete bleiben erhalten; den verfügbaren eigenen Spielerstand kannst du bewusst übernehmen.

Der Export verwendet die vorbereiteten Daten erneut, arbeitet sparsamer und zeigt Phasen sowie Fortschritt ausführlicher an. Die Dateiprüfung vergleicht erwartete Inhalte und trennt gültige Dateien von vollständigen Aufnahmen. Behoben sind unter anderem Recovery-Kollisionen, ungültige Ersatzblöcke, beschädigte Cachedateien, veraltetes Licht nach Ersetzungen, rotierende Spieler-Markierungen, abgeschnittene Ersatzblock-Buttons und wiederholte Exportstarts nach dem Abbrechen. Legacy-Mappings und der 1.13-Chunkstatus wurden verbessert.

Getrennte JARs: **Fabric für 26.1, 26.1.1, 26.1.2, 26.2 und 26.3**, **NeoForge für 26.3**. Java 25; Fabric benötigt zusätzlich die passende Fabric API. Alte Archive und Einstellungen bleiben lesbar.

WorldBinder only saves data received by the client; missing content and uncertain conversions remain visible. Archive only content you own or have permission to save. / WorldBinder sichert ausschließlich empfangene Daten. Fehlende Inhalte und unsichere Konvertierungen bleiben sichtbar. Nimm nur eigene oder zur Sicherung freigegebene Inhalte auf.

[User guide / Anleitung](https://github.com/Philiipp06/WorldBinder/blob/main/HOW_TO_USE.md) · [Tested versions and limits / Prüfmatrix](https://github.com/Philiipp06/WorldBinder/blob/main/docs/1.7.0-validation.md)

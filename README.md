# WorldBinder 1.7.0

WorldBinder is a client-side Fabric and NeoForge mod for permitted Minecraft world capture, persistent world projects, inspection and target-version vanilla world export.

It is designed for players, builders, server owners, developers and preservation-focused users who want to save world data that their client has already received. Optional Movement Tools support navigation in singleplayer, local test worlds and servers that explicitly permit them. WorldBinder does not bypass server permissions or anti-cheat checks and cannot reveal hidden world data.

> **1.7.0 – Keep the project, continue tomorrow:**
> - Reopen finished archives in the matching world project. Explore new chunks or deliberately rescan known ones, including removed blocks. Related dimensions can share a project; unrelated proxy backends stay isolated. The map, committed chunks and received auxiliary data survive restarts.
> - Review new/updated chunk counts, version impacts and your replacement blocks before saving a new revision. Failed writes leave the previous revision intact.
> - Preserve server-confirmed container slots and full item data, checked empty inventories, original section biomes/light, received Minecraft map images, and optional own player/ender chest/statistics/advancements.
> - Choose Void, Flat or Normal generation outside the recording, with an optional seed. A seed cannot reconstruct unknown server areas.
> - Reopen the written world files for validation. **Valid files** and **complete received data** are separate results; unknown inventories, maps and original section data remain visible as gaps.
> - A separate **NeoForge 26.3** jar uses the same capture, export, map, HUD and movement implementation. See the [tested-version matrix and evidence](docs/1.7.0-validation.md).
> - Strict replacement validation rejects unknown IDs/properties/values without changing your choice. Missing, empty or damaged expected cache files stop saving and retain the previous revision.
> - Changed blocks invalidate exported light in the affected chunk and neighbours; original archive light is retained. Player validation compares actual slots, components, equipment, statistic values and advancement criteria.
> - `/worldbinder` opens the F9 Control Center on either loader, in singleplayer and multiplayer, entirely on the client. Both map views build received terrain while open; Explore keeps it running with the map closed. Player headings use bounded angles and the shown camera projection.
> - Export preparation and recovery cache reads run in the background. Acceptance includes a real dedicated vanilla server with an HTTP pack and a full process restart, plus measured 10,000-chunk projects.
> - Recovery belongs to a persistent project/world/dimension identity. Equally named projects cannot overwrite or clean up each other's recovery.
> - F7/F8 and F10 share an inclusive 3D area selection. Large selections use bounded processing; unreceived areas stay unknown. Export complete recorded block data as a selected world or **Sponge v3 `.schem`**.
> - Explicitly enable the **container scanner** to open one reachable container at a time and wait for verified full server contents. It supports checked empty containers, double chests and supported container vehicles, and stops on manual input, pause or world changes.
> - Optional world chunk frames distinguish confirmed scope, pending work and failures, retaining a visible previous secured revision during updates. Export filters exclude block/entity types in the export copy while preserving the complete source project.
> - DE/EN text audits check keys and argument counts. New report findings retain translation keys; old text reports remain readable. Generated world help and validation reports are bilingual.
> - **French** joins English and German as a full interface language; the audit checks all three catalogs.
> - **Unopened containers** are outlined in the world (amber = unopened, blue = partly known, optional green = verified) with slot labels and an HUD counter.
> - Exports can add a **player list** (`worldbinder/players.csv`, opt-in recording) and a **dimension tree** (`worldbinder/dimensions.txt`).
> - A new **Capture** settings tab and a version-aware game-rule editor: 60 rules with exact per-version availability and defaults, seven categories, "changed only", per-rule reset and tooltips.
> - Choose container **Keep / Overwrite confirmed slots / Fill missing** in the optional container overlay. Project live-entity filters reduce new capture data while preserving previously saved records and normal Minecraft networking.
> - Capture during optional **ReplayMod / Flashback playback**, including pause, seek fences and explicit earlier-time branches. Saved projects can be resumed with the same recording after a full restart. Replay cameras never invent own player data.
> - Enable default-off automatic capture on join, with explicit backend/project association and optional server profiles. Unknown network transitions commit Recovery and wait for assignment. F10 can display the project's stored dimensions.
> - Exact legacy registry facts and structural NBT mappings improve older exports. Minecraft 1.13 receives its required final `fullchunk` status; read-back rejects an incompatible generation stage before it can cause regenerated terrain.
>
> [Projects, networks, profiles and replay guide / Projekte, Netzwerke, Profile und Replays](docs/1.7.0-beta-expansion.md) · [Areas, scanner and filters / Bereiche, Scanner und Filter](docs/1.7.0-area-scanner-and-filters.md) · [Containers, lists and settings / Container, Listen und Einstellungen](docs/1.7.0-containers-lists-settings.md) · [Continue a project / Projekt fortsetzen](docs/1.7.0-projects.md) · [User guide](HOW_TO_USE.md) · [Release notes](CHANGELOG.md)

The following notes describe earlier releases; their fixes remain included.

> **1.6.3 – Hotfix for Minecraft 26.3 exports:**
> - **Blocks are exported again:** Minecraft 26.3 changed how block states are stored in chunks (`id`/`properties` instead of `Name`/`Properties`). Exports for 26.3 now use the new format; before, every block loaded as air and only entities remained.
> - **Server resource pack is loaded again:** 26.x expects the bundled world pack at `resourcepacks/resources.zip`; exports now place it there, so Minecraft offers it when you open the world.
> - **No more "experimental settings" warning:** exports define the complete vanilla dimension set (Overworld, Nether, End) and write `stopwatches.dat` in the expected format.
> - **Map shows the ground under barriers again:** blocks without a map color (barriers, light blocks, glass, panes …) are now looked through like on a vanilla map, so grass under a barrier layer no longer appears as gray stone on the radar, the 2D and the 3D map. A new **Layers > Invisible blocks** toggle shows barriers, light blocks and structure voids when you need them.
> - **Export again:** select an older export in the archive list and click **Export again** to rebuild it from its stored archive with the fixed exporter (the original folder is kept).

> **1.6.2 – 3D map, custom blocks & waypoints:**
> - **3D live map:** press **V** (or the 3D button) in F10 for a rotatable, tiltable 3D landscape with an animated switch from the 2D map. Loaded chunks are drawn with their real vertical structure – air under bridges, roller coasters or tree crowns stays visible – with directional light, sun shadows and block edges. Rendering runs in the background, so the game keeps its frame rate.
> - **3D export:** export the map as a colored 3D block model with real overhangs (`.glb`, plus `.obj` for smaller areas) or as an anti-aliased high-resolution 3D render (PNG, transparent and with sky). 2D PNG exports of small and medium areas now use up to 4 pixels per block with block relief.
> - **Legal notice in every export:** world exports (`README.md`, `EXPORT_VALIDATION.txt`) and map exports (`map_info.txt`) now include the legal and responsible-use notice.
> - **Custom server blocks on the map:** map colors can now come from the active resource packs. In *Auto* mode, blocks that a server pack adds through unused block states (for example note block instruments) or replaces get their real texture color.
> - **Waypoints:** place named points with **P** or Shift+right click, see them in 2D and 3D (with edge markers when off-screen) and get an automatic "last death" point.
> - **Community docs:** reworked Code of Conduct, Contributing guide and Security policy, a new Accessibility statement and GitHub issue/PR templates.

> **1.6.1 – HUD polish:**
> - **Chunk radar:** the radar map now fills the full widget width; current chunk, coverage percentage and a coverage bar sit in one info strip below. Coordinates are optional (off by default) and shown as one compact line that no longer gets cut off.
> - **Status bar:** title and status line are centered, with the real WorldBinder logo, a pulsing status dot, the ETA and a percentage pill.

> **1.6.0 – Interface, Movement & Live Map update:**
> - **New interface system:** rounded panels with soft shadows and gradients, pixel icons in every navigation, animated toggle switches, sliders with value bubbles, smooth screen/page transitions, eight theme accents plus a custom color, adjustable background dim and animation speed.
> - **New tooltips:** delayed, fading info cards with title, text and shortcut hint that always render above every other element. Focus tooltips only appear for keyboard users.
> - **Interface sounds:** subtle click, toggle, tab, slider and notification sounds with their own volume.
> - **Settings rework:** new *Interface* tab, unsaved-changes indicator with discard confirmation, *Reset page*, Ctrl+S to save.
> - **Movement Tools 2.0:** 24 tools (10 new: Bunny Hop, Long Jump, Sneak Speed, No Slow, Parkour, No Jump Delay, Jetpack, Low Gravity, Elytra Boost, Auto Surface), card-based settings, five one-click profiles, a master switch, optional toggle hotkeys for every tool, “off on servers” safety option and a movement HUD with three styles and value display.
> - **Live Map (F10) rebuilt:** texture-based rendering with real vanilla map colors and relief shading, smooth zoom to the cursor, drag/keyboard panning that stays inside the known area, layers & filters dropdowns, info sidebar (coverage, cursor, chunk inspector), navigation arrow with view cone, entity markers (hostile, passive, items, players, other), **Explore mode** (maps every loaded chunk without capturing) and **PNG / region-tile export** – with far less CPU work per frame.
> - **Chunk radar rebuilt:** the HUD radar uses the same terrain textures, follows you smoothly, can rotate with your view, shows entities, coordinates and facing, and can stay visible as a minimap without a capture.
> - **Widget Editor 2.0:** snapping with guide lines, anchor presets, keyboard nudging, undo/redo, preview mode, color tools and per-widget extras.
> - **New target version picker** grouped by official updates (Update Aquatic … Chase the Skies, Game Drops 2026) with colors, emblems, years and a detail card that explains what the export changes.
> - **New F6 capture setup** with live folder preview, quick setup cards (target, preset, radius, entities) and a compact legal notice.
>
> One Fabric jar runs on Minecraft 26.1 through 26.3; capture, cache, recovery and export keep their established workflow.

The reload setting offers Interactive and Vanilla modes. Interactive keeps a ready menu or world visible and accepts input while resources reload, without an extra WorldBinder progress display. Vanilla keeps Minecraft's blocking loading screen. Early startup and failed reloads retain Minecraft's loading/error handling. The resource reload itself is unchanged.

## Supported loaders

| Loader | Declared Minecraft support | 1.7.0 runtime verification | Requirements |
| --- | --- | --- | --- |
| Fabric | 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Packaged 1.7.0 artifact checks; full hash/version matrix in the evidence | Loader 0.19.5+, matching Fabric API, Java 25 |
| NeoForge | 26.3 | 26.3 with NeoForge 26.3.0.95 | NeoForge 26.3.0.95+, Java 25 |

Install **one** loader-specific jar. Fabric requires the Fabric API matching Minecraft (for example 0.145.1+26.1 or 0.160.7+26.3); Mod Menu is optional. NeoForge does not require Fabric API or Mod Menu. Forge and Quilt are not declared supported. All five listed Fabric versions loaded the same final 1.7.0 jar; the validation record describes the tested flows and remaining limits.

Loader support is separate from support for additional mod content. Unknown mod registries, plugin data, server-only logic and pack-dependent content still require target-game review; NeoForge support does not turn a vanilla export into a full modpack backup.

The dedicated-server lifecycle uses a real unmodded matching-version vanilla server on loopback. The current [verification matrix](docs/1.7.0-validation.md) lists the separately tested ViaFabric, ViaFabricPlus, ViaVersion and proxy combinations. The source includes the reproducible test drivers; the verification matrix distinguishes previously completed native tests from a new source build.

## Main features

- HotCache-first client-side chunk capture
- Persistent, world-bound projects with deliberate chunk updates and retained revisions
- Authoritative container inventories, received map images and optional own player state
- Original section biomes and light, with explicit missing-data reporting
- Version-impact review, per-state replacement blocks and Void/Flat/Normal generation
- Durable chunk block cache for large captures
- Vanilla world folder export
- Faster export with reusable prepared recordings, compact archives and chunk-by-chunk read-back validation
- Export dashboard with recording counts, work phases, timing and report access, including after disconnect
- Client-only `/worldbinder` shortcuts for settings, map, capture, archives, movement, tools, profiler, status and help
- Target output version selection
- Block, block entity and entity export where available
- Server resource pack export with pinned archive provenance (`resourcepacks/resources.zip` on 26.x)
- Recovery and autosave tools
- Recovery cache reuse after crashes
- Automatic temporary cache cleanup after successful exports
- Configurable performance presets
- F9 Control Center
- F10 live map with texture-based terrain, vanilla map colors, relief shading, smooth zoom/pan, layers, filters, info sidebar and chunk inspector
- F12 Movement Tools shortcut, optional toggle hotkeys for every movement tool and a master toggle key
- Ctrl+C chunk center coordinate copying
- Queue Dashboard / Profiler for capture and export telemetry
- Written-region/NBT read-back validation with separate file validity and recording completeness
- HUD Widget Editor with snapping guides, 3×3 anchor presets, keyboard nudging, undo/redo, preview mode, color tools and widget specific options (radar range/zoom/rotation, movement HUD style, toast duration)
- Visual color picker with HEX/ARGB input, opacity control and per-widget quick colors
- Refined WorldBinder toast notifications with multi-line text and proportional scaling
- Central Chat / Toast / Chat & Toast / None notification selection
- 24 optional Movement Tools with individual toggles, bounded strength sliders, profiles and a master switch
- Themeable interface: 8 accent colors + custom color, background dim, animation speed, icons, screen transitions
- Animated tooltips with title, description and shortcut hints
- Configurable interface and notification sounds
- Unsaved-changes indicator, page reset and Ctrl+S in Settings
- Keyboard navigation, clear focus rings, high-contrast and reduced-motion options
- Resource-reload choice: Interactive or Vanilla
- WorldBinder icon buttons in the Minecraft main menu and pause menu
- Fixed virtual GUI sizing across different window sizes and GUI scales
- Dedicated WorldBinder keybind category in Minecraft Controls
- English, German and French UI, status and report text through a shared, audited language catalog; generated export help is bilingual German/English ([translation guidelines](CONTRIBUTING.md#translations))
- Unopened and partly known containers highlighted directly in the world, with slot labels and an HUD counter
- Optional `players.csv` player list and `dimensions.txt` dimension tree in exports
- Capture settings tab and version-aware game rules in seven categories with per-rule tooltips and reset ([guide](docs/1.7.0-containers-lists-settings.md))

## Movement Tools

Open **Settings > Movement Tools** (or press **F12**). Tools are grouped into **Ground**, **Flight & climbing**, **Water** and **Air & jumping**; every tool is disabled by default and saved independently. Click a tool card to toggle it, use its slider for the strength, or apply one of the profiles (**Explorer, Builder, Parkour, Aquatic, Sky**). The **Options** page holds the master switch, toggle feedback, the *Off on servers* safety option, the movement HUD style and a shortcut to the key bindings.

| Tool | Category | Behavior / range |
| --- | --- | --- |
| Speed | Ground | Movement speed, 1.0-5.0x |
| Step | Ground | Step height, 1.0-3.0 blocks |
| High Jump | Ground | Jump strength, 1.0-3.0x |
| Auto Sprint | Ground | Sprint when moving forward and vanilla conditions allow it |
| Safe Walk | Ground | Stop at block edges while grounded |
| Auto Jump | Ground | Jump against small obstacles when Step is disabled |
| **Bunny Hop** (new) | Ground | Automatic hops while moving forward, keeps 1.0-3.0x extra speed per hop |
| **Sneak Speed** (new) | Ground | Faster sneaking, 1.0-3.0x |
| **No Slow** (new) | Ground | Full speed on soul sand, honey and other slowing blocks |
| **Parkour** (new) | Ground | Jumps automatically at the last moment at a block edge |
| Fly | Flight & climbing | Double Jump toggles collision-aware free flight; separate 0.5-5.0x speed, Sprint boost |
| Spider | Flight & climbing | Climb walls while moving forward against them |
| No Fall | Flight & climbing | Prevent fall damage in local worlds |
| Glide | Flight & climbing | Reduced falling speed, strength 1.0-5.0 |
| Fast Ladder | Flight & climbing | Faster ascent/descent, 1.0-5.0x |
| **Jetpack** (new) | Flight & climbing | Hold Jump in the air for smooth upward thrust, 1.0-5.0x |
| Jesus | Water | Walk on water surfaces; sneak to enter the water |
| Water Speed | Water | Additional underwater acceleration, 1.0-5.0x |
| **Auto Surface** (new) | Water | Float back up automatically; Sneak or sprint-swim to dive |
| Air Control | Air & jumping | Additional airborne steering, 1.0-5.0x |
| **Long Jump** (new) | Air & jumping | Longer sprint jumps, 1.0-4.0x (speed capped) |
| **No Jump Delay** (new) | Air & jumping | Removes the cooldown between held jumps |
| **Low Gravity** (new) | Air & jumping | Gravity from 100% down to 10% |
| **Elytra Boost** (new) | Air & jumping | Hold Forward while gliding to accelerate, 1.0-5.0x |

Every tool also has an optional **toggle key** (unbound by default) and there is a master key that pauses or resumes all tools at once. Set them in Minecraft **Controls** under the `WorldBinder` category; a short banner and sound confirm each toggle.

Enable Fly in Settings, then double-tap Jump to enter or leave flight. Hold Jump to rise, Sneak to descend and Sprint for faster horizontal flight. Landing ends flight. Fly takes priority over other movement helpers without granting or changing creative abilities; native creative flight stays untouched. Spider requires actual wall contact and suppresses ground jumps; Step suppresses Auto Jump, which requires a real obstacle and clearance above it. Glide only limits descent; Air Control only changes steering. Jesus uses per-query collision shapes at the actual source/flowing water height, releases on Sneak and leaves underwater movement to vanilla/Water Speed. Input-driven helpers ignore open menus. These tools are separate from capture processing. On remote servers, server rules and movement validation remain authoritative; No Fall is not a remote-server damage bypass. Only enable tools where explicitly allowed.

The same jar runs on Minecraft 26.1-26.3. Minecraft 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 are explicit export targets with their own DataVersions, alongside older export targets. Available gamerules follow the selected export target; 26.x exports translate legacy gamerule names to the modern registry keys.

## Default keybinds

| Key | Action |
| --- | --- |
| F6 | Start/stop world download |
| F7 | Set position 1 |
| F8 | Set position 2 |
| F9 | Open WorldBinder Control Center |
| F10 | Open WorldBinder Map |
| F12 | Open Movement Tools directly |
| (unbound) | Toggle all movement tools / toggle each single tool |

All keybinds can be changed in Minecraft Controls under the `WorldBinder` category.

## Live Map (F10)

The live map renders captured chunks as cached region textures (16×16 chunks each) that are only repainted when a chunk changes, so even large captures stay smooth. Terrain uses vanilla map colors and optional relief shading; a second layer shows queued, scanning, partial and failed chunks.

| Input | Action |
| --- | --- |
| Drag / W A S D / arrow keys | Pan |
| Mouse wheel / + / - | Zoom (towards the cursor) |
| Click | Select a chunk (sidebar inspector) |
| Double-click | Center on a chunk |
| Right-click | Queue a chunk for rescan (on a waypoint: remove it) |
| P / Shift+right-click / middle click | Place a waypoint |
| V | Toggle the 3D view |
| Right-drag (3D) | Rotate and tilt |
| , / . and Page Up / Page Down (3D) | Rotate / tilt |
| Space / F | Center on player / toggle follow |
| G / H | Toggle grid / sidebar |
| E | Toggle explore mode |
| Ctrl+C | Copy the selected chunk center |

The toolbar offers Follow, Player, Origin, zoom, **Layers** (view mode, relief, grid, status tint, players, hostile mobs, animals, items, other entities, stay-in-map-area), **Filters** (status: not captured, saved, in progress, failed – content: entities, block entities, low quality, recently scanned, explored only – plus Invert and Hide others), **Explore** (E), **Export** (full PNG or 256×256 region tiles named `r.<x>.<z>.png`, optional status tint, written to `saves/WorldBinder/map_exports/`) and a coordinate jump field (`X Z` or `X Y Z`). The sidebar shows coverage statistics with saved area and explored chunks, cursor coordinates/height/status or nearby entity counts, and the selected chunk with quality bar and Rescan/Copy/Center actions. Hover an entity marker to see its type, position and distance.

**3D view** (V) draws the map as an orthographic 3D landscape that you can rotate, tilt (20–89°) and exaggerate in height (50–300 %); switching between 2D and 3D is animated. Chunks loaded by your client are sampled with up to six solid block spans per column, so bridges, tracks, tree crowns and overhangs keep the air beneath them; other known chunks are drawn from the height map. Faces are lit by orientation, spans cast sun shadows, and frames are rendered on a background thread (half resolution while moving, full resolution at rest). The player marker lies on the ground at your feet and is shown as an outline when terrain covers it; entities and waypoints are drawn in place. The buttons at the bottom right rotate, tilt, change the height scale and reset the view. The Export menu adds **3D model (GLB/OBJ)** – the known surface as a colored block mesh, merged into larger cells for huge areas – and **3D render (PNG)**, a 2×2 supersampled high-resolution image of the current 3D view, saved with a transparent and with a sky background. Every map export writes a `map_info.txt` with details and the legal notice.

**Map colors** (Layers > Colors) can be *Auto* (vanilla map colors; blocks changed by a resource pack, such as custom server blocks made from note block states, use their texture color), *Vanilla* or *Textures* (every block uses its texture color). Colors are read from the blockstate, model and texture files of the active resource packs, including server packs.

**Waypoints** are stored per world/server and dimension. Place one with P, Shift+right click or middle click; right click a waypoint to remove it. The last death point is added automatically and can be turned off in the config (`mapDeathWaypoint`).

**Both 2D and 3D** sample received chunks while the map is open, even without capture or Explore. They read the client chunk cache independently of visible/rendered sections. **Explore mode** continues that sampling with the map closed (colors/heights only; no world recording). Changing views keeps map data. Received block updates invalidate the corresponding surface/voxel samples. Session exploration resets on disconnect and dimension change.

## Chunk radar

The HUD radar shares the live map's textures. It follows the player with sub-pixel precision, shows capture status, the roaming capture radius, entities and a navigation arrow, and can optionally rotate with your view, show coordinates and facing, and stay visible as a minimap without a capture. Range, zoom and all options are in the Widget Editor (Extras page).

## Export output

WorldBinder exports into a normal Minecraft save folder. A typical export may contain:

```text
level.dat
level.dat_old
session.lock
icon.png
resourcepacks/resources.zip   # 26.x; resources.zip on older targets
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
- `dimensions/minecraft/<dimension>/` for modern 26.x world loading (the recorded dimension is preserved)
- one optional `dimensions/minecraft/<world-key>/` mirror for server world keys when a clean world name is available
- modern saved-data files under `data/minecraft/`
- a single export `README.md` instead of multiple loose note files
- `data/minecraft/maps/<id>.dat` for received map images; older targets use `data/map_<id>.dat`

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

The export review lists known block/property/biome/entity/item-data changes with counts. Select a known block/property row to set a replacement such as `minecraft:stone`; the same policy drives preview and export. Uncatalogued changes are explicitly unknown. Original archive data stays available for another target.

Downgrades may still be imperfect when captured data contains blocks, entities, items, resource pack content or world features that did not exist in the selected target version. Automated read-back is supplemented by actual target-game checks; see [their scope and remaining checks](docs/1.7.0-validation.md).

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
- Loader/version (and Fabric API version on Fabric)
- Selected target output version
- Window mode and GUI scale if the issue is UI-related
- What was captured
- What went wrong
- Whether the export was opened in singleplayer, Paper or Multiverse
- Screenshots, logs or exported test worlds if possible

## Community

- [Code of Conduct](CODE_OF_CONDUCT.md) – how we treat each other in all WorldBinder spaces
- [Contributing](CONTRIBUTING.md) – bug reports, translations, development setup and pull requests
- [Security policy](SECURITY.md) – how to report vulnerabilities privately
- [Accessibility](ACCESSIBILITY.md) – our commitment, supported features, known limitations and how to report barriers

## Build from source

Build with JDK 25. Common code lives in `src/main/`; `src/fabric/` and `neoforge/src/main/` contain the loader adapters. Both build against Minecraft 26.3. Fabric compatibility checks load the packaged jar on earlier releases.

```bash
./gradlew build regressionTest
# Individual artifacts: ./gradlew buildFabric   /   ./gradlew buildNeoForge
```

Separate loader artifacts: `build/libs/worldbinder-1.7.0+mc26.1-26.3-fabric.jar` for Fabric and `neoforge/build/libs/worldbinder-1.7.0+mc26.3-neoforge.jar` for NeoForge. Install the matching loader's JAR. Gradle includes the loader in each filename, including the source archives. Test harnesses are separate source sets and are not included in these jars. See [CONTRIBUTING.md](CONTRIBUTING.md) for actual Minecraft test tasks.

For a clean package build without running tests or verification tasks, use `gradlew :clean :neoforge:clean :assemble :neoforge:assemble --no-parallel`. Development run directories and QA evidence may live below `build/`; preserve needed worlds and evidence before cleaning them.

## Status

WorldBinder 1.7.0 adds persistent projects and a separate NeoForge build. The [changelog](CHANGELOG.md) lists changes since 1.6.3, while [verification results](docs/1.7.0-validation.md) distinguish builds, automated checks, actual Minecraft execution and known limits. The [source layout](docs/source-layout.md) explains the build and test inputs included in this repository.

Real BungeeCord, Waterfall, server-side ViaVersion and client-side ViaFabric/ViaFabricPlus runs pass within the documented version scope. Both loaders were checked with End portal round trips and a custom dimension using a received vanilla type. Minecraft 1.13 cannot retain map locks and may leave changed chunks dark instead of recalculating lighting; the export review reports both limits. Nonstandard Void/Flat generation in recorded Nether/End dimensions can produce Minecraft's experimental-settings warning. See the verification matrix for precise tested artifacts and limits.

The project will continue to improve capture accuracy, target-version compatibility, entity handling, recovery behavior, performance and overall usability based on real feedback.

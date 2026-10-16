# WorldBinder 1.6.0

WorldBinder is a client-side Fabric mod for permitted Minecraft world capture, archive recovery, inspection and target-version vanilla world export.

It is designed for players, builders, server owners, developers and preservation-focused users who want to save world data that their client has already received. Optional Movement Tools support navigation in singleplayer, local test worlds and servers that explicitly permit them. WorldBinder does not bypass server permissions or anti-cheat checks and cannot reveal hidden world data.

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

## Supported loader

WorldBinder uses one **Fabric** jar for Minecraft **26.1, 26.1.1, 26.1.2, 26.2 and 26.3**.

Use Fabric Loader 0.19.5 or newer and the Fabric API build matching your Minecraft version (for example, 0.145.1+26.1 on 26.1 or 0.160.7+26.3 on 26.3). Forge, NeoForge and Quilt are not supported at this time.

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
- F10 live map with texture-based terrain, vanilla map colors, relief shading, smooth zoom/pan, layers, filters, info sidebar and chunk inspector
- F12 Movement Tools shortcut, optional toggle hotkeys for every movement tool and a master toggle key
- Ctrl+C chunk center coordinate copying
- Queue Dashboard / Profiler for capture and export telemetry
- Export validation reports
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
- German and English language support

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
| Right-click | Queue a chunk for rescan |
| Space / F | Center on player / toggle follow |
| G / H | Toggle grid / sidebar |
| E | Toggle explore mode |
| Ctrl+C | Copy the selected chunk center |

The toolbar offers Follow, Player, Origin, zoom, **Layers** (view mode, relief, grid, status tint, players, hostile mobs, animals, items, other entities, stay-in-map-area), **Filters** (status: not captured, saved, in progress, failed – content: entities, block entities, low quality, recently scanned, explored only – plus Invert and Hide others), **Explore** (E), **Export** (full PNG or 256×256 region tiles named `r.<x>.<z>.png`, optional status tint, written to `saves/WorldBinder/map_exports/`) and a coordinate jump field (`X Z` or `X Y Z`). The sidebar shows coverage statistics with saved area and explored chunks, cursor coordinates/height/status or nearby entity counts, and the selected chunk with quality bar and Rescan/Copy/Center actions. Hover an entity marker to see its type, position and distance.

**Explore mode** samples the surface of every chunk your client has loaded (colors and heights only, nothing is captured or written to an archive) so you can build overview maps or exports while simply playing. It resets on disconnect and dimension change.

## Chunk radar

The HUD radar shares the live map's textures. It follows the player with sub-pixel precision, shows capture status, the roaming capture radius, entities and a navigation arrow, and can optionally rotate with your view, show coordinates and facing, and stay visible as a minimap without a capture. Range, zoom and all options are in the Widget Editor (Extras page).

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

WorldBinder requires Java 25, Fabric Loader 0.19.5 or newer and a Fabric API build matching Minecraft 26.1-26.3. The build targets 26.3 while compatibility tests load the same packaged jar on the earlier releases.

```bash
./gradlew clean build
```

The official release artifact is the Fabric jar from `build/libs/`.

## Status

WorldBinder 1.6.0 targets Minecraft 26.1-26.3 with a single Fabric jar. The matching 1.6.0 release texts are in `Publish/`; 1.4.x and 1.5.0 files remain as historical release records.

The project will continue to improve capture accuracy, target-version compatibility, entity handling, recovery behavior, performance and overall usability based on real feedback.

# WorldBinder 1.6.2 — How to Use

This guide explains the normal WorldBinder workflow for permitted world capture, recovery and export.

WorldBinder is a client-side Fabric mod. Use it only on worlds, servers, maps, builds and resource packs that you own, administer, created yourself, or where you have explicit permission to archive or export the content.

## 1. Install WorldBinder

1. Install Minecraft 26.1, 26.1.1, 26.1.2, 26.2 or 26.3 with Fabric Loader 0.19.5 or newer.
2. Install a Fabric API build for that Minecraft version (for example, 0.145.1+26.1 for 26.1 or 0.160.7+26.3 for 26.3).
3. Place the same WorldBinder 1.6.2 jar into your `mods` folder on any of those versions.
4. Start the game.
5. Open Minecraft Controls and check the `WorldBinder` keybind category.

Default keybinds:

| Key | Action |
| --- | --- |
| F6 | Start/stop world download |
| F7 | Set position 1 |
| F8 | Set position 2 |
| F9 | Open WorldBinder Control Center |
| F10 | Open WorldBinder Map |
| F12 | Open Movement Tools directly |
| (unbound) | Toggle all movement tools / toggle a single movement tool |

## 2. Open the Control Center

Press **F9** to open the WorldBinder Control Center.

You can also select the WorldBinder icon button in Minecraft's main menu or pause menu to open WorldBinder directly.

The Control Center provides access to:

- Capture controls
- Archive status
- Recent activity
- Validation results
- Recovery tools
- Settings
- F10 map
- Queue Dashboard / Profiler

WorldBinder 1.6.2 keeps the fixed virtual GUI canvas, so menus remain at a stable size across fullscreen, windowed mode and GUI scales. F9 uses the visible sidebar for navigation. To choose an export target, select **Minecraft version** in Capture, General settings or the F6 dialog. The picker groups every supported release by its official update (Update Aquatic, Village & Pillage, … Chase the Skies, Game Drops 2026) with color, emblem and release year. Hover a version to see its DataVersion, generation profile, which file formats it uses and whether the export is native or a downgrade; click it to use it. Search by version, update name or year. The Gamerules page only shows rules available for the selected target. The HUD Widget Editor continues to provide live placement, size and color controls.

Use **Tab** and **Shift+Tab** to move focus between controls. Focused buttons, fields and sliders have a visible glow; **Enter** or **Space** activates a focused button. **Ctrl+Tab** and **Ctrl+Shift+Tab** switch F9 sections or Settings tabs, **Ctrl+S** saves Settings without closing them. Hover any control to see a tooltip with a short explanation and, where available, its shortcut.

The Settings footer always shows whether you have **unsaved changes**. Cancel/Esc with unsaved changes asks once more before discarding them, and **Reset page** (two clicks) restores the defaults of the current page.

### Interface settings

Open **Settings > Interface** (or the palette button in the F9 footer):

- **Theme:** choose one of eight accent colors (Aqua, Violet, Emerald, Amber, Rose, Crimson, Mono) or pick a **custom accent color**. The preview updates instantly; Save to keep it.
- **Background & motion:** background dim (20–100%), animation speed (0.5–2.0x), screen transitions, Reduce motion, High contrast and Show icons.
- **Sounds:** interface sounds, optional hover ticks, notification sounds and a volume slider (0–200%) with a test button.
- **Tooltips:** turn tooltips on or off, show shortcut hints and set the delay (0–1500 ms).

## Messages and Movement Tools

Open **Settings > HUD & Map**, scroll to notifications and select **Chat**, **Toast**, **Chat & Toast** or **None**, then **Save**. This controls WorldBinder status, success, warning and error messages centrally. New installations default to Toast; existing notification preferences are migrated. None hides even warning/error popups, but does not disable log files. Turning notifications off in the Widget Editor removes only the toast channel and keeps a selected chat channel.

Press **F12** or open **Settings > Movement Tools**. The 24 tools are grouped into **Ground**, **Flight & climbing**, **Water** and **Air & jumping**; each tab shows how many of its tools are enabled. All tools default to OFF. Click a tool card to toggle it, adjust its slider where present and select **Save**; Back discards unapplied edits.

The **Quick controls** at the top of every category contain the **master switch** (pauses all tools without losing their settings), **Reset values**, **All off** and five **profiles**: Explorer, Builder, Parkour, Aquatic and Sky. The **Options** tab adds toggle feedback, **Off on servers** (automatically pauses all tools in multiplayer and LAN), the movement HUD (show/hide, values, style: Detailed, Compact or Icons only) and a button that opens the key bindings. Every tool has an optional toggle key plus a master key; they are unbound by default and live in the `WorldBinder` category of Minecraft Controls.

- **Speed:** 1.0-5.0x movement speed.
- **Fly:** enable the tool, then double-tap Jump (Space by default) to start or stop flying. Jump rises, Sneak descends and Sprint boosts horizontal flight speed. Landing ends flight. Separate 0.5-5.0x speed; collisions remain active. Native creative flight is unaffected.
- **Spider:** move forward against a wall to climb; Sneak stops the assistance.
- **Jesus:** walk on source and flowing water at their actual surface height; Sneak to enter the water. Underwater movement is not forced upward. Water Speed applies while swimming, not while standing on the surface.
- **No Fall:** prevent fall damage in local worlds, not on remote servers.
- **Step:** step up 1.0-3.0 blocks where there is headroom.
- **High Jump:** 1.0-3.0x jump strength, not a fixed jump height in blocks.
- **Glide:** reduce falling speed; strength 1.0-5.0.
- **Fast Ladder:** 1.0-5.0x climb speed; forward/Jump ascends, release to descend, Sneak holds position.
- **Auto Sprint:** sprint while moving forward when hunger and other vanilla conditions allow it.
- **Safe Walk:** prevent grounded movement beyond unsupported block edges.
- **Auto Jump:** jump against actual small obstacles with enough headroom; automatically suppressed while Step or active wall climbing takes priority.
- **Water Speed:** 1.0-5.0x underwater acceleration setting.
- **Air Control:** 1.0-5.0x airborne steering setting.
- **Bunny Hop (new):** hops automatically while moving forward on the ground and keeps 1.0-3.0x extra speed per hop.
- **Long Jump (new):** sprint jumps travel 1.0-4.0x further; horizontal speed is capped.
- **Sneak Speed (new):** 1.0-3.0x sneaking speed; edge protection of sneaking stays.
- **No Slow (new):** full speed on soul sand, honey and other slowing blocks.
- **Parkour (new):** jumps at the last moment when you run off a block edge.
- **No Jump Delay (new):** removes the cooldown between jumps while Jump is held.
- **Jetpack (new):** hold Jump in the air for smooth upward thrust, 1.0-5.0x.
- **Low Gravity (new):** gravity from 100% down to 10%.
- **Elytra Boost (new):** hold Forward while gliding with an elytra to accelerate in your look direction, 1.0-5.0x.
- **Auto Surface (new):** floats you back up in water; Sneak or sprint-swim to dive.

Fly takes priority over the other helpers. Glide does not override ladder climbing or vanilla elytra flight. Movement settings do not change capture/cache/export processing. Use these tools only in singleplayer, local test worlds or on servers that expressly allow them. Server validation remains authoritative and may correct or reject movement; these tools do not bypass anti-cheat or server permissions.

## 3. Choose capture settings

Open **Settings** from the Control Center and review:

- target output version
- performance preset
- capture radius
- autosave behavior
- recovery behavior
- resource pack export
- gamerule export
- HUD and map options
- message delivery: Chat, Toast, Chat & Toast or None
- custom toast duration (40–250%)
- the HUD Widget Editor for status, radar and notification widgets

Open **Settings > HUD & Map > HUD Widget Editor** to customize status, radar, notification and Movement Tool overlays. Widgets snap to the screen edges, the center and each other (hold Shift to place freely); the Layout page offers a 3×3 anchor grid, the Style page colors and opacity, the Extras page widget specific options. Arrow keys nudge the selected widget (Shift = 10 px), Ctrl+Tab selects the next widget, Delete shows/hides it, Ctrl+Z/Ctrl+Y undo and redo, and **Preview** (H) hides the editor to see the HUD as in game. Drag the settings panel by its header to clear the part of the canvas you are editing, or collapse it. Select a widget from the panel or click it directly in the preview. Drag a widget to position it, and use the bottom-right handle or size slider to scale its complete frame, text and contents proportionally. Active Movement Tools appear as small, stacked badges; the Movement widget controls their position, scale, colors and visibility. Click either color row to open the visual color picker, choose a color or enter HEX/ARGB directly, then select **Apply** to return the changes to Settings and **Save** to write the configuration.

Recommended presets:

| Preset | Recommended use |
| --- | --- |
| Safe | Large servers, low-end systems or cautious capture |
| Balanced | Normal use |
| Fast | Stronger systems and smaller captures |
| Extreme | Testing only; may increase client/server load |

## 4. Start a capture

You can start a world download from the Capture screen or with **F6**.
The F6 capture setup shows the archive name with a live preview of the resulting folder, buttons to use the world/server name or reset the name, and four quick setup cards: **target version** (opens the picker), **preset** (click to cycle), **radius** (click to cycle 2–12 chunks) and **entities** (on/off). Press **Enter** to start. Select **Full legal notice** to open the detailed notice in F9; closing F9 returns to F6 with the entered archive name intact.

WorldBinder will collect chunks, block states, block entities, visible entities, chunk coverage and available metadata while you move through the world. The client can only save what the server actually sends to it.

Use **F10** to open the live map. Captured chunks are drawn with real vanilla map colors and optional relief shading; queued, scanning, partial and failed chunks get a colored status tint.

- **Drag**, **W A S D** or the arrow keys pan the map; the **mouse wheel** or **+ / -** zoom towards the cursor.
- **Click** selects a chunk and shows it in the sidebar inspector (status, blocks, entities, block entities, last scan, quality) with **Rescan**, **Copy** and **Center** actions. **Double-click** centers a chunk, **right-click** queues it for rescan and **Ctrl+C** copies its center coordinates.
- **Space** centers on you, **F** toggles following, **G** toggles the grid, **H** the sidebar.
- **Layers** switches between terrain + status, status only and terrain only and toggles relief shading, grid, status tint and players. **Filters** dim everything except missing, incomplete, entity or block-entity chunks.
- Type coordinates as `X Z` or `X Y Z` into the field in the toolbar and press Enter to jump there.

- **Explore** (or **E**) adds every chunk your client has loaded to the map without capturing; **Export** writes the map as one PNG or as 256×256 region tiles to `saves/WorldBinder/map_exports/`.
- Layers can show hostile mobs, animals, dropped items, other entities and players as markers; hover a marker for details.
- With **Stay in map area** enabled you cannot pan far away from known chunks.
- **3D view:** press **V** or the **3D** button – the map tilts smoothly into 3D (and back). Bridges, roller coasters, trees and overhangs of loaded chunks keep the air beneath them; your marker lies on the ground at your position and turns into an outline when something covers it. Drag with the right mouse button to rotate and tilt, drag with the left button to move, use the wheel to zoom. The buttons at the bottom right rotate by 45°, tilt, cycle the height scale (50–300 %) and reset the view; `,` / `.` and Page Up / Page Down work on the keyboard.
- **Export > 3D model (GLB/OBJ)** saves the known surface as a colored 3D model for Blender, the Windows 3D Viewer or web viewers; **Export > 3D render (PNG)** saves the current 3D view in high resolution, anti-aliased, with a transparent and a sky background. Every export folder contains a `map_info.txt` with the legal notice.
- **Layers > Colors** chooses map colors: *Auto* shows custom server blocks (for example note block based blocks from a server resource pack) in their real texture color, *Vanilla* uses only the vanilla map palette, *Textures* uses texture colors for every block. Already captured chunks keep the colors they were recorded with; explored chunks are redrawn.
- **Waypoints:** press **P**, Shift+right click or middle click to place a waypoint at the cursor; right click a waypoint to remove it. Your last death point appears automatically. Waypoints are saved per world/server and dimension and can be hidden in Layers.

The map keeps a texture cache of the captured area, so reopening it is instant and large captures stay smooth. The HUD chunk radar uses the same textures; range, zoom, rotation, entities, coordinates and *Show without capture* are set in the Widget Editor. The radar map uses the full widget width; below it, the info strip shows the current chunk and how much of the radar area is captured. Coordinates are off by default and can be enabled on the Extras page.

## 5. Finish and export

When you are done capturing:

1. Open the Control Center with **F9**.
2. Open the Capture screen.
3. Choose finish/export.
4. Let WorldBinder process the remaining queue.
5. Wait until the export is complete.

The exported folder is written as a local Minecraft save. It may include:

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

The export `README.md` explains what was captured and how to review the result.

## 6. Open or import an export

For local testing, place the exported folder in your Minecraft `saves` folder and open it from singleplayer.

For Paper or Multiverse testing, copy the exported world folder to your server world directory and import it with your server tools. WorldBinder writes a compatibility layout with root-level region folders and modern 26.x dimension folders to make server imports easier.

Always check the imported world before using it publicly.

## 7. Use recovery

WorldBinder can save recovery data while capturing.

If Minecraft closes, the server disconnects you or you leave before finishing an export:

1. Reopen Minecraft.
2. Join a world if needed.
3. Open WorldBinder with **F9**.
4. Go to Archives/Recovery.
5. Continue or finalize the recovery entry.

Recovery can only preserve data that had already been captured or cached.

WorldBinder stores temporary chunk cache data in:

```text
saves/WorldBinder/.cache/
```

Successful exports clean up their temporary cache automatically. If Minecraft crashes or an export fails, cache data may remain so recovery has the best possible chance of preserving the capture.

## 8. Validate exports

WorldBinder writes validation information into the `worldbinder/` folder of an export.

Review validation if:

- chunks are missing
- entities did not export
- the spawn is wrong
- the export appears empty
- the server import prints warnings
- the target output version is older than the captured client version

A warning does not always mean the export is unusable. It means the result should be checked before sharing or importing it permanently.

## 9. Resource packs

In Settings > Safety > Resource Pack, **Reload display** offers Interactive and Vanilla. Interactive is the default: a ready menu or world stays visible and usable while resources reload, with no separate WorldBinder percentage indicator. Vanilla keeps Minecraft's original blocking loading screen. Old Compact choices and unmarked legacy Vanilla values migrate to Interactive; explicitly choose Vanilla again if you prefer it. During early startup without a ready screen and on reload errors, Minecraft's loading/error handling remains in place. No mode changes the resource reload itself.

If the server sent a resource pack and WorldBinder could access it, the pack is exported as:

```text
resources.zip
```

Minecraft can use this file as the world resource pack when opening the save locally. Server-specific resource packs, protected downloads or packs that were never fully received by the client may not be available.

## 10. Responsible use

WorldBinder is a preservation and export tool. It does not give permission to copy, redistribute or publish other people's work.

Use it only when you own the content, administer the server, created the build yourself, or have explicit permission.

## 11. Reporting bugs

See also [CONTRIBUTING.md](CONTRIBUTING.md) for good bug reports, [SECURITY.md](SECURITY.md) for security problems (never as public issues) and [ACCESSIBILITY.md](ACCESSIBILITY.md) for accessibility barriers.

Please report issues here:

- Repository: https://github.com/Philiipp06/WorldBinder
- Issues: https://github.com/Philiipp06/WorldBinder/issues

Useful reports include:

- Minecraft version
- WorldBinder version
- Fabric Loader and Fabric API version
- which Minecraft-matched Fabric API build is installed
- selected target output version
- capture mode and performance preset
- whether the issue happened in singleplayer, Paper or Multiverse
- screenshots, logs and the export validation report if possible

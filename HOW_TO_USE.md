# WorldBinder 1.5.0 — How to Use

This guide explains the normal WorldBinder workflow for permitted world capture, recovery and export.

WorldBinder is a client-side Fabric mod. Use it only on worlds, servers, maps, builds and resource packs that you own, administer, created yourself, or where you have explicit permission to archive or export the content.

## 1. Install WorldBinder

1. Install Minecraft 26.1, 26.1.1, 26.1.2, 26.2 or 26.3 with Fabric Loader 0.19.5 or newer.
2. Install a Fabric API build for that Minecraft version (for example, 0.145.1+26.1 for 26.1 or 0.160.7+26.3 for 26.3).
3. Place the same WorldBinder 1.5.0 jar into your `mods` folder on any of those versions.
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

WorldBinder 1.5.0 keeps the fixed virtual GUI canvas, so menus remain at a stable size across fullscreen, windowed mode and GUI scales. F9 uses the visible sidebar for navigation. To choose an export target, select **Minecraft version** in Capture or General settings, search the supported releases (including 26.1 through 26.3) and choose one. Recent versions appear first. The Gamerules page only shows rules available for the selected target. The HUD Widget Editor continues to provide live placement, size and color controls.

Use **Tab** and **Shift+Tab** to move focus between controls. Focused buttons, fields and sliders have a visible outline; **Enter** or **Space** activates a focused button. **Ctrl+Tab** and **Ctrl+Shift+Tab** switch F9 sections or Settings tabs. In **Settings > Display & Input**, enable **High contrast** or **Reduce motion** independently; both are off by default. Save to keep your choices.

## Messages and Movement Tools

Open **Settings > HUD & Map**, scroll to notifications and select **Chat**, **Toast**, **Chat & Toast** or **None**, then **Save**. This controls WorldBinder status, success, warning and error messages centrally. New installations default to Toast; existing notification preferences are migrated. None hides even warning/error popups, but does not disable log files. Turning notifications off in the Widget Editor removes only the toast channel and keeps a selected chat channel.

Press **F12** or open **Settings > Movement Tools** and scroll through the independently configurable tools. All default to OFF. Toggle a tool, adjust its slider where present and select **Save**; Back discards unapplied edits. F12 can be rebound in Minecraft Controls.

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

Open **Settings > HUD & Map > HUD Widget Editor** to customize status, radar, notification and Movement Tool overlays. Drag the settings panel by its header to clear the part of the canvas you are editing, or collapse it. Select a widget from the panel or click it directly in the preview. Drag a widget to position it, and use the bottom-right handle or size slider to scale its complete frame, text and contents proportionally. Active Movement Tools appear as small, stacked badges; the Movement widget controls their position, scale, colors and visibility. Click either color row to open the visual color picker, choose a color or enter HEX/ARGB directly, then select **Apply** to return the changes to Settings and **Save** to write the configuration.

Recommended presets:

| Preset | Recommended use |
| --- | --- |
| Safe | Large servers, low-end systems or cautious capture |
| Balanced | Normal use |
| Fast | Stronger systems and smaller captures |
| Extreme | Testing only; may increase client/server load |

## 4. Start a capture

You can start a world download from the Capture screen or with **F6**.
The F6 quick-start dialog shows the short usage notice. Select **Full legal notice** to open the detailed notice in F9; closing F9 returns to F6 with the entered archive name intact.

WorldBinder will collect chunks, block states, block entities, visible entities, chunk coverage and available metadata while you move through the world. The client can only save what the server actually sends to it.

Use **F10** to open the live map. Captured, partial, queued and errored chunks are displayed with different colors.

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

# WorldBinder 1.4.0 — How to Use

This guide explains the normal WorldBinder workflow for permitted world capture, recovery and export.

WorldBinder is a client-side Fabric mod. Use it only on worlds, servers, maps, builds and resource packs that you own, administer, created yourself, or where you have explicit permission to archive or export the content.

## 1. Install WorldBinder

1. Install Minecraft 26.3 with Fabric Loader 0.19.5 or newer.
2. Install Fabric API 0.160.7+26.3 or newer.
3. Place the WorldBinder jar into your `mods` folder.
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

WorldBinder 1.4.0 retains the responsive virtual GUI canvas, settings, dropdowns and menus introduced in the previous release while its internals are reorganized into smaller, maintainable components. Its visual HUD Widget Editor keeps overlay placement stable across fullscreen, windowed mode and different GUI scales.

## Messages and Movement Tools

Open **Settings > HUD & Map**, scroll to notifications and select **Chat**, **Toast**, **Chat & Toast** or **None**, then **Save**. This controls WorldBinder status, success, warning and error messages centrally. New installations default to Toast; existing notification preferences are migrated. None hides even warning/error popups, but does not disable log files. Turning notifications off in the Widget Editor removes only the toast channel and keeps a selected chat channel.

Open **Settings > Movement Tools** and scroll through the independently configurable tools. All default to OFF. Toggle a tool, adjust its slider where present and select **Save**; Back discards unapplied edits.

- **Speed:** 1.0-5.0x movement speed.
- **Fly:** use movement keys plus Jump to rise and Sneak to descend; separate 0.5-5.0x flight speed. Collisions remain active.
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

Open **Settings > HUD & Map > HUD Widget Editor** to customize overlays. Select a widget from the compact panel or click it directly in the preview. Collapse the panel whenever you need the complete canvas, drag a widget to position it, and use the bottom-right handle or size slider to scale its complete frame, text and contents proportionally. Click either color row to open the visual color picker, choose a color or enter HEX/ARGB directly, then select **Apply** to return the changes to Settings and **Save** to write the configuration.

Recommended presets:

| Preset | Recommended use |
| --- | --- |
| Safe | Large servers, low-end systems or cautious capture |
| Balanced | Normal use |
| Fast | Stronger systems and smaller captures |
| Extreme | Testing only; may increase client/server load |

## 4. Start a capture

You can start a world download from the Capture screen or with **F6**.

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
- whether Fabric API 0.160.7+26.3 or newer is installed
- selected target output version
- capture mode and performance preset
- whether the issue happened in singleplayer, Paper or Multiverse
- screenshots, logs and the export validation report if possible

# WorldBinder 1.2.0 — How to Use

This guide explains the normal WorldBinder workflow for permitted world capture, recovery and export.

WorldBinder is a client-side Fabric mod. Use it only on worlds, servers, maps, builds and resource packs that you own, administer, created yourself, or where you have explicit permission to archive or export the content.

## 1. Install WorldBinder

1. Install Minecraft 26.2 with Fabric Loader 0.19.2 or newer.
2. Install Fabric API 0.152.0+26.2 or newer.
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

The Control Center provides access to:

- Capture controls
- Archive status
- Recent activity
- Validation results
- Recovery tools
- Settings
- F10 map
- Queue Dashboard / Profiler

WorldBinder 1.2.0 uses a responsive virtual GUI canvas with modernized menu navigation, dropdowns and polished HUD notifications. Menus keep the same layout across fullscreen, windowed mode and different GUI scales.

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
- whether Fabric API 0.152.0+26.2 or newer is installed
- selected target output version
- capture mode and performance preset
- whether the issue happened in singleplayer, Paper or Multiverse
- screenshots, logs and the export validation report if possible

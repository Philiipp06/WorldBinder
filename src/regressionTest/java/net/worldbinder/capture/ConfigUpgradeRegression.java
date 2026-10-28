package net.worldbinder.capture;

import com.google.gson.JsonParser;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.movement.MovementTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Old partial configs receive new defaults without rewriting the user's explicit choices. */
public final class ConfigUpgradeRegression {
    private ConfigUpgradeRegression() {}

    /** The caller must already have installed the isolated regression platform and initialized WorldBinder. */
    public static int run(Path regressionRoot) throws Exception {
        Path root = regressionRoot.toAbsolutePath().normalize();
        Path file = WorldBinderPaths.CONFIG_FILE.toAbsolutePath().normalize();
        Path expected = root.resolve("config").resolve(WorldBinder.MOD_ID).resolve("config.json");
        if (!root.getFileName().toString().matches("run-[0-9a-fA-F-]{36}")
                || !WorldBinderPaths.GAME_DIR.toAbsolutePath().normalize().equals(root)
                || !file.equals(expected)) {
            throw new AssertionError("Config upgrade regression may only touch the isolated run-UUID fixture config");
        }

        byte[] previous = Files.exists(file) ? Files.readAllBytes(file) : null;
        int checks = 0;
        try {
            String rules = "doDaylightCycle=true;keepInventory=false;custom_rule=true;randomTickSpeed=7";
            String json = """
                    {
                      "defaultArchiveName": "My existing project",
                      "appendTimestampToArchiveName": false,
                      "performancePreset": "CUSTOM",
                      "blocksPerTick": 24576,
                      "commandsPerTick": 123,
                      "tickBudgetMillis": 11,
                      "roamingRadiusChunks": 7,
                      "mapShowPlayers": false,
                      "uiSoundVolumePercent": 0,
                      "messageMode": "CHAT",
                      "reloadDisplayConfigured": true,
                      "reloadDisplayMode": "VANILLA",
                      "gameRulesOverride": "doDaylightCycle=true;keepInventory=false;custom_rule=true;randomTickSpeed=7",
                      "movement": {"tools": {
                        "SPEED": {"enabled": true, "value": 2.75},
                        "FLY": {"enabled": false, "value": 3.5}
                      }}
                    }
                    """;
            byte[] sparse = json.getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(file.getParent());
            Files.write(file, sparse);

            WorldBinderConfig loaded = WorldBinderConfig.load();
            check(loaded.defaultArchiveName.equals("My existing project") && !loaded.appendTimestampToArchiveName,
                    "Loading a partial old config preserves the project name and explicit false choice"); checks++;
            check(loaded.performancePreset == WorldBinderConfig.PerformancePreset.CUSTOM && loaded.blocksPerTick == 24576
                            && loaded.commandsPerTick == 123 && loaded.tickBudgetMillis == 11 && loaded.roamingRadiusChunks == 7,
                    "Loading retains custom performance settings rather than replacing them with a preset"); checks++;
            check(!loaded.mapShowPlayers && loaded.uiSoundVolumePercent == 0 && loaded.messageMode == WorldBinderConfig.MessageMode.CHAT
                            && loaded.reloadDisplayMode == WorldBinderConfig.ReloadDisplayMode.VANILLA,
                    "Existing privacy, muted audio, message and explicitly chosen reload settings survive normalization"); checks++;
            check(loaded.gameRulesOverride.equals(rules),
                    "Loading preserves existing known and custom game rule values verbatim"); checks++;
            check(loaded.scannerSkipVerified && !loaded.worldChunkOverlay && loaded.worldChunkOverlayRadius == 4,
                    "New scanner and overlay options receive constructor defaults even when absent from old JSON"); checks++;
            check(loaded.movement.enabled(MovementTool.SPEED) && loaded.movement.value(MovementTool.SPEED) == 2.75
                            && !loaded.movement.enabled(MovementTool.FLY) && loaded.movement.value(MovementTool.FLY) == 3.5,
                    "Nested movement normalization preserves enabled and disabled existing tools and their strengths"); checks++;
            check(!loaded.movement.enabled(MovementTool.LOW_GRAVITY)
                            && loaded.movement.value(MovementTool.LOW_GRAVITY) == MovementTool.LOW_GRAVITY.initial,
                    "Tools absent from an old nested movement map receive their safe defaults"); checks++;
            check(Arrays.equals(sparse, Files.readAllBytes(file)),
                    "Reading an old config does not silently overwrite or expand its on-disk bytes"); checks++;

            loaded.save();
            var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            check(saved.has("scannerSkipVerified") && saved.has("worldChunkOverlay") && saved.has("worldChunkOverlayRadius")
                            && saved.getAsJsonObject("movement").getAsJsonObject("tools").size() == MovementTool.values().length,
                    "Explicit save writes new top-level defaults and all normalized movement settings"); checks++;

            WorldBinderConfig reopened = WorldBinderConfig.load();
            check(reopened != loaded && reopened.defaultArchiveName.equals("My existing project") && reopened.gameRulesOverride.equals(rules)
                            && !reopened.appendTimestampToArchiveName && !reopened.mapShowPlayers && reopened.uiSoundVolumePercent == 0,
                    "A fresh load of the expanded file retains the original user preferences and rule data"); checks++;
            check(reopened.performancePreset == WorldBinderConfig.PerformancePreset.CUSTOM && reopened.blocksPerTick == 24576
                            && reopened.commandsPerTick == 123 && reopened.tickBudgetMillis == 11 && reopened.roamingRadiusChunks == 7
                            && reopened.movement.enabled(MovementTool.SPEED) && reopened.movement.value(MovementTool.SPEED) == 2.75
                            && !reopened.movement.enabled(MovementTool.FLY) && reopened.movement.value(MovementTool.FLY) == 3.5,
                    "Save/reload preserves custom performance and nested movement values"); checks++;
            check(reopened.scannerSkipVerified && !reopened.worldChunkOverlay && reopened.worldChunkOverlayRadius == 4
                            && reopened.movement.value(MovementTool.LOW_GRAVITY) == MovementTool.LOW_GRAVITY.initial,
                    "New defaults remain available after the expanded file is reopened"); checks++;
        } finally {
            if (previous == null) Files.deleteIfExists(file);
            else Files.write(file, previous);
        }
        return checks;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

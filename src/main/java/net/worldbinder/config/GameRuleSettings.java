package net.worldbinder.config;

import net.worldbinder.version.TargetMinecraftVersion;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameRuleSettings {
    private static final String[] RULES = {
            "announceAdvancements", "commandBlockOutput", "disableElytraMovementCheck", "disableRaids",
            "doDaylightCycle", "doEntityDrops", "doFireTick", "doImmediateRespawn", "doInsomnia",
            "doLimitedCrafting", "doMobLoot", "doMobSpawning", "doPatrolSpawning", "doTileDrops",
            "doTraderSpawning", "doVinesSpread", "doWardenSpawning", "doWeatherCycle", "drowningDamage",
            "fallDamage", "fireDamage", "forgiveDeadPlayers", "keepInventory", "logAdminCommands",
            "mobGriefing", "naturalRegeneration", "reducedDebugInfo", "sendCommandFeedback",
            "showDeathMessages", "spectatorsGenerateChunks", "universalAnger"
    };

    private final Map<String, Boolean> values = new LinkedHashMap<>();
    private final Map<String, String> unknownValues = new LinkedHashMap<>();
    private int randomTickSpeed;
    private boolean dirty;

    public GameRuleSettings() {
        load(null);
    }

    public static String[] rules() {
        return RULES.clone();
    }

    public static String[] rules(String targetVersion) {
        return Arrays.stream(RULES).filter(rule -> supports(targetVersion, rule)).toArray(String[]::new);
    }

    public static String[] rules(String targetVersion, int category) {
        return Arrays.stream(rules(targetVersion)).filter(rule -> category(rule) == category).toArray(String[]::new);
    }

    public static int category(String rule) {
        return switch (rule) {
            case "doDaylightCycle", "doWeatherCycle", "doFireTick", "doVinesSpread" -> 0;
            case "disableRaids", "doInsomnia", "doMobLoot", "doMobSpawning", "doPatrolSpawning",
                    "doTraderSpawning", "doWardenSpawning", "forgiveDeadPlayers", "mobGriefing",
                    "universalAnger", "doEntityDrops", "doTileDrops" -> 1;
            case "disableElytraMovementCheck", "doImmediateRespawn", "drowningDamage", "fallDamage",
                    "fireDamage", "keepInventory", "naturalRegeneration", "spectatorsGenerateChunks" -> 2;
            default -> 3;
        };
    }

    public static boolean supports(String targetVersion, String rule) {
        int dataVersion = TargetMinecraftVersion.resolve(targetVersion).effectiveDataVersion();
        int first = switch (rule) {
            case "disableRaids" -> 1968;
            case "doPatrolSpawning", "doTraderSpawning" -> 2230;
            case "doImmediateRespawn", "doInsomnia", "drowningDamage", "fallDamage", "fireDamage" -> 2225;
            case "forgiveDeadPlayers", "universalAnger" -> 2566;
            case "doVinesSpread" -> 3337;
            case "doWardenSpawning" -> 3105;
            default -> 1519;
        };
        return dataVersion >= first;
    }

    public void load(String raw) {
        values.clear();
        unknownValues.clear();
        dirty = false;
        for (String rule : RULES) {
            values.put(rule, defaultValue(rule));
        }
        randomTickSpeed = 3;
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(";")) {
            String[] split = part.split("=", 2);
            if (split.length != 2) {
                continue;
            }
            String key = split[0].trim();
            String value = split[1].trim();
            if ("randomTickSpeed".equals(key)) {
                Integer parsed = parseInt(value);
                randomTickSpeed = parsed == null ? randomTickSpeed : clampRandomTickSpeed(parsed);
            } else if (values.containsKey(key)) {
                values.put(key, Boolean.parseBoolean(value));
            } else if (key.matches("[A-Za-z0-9_.-]+") && !value.isBlank()) {
                unknownValues.put(key, value);
            }
        }
    }

    public boolean value(String rule) {
        return values.getOrDefault(rule, defaultValue(rule));
    }

    public boolean toggle(String rule) {
        boolean next = !value(rule);
        values.put(rule, next);
        dirty = true;
        return next;
    }

    public int randomTickSpeed() {
        return randomTickSpeed;
    }

    public void setRandomTickSpeed(int value) {
        int next = clampRandomTickSpeed(value);
        if (next != randomTickSpeed) {
            randomTickSpeed = next;
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public String serializeAndMarkClean() {
        StringBuilder builder = new StringBuilder();
        for (String rule : RULES) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(rule).append('=').append(value(rule));
        }
        builder.append(";randomTickSpeed=").append(randomTickSpeed);
        for (Map.Entry<String, String> entry : unknownValues.entrySet()) {
            builder.append(';').append(entry.getKey()).append('=').append(entry.getValue());
        }
        dirty = false;
        return builder.toString();
    }

    private static int clampRandomTickSpeed(int value) {
        return Math.max(0, Math.min(64, value));
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean defaultValue(String rule) {
        return switch (rule) {
            case "announceAdvancements", "commandBlockOutput", "doDaylightCycle", "doEntityDrops",
                    "doFireTick", "doInsomnia", "doMobLoot", "doMobSpawning", "doPatrolSpawning",
                    "doTileDrops", "doTraderSpawning", "doVinesSpread", "doWardenSpawning",
                    "doWeatherCycle", "drowningDamage", "fallDamage", "fireDamage",
                    "forgiveDeadPlayers", "logAdminCommands", "mobGriefing", "naturalRegeneration",
                    "sendCommandFeedback", "showDeathMessages", "spectatorsGenerateChunks" -> true;
            default -> false;
        };
    }
}

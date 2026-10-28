package net.worldbinder.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editable game-rule overrides of the settings screen, stored as {@code rule=value;…} with canonical rule IDs.
 * Types, availability per target version and defaults come from {@link GameRuleCatalog}. Unknown entries from
 * newer versions or mods are kept unchanged.
 */
public final class GameRuleSettings {
    /** Canonical IDs in display order; must match {@link GameRuleCatalog} (checked on class load and by the language audit). */
    private static final String[] RULES = {
            "doDaylightCycle", "doWeatherCycle", "doFireTick", "randomTickSpeed", "doVinesSpread", "snowAccumulationHeight",
            "waterSourceConversion", "lavaSourceConversion",
            "mobGriefing", "universalAnger", "forgiveDeadPlayers", "disableRaids", "maxEntityCramming",
            "doMobSpawning", "spawnMonsters", "doInsomnia", "doPatrolSpawning", "doTraderSpawning", "doWardenSpawning",
            "spawnerBlocksWork", "spawnChunkRadius",
            "keepInventory", "naturalRegeneration", "doImmediateRespawn", "drowningDamage", "fallDamage", "fireDamage",
            "freezeDamage", "pvp", "playersSleepingPercentage", "spawnRadius", "doLimitedCrafting", "enderPearlsVanishOnDeath",
            "locatorBar", "spectatorsGenerateChunks", "disableElytraMovementCheck", "disablePlayerMovementCheck",
            "allowEnteringNetherUsingPortals", "playersNetherPortalDefaultDelay", "playersNetherPortalCreativeDelay",
            "doTileDrops", "doEntityDrops", "doMobLoot", "blockExplosionDropDecay", "mobExplosionDropDecay",
            "tntExplosionDropDecay", "projectilesCanBreakBlocks",
            "announceAdvancements", "showDeathMessages", "sendCommandFeedback", "commandBlockOutput", "logAdminCommands",
            "reducedDebugInfo", "globalSoundEvents", "commandBlocksWork", "tntExplodes", "maxCommandChainLength",
            "maxCommandForkCount", "commandModificationBlockLimit", "maxMinecartSpeed"
    };

    static {
        var catalog = GameRuleCatalog.all().stream().map(GameRuleCatalog.Rule::id).toList();
        if (!catalog.equals(Arrays.asList(RULES))) throw new IllegalStateException("Game-rule list differs from the catalog");
    }

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> unknownValues = new LinkedHashMap<>();
    private boolean dirty;

    public GameRuleSettings() {
        load(null);
    }

    public static String[] rules() {
        return RULES.clone();
    }

    /** Canonical archive/config IDs have stable labels independent of the running Minecraft version. */
    public static String labelKey(String rule) {
        return "worldbinder.gamerules.rule." + rule;
    }

    public static String tooltipKey(String rule) {
        return "worldbinder.gamerules.rule." + rule + ".tooltip";
    }

    public static String[] rules(String targetVersion) {
        return Arrays.stream(RULES).filter(rule -> supports(targetVersion, rule)).toArray(String[]::new);
    }

    public static String[] rules(String targetVersion, int category) {
        return Arrays.stream(rules(targetVersion)).filter(rule -> category(rule) == category).toArray(String[]::new);
    }

    public static int categories() {
        return GameRuleCatalog.Category.values().length;
    }

    public static int category(String rule) {
        GameRuleCatalog.Rule entry = GameRuleCatalog.rule(rule);
        return entry == null ? GameRuleCatalog.Category.MISC.ordinal() : entry.category().ordinal();
    }

    public static boolean supports(String targetVersion, String rule) {
        String canonical = GameRuleCatalog.canonical(rule);
        return canonical != null && GameRuleCatalog.supports(targetVersion, GameRuleCatalog.rule(canonical));
    }

    public static boolean isInteger(String rule) {
        GameRuleCatalog.Rule entry = GameRuleCatalog.rule(rule);
        return entry != null && entry.type() == GameRuleCatalog.Type.INT;
    }

    public void load(String raw) {
        values.clear();
        unknownValues.clear();
        dirty = false;
        for (GameRuleCatalog.Rule rule : GameRuleCatalog.all()) {
            values.put(rule.id(), rule.fallback());
        }
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
            GameRuleCatalog.Rule rule = GameRuleCatalog.rule(key);
            if (rule != null) {
                if (rule.bool()) {
                    values.put(key, Boolean.toString(Boolean.parseBoolean(value)));
                } else {
                    Integer parsed = parseInt(value);
                    if (parsed != null) values.put(key, Integer.toString(clamp(rule, parsed)));
                }
            } else if (key.matches("[A-Za-z0-9_.:-]+") && !value.isBlank()) {
                unknownValues.put(key, value);
            }
        }
    }

    public boolean value(String rule) {
        return Boolean.parseBoolean(values.getOrDefault(rule, "false"));
    }

    public int intValue(String rule) {
        Integer parsed = parseInt(values.get(rule));
        GameRuleCatalog.Rule entry = GameRuleCatalog.rule(rule);
        return parsed != null ? parsed : entry == null ? 0 : Integer.parseInt(entry.fallback());
    }

    public String raw(String rule) {
        return values.get(rule);
    }

    public boolean toggle(String rule) {
        boolean next = !value(rule);
        values.put(rule, Boolean.toString(next));
        dirty = true;
        return next;
    }

    public void setInt(String rule, int value) {
        GameRuleCatalog.Rule entry = GameRuleCatalog.rule(rule);
        if (entry == null) return;
        String next = Integer.toString(clamp(entry, value));
        if (!next.equals(values.get(rule))) {
            values.put(rule, next);
            dirty = true;
        }
    }

    /** True when the stored value equals the vanilla default of the target version. */
    public boolean isDefault(String targetVersion, String rule) {
        GameRuleCatalog.Rule entry = GameRuleCatalog.rule(rule);
        return entry == null || GameRuleCatalog.defaultValue(targetVersion, entry).equalsIgnoreCase(values.getOrDefault(rule, ""));
    }

    /** Sets a rule back to the vanilla default of the target version. */
    public void reset(String targetVersion, String rule) {
        GameRuleCatalog.Rule entry = GameRuleCatalog.rule(rule);
        if (entry == null) return;
        values.put(rule, GameRuleCatalog.defaultValue(targetVersion, entry));
        dirty = true;
    }

    /** Number of supported rules of the target that differ from its vanilla default. */
    public int changedCount(String targetVersion) {
        int changed = 0;
        for (String rule : rules(targetVersion)) if (!isDefault(targetVersion, rule)) changed++;
        return changed;
    }

    public int randomTickSpeed() {
        return intValue("randomTickSpeed");
    }

    public void setRandomTickSpeed(int value) {
        setInt("randomTickSpeed", value);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public String serializeAndMarkClean() {
        StringBuilder builder = new StringBuilder();
        for (String rule : RULES) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(rule).append('=').append(values.get(rule));
        }
        for (Map.Entry<String, String> entry : unknownValues.entrySet()) {
            builder.append(';').append(entry.getKey()).append('=').append(entry.getValue());
        }
        dirty = false;
        return builder.toString();
    }

    private static int clamp(GameRuleCatalog.Rule rule, int value) {
        return Math.max(rule.uiMin(), Math.min(rule.uiMax(), value));
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
}

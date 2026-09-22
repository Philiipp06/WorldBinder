package net.worldbinder.export.common;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class WorldExportGameRules {
    private static final Map<String, String> VANILLA_KEYS = vanillaKeys();
    private static final Map<String, Rule26Mapping> RULE_26_KEYS = rule26Keys();

    private WorldExportGameRules() {
    }

    static void write(WorldScene scene, Path worldFolder) throws IOException {
        TargetMinecraftVersion.Entry target = targetVersion(scene);
        if (!target.usesGameRulesFile()) {
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", target.effectiveDataVersion());
        root.put("data", exportData(scene, target));
        Path targetFile = worldFolder.resolve("data").resolve("minecraft").resolve("game_rules.dat");
        Files.createDirectories(targetFile.getParent());
        NbtIo.writeCompressed(root, targetFile);
    }

    static CompoundTag legacyRules(WorldScene scene) {
        CompoundTag rules = defaultRules();
        if (scene.gameRulesNbt == null || scene.gameRulesNbt.isBlank()) {
            return rules;
        }

        String raw = scene.gameRulesNbt.trim();
        if (raw.contains("=") && !raw.startsWith("{")) {
            applyDelimitedOverrides(rules, raw);
            return rules;
        }

        try {
            applyNbtOverrides(rules, TagParser.parseCompoundFully(raw));
        } catch (CommandSyntaxException exception) {
            WorldBinder.LOGGER.warn("Failed to parse captured game rules. Falling back to safe defaults.", exception);
        }
        return rules;
    }

    private static CompoundTag exportData(WorldScene scene, TargetMinecraftVersion.Entry target) {
        CompoundTag data = new CompoundTag();
        CompoundTag exported = legacyRules(scene);
        for (String key : exported.keySet()) {
            String legacyKey = canonicalKey(key);
            if (legacyKey == null) {
                continue;
            }
            String value = exported.getString(key).orElse("false");
            RuleExport rule = exportRule(target, legacyKey, value);
            if (rule == null || !isAllowedOutput(target, rule.key())) {
                continue;
            }
            putTyped(data, rule.key(), rule.value());
        }
        return data;
    }

    private static void applyDelimitedOverrides(CompoundTag rules, String raw) {
        for (String part : raw.split(";")) {
            int split = part.indexOf('=');
            if (split <= 0 || split >= part.length() - 1) {
                continue;
            }
            String key = canonicalKey(part.substring(0, split).trim());
            String value = part.substring(split + 1).trim();
            if (key != null && !value.isBlank()) {
                rules.putString(key, value);
            }
        }
    }

    private static void applyNbtOverrides(CompoundTag rules, CompoundTag overrides) {
        for (String key : overrides.keySet()) {
            String value = overrides.getString(key).orElse(null);
            if (value == null && overrides.get(key) != null) {
                value = overrides.get(key).toString().replace("\"", "");
            }
            String legacyKey = canonicalKey(key);
            if (legacyKey != null && value != null && !value.isBlank()) {
                rules.putString(legacyKey, value);
            }
        }
    }

    private static boolean isAllowedOutput(TargetMinecraftVersion.Entry target, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (target != null && target.profile() == TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return key.startsWith("minecraft:")
                    && RULE_26_KEYS.values().stream().anyMatch(mapping -> mapping.registryKey().equals(key));
        }
        return key.startsWith("minecraft:");
    }

    private static void putTyped(CompoundTag tag, String key, String value) {
        String cleaned = value == null ? "false" : value.trim();
        if ("true".equalsIgnoreCase(cleaned) || "false".equalsIgnoreCase(cleaned)) {
            tag.putBoolean(key, Boolean.parseBoolean(cleaned));
            return;
        }
        try {
            tag.putInt(key, Integer.parseInt(cleaned));
        } catch (NumberFormatException ignored) {
            tag.putString(key, cleaned);
        }
    }

    private static RuleExport exportRule(TargetMinecraftVersion.Entry target, String legacyKey, String value) {
        if (target == null || target.profile() != TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return new RuleExport(namespacedSnakeKey(legacyKey), value);
        }
        Rule26Mapping mapping = RULE_26_KEYS.get(legacyKey);
        if (mapping == null) {
            return null;
        }
        return new RuleExport(mapping.registryKey(), mapping.transform(value));
    }

    private static Map<String, Rule26Mapping> rule26Keys() {
        Map<String, Rule26Mapping> keys = new LinkedHashMap<>();
        register26(keys, "doDaylightCycle", "minecraft:advance_time");
        register26(keys, "doWeatherCycle", "minecraft:advance_weather");
        register26(keys, "doTileDrops", "minecraft:block_drops");
        register26(keys, "doEntityDrops", "minecraft:entity_drops");
        register26(keys, "doMobLoot", "minecraft:mob_drops");
        register26(keys, "doMobSpawning", "minecraft:spawn_mobs");
        register26(keys, "doInsomnia", "minecraft:spawn_phantoms");
        register26(keys, "doPatrolSpawning", "minecraft:spawn_patrols");
        register26(keys, "doTraderSpawning", "minecraft:spawn_wandering_traders");
        register26(keys, "doWardenSpawning", "minecraft:spawn_wardens");
        register26(keys, "spawnRadius", "minecraft:respawn_radius");
        register26(keys, "naturalRegeneration", "minecraft:natural_health_regeneration");
        register26Inverted(keys, "disableRaids", "minecraft:raids");
        register26Inverted(keys, "disableElytraMovementCheck", "minecraft:elytra_movement_check");
        return keys;
    }

    private static void register26(Map<String, Rule26Mapping> keys, String legacyKey, String registryKey) {
        keys.put(legacyKey, new Rule26Mapping(registryKey, false));
    }

    private static void register26Inverted(Map<String, Rule26Mapping> keys, String legacyKey, String registryKey) {
        keys.put(legacyKey, new Rule26Mapping(registryKey, true));
    }

    private static String canonicalKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String cleaned = key.trim();
        String direct = VANILLA_KEYS.get(cleaned);
        if (direct != null) {
            return direct;
        }
        direct = VANILLA_KEYS.get(cleaned.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        if (cleaned.contains(":")) {
            int split = cleaned.indexOf(':');
            if (!"minecraft".equals(cleaned.substring(0, split))) {
                return null;
            }
            cleaned = cleaned.substring(split + 1);
        }
        direct = VANILLA_KEYS.get(cleaned);
        return direct != null ? direct : VANILLA_KEYS.get(cleaned.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> vanillaKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        String[] legacyKeys = {
                "announceAdvancements", "blockExplosionDropDecay", "commandBlockOutput",
                "commandModificationBlockLimit", "disableElytraMovementCheck", "disableRaids",
                "doDaylightCycle", "doEntityDrops", "doFireTick", "doImmediateRespawn",
                "doInsomnia", "doLimitedCrafting", "doMobLoot", "doMobSpawning",
                "doPatrolSpawning", "doTileDrops", "doTraderSpawning", "doVinesSpread",
                "doWardenSpawning", "doWeatherCycle", "drowningDamage", "enderPearlsVanishOnDeath",
                "fallDamage", "fireDamage", "forgiveDeadPlayers", "globalSoundEvents",
                "keepInventory", "lavaSourceConversion", "locatorBar", "logAdminCommands",
                "maxCommandChainLength", "maxEntityCramming", "mobExplosionDropDecay",
                "mobGriefing", "naturalRegeneration", "playersSleepingPercentage",
                "projectilesCanBreakBlocks", "randomTickSpeed", "reducedDebugInfo",
                "sendCommandFeedback", "showDeathMessages", "snowAccumulationHeight",
                "spawnChunkRadius", "spawnRadius", "spectatorsGenerateChunks",
                "tntExplosionDropDecay", "universalAnger", "waterSourceConversion"
        };
        for (String key : legacyKeys) {
            registerKey(keys, key);
        }
        return keys;
    }

    private static void registerKey(Map<String, String> keys, String legacyKey) {
        keys.put(legacyKey, legacyKey);
        keys.put(legacyKey.toLowerCase(Locale.ROOT), legacyKey);
        String namespaced = namespacedSnakeKey(legacyKey);
        keys.put(namespaced, legacyKey);
        keys.put(namespaced.substring("minecraft:".length()), legacyKey);
    }

    private static String namespacedSnakeKey(String key) {
        if (key == null || key.isBlank()) {
            return "minecraft:unknown";
        }
        String raw = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        StringBuilder result = new StringBuilder("minecraft:");
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (Character.isUpperCase(character)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(character));
            } else {
                result.append(character == '-' ? '_' : character);
            }
        }
        return result.toString();
    }

    private static CompoundTag defaultRules() {
        CompoundTag rules = new CompoundTag();
        putRule(rules, "announceAdvancements", "true");
        putRule(rules, "commandBlockOutput", "false");
        putRule(rules, "disableElytraMovementCheck", "false");
        putRule(rules, "disableRaids", "true");
        putRule(rules, "doDaylightCycle", "false");
        putRule(rules, "doEntityDrops", "true");
        putRule(rules, "doFireTick", "false");
        putRule(rules, "doImmediateRespawn", "false");
        putRule(rules, "doInsomnia", "false");
        putRule(rules, "doLimitedCrafting", "false");
        putRule(rules, "doMobLoot", "true");
        putRule(rules, "doMobSpawning", "false");
        putRule(rules, "doPatrolSpawning", "false");
        putRule(rules, "doTileDrops", "true");
        putRule(rules, "doTraderSpawning", "false");
        putRule(rules, "doVinesSpread", "false");
        putRule(rules, "doWardenSpawning", "false");
        putRule(rules, "doWeatherCycle", "false");
        putRule(rules, "drowningDamage", "true");
        putRule(rules, "fallDamage", "true");
        putRule(rules, "fireDamage", "true");
        putRule(rules, "forgiveDeadPlayers", "true");
        putRule(rules, "keepInventory", "true");
        putRule(rules, "logAdminCommands", "false");
        putRule(rules, "maxCommandChainLength", "65536");
        putRule(rules, "maxEntityCramming", "24");
        putRule(rules, "mobGriefing", "false");
        putRule(rules, "naturalRegeneration", "true");
        putRule(rules, "playersSleepingPercentage", "100");
        putRule(rules, "randomTickSpeed", "0");
        putRule(rules, "reducedDebugInfo", "false");
        putRule(rules, "sendCommandFeedback", "true");
        putRule(rules, "showDeathMessages", "true");
        putRule(rules, "spawnRadius", "0");
        putRule(rules, "spectatorsGenerateChunks", "true");
        putRule(rules, "universalAnger", "false");
        return rules;
    }

    private static void putRule(CompoundTag rules, String key, String value) {
        rules.putString(key, value);
    }

    private static TargetMinecraftVersion.Entry targetVersion(WorldScene scene) {
        return TargetMinecraftVersion.resolve(scene == null ? null : scene.targetMinecraftVersion);
    }

    private record RuleExport(String key, String value) {
    }

    private record Rule26Mapping(String registryKey, boolean inverted) {
        private String transform(String value) {
            if (!inverted) {
                return value;
            }
            if ("true".equalsIgnoreCase(value)) {
                return "false";
            }
            if ("false".equalsIgnoreCase(value)) {
                return "true";
            }
            return value;
        }
    }
}

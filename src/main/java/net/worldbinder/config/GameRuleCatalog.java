package net.worldbinder.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Version-aware catalog of every vanilla game rule WorldBinder can export.
 *
 * <p>Rules use stable canonical IDs (the classic camelCase names; rules that only exist in 26.x get a camelCase form
 * of their registry ID). For 26.x targets the exact set, type, default and bounds come from
 * {@code data/worldbinder/gamerules/26.json}, generated from the official Minecraft jars by
 * {@code scripts/qa/generate-gamerule-facts.mjs}. Older targets use the first data version in which a rule
 * appeared. Values are always stored canonically (for example {@code disableRaids=true}) and converted when a 26.x
 * world is written ({@code minecraft:raids=false}).
 */
public final class GameRuleCatalog {
    public enum Type { BOOL, INT }

    /** Display groups. The order is the order of the sub-menu in Settings › Game rules. */
    public enum Category { WORLD, MOBS, SPAWNING, PLAYER, DROPS, CHAT, MISC }

    /** First data version of the 26.x storage (game_rules.dat with registry IDs). */
    public static final int REGISTRY_FORMAT = 4786;
    /** Data version used for rules that never existed before the 26.x registry. */
    private static final int ONLY_26 = REGISTRY_FORMAT;

    /**
     * @param id         canonical ID (stored in configs and archives)
     * @param id26       26.x registry path without namespace, or {@code null} when removed in 26.x
     * @param inverted   26.x stores the opposite boolean (disableRaids ↔ raids)
     * @param since      first data version that knows the rule (legacy name)
     * @param until      last data version that knows the rule, or {@link Integer#MAX_VALUE}
     * @param fallback   default value when no exact fact is available
     * @param uiMin      lower bound offered by the slider
     * @param uiMax      upper bound offered by the slider
     * @param steps      fixed choices for very large ranges, or {@code null} for a slider
     */
    public record Rule(String id, String id26, boolean inverted, Type type, Category category, int since, int until,
                       String fallback, int uiMin, int uiMax, int[] steps) {
        public boolean bool() {
            return type == Type.BOOL;
        }
    }

    public record Fact(Type type, String defaultValue, int min, int max, boolean experimental) {
    }

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    private static final Map<String, Map<String, Fact>> FACTS = loadFacts();
    private static final int[] COMMAND_STEPS = {1024, 4096, 16384, 32768, 65536, 131072, 524288, 1048576};

    static {
        Category w = Category.WORLD, m = Category.MOBS, s = Category.SPAWNING, p = Category.PLAYER, d = Category.DROPS,
                c = Category.CHAT, x = Category.MISC;
        bool("doDaylightCycle", "advance_time", w, 1519, true);
        bool("doWeatherCycle", "advance_weather", w, 1519, true);
        bool("doFireTick", "fire_spread_radius_around_player", w, 1519, true);
        integer("randomTickSpeed", "random_tick_speed", w, 1519, 3, 0, 64, null);
        bool("doVinesSpread", "spread_vines", w, 3337, true);
        integer("snowAccumulationHeight", "max_snow_accumulation_height", w, 3218, 1, 0, 8, null);
        bool("waterSourceConversion", "water_source_conversion", w, 3218, true);
        bool("lavaSourceConversion", "lava_source_conversion", w, 3218, false);

        bool("mobGriefing", "mob_griefing", m, 1519, true);
        bool("universalAnger", "universal_anger", m, 2566, false);
        bool("forgiveDeadPlayers", "forgive_dead_players", m, 2566, true);
        inverted("disableRaids", "raids", m, 1968, false);
        integer("maxEntityCramming", "max_entity_cramming", m, 1519, 24, 0, 100, null);

        bool("doMobSpawning", "spawn_mobs", s, 1519, true);
        bool("spawnMonsters", "spawn_monsters", s, ONLY_26, true);
        bool("doInsomnia", "spawn_phantoms", s, 2225, true);
        bool("doPatrolSpawning", "spawn_patrols", s, 2230, true);
        bool("doTraderSpawning", "spawn_wandering_traders", s, 2230, true);
        bool("doWardenSpawning", "spawn_wardens", s, 3105, true);
        bool("spawnerBlocksWork", "spawner_blocks_work", s, ONLY_26, true);
        add(new Rule("spawnChunkRadius", null, false, Type.INT, s, 3837, REGISTRY_FORMAT - 1, "2", 0, 32, null));

        bool("keepInventory", "keep_inventory", p, 1519, false);
        bool("naturalRegeneration", "natural_health_regeneration", p, 1519, true);
        bool("doImmediateRespawn", "immediate_respawn", p, 2225, false);
        bool("drowningDamage", "drowning_damage", p, 2225, true);
        bool("fallDamage", "fall_damage", p, 2225, true);
        bool("fireDamage", "fire_damage", p, 2225, true);
        bool("freezeDamage", "freeze_damage", p, 2724, true);
        bool("pvp", "pvp", p, ONLY_26, true);
        integer("playersSleepingPercentage", "players_sleeping_percentage", p, 2724, 100, 0, 100, null);
        integer("spawnRadius", "respawn_radius", p, 1519, 10, 0, 64, null);
        bool("doLimitedCrafting", "limited_crafting", p, 1519, false);
        bool("enderPearlsVanishOnDeath", "ender_pearls_vanish_on_death", p, 3578, true);
        bool("locatorBar", "locator_bar", p, 4435, true);
        bool("spectatorsGenerateChunks", "spectators_generate_chunks", p, 1519, true);
        inverted("disableElytraMovementCheck", "elytra_movement_check", p, 1519, false);
        inverted("disablePlayerMovementCheck", "player_movement_check", p, 4080, false);
        bool("allowEnteringNetherUsingPortals", "allow_entering_nether_using_portals", p, ONLY_26, true);
        integer("playersNetherPortalDefaultDelay", "players_nether_portal_default_delay", p, 3698, 80, 0, 400, null);
        integer("playersNetherPortalCreativeDelay", "players_nether_portal_creative_delay", p, 3698, 1, 0, 400, null);

        bool("doTileDrops", "block_drops", d, 1519, true);
        bool("doEntityDrops", "entity_drops", d, 1519, true);
        bool("doMobLoot", "mob_drops", d, 1519, true);
        bool("blockExplosionDropDecay", "block_explosion_drop_decay", d, 3218, true);
        bool("mobExplosionDropDecay", "mob_explosion_drop_decay", d, 3218, true);
        bool("tntExplosionDropDecay", "tnt_explosion_drop_decay", d, 3218, false);
        bool("projectilesCanBreakBlocks", "projectiles_can_break_blocks", d, 3698, true);

        bool("announceAdvancements", "show_advancement_messages", c, 1519, true);
        bool("showDeathMessages", "show_death_messages", c, 1519, true);
        bool("sendCommandFeedback", "send_command_feedback", c, 1519, true);
        bool("commandBlockOutput", "command_block_output", c, 1519, true);
        bool("logAdminCommands", "log_admin_commands", c, 1519, true);

        bool("reducedDebugInfo", "reduced_debug_info", x, 1519, false);
        bool("globalSoundEvents", "global_sound_events", x, 3218, true);
        bool("commandBlocksWork", "command_blocks_work", x, ONLY_26, true);
        bool("tntExplodes", "tnt_explodes", x, ONLY_26, true);
        integer("maxCommandChainLength", "max_command_sequence_length", x, 1519, 65536, 0, 1048576, COMMAND_STEPS);
        integer("maxCommandForkCount", "max_command_forks", x, 3698, 65536, 0, 1048576, COMMAND_STEPS);
        integer("commandModificationBlockLimit", "max_block_modifications", x, 3337, 32768, 1, 1048576, COMMAND_STEPS);
        integer("maxMinecartSpeed", "max_minecart_speed", x, ONLY_26, 8, 1, 1000, null);
    }

    private GameRuleCatalog() {
    }

    private static void bool(String id, String id26, Category category, int since, boolean fallback) {
        add(new Rule(id, id26, false, Type.BOOL, category, since, Integer.MAX_VALUE, Boolean.toString(fallback), 0, 1, null));
    }

    private static void inverted(String id, String id26, Category category, int since, boolean fallback) {
        add(new Rule(id, id26, true, Type.BOOL, category, since, Integer.MAX_VALUE, Boolean.toString(fallback), 0, 1, null));
    }

    private static void integer(String id, String id26, Category category, int since, int fallback, int min, int max, int[] steps) {
        add(new Rule(id, id26, false, Type.INT, category, since, Integer.MAX_VALUE, Integer.toString(fallback), min, max, steps));
    }

    private static void add(Rule rule) {
        if (RULES.put(rule.id(), rule) != null) throw new IllegalStateException("Duplicate game rule " + rule.id());
    }

    private static Map<String, Map<String, Fact>> loadFacts() {
        Map<String, Map<String, Fact>> facts = new LinkedHashMap<>();
        try (var input = GameRuleCatalog.class.getResourceAsStream("/data/worldbinder/gamerules/26.json")) {
            if (input == null) throw new IllegalStateException("Missing bundled game-rule facts");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var version : root.getAsJsonObject("versions").entrySet()) {
                Map<String, Fact> rules = new LinkedHashMap<>();
                for (var element : version.getValue().getAsJsonObject().getAsJsonArray("rules")) {
                    JsonObject rule = element.getAsJsonObject();
                    Type type = "int".equals(rule.get("type").getAsString()) ? Type.INT : Type.BOOL;
                    rules.put(rule.get("id").getAsString(), new Fact(type, rule.get("default").getAsString(),
                            rule.has("min") ? rule.get("min").getAsInt() : 0, rule.has("max") ? rule.get("max").getAsInt() : 1,
                            rule.has("experimental") && rule.get("experimental").getAsBoolean()));
                }
                facts.put(version.getKey(), rules);
            }
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return facts;
    }

    // ------------------------------------------------------------------ lookup

    public static List<Rule> all() {
        return Collections.unmodifiableList(new ArrayList<>(RULES.values()));
    }

    public static Rule rule(String id) {
        return RULES.get(id);
    }

    /** Canonical ID for any known spelling: camelCase, snake_case, {@code minecraft:} prefixed or 26.x registry ID. */
    public static String canonical(String key) {
        if (key == null || key.isBlank()) return null;
        String cleaned = key.trim();
        if (cleaned.contains(":")) {
            if (!cleaned.startsWith("minecraft:")) return null;
            cleaned = cleaned.substring("minecraft:".length());
        }
        if (RULES.containsKey(cleaned)) return cleaned;
        for (Rule rule : RULES.values()) {
            if (cleaned.equals(rule.id26()) || cleaned.equalsIgnoreCase(rule.id()) || cleaned.equals(snake(rule.id()))) return rule.id();
        }
        return null;
    }

    static String snake(String camel) {
        StringBuilder result = new StringBuilder();
        for (char character : camel.toCharArray()) {
            if (Character.isUpperCase(character)) result.append('_').append(Character.toLowerCase(character));
            else result.append(character);
        }
        return result.toString();
    }

    private static Map<String, Fact> facts(String targetVersion) {
        Map<String, Fact> exact = FACTS.get(targetVersion);
        if (exact != null) return exact;
        // A newer 26.x target than the bundled facts: use the newest known set.
        List<String> versions = new ArrayList<>(FACTS.keySet());
        return versions.isEmpty() ? Map.of() : FACTS.get(versions.get(versions.size() - 1));
    }

    public static int dataVersion(String targetVersion) {
        return TargetMinecraftVersion.resolve(targetVersion).effectiveDataVersion();
    }

    public static boolean registryFormat(String targetVersion) {
        return dataVersion(targetVersion) >= REGISTRY_FORMAT;
    }

    /** Exact fact of a rule in a 26.x target, or {@code null}. */
    public static Fact fact(String targetVersion, Rule rule) {
        if (rule == null || rule.id26() == null || !registryFormat(targetVersion)) return null;
        return facts(targetVersion).get(rule.id26());
    }

    public static boolean supports(String targetVersion, Rule rule) {
        if (rule == null) return false;
        if (registryFormat(targetVersion)) return fact(targetVersion, rule) != null;
        int dataVersion = dataVersion(targetVersion);
        return dataVersion >= rule.since() && dataVersion <= rule.until();
    }

    public static boolean experimental(String targetVersion, Rule rule) {
        Fact fact = fact(targetVersion, rule);
        return fact != null && fact.experimental();
    }

    /** Vanilla default of the target version in canonical form. */
    public static String defaultValue(String targetVersion, Rule rule) {
        Fact fact = fact(targetVersion, rule);
        if (fact == null) return rule.fallback();
        if (rule.id().equals("doFireTick")) return Boolean.toString(!"0".equals(fact.defaultValue()));
        if (rule.inverted()) return Boolean.toString(!Boolean.parseBoolean(fact.defaultValue()));
        return fact.defaultValue();
    }

    public static int minimum(String targetVersion, Rule rule) {
        Fact fact = fact(targetVersion, rule);
        return fact == null ? rule.uiMin() : Math.max(rule.uiMin(), fact.min());
    }

    public static int maximum(String targetVersion, Rule rule) {
        Fact fact = fact(targetVersion, rule);
        return fact == null ? rule.uiMax() : Math.min(rule.uiMax(), fact.max());
    }

    /** Key written into the target world: {@code minecraft:<id>} for 26.x, the classic name otherwise. */
    public static String exportKey(String targetVersion, Rule rule) {
        return registryFormat(targetVersion) ? "minecraft:" + rule.id26() : rule.id();
    }

    /** Converts a canonical value to the representation of the target version. */
    public static String exportValue(String targetVersion, Rule rule, String canonical) {
        String value = canonical == null ? defaultValue(targetVersion, rule) : canonical.trim();
        if (!registryFormat(targetVersion)) return value;
        if (rule.id().equals("doFireTick")) {
            Fact fact = fact(targetVersion, rule);
            return "true".equalsIgnoreCase(value) ? (fact == null ? "128" : fact.defaultValue()) : "0";
        }
        if (rule.inverted()) {
            if ("true".equalsIgnoreCase(value)) return "false";
            if ("false".equalsIgnoreCase(value)) return "true";
        }
        return value;
    }

    /** Human readable first version of a rule for tooltips, e.g. "1.19.3" or "26.1". */
    public static String sinceLabel(Rule rule) {
        String best = null;
        int bestVersion = Integer.MAX_VALUE;
        for (TargetMinecraftVersion.Entry entry : TargetMinecraftVersion.FINAL_RELEASES) {
            int dataVersion = entry.effectiveDataVersion();
            if (dataVersion >= rule.since() && dataVersion < bestVersion) {
                bestVersion = dataVersion;
                best = entry.name();
            }
        }
        return best == null ? "?" : best;
    }

    public static String categoryKey(Category category) {
        return "worldbinder.gamerules.category." + category.ordinal();
    }

    public static String categoryName(Category category) {
        return category.name().toLowerCase(Locale.ROOT);
    }
}

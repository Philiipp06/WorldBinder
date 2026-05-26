package net.worldbinder.export.common;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.worldbinder.WorldBinder;
import net.worldbinder.export.VanillaWorldExporter;
import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.api.WorldExportModule;
import net.worldbinder.export.family.WorldExportModules;
import net.worldbinder.version.VersionProfile;
import net.worldbinder.version.VersionRegistry;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.storage.StorageStage;
import net.worldbinder.util.BlockStateStrings;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class WorldExportEngine {
    private static final int SECTION_SIZE = 16 * 16 * 16;
    private static final int BIOME_SECTION_SIZE = 4 * 4 * 4;
    private static final int LIGHT_ARRAY_SIZE = 2048;
    private static final String DEFAULT_BIOME = "minecraft:plains";
    private static final Map<String, String> VANILLA_GAME_RULE_KEYS = vanillaGameRuleKeys();
    private static final Map<String, GameRule26Mapping> GAME_RULE_26X_REGISTRY_KEYS = gameRule26xRegistryKeys();

    private WorldExportEngine() {
    }

    public static VanillaWorldExporter.ExportResult export(WorldScene scene, Path worldFolder) throws IOException {
        return export(scene, worldFolder, null);
    }

    public static VanillaWorldExporter.ExportResult export(WorldScene scene, Path worldFolder, StorageProgress progress) throws IOException {
        Files.createDirectories(worldFolder);
        VersionProfile profile = VersionRegistry.resolve(targetVersion(scene));
        WorldExportModule module = WorldExportModules.forProfile(profile);
        ExportContext context = new ExportContext(scene, worldFolder, profile);
        if (profile.writesGameRulesFile() || profile.writesWorldGenSettingsFile()) {
            Files.createDirectories(worldFolder.resolve("data").resolve("minecraft"));
        }

        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.string("worldbinder.export.progress.collecting_chunks"), 0.20D);
        Map<ChunkKey, ChunkBuilder> chunks = collectChunks(scene);
        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.string("worldbinder.export.progress.writing_level"), 0.26D);
        writeLevelDat(scene, worldFolder);
        if (profile.writesWorldGenSettingsFile()) {
            writeWorldGenSettingsFile(scene, worldFolder);
        }
        writeGameRulesFile(scene, worldFolder);
        writePlayerDataFiles(scene, worldFolder, profile);
        writeSessionLock(worldFolder);
        writeBukkitCompatibilityFiles(scene, worldFolder);
        writeIconPlaceholder(worldFolder);

        List<ExportLayoutSpec> layouts = module.layouts(context);
        int layoutIndex = 0;
        for (ExportLayoutSpec layout : layouts) {
            layoutIndex++;
            writeLayout(scene, chunks, layout, progress, layoutIndex, layouts.size());
        }

        writeWorldBinderInfo(scene, worldFolder, chunks.size());
        writeServerImportNotes(scene, worldFolder);
        return new VanillaWorldExporter.ExportResult(worldFolder, chunks.size(), scene.blockCount(), scene.blockEntityCount(), scene.entityCount());
    }

    public static Path overworldFolder(Path worldFolder) {
        return worldFolder;
    }

    private static void writeLayout(WorldScene scene, Map<ChunkKey, ChunkBuilder> chunks, ExportLayoutSpec layout,
                                    StorageProgress progress, int layoutIndex, int layoutCount) throws IOException {
        Files.createDirectories(layout.dimensionFolder());
        Files.createDirectories(layout.regionFolder());
        if (layout.writeEntities() && layout.entityFolder() != null) {
            Files.createDirectories(layout.entityFolder());
        }
        if (layout.writePoi() && layout.poiFolder() != null) {
            Files.createDirectories(layout.poiFolder());
        }

        RegionStorageInfo regionKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "chunk");
        RegionStorageInfo entityKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "entities");
        RegionStorageInfo poiKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "poi");

        RegionWriter entityWriter = null;
        RegionWriter poiWriter = null;
        try (RegionWriter regionWriter = new RegionWriter(regionKey, layout.regionFolder())) {
            if (layout.writeEntities() && layout.entityFolder() != null) {
                entityWriter = new RegionWriter(entityKey, layout.entityFolder());
            }
            if (layout.writePoi() && layout.poiFolder() != null) {
                poiWriter = new RegionWriter(poiKey, layout.poiFolder());
            }

            int written = 0;
            int total = Math.max(1, chunks.size());
            for (ChunkBuilder chunk : chunks.values()) {
                regionWriter.write(chunk.pos(), chunk.toChunkNbt(scene, layout.embedEntitiesInChunks()));
                if (entityWriter != null) {
                    entityWriter.write(chunk.pos(), chunk.toEntitiesNbt(scene));
                }
                if (poiWriter != null) {
                    poiWriter.write(chunk.pos(), chunk.toPoiNbt(scene));
                }
                written++;
                if (progress != null && (written == total || written % 16 == 0)) {
                    double ratio = written / (double) total;
                    double base = 0.30D + ((layoutIndex - 1) / (double) Math.max(1, layoutCount)) * 0.36D;
                    double span = 0.36D / Math.max(1, layoutCount);
                    progress.update(StorageStage.VANILLA_WORLD,
                            Lang.string("worldbinder.export.progress.writing_chunks", written, total, layout.label()),
                            base + ratio * span);
                }
            }
        } finally {
            if (entityWriter != null) {
                entityWriter.close();
            }
            if (poiWriter != null) {
                poiWriter.close();
            }
        }
    }

    
    private static Map<ChunkKey, ChunkBuilder> collectChunks(WorldScene scene) {
        Map<ChunkKey, ChunkBuilder> chunks = new TreeMap<>();
        if (scene.blocks != null) {
            targetVersionHolder.set(targetVersion(scene));
            try {
                for (BlockRecord record : scene.blocks) {
                    int absX = scene.originX + record.x;
                    int absY = scene.originY + record.y;
                    int absZ = scene.originZ + record.z;
                    ChunkKey key = new ChunkKey(Math.floorDiv(absX, 16), Math.floorDiv(absZ, 16));
                    chunks.computeIfAbsent(key, ChunkBuilder::new).addBlock(absX, absY, absZ, record);
                }
            } finally {
                targetVersionHolder.remove();
            }
        }
        if (scene.entities != null) {
            for (EntityRecord record : scene.entities) {
                if (record == null) {
                    continue;
                }
                double absX = scene.originX + record.x;
                double absZ = scene.originZ + record.z;
                int chunkX = Math.floorDiv((int) Math.floor(absX), 16);
                int chunkZ = Math.floorDiv((int) Math.floor(absZ), 16);
                ChunkKey key = new ChunkKey(chunkX, chunkZ);
                chunks.computeIfAbsent(key, ChunkBuilder::new).addEntity(record);
            }
        }
        addSnapshotFallbackChunks(scene, chunks);
        return chunks;
    }


    private static void addSnapshotFallbackChunks(WorldScene scene, Map<ChunkKey, ChunkBuilder> chunks) {
        if (scene.chunkSnapshots == null || scene.chunkSnapshots.isEmpty()) {
            return;
        }
        for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
            if (snapshot == null) {
                continue;
            }
            ChunkKey key = new ChunkKey(snapshot.chunkX, snapshot.chunkZ);
            ChunkBuilder builder = chunks.computeIfAbsent(key, ChunkBuilder::new);
            if (builder.hasBlockSections()) {
                continue;
            }
            int added = 0;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = z * 16 + x;
                    if (snapshot.heights == null || index >= snapshot.heights.length || snapshot.heights[index] == Integer.MIN_VALUE) {
                        continue;
                    }
                    int y = snapshot.heights[index];
                    String state = snapshot.states != null && index < snapshot.states.length && snapshot.states[index] != null && !snapshot.states[index].isBlank()
                            ? snapshot.states[index]
                            : "minecraft:stone";
                    builder.addSyntheticBlock((snapshot.chunkX << 4) + x, y, (snapshot.chunkZ << 4) + z, state);
                    added++;
                }
            }
            if (added == 0 && snapshot.hasSnapshot) {
                builder.addSyntheticBlock((snapshot.chunkX << 4) + 8, Math.max(-64, scene.originY), (snapshot.chunkZ << 4) + 8, "minecraft:stone");
            }
        }
    }
    private static void writeLevelDat(WorldScene scene, Path worldFolder) throws IOException {
        CompoundTag data = new CompoundTag();
        TargetMinecraftVersion.Entry targetVersion = targetVersion(scene);
        data.putInt("DataVersion", targetVersion.effectiveDataVersion());
        data.putString("LevelName", scene.name == null || scene.name.isBlank() ? "WorldBinder Export" : scene.name);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putLong("Time", 0L);
        data.putLong("DayTime", 6000L);
        data.putInt("version", 19133);
        data.putBoolean("initialized", true);
        data.putBoolean("allowCommands", true);
        data.putBoolean("WasModded", true);
        data.putBoolean("hardcore", false);
        data.putByte("Difficulty", (byte) 2);
        data.putBoolean("DifficultyLocked", false);
        data.putInt("GameType", 1);
        SpawnPoint spawn = resolveSpawnPoint(scene);
        UUID singleplayerUuid = exportPlayerUuid(scene);
        data.put("singleplayer_uuid", uuidIntArray(singleplayerUuid));
        data.putInt("SpawnX", spawn.blockX());
        data.putInt("SpawnY", spawn.blockY());
        data.putInt("SpawnZ", spawn.blockZ());
        data.putFloat("SpawnAngle", spawn.yaw());
        data.put("spawn", spawnCompound(scene, worldFolder, spawn, targetVersion));
        data.put("difficulty_settings", difficultySettings());
        data.putInt("clearWeatherTime", 0);
        data.putInt("rainTime", 0);
        data.putInt("thunderTime", 0);
        data.putBoolean("raining", false);
        data.putBoolean("thundering", false);
        data.put("GameRules", gameRules(scene));
        data.put("WorldGenSettings", worldGenSettings(scene));
        data.put("DragonFight", new CompoundTag());
        data.put("Version", versionInfo(targetVersion));
        data.put("DataPacks", dataPacks());
        data.put("ServerBrands", singleStringList("WorldBinder"));
        data.put("Player", playerNbt(scene));

        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, worldFolder.resolve("level.dat"));
    }


    private static void writeWorldGenSettingsFile(WorldScene scene, Path worldFolder) throws IOException {
        CompoundTag settings = worldGenSettings(scene);
        CompoundTag savedData = new CompoundTag();
        savedData.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
        savedData.put("data", settings);

        writeWorldGenSettingsFile(worldFolder.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat"), savedData);
    }

    private static void writeWorldGenSettingsFile(Path target, CompoundTag savedData) throws IOException {
        Files.createDirectories(target.getParent());
        NbtIo.writeCompressed(savedData, target);
    }

    private static void writeGameRulesFile(WorldScene scene, Path worldFolder) throws IOException {
        TargetMinecraftVersion.Entry target = targetVersion(scene);
        if (!target.usesGameRulesFile()) {
            return;
        }
        CompoundTag data = new CompoundTag();
        CompoundTag exported = gameRules(scene);
        for (String key : exported.keySet()) {
            String legacyKey = canonicalGameRuleKey(key);
            if (legacyKey == null) {
                continue;
            }
            String value = exported.getString(key).orElse("false");
            GameRuleExport gameRule = exportGameRule(target, legacyKey, value);
            if (gameRule == null) {
                continue;
            }
            putTypedGameRule(data, gameRule.key(), gameRule.value());
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", target.effectiveDataVersion());
        root.put("data", data);
        Path targetFile = worldFolder.resolve("data").resolve("minecraft").resolve("game_rules.dat");
        Files.createDirectories(targetFile.getParent());
        NbtIo.writeCompressed(root, targetFile);
    }

    private static void putTypedGameRule(CompoundTag tag, String key, String value) {
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

    private static GameRuleExport exportGameRule(TargetMinecraftVersion.Entry target, String legacyKey, String value) {
        if (target == null || target.profile() != TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return new GameRuleExport(namespacedSnakeGameRule(legacyKey), value);
        }
        GameRule26Mapping mapping = GAME_RULE_26X_REGISTRY_KEYS.get(legacyKey);
        if (mapping == null) {
            return null;
        }
        return new GameRuleExport(mapping.registryKey(), mapping.transformValue(value));
    }

    private static Map<String, GameRule26Mapping> gameRule26xRegistryKeys() {
        Map<String, GameRule26Mapping> keys = new LinkedHashMap<>();
        register26GameRule(keys, "doDaylightCycle", "minecraft:advance_time");
        register26GameRule(keys, "doWeatherCycle", "minecraft:advance_weather");
        register26GameRule(keys, "doTileDrops", "minecraft:block_drops");
        register26GameRule(keys, "doEntityDrops", "minecraft:entity_drops");
        register26GameRule(keys, "doMobLoot", "minecraft:mob_drops");
        register26GameRule(keys, "doMobSpawning", "minecraft:spawn_mobs");
        register26GameRule(keys, "doInsomnia", "minecraft:spawn_phantoms");
        register26GameRule(keys, "doPatrolSpawning", "minecraft:spawn_patrols");
        register26GameRule(keys, "doTraderSpawning", "minecraft:spawn_wandering_traders");
        register26GameRule(keys, "doWardenSpawning", "minecraft:spawn_wardens");
        register26GameRule(keys, "spawnRadius", "minecraft:respawn_radius");
        register26GameRule(keys, "naturalRegeneration", "minecraft:natural_health_regeneration");
        register26InvertedGameRule(keys, "disableRaids", "minecraft:raids");
        register26InvertedGameRule(keys, "disableElytraMovementCheck", "minecraft:elytra_movement_check");
        return keys;
    }

    private static void register26GameRule(Map<String, GameRule26Mapping> keys, String legacyKey, String registryKey) {
        keys.put(legacyKey, new GameRule26Mapping(registryKey, false));
    }

    private static void register26InvertedGameRule(Map<String, GameRule26Mapping> keys, String legacyKey, String registryKey) {
        keys.put(legacyKey, new GameRule26Mapping(registryKey, true));
    }

    private record GameRuleExport(String key, String value) {
    }

    private record GameRule26Mapping(String registryKey, boolean inverted) {
        private String transformValue(String value) {
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

    private static String namespacedSnakeGameRule(String key) {
        if (key == null || key.isBlank()) {
            return "minecraft:unknown";
        }
        String raw = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        StringBuilder result = new StringBuilder("minecraft:");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c == '-' ? '_' : c);
            }
        }
        return result.toString();
    }

    private static CompoundTag gameRules(WorldScene scene) {
        CompoundTag rules = defaultGameRules();
        if (scene.gameRulesNbt != null && !scene.gameRulesNbt.isBlank()) {
            String raw = scene.gameRulesNbt.trim();
            if (raw.contains("=") && !raw.startsWith("{")) {
                for (String part : raw.split(";")) {
                    int split = part.indexOf('=');
                    if (split <= 0 || split >= part.length() - 1) continue;
                    String key = canonicalGameRuleKey(part.substring(0, split).trim());
                    String value = part.substring(split + 1).trim();
                    if (key != null && !value.isBlank()) {
                        rules.putString(key, value);
                    }
                }
            } else {
                try {
                    CompoundTag overrides = TagParser.parseCompoundFully(raw);
                    for (String key : overrides.keySet()) {
                        String value = overrides.getString(key).orElse(null);
                        if (value == null && overrides.get(key) != null) {
                            value = overrides.get(key).toString().replace("\"", "");
                        }
                        String legacyKey = canonicalGameRuleKey(key);
                        if (legacyKey != null && value != null && !value.isBlank()) {
                            rules.putString(legacyKey, value);
                        }
                    }
                } catch (CommandSyntaxException exception) {
                    WorldBinder.LOGGER.warn("Failed to parse captured game rules. Falling back to safe defaults.", exception);
                }
            }
        }
        return rules;
    }


    private static String canonicalGameRuleKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String cleaned = key.trim();
        String direct = VANILLA_GAME_RULE_KEYS.get(cleaned);
        if (direct != null) {
            return direct;
        }
        direct = VANILLA_GAME_RULE_KEYS.get(cleaned.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        if (cleaned.contains(":")) {
            int split = cleaned.indexOf(':');
            String namespace = cleaned.substring(0, split);
            if (!"minecraft".equals(namespace)) {
                return null;
            }
            cleaned = cleaned.substring(split + 1);
        }
        direct = VANILLA_GAME_RULE_KEYS.get(cleaned);
        if (direct != null) {
            return direct;
        }
        return VANILLA_GAME_RULE_KEYS.get(cleaned.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> vanillaGameRuleKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        registerGameRule(keys, "announceAdvancements");
        registerGameRule(keys, "blockExplosionDropDecay");
        registerGameRule(keys, "commandBlockOutput");
        registerGameRule(keys, "commandModificationBlockLimit");
        registerGameRule(keys, "disableElytraMovementCheck");
        registerGameRule(keys, "disableRaids");
        registerGameRule(keys, "doDaylightCycle");
        registerGameRule(keys, "doEntityDrops");
        registerGameRule(keys, "doFireTick");
        registerGameRule(keys, "doImmediateRespawn");
        registerGameRule(keys, "doInsomnia");
        registerGameRule(keys, "doLimitedCrafting");
        registerGameRule(keys, "doMobLoot");
        registerGameRule(keys, "doMobSpawning");
        registerGameRule(keys, "doPatrolSpawning");
        registerGameRule(keys, "doTileDrops");
        registerGameRule(keys, "doTraderSpawning");
        registerGameRule(keys, "doVinesSpread");
        registerGameRule(keys, "doWardenSpawning");
        registerGameRule(keys, "doWeatherCycle");
        registerGameRule(keys, "drowningDamage");
        registerGameRule(keys, "enderPearlsVanishOnDeath");
        registerGameRule(keys, "fallDamage");
        registerGameRule(keys, "fireDamage");
        registerGameRule(keys, "forgiveDeadPlayers");
        registerGameRule(keys, "globalSoundEvents");
        registerGameRule(keys, "keepInventory");
        registerGameRule(keys, "lavaSourceConversion");
        registerGameRule(keys, "locatorBar");
        registerGameRule(keys, "logAdminCommands");
        registerGameRule(keys, "maxCommandChainLength");
        registerGameRule(keys, "maxEntityCramming");
        registerGameRule(keys, "mobExplosionDropDecay");
        registerGameRule(keys, "mobGriefing");
        registerGameRule(keys, "naturalRegeneration");
        registerGameRule(keys, "playersSleepingPercentage");
        registerGameRule(keys, "projectilesCanBreakBlocks");
        registerGameRule(keys, "randomTickSpeed");
        registerGameRule(keys, "reducedDebugInfo");
        registerGameRule(keys, "sendCommandFeedback");
        registerGameRule(keys, "showDeathMessages");
        registerGameRule(keys, "snowAccumulationHeight");
        registerGameRule(keys, "spawnChunkRadius");
        registerGameRule(keys, "spawnRadius");
        registerGameRule(keys, "spectatorsGenerateChunks");
        registerGameRule(keys, "tntExplosionDropDecay");
        registerGameRule(keys, "universalAnger");
        registerGameRule(keys, "waterSourceConversion");
        return keys;
    }

    private static void registerGameRule(Map<String, String> keys, String legacyKey) {
        keys.put(legacyKey, legacyKey);
        keys.put(legacyKey.toLowerCase(Locale.ROOT), legacyKey);
        String namespaced = namespacedSnakeGameRule(legacyKey);
        keys.put(namespaced, legacyKey);
        keys.put(namespaced.substring("minecraft:".length()), legacyKey);
    }

    private static CompoundTag defaultGameRules() {
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

    private static CompoundTag worldGenSettings(WorldScene scene) {
        CompoundTag settings = new CompoundTag();
        settings.putLong("seed", 0L);
        settings.putBoolean("generate_features", false);
        settings.putBoolean("bonus_chest", false);
        settings.putString("worldbinder_target_version", targetVersion(scene).name());
        settings.putString("worldbinder_generation_profile", targetVersion(scene).profile().name());

        CompoundTag dimensions = new CompoundTag();
        CompoundTag overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        overworld.put("generator", flatGenerator());
        dimensions.put("minecraft:overworld", overworld);
        settings.put("dimensions", dimensions);
        return settings;
    }

    private static CompoundTag flatGenerator() {
        CompoundTag generator = new CompoundTag();
        generator.putString("type", "minecraft:flat");
        CompoundTag settingsTag = new CompoundTag();
        settingsTag.putString("biome", DEFAULT_BIOME);
        ListTag layers = new ListTag();
        CompoundTag airLayer = new CompoundTag();
        airLayer.putInt("height", 1);
        airLayer.putString("block", "minecraft:air");
        layers.add(airLayer);
        settingsTag.put("layers", layers);
        settingsTag.put("structure_overrides", new ListTag());
        settingsTag.putBoolean("features", false);
        settingsTag.putBoolean("lakes", false);
        generator.put("settings", settingsTag);
        return generator;
    }

    private static CompoundTag versionInfo(TargetMinecraftVersion.Entry targetVersion) {
        CompoundTag version = new CompoundTag();
        version.putString("Name", targetVersion.name());
        version.putInt("Id", targetVersion.effectiveDataVersion());
        version.putBoolean("Snapshot", false);
        version.putString("Series", SharedConstants.getCurrentVersion().dataVersion().series());
        return version;
    }

    private static CompoundTag dataPacks() {
        CompoundTag dataPacks = new CompoundTag();
        dataPacks.put("Enabled", singleStringList("vanilla"));
        dataPacks.put("Disabled", new ListTag());
        return dataPacks;
    }

    private static CompoundTag spawnCompound(WorldScene scene, Path worldFolder, SpawnPoint spawn, TargetMinecraftVersion.Entry target) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", new IntArrayTag(new int[]{spawn.blockX(), spawn.blockY(), spawn.blockZ()}));
        tag.putString("dimension", spawnDimensionKey(scene, worldFolder, target));
        tag.putFloat("yaw", spawn.yaw());
        tag.putFloat("pitch", spawn.pitch());
        return tag;
    }

    private static String spawnDimensionKey(WorldScene scene, Path worldFolder, TargetMinecraftVersion.Entry target) {
        if (target == null || target.profile() != TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return "minecraft:overworld";
        }
        List<String> keys = ExportPathUtil.serverImportKeys(scene, worldFolder);
        if (keys.isEmpty()) {
            return "minecraft:overworld";
        }
        return "minecraft:" + keys.get(0);
    }

    private static CompoundTag difficultySettings() {
        CompoundTag tag = new CompoundTag();
        tag.putString("difficulty", "normal");
        tag.putBoolean("hardcore", false);
        tag.putBoolean("locked", false);
        return tag;
    }

    private static CompoundTag playerNbt(WorldScene scene) {
        SpawnPoint spawn = resolveSpawnPoint(scene);
        CompoundTag player = new CompoundTag();
        UUID uuid = exportPlayerUuid(scene);
        player.put("UUID", uuidIntArray(uuid));
        player.putLong("UUIDMost", uuid.getMostSignificantBits());
        player.putLong("UUIDLeast", uuid.getLeastSignificantBits());
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(spawn.x()));
        pos.add(DoubleTag.valueOf(spawn.y()));
        pos.add(DoubleTag.valueOf(spawn.z()));
        player.put("Pos", pos);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(spawn.yaw()));
        rotation.add(FloatTag.valueOf(spawn.pitch()));
        player.put("Rotation", rotation);
        player.putInt("playerGameType", 1);
        player.putShort("Health", (short) 20);
        player.putFloat("foodLevel", 20.0F);
        player.put("Inventory", new ListTag());
        return player;
    }

    private static SpawnPoint resolveSpawnPoint(WorldScene scene) {
        if (scene != null && scene.hasPlayerSpawn) {
            double x = finiteOr(scene.playerSpawnX, scene.originX + 0.5D);
            double y = finiteOr(scene.playerSpawnY, Math.max(-64, scene.originY + 2.0D));
            double z = finiteOr(scene.playerSpawnZ, scene.originZ + 0.5D);
            return new SpawnPoint(x, y, z, scene.playerSpawnYaw, scene.playerSpawnPitch);
        }

        BlockRecord fallback = nearestCapturedBlock(scene);
        if (fallback != null) {
            double x = scene.originX + fallback.x + 0.5D;
            double y = scene.originY + fallback.y + 1.5D;
            double z = scene.originZ + fallback.z + 0.5D;
            return new SpawnPoint(x, Math.max(-64.0D, y), z, 0.0F, 0.0F);
        }

        int originX = scene == null ? 0 : scene.originX;
        int originY = scene == null ? 64 : scene.originY;
        int originZ = scene == null ? 0 : scene.originZ;
        return new SpawnPoint(originX + 0.5D, Math.max(-64.0D, originY + 2.0D), originZ + 0.5D, 0.0F, 0.0F);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static BlockRecord nearestCapturedBlock(WorldScene scene) {
        if (scene == null || scene.blocks == null || scene.blocks.isEmpty()) {
            return null;
        }
        BlockRecord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockRecord block : scene.blocks) {
            if (block == null || block.state == null || block.state.contains("minecraft:air")) {
                continue;
            }
            long dx = block.x;
            long dz = block.z;
            long distance = dx * dx + dz * dz;
            if (best == null || distance < bestDistance || (distance == bestDistance && block.y > best.y)) {
                best = block;
                bestDistance = distance;
            }
        }
        return best;
    }


    private static void writePlayerDataFiles(WorldScene scene, Path worldFolder, VersionProfile profile) throws IOException {
        CompoundTag player = playerNbt(scene);
        UUID uuid = exportPlayerUuid(scene);
        if (profile != null && profile.writesModernDimensionFolders()) {
            writePlayerFile(worldFolder.resolve("players").resolve("data").resolve(uuid + ".dat"), player);
        }
        writePlayerFile(worldFolder.resolve("playerdata").resolve(uuid + ".dat"), player);
    }

    private static void writePlayerFile(Path target, CompoundTag player) throws IOException {
        Files.createDirectories(target.getParent());
        NbtIo.writeCompressed(player, target);
    }

    private static UUID exportPlayerUuid(WorldScene scene) {
        String seed = scene == null || scene.name == null || scene.name.isBlank() ? "WorldBinder Export" : scene.name;
        return UUID.nameUUIDFromBytes(("WorldBinder:player:" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static IntArrayTag uuidIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new IntArrayTag(new int[]{
                (int) (most >> 32),
                (int) most,
                (int) (least >> 32),
                (int) least
        });
    }

    private static ListTag singleStringList(String value) {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(value));
        return list;
    }

    private static void writeSessionLock(Path worldFolder) throws IOException {
        Files.writeString(worldFolder.resolve("session.lock"), Long.toString(System.currentTimeMillis()));
    }

    private static void writeBukkitCompatibilityFiles(WorldScene scene, Path worldFolder) throws IOException {
        // Bukkit/Paper/Multiverse accept normal vanilla world folders, but writing uid.dat makes
        // server-side imports look closer to a world that was already prepared by Bukkit.
        Path uidFile = worldFolder.resolve("uid.dat");
        if (Files.exists(uidFile)) {
            return;
        }
        UUID uuid = UUID.nameUUIDFromBytes(("WorldBinder:" + (scene == null ? "world" : scene.name)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(uidFile))) {
            output.writeLong(uuid.getMostSignificantBits());
            output.writeLong(uuid.getLeastSignificantBits());
        }
    }

    private static void writeServerImportNotes(WorldScene scene, Path worldFolder) throws IOException {
        TargetMinecraftVersion.Entry target = targetVersion(scene);
        if (!target.usesModernDimensionFolders()) {
            return;
        }
        List<String> keys = ExportPathUtil.serverImportKeys(scene, worldFolder);
        String keyText = keys.isEmpty() ? "<world-name>" : String.join(", ", keys);
        Files.writeString(worldFolder.resolve("SERVER_IMPORT_NOTES.txt"),
                Lang.string("worldbinder.export.notes.server_import", keyText));
    }

    private static void writeIconPlaceholder(Path worldFolder) throws IOException {
        // Keep this intentionally empty for now. Minecraft does not require icon.png.
    }

    private static void writeWorldBinderInfo(WorldScene scene, Path worldFolder, int chunkCount) throws IOException {
        Files.writeString(worldFolder.resolve("WORLD_BINDER_VANILLA_EXPORT.txt"),
                Lang.string("worldbinder.export.notes.vanilla_export",
                        Instant.now(),
                        scene.name,
                        scene.minecraftVersion,
                        targetVersion(scene).name(),
                        targetVersion(scene).profile().label(),
                        chunkCount,
                        scene.blockCount(),
                        scene.blockEntityCount(),
                        scene.entityCount()));
    }

    private static TargetMinecraftVersion.Entry targetVersion(WorldScene scene) {
        String target = scene == null ? null : scene.targetMinecraftVersion;
        return TargetMinecraftVersion.resolve(target);
    }

    private static String exportState(String state) {
        if (state == null || state.isBlank()) {
            return "minecraft:air";
        }
        TargetMinecraftVersion.Entry target = targetVersionHolder.get();
        String downgraded = target == null ? state : downgradeBlockState(state, target);
        return normalizeBlockStateForExport(downgraded);
    }

    private static String normalizeBlockStateForExport(String state) {
        if (state == null || state.isBlank()) {
            return "minecraft:air";
        }
        try {
            net.minecraft.world.level.block.state.BlockState parsed = BlockStateStrings.parse(state);
            return parsed == null ? state : BlockStateStrings.toCommandString(parsed);
        } catch (Throwable ignored) {
            int propertyStart = state.indexOf('[');
            return propertyStart > 0 ? state.substring(0, propertyStart) : "minecraft:air";
        }
    }

    private static String downgradeBlockState(String state, TargetMinecraftVersion.Entry target) {
        String name = parseState(state).name();
        int dataVersion = target.effectiveDataVersion();
        if (dataVersion < 3463 && (name.contains("cherry") || name.contains("bamboo_block") || name.contains("pink_petals") || name.contains("decorated_pot") || name.contains("suspicious_gravel"))) {
            return "minecraft:oak_planks";
        }
        if (dataVersion < 3105 && (name.contains("mangrove") || name.contains("mud") || name.contains("sculk") || name.contains("frog") || name.contains("ochre_froglight") || name.contains("verdant_froglight") || name.contains("pearlescent_froglight"))) {
            return "minecraft:dirt";
        }
        if (dataVersion < 2724 && (name.contains("deepslate") || name.contains("tuff") || name.contains("calcite") || name.contains("amethyst") || name.contains("copper") || name.contains("dripstone") || name.contains("azalea") || name.contains("moss") || name.contains("candle"))) {
            return name.contains("ore") ? "minecraft:stone" : "minecraft:cobblestone";
        }
        if (dataVersion < 2566 && (name.contains("crimson") || name.contains("warped") || name.contains("basalt") || name.contains("blackstone") || name.contains("soul_soil") || name.contains("netherite") || name.contains("ancient_debris") || name.contains("shroomlight"))) {
            return "minecraft:netherrack";
        }
        return state;
    }

    private static final ThreadLocal<TargetMinecraftVersion.Entry> targetVersionHolder = new ThreadLocal<>();

    private record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
        private int blockX() {
            return (int) Math.floor(x);
        }

        private int blockY() {
            return (int) Math.floor(y);
        }

        private int blockZ() {
            return (int) Math.floor(z);
        }
    }

    private record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
        @Override
        public int compareTo(ChunkKey other) {
            int byX = Integer.compare(x, other.x);
            return byX != 0 ? byX : Integer.compare(z, other.z);
        }
    }

    private static final class ChunkBuilder {
        private final ChunkKey key;
        private final Map<Integer, SectionBuilder> sections = new TreeMap<>();
        private final List<CompoundTag> blockEntities = new ArrayList<>();
        private final List<EntityRecord> entities = new ArrayList<>();
        private final int[] heightmap = new int[16 * 16];

        private ChunkBuilder(ChunkKey key) {
            this.key = key;
            for (int i = 0; i < heightmap.length; i++) {
                heightmap[i] = 0;
            }
        }

        private ChunkPos pos() {
            return new ChunkPos(key.x, key.z);
        }

        private boolean hasBlockSections() {
            return !sections.isEmpty();
        }

        private void addSyntheticBlock(int absX, int absY, int absZ, String state) {
            int sectionY = Math.floorDiv(absY, 16);
            int localX = Math.floorMod(absX, 16);
            int localY = Math.floorMod(absY, 16);
            int localZ = Math.floorMod(absZ, 16);
            sections.computeIfAbsent(sectionY, SectionBuilder::new).set(localX, localY, localZ, exportState(state));
            int hmIndex = localZ * 16 + localX;
            heightmap[hmIndex] = Math.max(heightmap[hmIndex], Math.max(0, absY + 65));
        }

        private void addBlock(int absX, int absY, int absZ, BlockRecord record) {
            int sectionY = Math.floorDiv(absY, 16);
            int localX = Math.floorMod(absX, 16);
            int localY = Math.floorMod(absY, 16);
            int localZ = Math.floorMod(absZ, 16);
            sections.computeIfAbsent(sectionY, SectionBuilder::new).set(localX, localY, localZ, exportState(record.state));
            int hmIndex = localZ * 16 + localX;
            heightmap[hmIndex] = Math.max(heightmap[hmIndex], Math.max(0, absY + 65));
            if (record.hasBlockEntity && record.blockEntityNbt != null && !record.blockEntityNbt.isBlank()) {
                CompoundTag blockEntity = parseCompound(record.blockEntityNbt);
                if (blockEntity != null) {
                    blockEntity.putInt("x", absX);
                    blockEntity.putInt("y", absY);
                    blockEntity.putInt("z", absZ);
                    blockEntity.putBoolean("keepPacked", false);
                    blockEntities.add(blockEntity);
                }
            }
        }

        private void addEntity(EntityRecord record) {
            entities.add(record);
            ensureChunkHasAtLeastOneSection();
        }

        private void ensureChunkHasAtLeastOneSection() {
            if (sections.isEmpty()) {
                sections.put(-4, new SectionBuilder(-4));
            }
        }

        private CompoundTag toChunkNbt(WorldScene scene, boolean includeEntities) {
            CompoundTag chunk = new CompoundTag();
            chunk.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            chunk.putInt("xPos", key.x);
            chunk.putInt("yPos", -4);
            chunk.putInt("zPos", key.z);
            chunk.putLong("LastUpdate", 0L);
            chunk.putLong("InhabitedTime", 0L);
            chunk.putString("Status", "minecraft:full");
            chunk.putBoolean("isLightOn", true);
            chunk.put("sections", sectionsNbt());
            chunk.put("block_entities", blockEntitiesNbt());
            chunk.put("Heightmaps", heightmapsNbt());
            chunk.put("block_ticks", new ListTag());
            chunk.put("fluid_ticks", new ListTag());
            chunk.put("PostProcessing", new ListTag());
            chunk.put("structures", structuresNbt());
            if (includeEntities) {
                chunk.put("Entities", entityListNbt(scene));
            }
            return chunk;
        }

        private ListTag sectionsNbt() {
            ListTag list = new ListTag();
            for (SectionBuilder section : sections.values()) {
                list.add(section.toNbt());
            }
            return list;
        }

        private ListTag blockEntitiesNbt() {
            ListTag list = new ListTag();
            for (CompoundTag blockEntity : blockEntities) {
                list.add(blockEntity);
            }
            return list;
        }

        private CompoundTag heightmapsNbt() {
            long[] packed = packValues(heightmap, 9);
            CompoundTag heightmaps = new CompoundTag();
            heightmaps.putLongArray("MOTION_BLOCKING", packed);
            heightmaps.putLongArray("WORLD_SURFACE", packed);
            return heightmaps;
        }

        private CompoundTag structuresNbt() {
            CompoundTag structures = new CompoundTag();
            structures.put("starts", new CompoundTag());
            structures.put("References", new CompoundTag());
            return structures;
        }

        private CompoundTag toEntitiesNbt(WorldScene scene) {
            CompoundTag root = new CompoundTag();
            root.put("Entities", entityListNbt(scene));
            root.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            root.put("Position", new IntArrayTag(new int[]{key.x, key.z}));
            return root;
        }

        private ListTag entityListNbt(WorldScene scene) {
            ListTag list = new ListTag();
            for (EntityRecord entity : entities) {
                CompoundTag nbt = normalizedEntityNbt(scene, entity);
                list.add(nbt);
            }
            return list;
        }

        private CompoundTag toPoiNbt(WorldScene scene) {
            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            root.put("Sections", new CompoundTag());
            return root;
        }
    }

    private static final class SectionBuilder {
        private final int sectionY;
        private final String[] states = new String[SECTION_SIZE];

        private SectionBuilder(int sectionY) {
            this.sectionY = sectionY;
        }

        private void set(int x, int y, int z, String state) {
            states[index(x, y, z)] = state == null || state.isBlank() ? "minecraft:air" : state;
        }

        private CompoundTag toNbt() {
            CompoundTag section = new CompoundTag();
            section.putByte("Y", (byte) sectionY);
            section.put("block_states", blockStatesNbt());
            section.put("biomes", biomesNbt());
            section.putByteArray("SkyLight", filledLight((byte) 0xFF));
            section.putByteArray("BlockLight", filledLight((byte) 0x00));
            return section;
        }

        private CompoundTag blockStatesNbt() {
            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            paletteIndex.put("minecraft:air", 0);
            int[] values = new int[SECTION_SIZE];
            for (int i = 0; i < SECTION_SIZE; i++) {
                String state = states[i] == null ? "minecraft:air" : states[i];
                Integer index = paletteIndex.get(state);
                if (index == null) {
                    index = paletteIndex.size();
                    paletteIndex.put(state, index);
                }
                values[i] = index;
            }

            CompoundTag blockStates = new CompoundTag();
            ListTag palette = new ListTag();
            for (String state : paletteIndex.keySet()) {
                palette.add(blockStateNbt(state));
            }
            blockStates.put("palette", palette);
            if (paletteIndex.size() > 1) {
                int bits = Math.max(4, bitsFor(paletteIndex.size() - 1));
                blockStates.putLongArray("data", packValues(values, bits));
            }
            return blockStates;
        }

        private CompoundTag biomesNbt() {
            CompoundTag biomes = new CompoundTag();
            ListTag palette = new ListTag();
            palette.add(StringTag.valueOf(DEFAULT_BIOME));
            biomes.put("palette", palette);
            return biomes;
        }

        private static int index(int x, int y, int z) {
            return (y << 8) | (z << 4) | x;
        }
    }

    private static CompoundTag blockStateNbt(String state) {
        ParsedState parsed = parseState(state);
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", parsed.name);
        if (!parsed.properties.isEmpty()) {
            CompoundTag properties = new CompoundTag();
            for (Map.Entry<String, String> entry : parsed.properties.entrySet()) {
                properties.putString(entry.getKey(), entry.getValue());
            }
            tag.put("Properties", properties);
        }
        return tag;
    }

    private static ParsedState parseState(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedState("minecraft:air", Map.of());
        }
        int start = raw.indexOf('[');
        if (start < 0 || !raw.endsWith("]")) {
            return new ParsedState(raw, Map.of());
        }
        String name = raw.substring(0, start);
        String body = raw.substring(start + 1, raw.length() - 1);
        Map<String, String> properties = new LinkedHashMap<>();
        if (!body.isBlank()) {
            for (String part : body.split(",")) {
                int equals = part.indexOf('=');
                if (equals > 0 && equals < part.length() - 1) {
                    properties.put(part.substring(0, equals), part.substring(equals + 1));
                }
            }
        }
        return new ParsedState(name, properties);
    }

    private record ParsedState(String name, Map<String, String> properties) {
    }

    private static CompoundTag parseCompound(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return null;
        }
        try {
            return TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException exception) {
            WorldBinder.LOGGER.warn("Failed to parse captured SNBT: {}", snbt, exception);
            return null;
        }
    }

    private static CompoundTag normalizedEntityNbt(WorldScene scene, EntityRecord entity) {
        CompoundTag nbt = parseCompound(entity.fullNbt);
        if (nbt == null) {
            nbt = new CompoundTag();
        }

        // EntityRecord stores coordinates relative to the capture origin so scenes can be moved.
        // Vanilla entity region files need absolute world coordinates inside the exported save.
        double absX = scene.originX + entity.x;
        double absY = scene.originY + entity.y;
        double absZ = scene.originZ + entity.z;

        nbt.putString("id", exportEntityType(scene, entity.type));

        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(absX));
        pos.add(DoubleTag.valueOf(absY));
        pos.add(DoubleTag.valueOf(absZ));
        nbt.put("Pos", pos);

        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(entity.yaw));
        rotation.add(FloatTag.valueOf(entity.pitch));
        nbt.put("Rotation", rotation);

        nbt.putBoolean("NoGravity", entity.noGravity);
        nbt.putBoolean("Glowing", entity.glowing);
        nbt.putBoolean("Invisible", entity.invisible);
        normalizeEntityForTarget(nbt, targetVersion(scene));
        return nbt;
    }

    private static void normalizeEntityForTarget(CompoundTag nbt, TargetMinecraftVersion.Entry target) {
        if (target.effectiveDataVersion() < 3837) {
            downgradeModernItemStack(nbt, "item");
            downgradeModernItemStack(nbt, "Item");
            downgradeItemStackList(nbt, "HandItems");
            downgradeItemStackList(nbt, "ArmorItems");
        }
    }

    private static void downgradeItemStackList(CompoundTag holder, String key) {
        if (!(holder.get(key) instanceof ListTag list)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag item) {
                CompoundTag wrapper = new CompoundTag();
                wrapper.put("value", item);
                downgradeModernItemStack(wrapper, "value");
            }
        }
    }

    private static void downgradeModernItemStack(CompoundTag holder, String key) {
        if (!(holder.get(key) instanceof CompoundTag item)) {
            return;
        }
        String id = item.getString("id").orElse("minecraft:air");
        item.putString("id", id);
        item.putByte("Count", (byte) readIntTag(item, "count", 1));
        item.remove("count");

        if (item.get("components") instanceof CompoundTag components) {
            CompoundTag tag = item.get("tag") instanceof CompoundTag existingTag ? existingTag : new CompoundTag();
            int damage = readIntTag(components, "minecraft:damage", Integer.MIN_VALUE);
            if (damage != Integer.MIN_VALUE) {
                tag.putInt("Damage", damage);
            }
            int customModelData = readIntTag(components, "minecraft:custom_model_data", Integer.MIN_VALUE);
            if (customModelData != Integer.MIN_VALUE) {
                tag.putInt("CustomModelData", customModelData);
            }
            String customName = readStringTag(components, "minecraft:custom_name");
            if (customName != null && !customName.isBlank()) {
                CompoundTag display = tag.get("display") instanceof CompoundTag existingDisplay ? existingDisplay : new CompoundTag();
                display.putString("Name", customName);
                tag.put("display", display);
            }
            if (!tag.keySet().isEmpty()) {
                item.put("tag", tag);
            }
            item.remove("components");
        }
    }

    private static int readIntTag(CompoundTag tag, String key, int fallback) {
        if (tag == null || tag.get(key) == null) {
            return fallback;
        }
        try {
            String raw = tag.get(key).toString().replace("\"", "");
            int suffix = raw.endsWith("b") || raw.endsWith("s") || raw.endsWith("l") || raw.endsWith("f") || raw.endsWith("d") ? 1 : 0;
            return Integer.parseInt(suffix == 1 ? raw.substring(0, raw.length() - 1) : raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readStringTag(CompoundTag tag, String key) {
        return tag == null ? null : tag.getString(key).orElse(null);
    }

    private static String exportEntityType(WorldScene scene, String type) {
        String id = type == null || type.isBlank() ? "minecraft:marker" : type;
        int dataVersion = targetVersion(scene).effectiveDataVersion();
        if (dataVersion < 3337 && (id.equals("minecraft:block_display") || id.equals("minecraft:item_display") || id.equals("minecraft:text_display") || id.equals("minecraft:interaction"))) {
            return "minecraft:armor_stand";
        }
        if (dataVersion < 3463 && (id.equals("minecraft:camel") || id.equals("minecraft:sniffer"))) {
            return "minecraft:pig";
        }
        if (dataVersion < 3953 && (id.equals("minecraft:bogged") || id.equals("minecraft:breeze") || id.equals("minecraft:wind_charge") || id.equals("minecraft:ominous_item_spawner"))) {
            return "minecraft:marker";
        }
        return id;
    }

    private static int bitsFor(int maxValue) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(maxValue));
    }

    private static long[] packValues(int[] values, int bitsPerValue) {
        int valuesPerLong = Math.max(1, 64 / bitsPerValue);
        long mask = (1L << bitsPerValue) - 1L;
        long[] packed = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
        for (int i = 0; i < values.length; i++) {
            int longIndex = i / valuesPerLong;
            int bitIndex = (i % valuesPerLong) * bitsPerValue;
            packed[longIndex] |= ((long) values[i] & mask) << bitIndex;
        }
        return packed;
    }

    private static byte[] filledLight(byte value) {
        byte[] light = new byte[LIGHT_ARRAY_SIZE];
        for (int i = 0; i < light.length; i++) {
            light[i] = value;
        }
        return light;
    }

    private static final class RegionWriter implements AutoCloseable {
        private final RegionStorageInfo storageKey;
        private final Path directory;
        private final Map<Long, RegionFile> files = new HashMap<>();

        private RegionWriter(RegionStorageInfo storageKey, Path directory) throws IOException {
            this.storageKey = storageKey;
            this.directory = directory;
            Files.createDirectories(directory);
        }

        private void write(ChunkPos pos, CompoundTag nbt) throws IOException {
            RegionFile file = file(pos);
            try (DataOutputStream output = file.getChunkDataOutputStream(pos)) {
                NbtIo.write(nbt, output);
            }
        }

        private RegionFile file(ChunkPos pos) throws IOException {
            long key = ChunkPos.pack(pos.getRegionX(), pos.getRegionZ());
            RegionFile existing = files.get(key);
            if (existing != null) {
                return existing;
            }
            Path path = directory.resolve(String.format(Locale.ROOT, "r.%d.%d.mca", pos.getRegionX(), pos.getRegionZ()));
            RegionFile created = new RegionFile(storageKey, path, directory, false);
            files.put(key, created);
            return created;
        }

        @Override
        public void close() throws IOException {
            IOException first = null;
            for (RegionFile file : files.values()) {
                try {
                    file.close();
                } catch (IOException exception) {
                    if (first == null) {
                        first = exception;
                    } else {
                        first.addSuppressed(exception);
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        }
    }
}

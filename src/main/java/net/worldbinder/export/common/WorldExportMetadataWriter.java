package net.worldbinder.export.common;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.version.TargetMinecraftVersion;
import net.worldbinder.version.VersionProfile;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

final class WorldExportMetadataWriter {
    private static final String DEFAULT_BIOME = "minecraft:plains";

    private WorldExportMetadataWriter() {
    }

    static void write(WorldScene scene, Path worldFolder, VersionProfile profile) throws IOException {
        writeLevelDat(scene, worldFolder);
        if (profile.writesWorldGenSettingsFile()) {
            writeWorldGenSettingsFile(scene, worldFolder);
        }
        WorldExportGameRules.write(scene, worldFolder);
        writePlayerDataFiles(scene, worldFolder, profile);
        writeSessionLock(worldFolder);
        writeBukkitCompatibilityFiles(scene, worldFolder);
    }

    private static void writeLevelDat(WorldScene scene, Path worldFolder) throws IOException {
        CompoundTag data = new CompoundTag();
        TargetMinecraftVersion.Entry target = targetVersion(scene);
        data.putInt("DataVersion", target.effectiveDataVersion());
        data.putString("LevelName", scene.name == null || scene.name.isBlank() ? net.worldbinder.util.Lang.string("worldbinder.export.default_name") : scene.name);
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
        data.putInt("GameType", scene.exportSettings.includePlayer ? playerNbt(scene).getInt("playerGameType").orElse(0) : 1);

        SpawnPoint spawn = resolveSpawnPoint(scene);
        UUID playerUuid = exportPlayerUuid(scene);
        data.put("singleplayer_uuid", uuidIntArray(playerUuid));
        data.putInt("SpawnX", spawn.blockX());
        data.putInt("SpawnY", spawn.blockY());
        data.putInt("SpawnZ", spawn.blockZ());
        data.putFloat("SpawnAngle", spawn.yaw());
        data.put("spawn", spawnCompound(scene, worldFolder, spawn, target));
        data.put("difficulty_settings", difficultySettings());
        data.putInt("clearWeatherTime", 0);
        data.putInt("rainTime", 0);
        data.putInt("thunderTime", 0);
        data.putBoolean("raining", false);
        data.putBoolean("thundering", false);
        if (target.profile() != TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            data.put("GameRules", WorldExportGameRules.legacyRules(scene));
            data.put("WorldGenSettings", worldGenSettings(scene));
            data.put("DragonFight", new CompoundTag());
            data.put("Player", playerNbt(scene));
        }
        data.put("Version", versionInfo(target));
        data.put("DataPacks", dataPacks());
        data.put("ServerBrands", singleStringList("WorldBinder"));
        if (target.effectiveDataVersion() < 2566) {
            data.putLong("RandomSeed", scene.exportSettings.seedValue());
            data.putBoolean("MapFeatures", scene.exportSettings.generation == net.worldbinder.scene.ExportSettings.Generation.NORMAL);
            data.putString("generatorName", scene.exportSettings.generation == net.worldbinder.scene.ExportSettings.Generation.NORMAL ? "default" : "flat");
            data.putInt("generatorVersion", 1);
            if (scene.exportSettings.generation != net.worldbinder.scene.ExportSettings.Generation.NORMAL) {
                CompoundTag options = flatGenerator(scene).getCompound("settings").orElseThrow().copy();
                options.put("structures", new CompoundTag()); data.put("generatorOptions", options);
            }
        }

        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, worldFolder.resolve("level.dat"));
    }

    private static void writeWorldGenSettingsFile(WorldScene scene, Path worldFolder) throws IOException {
        CompoundTag savedData = new CompoundTag();
        savedData.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
        savedData.put("data", worldGenSettings(scene));
        Path target = worldFolder.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
        Files.createDirectories(target.getParent());
        NbtIo.writeCompressed(savedData, target);
    }

    private static CompoundTag worldGenSettings(WorldScene scene) {
        CompoundTag settings = new CompoundTag();
        settings.putLong("seed", scene.exportSettings.seedValue());
        settings.putBoolean("generate_structures", scene.exportSettings.generation == net.worldbinder.scene.ExportSettings.Generation.NORMAL);
        settings.putBoolean("bonus_chest", false);
        settings.putString("worldbinder_target_version", targetVersion(scene).name());
        settings.putString("worldbinder_generation_profile", targetVersion(scene).profile().name());

        CompoundTag dimensions = new CompoundTag();
        CompoundTag overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        if (scene.exportSettings.generation == net.worldbinder.scene.ExportSettings.Generation.NORMAL) {
            CompoundTag source = new CompoundTag();
            source.putString("type", "minecraft:multi_noise"); source.putString("preset", "minecraft:overworld");
            if (targetVersion(scene).effectiveDataVersion() < 2860) {
                source.putString("type", "minecraft:vanilla_layered"); source.remove("preset");
                source.putLong("seed", scene.exportSettings.seedValue()); source.putBoolean("large_biomes", false);
            }
            overworld = noiseDimension("minecraft:overworld", "minecraft:overworld", source);
            overworld.getCompound("generator").orElseThrow().putLong("seed", scene.exportSettings.seedValue());
        } else overworld.put("generator", flatGenerator(scene));
        dimensions.put("minecraft:overworld", overworld);
        // The vanilla dimension set must be complete; a missing Nether or End marks the world as using
        // experimental settings and shows the "unsupported experimental settings" warning on load.
        dimensions.put("minecraft:the_nether", noiseDimension("minecraft:the_nether", "minecraft:nether", netherBiomeSource()));
        dimensions.put("minecraft:the_end", noiseDimension("minecraft:the_end", "minecraft:end", endBiomeSource()));
        if (targetVersion(scene).effectiveDataVersion() < 2860) for (String key : dimensions.keySet()) {
            CompoundTag dimension = dimensions.getCompound(key).orElseThrow();
            CompoundTag generator = dimension.getCompound("generator").orElseThrow();
            if (generator.getString("type").orElse("").equals("minecraft:noise")) {
                generator.putLong("seed",scene.exportSettings.seedValue());
                generator.getCompound("biome_source").orElseThrow().putLong("seed",scene.exportSettings.seedValue());
            }
        }
        for(var scope:net.worldbinder.scene.ProjectDimensions.all(scene)){
        String dimension=net.worldbinder.scene.ProjectDimensions.canonical(scope.dimension);
        if(dimension!=null&&!dimensions.contains(dimension)){
            if(targetVersion(scene).effectiveDataVersion()<2566)throw dimensionError(dimension);
            String type=scope.dimensionType;
            CompoundTag stem;
            if(type!=null&&dimensions.contains(type))stem=dimensions.getCompound(type).orElseThrow().copy();
            else{
                if(scope.dimensionTypeNbt==null||!java.util.Objects.equals(scene.minecraftVersion,targetVersion(scene).name()))throw dimensionError(dimension);
                CompoundTag received=AuxiliaryDataWriter.parse(scope.dimensionTypeNbt);
                // Referenced registries must exist in vanilla; unknown modded timelines/attributes cannot be invented.
                var json=net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,received);
                if(containsForeignReference(json))throw dimensionError(dimension);
                stem=overworld.copy();stem.put("type",received);
            }
            dimensions.put(dimension,stem);
        }
        if (dimension!=null&&dimensions.contains(dimension)&&!dimension.equals("minecraft:overworld")
                && scene.exportSettings.generation != net.worldbinder.scene.ExportSettings.Generation.NORMAL) {
            CompoundTag generator=flatGenerator(scene);
            String type=scope.dimensionType==null?dimension:scope.dimensionType;
            generator.getCompound("settings").orElseThrow().putString("biome",type.equals("minecraft:the_end")?"minecraft:the_end":type.equals("minecraft:the_nether")?"minecraft:nether_wastes":"minecraft:plains");
            dimensions.getCompound(dimension).orElseThrow().put("generator",generator);
        }
        }
        settings.put("dimensions", dimensions);
        return settings;
    }
    private static net.worldbinder.validation.FindingException dimensionError(String dimension){return new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.dimensions.definition_unknown",dimension));}
    private static boolean containsForeignReference(com.google.gson.JsonElement json){
        if(json.isJsonPrimitive()&&json.getAsJsonPrimitive().isString()){
            String text=json.getAsString();return text.contains(":")&&!text.startsWith("minecraft:");
        }
        if(json.isJsonArray())for(var value:json.getAsJsonArray())if(containsForeignReference(value))return true;
        if(json.isJsonObject())for(var entry:json.getAsJsonObject().entrySet())if(entry.getKey().contains(":")&&!entry.getKey().startsWith("minecraft:")||containsForeignReference(entry.getValue()))return true;
        return false;
    }

    private static CompoundTag noiseDimension(String type, String noiseSettings, CompoundTag biomeSource) {
        CompoundTag dimension = new CompoundTag();
        dimension.putString("type", type);
        CompoundTag generator = new CompoundTag();
        generator.putString("type", "minecraft:noise");
        generator.putString("settings", noiseSettings);
        generator.put("biome_source", biomeSource);
        dimension.put("generator", generator);
        return dimension;
    }

    private static CompoundTag netherBiomeSource() {
        CompoundTag source = new CompoundTag();
        source.putString("type", "minecraft:multi_noise");
        source.putString("preset", "minecraft:nether");
        return source;
    }

    private static CompoundTag endBiomeSource() {
        CompoundTag source = new CompoundTag();
        source.putString("type", "minecraft:the_end");
        return source;
    }

    private static CompoundTag flatGenerator(WorldScene scene) {
        CompoundTag generator = new CompoundTag();
        generator.putString("type", "minecraft:flat");
        CompoundTag settings = new CompoundTag();
        settings.putString("biome", DEFAULT_BIOME);
        ListTag layers = new ListTag();
        CompoundTag airLayer = new CompoundTag();
        airLayer.putInt("height", 1);
        airLayer.putString("block", scene.exportSettings.generation == net.worldbinder.scene.ExportSettings.Generation.FLAT ? "minecraft:bedrock" : "minecraft:air");
        layers.add(airLayer);
        if (scene.exportSettings.generation == net.worldbinder.scene.ExportSettings.Generation.FLAT) {
            CompoundTag dirt = new CompoundTag(); dirt.putInt("height", 2); dirt.putString("block", "minecraft:dirt"); layers.add(dirt);
            CompoundTag grass = new CompoundTag(); grass.putInt("height", 1); grass.putString("block", "minecraft:grass_block"); layers.add(grass);
        }
        settings.put("layers", layers);
        settings.put("structure_overrides", new ListTag());
        settings.putBoolean("features", false);
        settings.putBoolean("lakes", false);
        if (targetVersion(scene).effectiveDataVersion() < 2860) {
            CompoundTag structures = new CompoundTag(); structures.put("structures", new CompoundTag()); settings.put("structures", structures);
        }
        generator.put("settings", settings);
        return generator;
    }

    private static CompoundTag versionInfo(TargetMinecraftVersion.Entry target) {
        CompoundTag version = new CompoundTag();
        version.putString("Name", target.name());
        version.putInt("Id", target.effectiveDataVersion());
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

    private static CompoundTag spawnCompound(WorldScene scene, Path worldFolder, SpawnPoint spawn,
                                             TargetMinecraftVersion.Entry target) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", new IntArrayTag(new int[]{spawn.blockX(), spawn.blockY(), spawn.blockZ()}));
        tag.putString("dimension", spawnDimensionKey(scene, worldFolder, target));
        tag.putFloat("yaw", spawn.yaw());
        tag.putFloat("pitch", spawn.pitch());
        return tag;
    }

    private static String spawnDimensionKey(WorldScene scene, Path worldFolder,
                                            TargetMinecraftVersion.Entry target) {
        if ("minecraft:the_nether".equals(scene.dimension) || "minecraft:the_end".equals(scene.dimension)) return scene.dimension;
        if (target == null || target.profile() != TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return "minecraft:overworld";
        }
        return "minecraft:overworld";
    }

    private static CompoundTag difficultySettings() {
        CompoundTag tag = new CompoundTag();
        tag.putString("difficulty", "normal");
        tag.putBoolean("hardcore", false);
        tag.putBoolean("locked", false);
        return tag;
    }

    private static CompoundTag playerNbt(WorldScene scene) {
        if (scene.exportSettings.includePlayer && scene.player != null && scene.player.nbt != null) {
            CompoundTag player = AuxiliaryDataWriter.parse(scene.player.nbt);
            int version = targetVersion(scene).effectiveDataVersion();
            player.putInt("DataVersion", version);
            player.putString("Dimension", "minecraft:the_nether".equals(scene.dimension) || "minecraft:the_end".equals(scene.dimension)
                    ? scene.dimension : "minecraft:overworld");
            if (scene.player.enderChest != null) player.put("EnderItems", ItemDataConverter.items(scene.player.enderChest, version,player.getList("EnderItems").orElseGet(ListTag::new)));
            else player.remove("EnderItems");
            if (version < 4325 && player.get("equipment") instanceof CompoundTag equipment) {
                ListTag inventory = player.getList("Inventory").orElseGet(ListTag::new);
                String[] armor = {"feet", "legs", "chest", "head", "offhand"};
                for (int i = 0; i < armor.length; i++) if (equipment.get(armor[i]) instanceof CompoundTag item) {
                    CompoundTag copy = item.copy(); copy.putByte("Slot", (byte) (i < 4 ? 100 + i : 150)); inventory.add(copy);
                }
                player.put("Inventory", inventory);
            }
            ItemDataConverter.inventoryLists(player, version);
            player.remove("ArmorItems"); player.remove("HandItems");
            player.put("UUID", uuidIntArray(exportPlayerUuid(scene)));
            if(version<2566){java.util.UUID uuid=exportPlayerUuid(scene);player.putLong("UUIDMost",uuid.getMostSignificantBits());player.putLong("UUIDLeast",uuid.getLeastSignificantBits());
                player.putInt("Dimension","minecraft:the_nether".equals(scene.dimension)?-1:"minecraft:the_end".equals(scene.dimension)?1:0);}
            return player;
        }
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
        player.putFloat("Health", 20.0F);
        player.putInt("foodLevel", 20);
        player.putString("Dimension", "minecraft:the_nether".equals(scene.dimension) || "minecraft:the_end".equals(scene.dimension) ? scene.dimension : "minecraft:overworld");
        player.put("Inventory", new ListTag());
        if(targetVersion(scene).effectiveDataVersion()<2566)player.putInt("Dimension","minecraft:the_nether".equals(scene.dimension)?-1:"minecraft:the_end".equals(scene.dimension)?1:0);
        return player;
    }

    private static SpawnPoint resolveSpawnPoint(WorldScene scene) {
        if (scene != null && scene.hasPlayerSpawn) {
            double x = finiteOr(scene.playerSpawnX, scene.originX + 0.5D);
            double y = finiteOr(scene.playerSpawnY, Math.max(64, scene.originY + 2.0D));
            double z = finiteOr(scene.playerSpawnZ, scene.originZ + 0.5D);
            SpawnPoint safe = nearestSafeCapturedSpawn(scene, x, y, z, scene.playerSpawnYaw, scene.playerSpawnPitch);
            if (safe != null) {
                return safe;
            }
            if (y > -60.0D) {
                return new SpawnPoint(x, y, z, scene.playerSpawnYaw, scene.playerSpawnPitch);
            }
        }

        BlockRecord fallback = nearestCapturedBlock(scene);
        if (fallback != null) {
            double x = scene.originX + fallback.x + 0.5D;
            double y = scene.originY + fallback.y + 1.5D;
            double z = scene.originZ + fallback.z + 0.5D;
            return new SpawnPoint(x, Math.max(-63.0D, y), z, 0.0F, 0.0F);
        }

        int originX = scene == null ? 0 : scene.originX;
        int originY = scene == null ? 64 : scene.originY;
        int originZ = scene == null ? 0 : scene.originZ;
        return new SpawnPoint(originX + 0.5D, Math.max(64.0D, originY + 2.0D), originZ + 0.5D, 0.0F, 0.0F);
    }

    private static SpawnPoint nearestSafeCapturedSpawn(WorldScene scene, double preferredX, double preferredY,
                                                       double preferredZ, float yaw, float pitch) {
        if (scene == null || scene.blocks == null || scene.blocks.isEmpty()) {
            return null;
        }
        BlockRecord best = null;
        long bestScore = Long.MAX_VALUE;
        int preferredBlockX = (int) Math.floor(preferredX) - scene.originX;
        int preferredBlockZ = (int) Math.floor(preferredZ) - scene.originZ;
        int preferredBlockY = (int) Math.floor(preferredY) - scene.originY;
        for (BlockRecord block : scene.blocks) {
            if (!isSolidSpawnSupport(block)) {
                continue;
            }
            long dx = block.x - preferredBlockX;
            long dz = block.z - preferredBlockZ;
            long dy = Math.max(0L, Math.abs(block.y - preferredBlockY) - 8L);
            long score = dx * dx + dz * dz + dy * dy;
            if (best == null || score < bestScore || (score == bestScore && block.y > best.y)) {
                best = block;
                bestScore = score;
            }
        }
        return best == null
                ? null
                : new SpawnPoint(scene.originX + best.x + 0.5D, scene.originY + best.y + 1.5D,
                scene.originZ + best.z + 0.5D, yaw, pitch);
    }

    private static BlockRecord nearestCapturedBlock(WorldScene scene) {
        if (scene == null || scene.blocks == null || scene.blocks.isEmpty()) {
            return null;
        }
        BlockRecord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockRecord block : scene.blocks) {
            if (!isSolidSpawnSupport(block)) {
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

    private static boolean isSolidSpawnSupport(BlockRecord block) {
        if (block == null || block.state == null) {
            return false;
        }
        String state = block.state;
        return !state.contains("minecraft:air")
                && !state.contains("minecraft:cave_air")
                && !state.contains("minecraft:void_air")
                && !state.contains("minecraft:water")
                && !state.contains("minecraft:lava")
                && !state.contains("minecraft:fire");
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
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
        if (scene.exportSettings.includePlayer && scene.player != null && scene.player.uuid != null) {
            try { return UUID.fromString(scene.player.uuid); } catch (IllegalArgumentException ignored) {}
        }
        String seed = scene == null || scene.name == null || scene.name.isBlank() ? "WorldBinder Export" : scene.name;
        return UUID.nameUUIDFromBytes(("WorldBinder:player:" + seed).getBytes(StandardCharsets.UTF_8));
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
        Path uidFile = worldFolder.resolve("uid.dat");
        if (Files.exists(uidFile)) {
            return;
        }
        UUID uuid = UUID.nameUUIDFromBytes(("WorldBinder:" + (scene == null ? "world" : scene.name))
                .getBytes(StandardCharsets.UTF_8));
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(uidFile))) {
            output.writeLong(uuid.getMostSignificantBits());
            output.writeLong(uuid.getLeastSignificantBits());
        }
    }

    private static TargetMinecraftVersion.Entry targetVersion(WorldScene scene) {
        return TargetMinecraftVersion.resolve(scene == null ? null : scene.targetMinecraftVersion);
    }

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
}

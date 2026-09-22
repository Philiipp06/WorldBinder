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
        CompoundTag settings = new CompoundTag();
        settings.putString("biome", DEFAULT_BIOME);
        ListTag layers = new ListTag();
        CompoundTag airLayer = new CompoundTag();
        airLayer.putInt("height", 1);
        airLayer.putString("block", "minecraft:air");
        layers.add(airLayer);
        settings.put("layers", layers);
        settings.put("structure_overrides", new ListTag());
        settings.putBoolean("features", false);
        settings.putBoolean("lakes", false);
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
        if (target == null || target.profile() != TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return "minecraft:overworld";
        }
        List<String> keys = ExportPathUtil.serverImportKeys(scene, worldFolder);
        return keys.isEmpty() ? "minecraft:overworld" : "minecraft:" + keys.get(0);
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

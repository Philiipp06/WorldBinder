package net.worldbinder.export.common;

import com.google.gson.*;
import net.minecraft.nbt.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import net.worldbinder.version.TargetMinecraftVersion;
import java.nio.file.*;
import java.io.IOException;

public final class AuxiliaryDataWriter {
    private AuxiliaryDataWriter() {}
    public static void write(WorldScene scene, Path folder) throws IOException {
        int version = TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion();
        Path data = folder.resolve(version >= 4786 ? "data/minecraft" : "data");
        Files.createDirectories(data);
        int largest = scene.mapIds.stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (CapturedMap map : scene.maps.values()) {
            largest = Math.max(largest, map.id);
            if (map.receivedPixels() == 0 || map.nbt == null) continue;
            CompoundTag contents = parse(map.nbt);
            contents.putByteArray("colors", map.colors.clone());
            if (version < 2566) {
                String dimension = contents.getString("dimension").orElse("minecraft:overworld");
                contents.putByte("dimension", (byte) (dimension.endsWith("the_nether") ? -1 : dimension.endsWith("the_end") ? 1 : 0));
            }
            CompoundTag root = new CompoundTag(); root.putInt("DataVersion", version); root.put("data", contents);
            Path target = version >= 4786 ? data.resolve("maps/" + map.id + ".dat") : data.resolve("map_" + map.id + ".dat");
            Files.createDirectories(target.getParent()); NbtIo.writeCompressed(root, target);
        }
        if (largest >= 0) {
            if (version < 1952) {
                CompoundTag legacy = new CompoundTag(); legacy.putShort("map",(short)largest);
                try (var output = new java.io.DataOutputStream(Files.newOutputStream(data.resolve("idcounts.dat")))) { NbtIo.write(legacy,output); }
            } else {
            CompoundTag ids = new CompoundTag(), root = new CompoundTag(); ids.putInt("map", largest);
            root.putInt("DataVersion", version); root.put("data", ids);
            Path target = data.resolve(version >= 4786 ? "maps/last_id.dat" : "idcounts.dat");
            Files.createDirectories(target.getParent()); NbtIo.writeCompressed(root, target);
            }
        }
        if (scene.exportSettings.includePlayer && scene.player != null) {
            CapturedPlayer player = scene.player;
            String prefix = version >= 4786 ? "players/" : "";
            if (player.statsReceived) {
                JsonObject stats = new JsonObject(); stats.addProperty("DataVersion", version); stats.add("stats", PlayerProgressConverter.statistics(player,version));
                writeJson(folder.resolve(prefix + "stats/" + player.uuid + ".json"), stats);
            }
            if (player.advancementsReceived) {
                JsonObject progress = new JsonObject(); progress.addProperty("DataVersion", version);
                player.advancements.forEach((id, json) -> progress.add(id, JsonParser.parseString(json)));
                writeJson(folder.resolve(prefix + "advancements/" + player.uuid + ".json"), progress);
            }
        }
        Path metadata = folder.resolve("worldbinder"); Files.createDirectories(metadata);
        Files.writeString(metadata.resolve("export_impact.json"), WorldBinder.GSON.toJson(ExportCompatibility.analyze(scene)));
    }
    private static void writeJson(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent()); Files.writeString(path, WorldBinder.GSON.toJson(json));
    }
    public static CompoundTag parse(String snbt) {
        try { return TagParser.parseCompoundFully(snbt).copy(); }
        catch (Exception e) { throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.unreadable_captured_nbt"), e); }
    }
}

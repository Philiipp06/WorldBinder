package net.worldbinder.scene;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class WorldScene {
    public int formatVersion = 2;
    public String archiveType = "world";
    public String name;
    public String createdAt = Instant.now().toString();
    public String minecraftVersion = "26.1.2";
    public String targetMinecraftVersion = "26.1.2";
    public String targetGenerationProfile = "CURRENT_26";
    public String dimension;
    public int originX;
    public int originY;
    public int originZ;
    public boolean hasPlayerSpawn;
    public double playerSpawnX;
    public double playerSpawnY;
    public double playerSpawnZ;
    public float playerSpawnYaw;
    public float playerSpawnPitch;
    public int sizeX;
    public int sizeY;
    public int sizeZ;
    public boolean includesBlockEntityNbt;
    public boolean includesEntityNbt;
    public String gameRulesNbt;
    public boolean includesMapData;
    public boolean includesAdvancements;
    public boolean includesStats;
    public boolean compressedZip;
    public List<Integer> mapIds = new ArrayList<>();
    public List<String> storageNotes = new ArrayList<>();
    public List<BlockRecord> blocks = new ArrayList<>();
    public List<EntityRecord> entities = new ArrayList<>();
    public Map<String, ChunkSnapshot> chunkSnapshots = new LinkedHashMap<>();

    public int blockCount() {
        return blocks == null ? 0 : blocks.size();
    }

    public int entityCount() {
        return entities == null ? 0 : entities.size();
    }

    public int mapCount() {
        return mapIds == null ? 0 : mapIds.size();
    }

    public int chunkSnapshotCount() {
        return chunkSnapshots == null ? 0 : chunkSnapshots.size();
    }

    public int blockEntityCount() {
        if (blocks == null) {
            return 0;
        }
        int count = 0;
        for (BlockRecord block : blocks) {
            if (block.hasBlockEntity) {
                count++;
            }
        }
        return count;
    }

    public long volume() {
        return (long) sizeX * (long) sizeY * (long) sizeZ;
    }
}

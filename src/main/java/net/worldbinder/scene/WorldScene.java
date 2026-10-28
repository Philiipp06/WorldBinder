package net.worldbinder.scene;

import net.worldbinder.version.TargetMinecraftVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class WorldScene {
    public int formatVersion = 4;
    public String projectId = java.util.UUID.randomUUID().toString();
    /** SHA-256 of the local save path or normalized server address. Never inferred from a display name. */
    public String worldIdentity;
    /** Explicit backend identity; a network endpoint alone never identifies a world. */
    public String backendId;
    public String backendName;
    /** Inactive dimensions contain frozen data only; children never contain another dimensions map. */
    public Map<String, WorldScene> dimensions = new LinkedHashMap<>();
    public String dimensionType;
    public String dimensionTypeNbt;
    public int dimensionMinY = Integer.MIN_VALUE;
    public int dimensionHeight;
    public String replayProvider;
    public String replayIdentity;
    public long replayHighWater = -1;
    public int replaySeekGaps;
    public boolean replayRewound;
    public int revision;
    public String updatedAt;
    public ExportSettings exportSettings = new ExportSettings();
    public CaptureSettings captureSettings = new CaptureSettings();
    /** Null for roaming projects. A selection remains bounded when resumed after restart. */
    public net.worldbinder.selection.CaptureBounds selectionBounds;
    public Map<String, ContainerRecord> containers = new LinkedHashMap<>();
    public Map<Integer, CapturedMap> maps = new LinkedHashMap<>();
    public CapturedPlayer player;
    /** Opt-in roster of other players seen during a server download (tab list and world), keyed by UUID. */
    public Map<String, SeenPlayer> seenPlayers = new LinkedHashMap<>();
    public boolean resourcePackExpected;
    public String resourcePackFile;
    public String resourcePackSha256;
    public transient String resourcePackSource;
    public transient boolean filtersApplied;
    public transient boolean dimensionMapsPrepared;
    public transient List<net.worldbinder.validation.Finding> exportNotices=new ArrayList<>();
    public transient java.util.Set<Long> exportRelightChunks=new java.util.HashSet<>();
    public transient net.worldbinder.export.common.ExportFiltering.Counts filterCounts;
    /** Work in progress is separate from the last committed chunk, so interruption cannot erase it. */
    public Map<String, ChunkSnapshot> pendingChunks = new LinkedHashMap<>();
    /** False=new, true=updated since the last complete world revision; recovery retains this distinction. */
    public Map<String,Boolean> recordedChunkChanges=new LinkedHashMap<>();
    public String archiveType = "world";
    public String name;
    public String createdAt = Instant.now().toString();
    public String minecraftVersion = TargetMinecraftVersion.CURRENT;
    public String targetMinecraftVersion = TargetMinecraftVersion.CURRENT;
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
    public String chunkCacheFolder;
    public List<Integer> mapIds = new ArrayList<>();
    public List<String> storageNotes = new ArrayList<>();
    public List<BlockRecord> blocks = new ArrayList<>();
    public List<EntityRecord> entities = new ArrayList<>();
    public Map<String, ChunkSnapshot> chunkSnapshots = new LinkedHashMap<>();

    public int blockCount() {
        return blocks == null ? 0 : blocks.size();
    }
    public int projectBlockCount(){return ProjectDimensions.all(this).stream().mapToInt(WorldScene::blockCount).sum();}
    public int projectBlockEntityCount(){return ProjectDimensions.all(this).stream().mapToInt(WorldScene::blockEntityCount).sum();}
    public int projectEntityCount(){return ProjectDimensions.all(this).stream().mapToInt(WorldScene::entityCount).sum();}
    public int projectChunkCount(){return ProjectDimensions.all(this).stream().mapToInt(WorldScene::chunkSnapshotCount).sum();}
    public int projectMapCount(){return dimensionMapsPrepared?mapCount():ProjectDimensions.all(this).stream().mapToInt(WorldScene::mapCount).sum();}
    public String projectDimensions(){return String.join(" · ",ProjectDimensions.all(this).stream().map(s->java.util.Objects.toString(ProjectDimensions.canonical(s.dimension),"?")).toList());}

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

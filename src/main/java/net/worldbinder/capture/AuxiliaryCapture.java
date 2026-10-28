package net.worldbinder.capture;

import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import java.util.*;

/** Copies received data, never queries server-only state or fabricates knowledge from empty client defaults. */
final class AuxiliaryCapture {
    private final Map<Long, Map<Integer, ChunkSectionData>> lights = new LinkedHashMap<>();
    private final Map<Integer, CapturedMap> sessionMaps = new LinkedHashMap<>();
    private CapturedPlayer sessionPlayer = new CapturedPlayer();
    private net.minecraft.client.multiplayer.ClientLevel lightLevel;

    void disconnected() { lights.clear(); lightLevel = null; sessionMaps.clear(); sessionPlayer = new CapturedPlayer(); }

    void light(Minecraft client, int x, int z, ClientboundLightUpdatePacketData data) {
        if (client.level == null) return;
        if (lightLevel != client.level) { lights.clear(); lightLevel = client.level; }
        int base = client.level.getLightEngine().getMinLightSection();
        Map<Integer, ChunkSectionData> sections = lights.computeIfAbsent(ProjectData.chunkKey(x, z), k -> new LinkedHashMap<>());
        lightLayer(sections, base, net.worldbinder.compat.PacketAccess.get(data,"blockYMask","getBlockYMask"), net.worldbinder.compat.PacketAccess.get(data,"emptyBlockYMask","getEmptyBlockYMask"), net.worldbinder.compat.PacketAccess.get(data,"blockUpdates","getBlockUpdates"), false);
        lightLayer(sections, base, net.worldbinder.compat.PacketAccess.get(data,"skyYMask","getSkyYMask"), net.worldbinder.compat.PacketAccess.get(data,"emptySkyYMask","getEmptySkyYMask"), net.worldbinder.compat.PacketAccess.get(data,"skyUpdates","getSkyUpdates"), true);
        // Bound transient packet data to loaded chunks; committed copies live in the archive.
        if (lights.size() > 4096) lights.remove(lights.keySet().iterator().next());
    }

    private void lightLayer(Map<Integer, ChunkSectionData> sections, int base, BitSet mask, BitSet empty,
                             List<byte[]> updates, boolean sky) {
        int cursor = 0;
        for (int bit = mask.nextSetBit(0); bit >= 0; bit = mask.nextSetBit(bit + 1)) {
            if (cursor >= updates.size()) break;
            byte[] bytes = updates.get(cursor++);
            if (bytes.length != 2048) continue;
            ChunkSectionData section = sections.computeIfAbsent(base + bit, k -> new ChunkSectionData());
            if (sky) section.skyLight = bytes.clone(); else section.blockLight = bytes.clone();
        }
        for (int bit = empty.nextSetBit(0); bit >= 0; bit = empty.nextSetBit(bit + 1)) {
            ChunkSectionData section = sections.computeIfAbsent(base + bit, k -> new ChunkSectionData());
            if (sky) section.skyLight = new byte[2048]; else section.blockLight = new byte[2048];
        }
    }

    void sections(Minecraft client, LevelChunk chunk, ChunkSnapshot snapshot) {
        if (client.level == null || chunk == null) return;
        var engine = client.level.getLightEngine();
        Map<Integer, ChunkSectionData> received = lightLevel == client.level ? lights.getOrDefault(ProjectData.chunkKey(snapshot.chunkX, snapshot.chunkZ), Map.of()) : Map.of();
        boolean allBiomes = true, allLight = true;
        for (int y = snapshot.captureMinY >> 4; y <= snapshot.captureMaxY >> 4; y++) {
            ChunkSectionData section = snapshot.sections.computeIfAbsent(y, k -> new ChunkSectionData());
            int index = chunk.getSectionIndex(y << 4);
            if (index >= 0 && index < chunk.getSections().length) {
                String[] biomes = new String[64];
                for (int by = 0; by < 4; by++) for (int bz = 0; bz < 4; bz++) for (int bx = 0; bx < 4; bx++) {
                    var holder = chunk.getSections()[index].getNoiseBiome(bx, by, bz);
                    biomes[(by << 4) | (bz << 2) | bx] = holder.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
                }
                if (Arrays.stream(biomes).allMatch(Objects::nonNull)) section.biomes = biomes;
            }
            var pos = SectionPos.of(snapshot.chunkX, y, snapshot.chunkZ);
            var block = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(pos);
            var sky = engine.getLayerListener(LightLayer.SKY).getDataLayerData(pos);
            if (block != null) section.blockLight = block.getData().clone();
            if (sky != null) section.skyLight = sky.getData().clone();
            var packet = received.get(y);
            if (packet != null) {
                if (packet.hasBlockLight()) section.blockLight = packet.blockLight.clone();
                if (packet.hasSkyLight()) section.skyLight = packet.skyLight.clone();
            }
            // In dimensions with no skylight a zero sky layer is an established dimension rule.
            if (!client.level.dimensionType().hasSkyLight()) section.skyLight = new byte[2048];
            allBiomes &= section.hasBiomes();
            allLight &= section.hasBlockLight() && section.hasSkyLight();
        }
        snapshot.hasBiomeData = allBiomes;
        snapshot.lightEstimated = !allLight;
    }

    void map(WorldScene scene, Minecraft client, ClientboundMapItemDataPacket packet) {
        if (client.level == null || !WorldBinder.config().exportMaps) return;
        int id = packet.mapId().id();
        Map<Integer, CapturedMap> target = scene == null ? sessionMaps : scene.maps;
        CapturedMap map = target.computeIfAbsent(id, k -> {
            if (sessionMaps.containsKey(k)) return WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(sessionMaps.get(k)), CapturedMap.class);
            CapturedMap m = new CapturedMap(); m.id = k; return m;
        });
        if (scene != null && !scene.mapIds.contains(id)) scene.mapIds.add(id);
        packet.colorPatch().ifPresent(p -> map.patch(p.startX(), p.startY(), p.width(), p.height(), p.mapColors()));
        packet.decorations().ifPresent(decorations -> {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (var decoration : decorations) {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.putString("type", decoration.type().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"));
                tag.putByte("x", decoration.x()); tag.putByte("y", decoration.y()); tag.putByte("rotation", decoration.rot());
                decoration.name().ifPresent(name -> tag.put("name",net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
                        client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE),name).getOrThrow())); list.add(tag);
            }
            map.decorationsNbt = list.toString();
        });
        MapItemSavedData state = client.level.getMapData(packet.mapId());
        if (state != null) {
            var ops = client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var encoded = MapItemSavedData.CODEC.encodeStart(ops, state).result();
            if (encoded.isPresent() && encoded.get() instanceof net.minecraft.nbt.CompoundTag nbt) {
                nbt.remove("colors"); map.nbt = nbt.toString(); // Pixel bytes already have a compact, coverage-aware archive field.
            }
        }
        // Map centers are not sent by the vanilla map packet. Keep that limitation explicit.
        sessionMaps.put(id, WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(map), CapturedMap.class));
        if (sessionMaps.size() > 512) sessionMaps.remove(sessionMaps.keySet().iterator().next());
        if (scene != null) scene.includesMapData = true;
    }

    static CapturedPlayer player(WorldScene scene, Minecraft client) {
        if(scene.player!=null&&client.player!=null&&scene.player.uuid!=null&&!scene.player.uuid.equals(client.player.getUUID().toString()))scene.player=null;
        if (scene.player == null) scene.player = new CapturedPlayer();
        if (client.player != null) scene.player.uuid = client.player.getUUID().toString();
        return scene.player;
    }

    void playerSnapshot(WorldScene scene, Minecraft client) {
        if(scene.replayIdentity!=null||net.worldbinder.replay.ReplayPlayback.state().present())return; // Replay camera is not the recorded player's inventory or health.
        if (client.player == null || client.level == null) return;
        try {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, client.level.registryAccess());
            client.player.saveWithoutId(output);
            var nbt = output.buildResult();
            if (client.gameMode != null) nbt.putInt("playerGameType", client.gameMode.getPlayerMode().getId());
            nbt.putString("Dimension", WorldIdentity.dimension(client));
            if (scene.player != null && scene.player.uuid != null && !scene.player.uuid.equals(client.player.getUUID().toString())) scene.player = null;
            CapturedPlayer player = player(scene, client);
            player.nbt = nbt.toString();
            player.observedAt = System.currentTimeMillis();
            mergeSession(scene, client);
        } catch (RuntimeException exception) { WorldBinder.LOGGER.warn("Could not snapshot own player", exception); }
        if (WorldBinder.config().recordPlayerList) roster(scene, client, System.currentTimeMillis());
    }

    /** Opt-in roster: received tab-list entries plus player entities the client renders. */
    static void roster(WorldScene scene, Minecraft client, long now) {
        if (scene.seenPlayers == null) scene.seenPlayers = new LinkedHashMap<>();
        var connection = client.getConnection();
        if (connection != null) {
            for (var info : connection.getOnlinePlayers()) {
                var profile = info.getProfile();
                if (profile == null || profile.id() == null) continue;
                SeenPlayer seen = seen(scene, profile.id().toString(), profile.name(), now);
                if (seen == null) continue;
                seen.inTabList = true;
                seen.gameMode = info.getGameMode() == null ? null : info.getGameMode().getName();
                seen.latencyMs = info.getLatency();
                seen.displayName = info.getTabListDisplayName() == null ? null : info.getTabListDisplayName().getString();
            }
        }
        if (client.level != null) {
            String dimension = WorldIdentity.dimension(client);
            for (var other : client.level.players()) {
                SeenPlayer seen = seen(scene, other.getUUID().toString(), other.getGameProfile().name(), now);
                if (seen == null) continue;
                seen.seenInWorld = true;
                seen.dimension = dimension;
                seen.hasPosition = true;
                seen.x = other.getBlockX();
                seen.y = other.getBlockY();
                seen.z = other.getBlockZ();
                seen.sightings++;
            }
        }
        if (client.player != null) {
            SeenPlayer self = scene.seenPlayers.get(client.player.getUUID().toString());
            if (self != null) self.self = true;
        }
    }

    private static SeenPlayer seen(WorldScene scene, String uuid, String name, long now) {
        SeenPlayer seen = scene.seenPlayers.get(uuid);
        if (seen == null) {
            if (scene.seenPlayers.size() >= 10_000) return null;
            seen = new SeenPlayer();
            seen.uuid = uuid;
            seen.firstSeen = now;
            scene.seenPlayers.put(uuid, seen);
        }
        if (name != null && !name.isBlank()) seen.name = name;
        seen.lastSeen = now;
        return seen;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void stats(WorldScene scene, Minecraft client, ClientboundAwardStatsPacket packet) {
        if(net.worldbinder.replay.ReplayPlayback.state().present())return;
        if (!WorldBinder.config().exportStats) return;
        CapturedPlayer player = sessionPlayer;
        for (var entry : packet.stats().object2IntEntrySet()) {
            var stat = entry.getKey();
            String type = BuiltInRegistries.STAT_TYPE.getKey(stat.getType()).toString();
            String key = ((net.minecraft.core.Registry) stat.getType().getRegistry()).getKey(stat.getValue()).toString();
            player.stats.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(key, entry.getIntValue());
        }
        player.statsReceived = true;
        if (scene != null) mergeSession(scene, client);
    }

    void advancements(WorldScene scene, Minecraft client, ClientboundUpdateAdvancementsPacket packet) {
        if(net.worldbinder.replay.ReplayPlayback.state().present())return;
        if (!WorldBinder.config().exportAdvancements) return;
        CapturedPlayer player = sessionPlayer;
        // A reset packet is authoritative for the current player's complete visible advancement set.
        if (packet.shouldReset()) player.advancements.clear();
        java.util.Set<net.minecraft.resources.Identifier> removed = net.worldbinder.compat.PacketAccess.get(packet,"removed","getRemoved");
        java.util.Map<net.minecraft.resources.Identifier,net.minecraft.advancements.AdvancementProgress> progressMap = net.worldbinder.compat.PacketAccess.get(packet,"progress","getProgress");
        removed.forEach(id -> player.advancements.remove(id.toString()));
        progressMap.forEach((id, progress) -> {
            String json = net.minecraft.advancements.AdvancementProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow().toString();
            player.advancements.put(id.toString(), json);
        });
        player.advancementsReceived = true;
        if (scene != null) mergeSession(scene, client);
    }

    private void mergeSession(WorldScene scene, Minecraft client) {
        CapturedPlayer player = player(scene, client);
        if (sessionPlayer.statsReceived) {
            sessionPlayer.stats.forEach((type, values) -> player.stats.computeIfAbsent(type, k -> new LinkedHashMap<>()).putAll(values));
            player.statsReceived = true; scene.includesStats = true;
        }
        if (sessionPlayer.advancementsReceived) {
            player.advancements = new LinkedHashMap<>(sessionPlayer.advancements);
            player.advancementsReceived = true; scene.includesAdvancements = true;
        }
        attachMaps(scene);
    }
    void attachMaps(WorldScene scene){
        for (var entry : sessionMaps.entrySet()) {
            CapturedMap saved = scene.maps.get(entry.getKey()), received = entry.getValue();
            if (saved == null) saved = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(received), CapturedMap.class);
            else for (int i = 0; i < 16384; i++) if (received.received[i]) { saved.colors[i] = received.colors[i]; saved.received[i] = true; }
            if (received.nbt != null) saved.nbt = received.nbt;
            scene.maps.put(entry.getKey(), saved);
            if (!scene.mapIds.contains(entry.getKey())) scene.mapIds.add(entry.getKey());
        }
    }
}

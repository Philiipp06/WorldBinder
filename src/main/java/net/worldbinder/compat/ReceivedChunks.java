package net.worldbinder.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Read the received chunk cache, independent of frustum, visible sections or current camera radius. */
public final class ReceivedChunks {
    private static Field storage, chunks;
    private ReceivedChunks() {}
    /** ClientLevel.hasChunk deliberately returns true even for missing chunks. Never use it as reception proof. */
    public static LevelChunk chunk(ClientLevel level,int x,int z){
        var chunk=level.getChunkSource().getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
        return chunk instanceof net.minecraft.world.level.chunk.EmptyLevelChunk?null:chunk;
    }
    public static List<LevelChunk> loaded(ClientLevel level) {
        try {
            Object source = level.getChunkSource();
            if (storage == null) { storage = source.getClass().getDeclaredField("storage"); storage.setAccessible(true); }
            Object holder = storage.get(source);
            if (chunks == null) { chunks = holder.getClass().getDeclaredField("chunks"); chunks.setAccessible(true); }
            var array = (AtomicReferenceArray<?>) chunks.get(holder);
            List<LevelChunk> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) if (array.get(i) instanceof LevelChunk chunk) result.add(chunk);
            return result;
        } catch (ReflectiveOperationException exception) { throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.received_chunks"), exception); }
    }
}

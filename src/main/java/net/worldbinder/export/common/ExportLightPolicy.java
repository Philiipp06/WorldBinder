package net.worldbinder.export.common;

import net.worldbinder.scene.*;
import net.worldbinder.version.TargetMinecraftVersion;
import java.util.*;

/** Changed states may change emission, opacity or occlusion shapes. Relight their entire chunk and neighbours. */
public final class ExportLightPolicy {
    private ExportLightPolicy() {}
    public static Set<Long> invalidatedChunks(WorldScene scene) {
        Set<Long> result = new HashSet<>();
        if(scene.exportRelightChunks!=null)for(long key:scene.exportRelightChunks){
            int x=(int)key,z=(int)(key>>32);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)result.add(ProjectData.chunkKey(x+dx,z+dz));
        }
        for(var snapshot:scene.chunkSnapshots.values())if(snapshot.relightRequired){
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)result.add(ProjectData.chunkKey(snapshot.chunkX+dx,snapshot.chunkZ+dz));
        }
        Map<String, String> mapped = new HashMap<>();
        int version = TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion();
        for (BlockRecord block : scene.blocks) {
            int y = scene.originY + block.y;
            String output = mapped.computeIfAbsent(block.state, state -> ExportCompatibility.state(scene, state));
            if (Objects.equals(block.state, output) && !(version < 2860 && (y < 0 || y > 255))) continue;
            int x = (scene.originX + block.x) >> 4, z = (scene.originZ + block.z) >> 4;
            // Light can cross a chunk boundary (including diagonals). Do not create uncaptured neighbour chunks.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) result.add(ProjectData.chunkKey(x + dx, z + dz));
        }
        return result;
    }
}

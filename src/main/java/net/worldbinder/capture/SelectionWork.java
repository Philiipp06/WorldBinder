package net.worldbinder.capture;

import net.minecraft.core.BlockPos;
import net.worldbinder.selection.CaptureBounds;

/** Constant-memory cursor: no set of all blocks or all chunks is built when a selection starts. */
final class SelectionWork {
    final CaptureBounds bounds;
    long nextChunk;
    CaptureBounds tile;
    long block;
    net.minecraft.world.level.chunk.LevelChunk source;
    net.worldbinder.scene.ChunkSnapshot snapshot;
    SelectionWork(CaptureBounds bounds){this.bounds=bounds;}
    CaptureBounds nextTile(){var result=bounds.chunk(nextChunk);nextChunk=(nextChunk+1)%bounds.chunkCount();return result;}
    void start(CaptureBounds bounds,net.minecraft.world.level.chunk.LevelChunk source,net.worldbinder.scene.ChunkSnapshot snapshot){tile=bounds;block=0;this.source=source;this.snapshot=snapshot;}
    BlockPos position(){
        long height=tile.height(),length=tile.length();
        return new BlockPos(Math.toIntExact(tile.minX()+block/(height*length)),Math.toIntExact(tile.minY()+block%height),Math.toIntExact(tile.minZ()+(block/height)%length));
    }
    void clear(){tile=null;source=null;snapshot=null;block=0;}
}

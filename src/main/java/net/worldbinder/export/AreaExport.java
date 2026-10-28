package net.worldbinder.export;

import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import net.worldbinder.selection.*;
import java.util.*;

public final class AreaExport {
    private AreaExport(){}
    public static WorldScene crop(WorldScene source,CaptureBounds bounds){
        var copy=ProjectData.metadataCopy(source);copy.projectId=UUID.randomUUID().toString();copy.revision=0;
        copy.dimensions.clear();copy.captureSettings.multiDimension=false;
        copy.name=source.name+"_selection";copy.selectionBounds=bounds;
        copy.originX=bounds.minX();copy.originY=bounds.minY();copy.originZ=bounds.minZ();
        copy.sizeX=bounds.width();copy.sizeY=bounds.height();copy.sizeZ=bounds.length();
        for(var block:source.blocks){int x=source.originX+block.x,y=source.originY+block.y,z=source.originZ+block.z;
            if(bounds.contains(x,y,z))copy.blocks.add(new BlockRecord(x-copy.originX,y-copy.originY,z-copy.originZ,block.state,block.hasBlockEntity,block.blockEntityNbt));
        }
        Set<String> entities=new HashSet<>();
        for(var entity:source.entities){double x=source.originX+entity.x,y=source.originY+entity.y,z=source.originZ+entity.z;if(!bounds.contains(x,y,z))continue;
            var record=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(entity),EntityRecord.class);record.x=x-copy.originX;record.y=y-copy.originY;record.z=z-copy.originZ;copy.entities.add(record);entities.add(entity.uuid);
        }
        copy.containers.entrySet().removeIf(entry->{
            if(entry.getKey().startsWith("entity:"))return !entities.contains(entry.getKey().substring(7));
            if(!entry.getKey().startsWith("block:"))return true;var pos=entry.getKey().substring(6).split(",");
            return pos.length!=3||!bounds.contains(Integer.parseInt(pos[0]),Integer.parseInt(pos[1]),Integer.parseInt(pos[2]));
        });
        for(var entry:source.chunkSnapshots.entrySet()){
            var snapshot=entry.getValue();int x=snapshot.chunkX*16,z=snapshot.chunkZ*16;
            if(x>bounds.maxX()||x+15<bounds.minX()||z>bounds.maxZ()||z+15<bounds.minZ())continue;
            var scoped=snapshot.copy();scoped.coverageBoxes.clear();scoped.captureBox=null;scoped.fullChunkColumns=false;scoped.relightRequired=true;
            if(snapshot.fullChunkColumns){clip(scoped,bounds,new int[]{x,snapshot.captureMinY,z,x+15,snapshot.captureMaxY,z+15});
                if(snapshot.replacementRanges!=null)for(var range:snapshot.replacementRanges)if(range.length==2)clip(scoped,bounds,new int[]{x,range[0],z,x+15,range[1],z+15});}
            if(snapshot.captureBox!=null)clip(scoped,bounds,snapshot.captureBox);
            if(snapshot.coverageBoxes!=null)for(var box:snapshot.coverageBoxes)clip(scoped,bounds,box);
            scoped.captureMinY=bounds.minY();scoped.captureMaxY=bounds.maxY();scoped.replacementRanges.clear();
            copy.chunkSnapshots.put(entry.getKey(),scoped);copy.exportRelightChunks.add(ProjectData.chunkKey(snapshot.chunkX,snapshot.chunkZ));
        }
        return copy;
    }
    private static void clip(ChunkSnapshot target,CaptureBounds bounds,int[] box){
        int x0=Math.max(bounds.minX(),box[0]),y0=Math.max(bounds.minY(),box[1]),z0=Math.max(bounds.minZ(),box[2]);
        int x1=Math.min(bounds.maxX(),box[3]),y1=Math.min(bounds.maxY(),box[4]),z1=Math.min(bounds.maxZ(),box[5]);
        if(x0<=x1&&y0<=y1&&z0<=z1)target.coverageBoxes.add(new int[]{x0,y0,z0,x1,y1,z1});
    }
}

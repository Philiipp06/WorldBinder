package net.worldbinder.selection;

import net.worldbinder.scene.*;
import java.util.*;

/** Coverage describes confirmed observations, including air, rather than rendered surface pixels. */
public final class RecordedCoverage {
    private RecordedCoverage() {}
    public static boolean known(WorldScene scene,int x,int y,int z) {
        var snapshot=scene.chunkSnapshots.get((x>>4)+","+(z>>4));
        if(snapshot==null||!snapshot.isDone())return false;
        if(snapshot.fullChunkColumns){
            if(y>=snapshot.captureMinY&&y<=snapshot.captureMaxY)return true;
            if(snapshot.replacementRanges!=null)for(var range:snapshot.replacementRanges)if(range.length==2&&y>=range[0]&&y<=range[1])return true;
        }
        if(snapshot.captureBox!=null&&CaptureBounds.from(snapshot.captureBox).contains(x,y,z))return true;
        if(snapshot.coverageBoxes!=null)for(var box:snapshot.coverageBoxes)if(CaptureBounds.from(box).contains(x,y,z))return true;
        return false;
    }
    public static long knownVolume(WorldScene scene,CaptureBounds bounds) {
        long total=0;
        var candidates=bounds.chunkCount()==1?java.util.Collections.singletonList(scene.chunkSnapshots.get((bounds.minX()>>4)+","+(bounds.minZ()>>4))):scene.chunkSnapshots.values();
        for(var snapshot:candidates){
            if(snapshot==null||!snapshot.isDone())continue;
            int x0=Math.max(bounds.minX(),snapshot.chunkX<<4),x1=Math.min(bounds.maxX(),(snapshot.chunkX<<4)+15);
            int z0=Math.max(bounds.minZ(),snapshot.chunkZ<<4),z1=Math.min(bounds.maxZ(),(snapshot.chunkZ<<4)+15);
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                List<int[]> intervals=new ArrayList<>();
                if(snapshot.fullChunkColumns){add(intervals,bounds,snapshot.captureMinY,snapshot.captureMaxY);
                    if(snapshot.replacementRanges!=null)for(var range:snapshot.replacementRanges)if(range.length==2)add(intervals,bounds,range[0],range[1]);}
                if(snapshot.captureBox!=null)addBox(intervals,bounds,snapshot.captureBox,x,z);
                if(snapshot.coverageBoxes!=null)for(var box:snapshot.coverageBoxes)addBox(intervals,bounds,box,x,z);
                intervals.sort(Comparator.comparingInt(a->a[0]));
                long end=Long.MIN_VALUE;
                for(var range:intervals){total+=Math.max(0L,(long)range[1]-Math.max((long)range[0],end+1)+1);end=Math.max(end,range[1]);}
            }
        }
        return total;
    }
    private static void addBox(List<int[]> list,CaptureBounds bounds,int[] box,int x,int z){
        if(box.length==6&&x>=box[0]&&x<=box[3]&&z>=box[2]&&z<=box[5])add(list,bounds,box[1],box[4]);
    }
    private static void add(List<int[]> list,CaptureBounds bounds,int min,int max){
        min=Math.max(min,bounds.minY());max=Math.min(max,bounds.maxY());if(min<=max)list.add(new int[]{min,max});
    }
}

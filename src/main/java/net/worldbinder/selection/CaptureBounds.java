package net.worldbinder.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Inclusive block bounds; arithmetic and chunk iteration never allocate the selected positions. */
public record CaptureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public CaptureBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.inverted_selection_bounds"));
        if(minX< -30_000_000||maxX>30_000_000||minZ< -30_000_000||maxZ>30_000_000||minY< -2048||maxY>2047)throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.selection_outside_supported_block_coordinates"));
        Math.multiplyExact(Math.multiplyExact((long)maxX-minX+1, (long)maxY-minY+1), (long)maxZ-minZ+1);
    }
    public static CaptureBounds of(Selection selection) {
        var min=selection.min();var max=selection.max();
        return new CaptureBounds(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ());
    }
    public long volume() { return Math.multiplyExact(Math.multiplyExact((long)maxX-minX+1,(long)maxY-minY+1),(long)maxZ-minZ+1); }
    public boolean contains(int x,int y,int z) { return x>=minX&&x<=maxX&&y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ; }
    public boolean contains(double x,double y,double z) { return x>=minX&&x<(long)maxX+1&&y>=minY&&y<(long)maxY+1&&z>=minZ&&z<(long)maxZ+1; }
    public int width() { return Math.toIntExact((long)maxX-minX+1); }
    public int height() { return Math.toIntExact((long)maxY-minY+1); }
    public int length() { return Math.toIntExact((long)maxZ-minZ+1); }
    public long chunkCount() { return ((long)(maxX>>4)-(minX>>4)+1)*((long)(maxZ>>4)-(minZ>>4)+1); }
    public CaptureBounds chunk(long index) {
        if(index<0||index>=chunkCount())throw new IndexOutOfBoundsException();
        long width=(long)(maxX>>4)-(minX>>4)+1;
        int x=Math.toIntExact((minX>>4)+index%width), z=Math.toIntExact((minZ>>4)+index/width);
        return new CaptureBounds(Math.max(minX,x<<4),minY,Math.max(minZ,z<<4),Math.min(maxX,(x<<4)+15),maxY,Math.min(maxZ,(z<<4)+15));
    }
    public int[] array() { return new int[]{minX,minY,minZ,maxX,maxY,maxZ}; }
    public static CaptureBounds from(int[] box) { return new CaptureBounds(box[0],box[1],box[2],box[3],box[4],box[5]); }
    public Selection selection() { return new Selection(new BlockPos(minX,minY,minZ),new BlockPos(maxX,maxY,maxZ)); }
    public AABB box() { return new AABB(minX,minY,minZ,(double)maxX+1,(double)maxY+1,(double)maxZ+1); }
}

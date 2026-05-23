package net.worldbinder.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record Selection(BlockPos first, BlockPos second) {
    public boolean complete() {
        return first != null && second != null;
    }

    public BlockPos min() {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())
        );
    }

    public BlockPos max() {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
    }

    public long volumeLong() {
        BlockPos min = min();
        BlockPos max = max();
        return (long) (max.getX() - min.getX() + 1) * (long) (max.getY() - min.getY() + 1) * (long) (max.getZ() - min.getZ() + 1);
    }

    public int volume() {
        return (int) Math.min(Integer.MAX_VALUE, volumeLong());
    }

    public AABB box() {
        BlockPos min = min();
        BlockPos max = max();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }
}

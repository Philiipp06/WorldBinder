package net.worldbinder.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Per-query water support, never a persistent player or block collision state. */
public final class WaterSurface {
    private WaterSurface() {}

    public static boolean active(Entity entity) {
        return entity != null && MovementController.walking(entity)
                && MovementController.enabled(entity, MovementTool.JESUS) && !entity.isShiftKeyDown();
    }

    public static VoxelShape shape(BlockGetter level, BlockPos pos, FluidState fluid) {
        if (!fluid.is(FluidTags.WATER) || level.getFluidState(pos.above()).is(FluidTags.WATER)) return Shapes.empty();
        return Shapes.box(0, 0, 0, 1, fluid.getHeight(level, pos), 1);
    }

    public static boolean supported(Entity entity) {
        if (!active(entity)) return false;
        // Sample the full footprint so straddling a shoreline does not switch to swimming.
        var box = entity.getBoundingBox();
        int y = (int)Math.floor(box.minY + 0.001);
        for (int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX); x++) {
            for (int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ); z++) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos pos = new BlockPos(x, y + dy, z);
                    FluidState fluid = entity.level().getFluidState(pos);
                    if (!fluid.is(FluidTags.WATER) || entity.level().getFluidState(pos.above()).is(FluidTags.WATER)) continue;
                    double surface = pos.getY() + fluid.getHeight(entity.level(), pos);
                    if (box.minY >= surface - 0.001 && box.minY <= surface + 0.1) return true;
                }
            }
        }
        return false;
    }
}

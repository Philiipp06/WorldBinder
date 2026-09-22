package net.worldbinder.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.worldbinder.movement.WaterSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public abstract class MovementWaterMixin {
    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void worldbinder$surface(BlockState state, BlockGetter level, BlockPos pos,
                                     CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext entityContext)
                || !WaterSurface.active(entityContext.getEntity())) return;
        VoxelShape surface = WaterSurface.shape(level, pos, state.getFluidState());
        if (!surface.isEmpty() && context.isAbove(surface, pos, false)) {
            cir.setReturnValue(net.minecraft.world.phys.shapes.Shapes.or(cir.getReturnValue(), surface));
        }
    }
}

package net.worldbinder.mixin;

import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
    @Inject(method="emitGizmos",at=@At("TAIL"))
    private void worldbinder$chunkFrames(Frustum frustum,double x,double y,double z,float partial,CallbackInfo ci){
        net.worldbinder.render.WorldBinderWorldOverlay.emit();
    }
}

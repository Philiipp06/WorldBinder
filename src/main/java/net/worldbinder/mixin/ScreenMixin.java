package net.worldbinder.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void worldbinder$screenRemoved(CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onContainerScreenClosed((Screen) (Object) this);
        }
    }
}

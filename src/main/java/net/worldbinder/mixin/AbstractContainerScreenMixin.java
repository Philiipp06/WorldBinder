package net.worldbinder.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void worldbinder$containerScreenRemoved(CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onContainerScreenClosed((Screen) (Object) this);
        }
    }
}

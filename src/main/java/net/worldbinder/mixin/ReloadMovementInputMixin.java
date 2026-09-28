package net.worldbinder.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.worldbinder.reload.ReloadInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class ReloadMovementInputMixin {
    @ModifyExpressionValue(method = "tick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;overlay:Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 2),
            require = 0)
    private Overlay worldbinder$legacyKeybinds(Overlay overlay) {
        return ReloadInteraction.forInput(overlay);
    }

    @ModifyExpressionValue(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;"),
            require = 0)
    private Overlay worldbinder$currentKeybinds(Overlay overlay) {
        return ReloadInteraction.forInput(overlay);
    }
}

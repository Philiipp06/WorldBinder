package net.worldbinder.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Overlay;
import net.worldbinder.reload.ReloadInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class)
public abstract class ReloadKeyboardInputMixin {
    @ModifyExpressionValue(method = {"keyPress", "charTyped", "preeditCallback"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"),
            require = 0)
    private Overlay worldbinder$legacyInput(Overlay overlay) {
        return ReloadInteraction.forInput(overlay);
    }

    @ModifyExpressionValue(method = {"keyPress", "charTyped", "preeditCallback"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;"),
            require = 0)
    private Overlay worldbinder$currentInput(Overlay overlay) {
        return ReloadInteraction.forInput(overlay);
    }
}

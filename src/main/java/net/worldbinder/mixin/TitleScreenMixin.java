package net.worldbinder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.ui.WorldBinderScreen;
import net.worldbinder.ui.component.WorldBinderMenuButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void worldbinder$addMenuButton(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        WorldBinderMenuButton button = new WorldBinderMenuButton(ignored ->
                client.gui.setScreen(new WorldBinderScreen(
                        WorldBinderClient.selections(),
                        WorldBinderClient.capture(),
                        WorldBinderClient.placement(),
                        WorldBinderClient.scenes(),
                        screen
                ))
        );

        ((ScreenAccessor) screen).worldbinder$addRenderableWidget(button);
        button.placeInIconRow(screen);
    }
}

package net.worldbinder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.worldbinder.WorldBinder;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.reload.ReloadOverlayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class WorldBinderLoadingOverlayMixin implements ReloadOverlayState {
    @Shadow private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Unique private boolean worldbinder$hideReload;

    @Override
    public boolean worldbinder$allowsInteraction() {
        Screen screen = ClientCompat.screen(minecraft);
        return WorldBinder.config().reloadDisplayMode != WorldBinderConfig.ReloadDisplayMode.VANILLA
                && !reload.done().isCompletedExceptionally()
                && (minecraft.level != null || screen != null && !(screen instanceof GenericMessageScreen));
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void worldbinder$beforeRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo ci) {
        worldbinder$hideReload = false;
        if (!worldbinder$allowsInteraction() || minecraft.font == null) {
            return;
        }
        worldbinder$hideReload = true;
        graphics.enableScissor(0, 0, 0, 0);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void worldbinder$afterRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        if (!worldbinder$hideReload) {
            return;
        }
        worldbinder$hideReload = false;
        graphics.disableScissor();
        Screen screen = ClientCompat.screen(minecraft);
        if (screen != null) {
            screen.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }
}

package net.worldbinder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.ui.WorldBinderScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class GameMenuScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void worldbinder$addPauseButton(CallbackInfo ci) {
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        boolean running = WorldBinderClient.capture() != null && WorldBinderClient.capture().isCapturing();
        boolean paused = running && WorldBinderClient.capture().isPaused();

        Button openButton = Button.builder(Component.translatable("worldbinder.pause.open"), b ->
                client.setScreen(new WorldBinderScreen(WorldBinderClient.selections(), WorldBinderClient.capture(), WorldBinderClient.placement(), WorldBinderClient.scenes()))
        ).bounds(10, self.height - 54, 204, 20)
          .tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.pause_open")))
          .build();
        ((ScreenAccessor) self).worldbinder$addRenderableWidget(openButton);

        if (running) {
            Button pauseButton = Button.builder(Component.translatable(paused ? "worldbinder.gui.resume_capture" : "worldbinder.gui.pause_capture"), b -> {
                WorldBinderClient.capture().togglePause();
                client.setScreen(null);
            }).bounds(10, self.height - 30, 98, 20)
              .tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.pause_capture")))
              .build();
            ((ScreenAccessor) self).worldbinder$addRenderableWidget(pauseButton);

            Button finishButton = Button.builder(Component.translatable("worldbinder.pause.finish"), b -> {
                WorldBinderClient.capture().requestFinishCapture();
            }).bounds(116, self.height - 30, 98, 20)
              .tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.pause_finish")))
              .build();
            ((ScreenAccessor) self).worldbinder$addRenderableWidget(finishButton);
        }
    }
}

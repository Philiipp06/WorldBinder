package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.Lang;
import net.worldbinder.capture.SceneCaptureService;

public final class WorldBinderFinishScreen extends Screen {
    private final Screen parent;
    private final SceneCaptureService capture;

    public WorldBinderFinishScreen(Screen parent, SceneCaptureService capture) {
        super(Component.translatable("worldbinder.finish.title"));
        this.parent = parent;
        this.capture = capture;
    }

    @Override
    protected void init() {
        int w = 360;
        int left = (width - w) / 2;
        int y = height / 2 + 40;
        addRenderableWidget(Button.builder(Component.translatable("worldbinder.finish.finish_queue"), button -> {
            capture.finishAfterQueue();
            minecraft.setScreen(new WorldBinderFinishProgressScreen(parent, capture));
        }).bounds(left, y, 170, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.finish.finish_queue.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("worldbinder.finish.save_now"), button -> {
            capture.abortQueueAndSaveNow();
            minecraft.setScreen(new WorldBinderFinishProgressScreen(parent, capture));
        }).bounds(left + 190, y, 170, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.finish.save_now.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("worldbinder.config.cancel"), button -> {
            minecraft.setScreen(parent);
        }).bounds(left + 95, y + 30, 170, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD905050C);
        int w = 420;
        int h = 178;
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        context.fill(left, top, left + w, top + h, 0xEE11101C);
        context.fill(left, top, left + w, top + 3, 0xFFFF55FF);
        context.fill(left, top + h - 3, left + w, top + h, 0xFF5E03FC);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.finish.title"), width / 2, top + 18, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.finish.message"), left + 28, top + 48, 0xFFE6E6F0);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.finish.queue_chunks", capture.queuedChunkCount()), left + 28, top + 72, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.finish.estimate", capture.estimatedFinishText()), left + 28, top + 90, 0xFFBDB6D9);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}

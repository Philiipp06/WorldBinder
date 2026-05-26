package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTooltips;
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
        int realWidth = width;
        int realHeight = height;
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
            initScaled();
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    private void initScaled() {
        int panelW = Math.max(260, Math.min(420, width - 24));
        int left = (width - panelW) / 2;
        int y = height / 2 + 40;
        int buttonW = Math.max(90, Math.min(170, (panelW - 40) / 2));
        addRenderableWidget(WbTooltips.register(Button.builder(Component.translatable("worldbinder.finish.finish_queue"), button -> {
            capture.finishAfterQueue();
            minecraft.setScreen(new WorldBinderFinishProgressScreen(parent, capture));
        }).bounds(left + 10, y, buttonW, 22).build(), Component.translatable("worldbinder.finish.finish_queue.tooltip")));
        addRenderableWidget(WbTooltips.register(Button.builder(Component.translatable("worldbinder.finish.save_now"), button -> {
            capture.abortQueueAndSaveNow();
            minecraft.setScreen(new WorldBinderFinishProgressScreen(parent, capture));
        }).bounds(left + panelW - buttonW - 10, y, buttonW, 22).build(), Component.translatable("worldbinder.finish.save_now.tooltip")));
        addRenderableWidget(Button.builder(Component.translatable("worldbinder.config.cancel"), button -> {
            minecraft.setScreen(parent);
        }).bounds(left + (panelW - buttonW) / 2, y + 30, buttonW, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, 0x00000000);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
        context.fill(0, 0, width, height, 0xD905050C);
        int w = Math.max(260, Math.min(420, width - 24));
        int h = Math.max(160, Math.min(178, height - 24));
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        context.fill(left, top, left + w, top + h, 0xEE11101C);
        context.fill(left, top, left + w, top + 3, 0xFFFF55FF);
        context.fill(left, top + h - 3, left + w, top + h, 0xFF5E03FC);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.finish.title"), width / 2, top + 18, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.finish.message"), left + 28, top + 48, 0xFFE6E6F0);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.finish.queue_chunks", capture.queuedChunkCount()), left + 28, top + 72, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.finish.estimate", capture.estimatedFinishText()), left + 28, top + 90, 0xFFBDB6D9);
        super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
    }
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(WbLayout.virtualMouseEvent(event, width, height), doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }

}

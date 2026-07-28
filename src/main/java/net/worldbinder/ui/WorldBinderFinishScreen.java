package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTheme;
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
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
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
        addRenderableWidget(WbButton.create(left + 10, y, buttonW, 22, Component.translatable("worldbinder.finish.finish_queue"),
                Component.translatable("worldbinder.finish.finish_queue.tooltip"), button -> {
            capture.finishAfterQueue();
            minecraft.gui.setScreen(new WorldBinderFinishProgressScreen(parent, capture));
        }));
        addRenderableWidget(WbButton.create(left + panelW - buttonW - 10, y, buttonW, 22, Component.translatable("worldbinder.finish.save_now"),
                Component.translatable("worldbinder.finish.save_now.tooltip"), button -> {
            if (capture.abortQueueAndSaveNow()) {
                minecraft.gui.setScreen(new WorldBinderStorageProgressScreen(parent));
            }
        }));
        addRenderableWidget(WbButton.create(left + (panelW - buttonW) / 2, y + 30, buttonW, 22, Component.translatable("worldbinder.config.cancel"),
                Component.translatable("worldbinder.tooltip.close"), button -> {
            minecraft.gui.setScreen(parent);
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, WbTheme.BACKDROP);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
        int w = Math.max(260, Math.min(420, width - 24));
        int h = Math.max(160, Math.min(178, height - 24));
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        WbChrome.drawPanel(context, left, top, w, h);
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

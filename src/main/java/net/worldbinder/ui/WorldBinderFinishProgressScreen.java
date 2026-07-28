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

public final class WorldBinderFinishProgressScreen extends Screen {
    private final Screen parent;
    private final SceneCaptureService capture;

    public WorldBinderFinishProgressScreen(Screen parent, SceneCaptureService capture) {
        super(Component.translatable("worldbinder.finish.progress_title"));
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
        int buttonGap = 8;
        int panelW = 460;
        int panelH = 190;
        int buttonW = Math.max(90, Math.min(170, (panelW - 40 - buttonGap) / 2));
        int left = (width - buttonW * 2 - buttonGap) / 2;
        int y = (height - panelH) / 2 + panelH - 36;
        addRenderableWidget(WbButton.create(left, y, buttonW, 22, Component.translatable("worldbinder.finish.save_now"),
                Component.translatable("worldbinder.finish.save_now.tooltip"), button -> {
            if (capture.abortQueueAndSaveNow()) {
                minecraft.gui.setScreen(new WorldBinderStorageProgressScreen(parent));
            }
        }));
        addRenderableWidget(WbButton.create(left + buttonW + buttonGap, y, buttonW, 22, Component.translatable("worldbinder.gui.open_map"),
                Component.translatable("worldbinder.tooltip.open_map"), button -> {
            minecraft.gui.setScreen(new WorldBinderMapScreen(this));
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
        if (!capture.isCapturing()) {
            minecraft.gui.setScreen(parent);
            return;
        }
        int w = 460;
        int h = 190;
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        WbChrome.drawPanel(context, left, top, w, h);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.finish.progress_title"), width / 2, top + 18, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(capture.finishStatusLine()), left + 28, top + 50, 0xFFE6E6F0);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.finish.chunks_left", capture.queuedChunkCount()), left + 28, top + 72, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.finish.blocks_processed", capture.processedBlockCount()), left + 28, top + 90, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.common.eta_value", capture.estimatedFinishText()), left + 28, top + 108, 0xFFBDB6D9);
        int barX = left + 28;
        int barY = top + 132;
        int barW = w - 56;
        int queueStart = Math.max(1, capture.queuedChunkCount() + capture.scannedChunks());
        int filled = (int) (barW * Math.min(1.0D, capture.scannedChunks() / (double) queueStart));
        WbChrome.drawProgressTrack(context, barX, barY, barW, 10, filled, WbTheme.ACCENT);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.finish.progress_hint"), left + 28, top + 152, 0xFF8F86B8);
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

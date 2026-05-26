package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.profiling.StorageProfiler;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.storage.StorageStage;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;

import java.util.Map;

public final class WorldBinderProfilerScreen extends Screen {
    private final Screen parent;
    private int scrollOffset;
    private int maxScroll;

    public WorldBinderProfilerScreen(Screen parent) {
        super(Lang.text("worldbinder.profiler.title"));
        this.parent = parent;
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
        addRenderableWidget(WbTooltips.register(Button.builder(Lang.text("worldbinder.gui.back"), b -> onClose())
                .bounds(Math.max(18, width - 108), Math.max(18, height - 32), 90, 22)
                .build(), Lang.text("worldbinder.profiler.back.tooltip")));
        addRenderableWidget(WbTooltips.register(Button.builder(Lang.text("worldbinder.section.map"), b -> minecraft.setScreen(new WorldBinderMapScreen(this)))
                .bounds(18, Math.max(18, height - 32), 90, 22)
                .build(), Lang.text("worldbinder.profiler.map.tooltip")));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        double virtualMouseX = uiScale.toVirtualX(mouseX);
        double virtualMouseY = uiScale.toVirtualY(mouseY);
        if (maxScroll <= 0) {
            return super.mouseScrolled(virtualMouseX, virtualMouseY, horizontalAmount, verticalAmount);
        }
        int before = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (verticalAmount < 0 ? 28 : -28)));
        return before != scrollOffset || super.mouseScrolled(virtualMouseX, virtualMouseY, horizontalAmount, verticalAmount);
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
            context.fill(0, 0, width, height, 0xD805050C);
            int panelW = Math.max(300, Math.min(860, width - 28));
            int panelH = Math.max(260, Math.min(500, height - 42));
            int left = (width - panelW) / 2;
            int top = (height - panelH) / 2;
            drawPanel(context, left, top, panelW, panelH);
            GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.profiler.header"), width / 2, top + 15, 0xFFFFFFFF);
            GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.profiler.subheader"), width / 2, top + 34, 0xFFBDB6D9);

            SceneCaptureService capture = WorldBinderClient.capture();
            StorageProgress progress = StorageFlow.progress();
            boolean narrow = panelW < 600;
            int visibleH = Math.max(120, panelH - 106);
            int contentH = narrow ? 620 : 410;
            maxScroll = Math.max(0, contentH - visibleH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            int contentTop = top + 70 - scrollOffset;
            int contentX = left + 26;
            int contentW = panelW - 52;

            int chipGap = 10;
            int chipW = Math.max(92, (contentW - chipGap * 3) / 4);
            drawMetricChip(context, contentX, contentTop, chipW, Lang.string("worldbinder.profiler.mode"), capture.modeName(), 0xFF55FFAA);
            drawMetricChip(context, contentX + chipW + chipGap, contentTop, chipW, Lang.string("worldbinder.profiler.progress"), (int) (progress.progress() * 100.0D) + "%", 0xFFFF55FF);
            drawMetricChip(context, contentX + (chipW + chipGap) * 2, contentTop, chipW, Lang.string("worldbinder.profiler.queue"), Integer.toString(capture.queuedChunks()), capture.highQueuePressure() ? 0xFFFF5555 : 0xFFFFAA33);
            drawMetricChip(context, contentX + (chipW + chipGap) * 3, contentTop, chipW, Lang.string("worldbinder.profiler.failed"), Integer.toString(StorageProfiler.failedJobs()), StorageProfiler.failedJobs() > 0 ? 0xFFFF5555 : 0xFF8F86B8);

            int cardsTop = contentTop + 58;
            int cardW = narrow ? contentW : (contentW - 22) / 2;
            int leftCardH = 154;
            drawCard(context, contentX, cardsTop, cardW, leftCardH, Lang.string("worldbinder.profiler.live_capture"));
            line(context, contentX + 16, cardsTop + 34, Lang.string("worldbinder.profiler.blocks"), Integer.toString(capture.capturedBlocks()));
            line(context, contentX + 16, cardsTop + 54, Lang.string("worldbinder.profiler.entities"), Integer.toString(capture.capturedEntities()));
            line(context, contentX + 16, cardsTop + 74, Lang.string("worldbinder.profiler.chunks"), Lang.string("worldbinder.profiler.chunks_value", capture.scannedChunks(), capture.partialChunks(), capture.queuedChunks()));
            line(context, contentX + 16, cardsTop + 94, Lang.string("worldbinder.profiler.queue_eta"), capture.estimatedFinishText());
            line(context, contentX + 16, cardsTop + 114, Lang.string("worldbinder.profiler.throttle"), capture.adaptiveThrottlePercent() + "%");

            int storageX = narrow ? contentX : contentX + cardW + 22;
            int storageY = narrow ? cardsTop + leftCardH + 14 : cardsTop;
            drawCard(context, storageX, storageY, cardW, leftCardH, Lang.string("worldbinder.profiler.storage_flow"));
            line(context, storageX + 16, storageY + 34, Lang.string("worldbinder.profiler.stage"), progress.stage().label());
            line(context, storageX + 16, storageY + 54, Lang.string("worldbinder.profiler.detail"), shorten(localizeDetail(progress.detail()), 48));
            line(context, storageX + 16, storageY + 74, Lang.string("worldbinder.profiler.elapsed"), millis(progress.elapsedMillis()));
            line(context, storageX + 16, storageY + 94, Lang.string("worldbinder.profiler.completed"), Integer.toString(StorageProfiler.completedJobs()));
            drawProgress(context, storageX + 16, storageY + 124, cardW - 32, progress.progress(), progress.isTerminal() ? 0xFF55FFAA : 0xFFFF55FF);

            int timingsY = narrow ? storageY + leftCardH + 14 : cardsTop + leftCardH + 18;
            int timingsH = Math.max(126, top + panelH - timingsY - 44);
            drawCard(context, contentX, timingsY, contentW, timingsH, Lang.string("worldbinder.profiler.stage_timings"));
            drawTimings(context, contentX + 16, timingsY + 34, contentW - 32);
            super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
    }

    private void drawTimings(GuiGraphicsExtractor context, int x, int y, int width) {
        Map<StorageStage, Long> times = StorageProfiler.stageTimesSnapshot();
        long max = 1L;
        for (Long value : times.values()) {
            max = Math.max(max, value == null ? 0L : value);
        }
        int row = 0;
        for (StorageStage stage : StorageStage.values()) {
            if (stage == StorageStage.IDLE) continue;
            long value = times.getOrDefault(stage, 0L);
            int yy = y + row * 18;
            int labelW = Math.min(190, Math.max(120, width / 4));
            WbText.drawClipped(context, font, stage.label(), x, yy, labelW, stage == StorageProfiler.currentStage() ? WbTheme.ACCENT : WbTheme.TEXT);
            int barX = x + labelW + 12;
            int timeW = 62;
            int barW = Math.max(50, width - labelW - timeW - 28);
            int fill = (int) (barW * (value / (double) max));
            context.fill(barX, yy + 4, barX + barW, yy + 11, 0x55000000);
            context.fill(barX, yy + 4, barX + fill, yy + 11, stage == StorageProfiler.currentStage() ? WbTheme.ACCENT : WbTheme.OK);
            WbText.drawClipped(context, font, millis(value), barX + barW + 10, yy, timeW, WbTheme.TEXT_MUTED);
            row++;
            if (row > 8) break;
        }
    }

    private void drawPanel(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xEE090914);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xCC121225);
        c.fill(x, y, x + w, y + 3, WbTheme.ACCENT);
        c.fill(x, y + h - 3, x + w, y + h, WbTheme.ACCENT_DARK);
        c.fill(x, y, x + 3, y + h, WbTheme.ACCENT_DARK);
        c.fill(x + w - 3, y, x + w, y + h, WbTheme.ACCENT);
    }

    private void drawCard(GuiGraphicsExtractor c, int x, int y, int w, int h, String title) {
        c.fill(x, y, x + w, y + h, 0xAA090914);
        c.fill(x, y, x + w, y + 1, WbTheme.ACCENT);
        WbText.drawClipped(c, font, title, x + 12, y + 10, w - 24, WbTheme.ACCENT);
    }

    private void drawMetricChip(GuiGraphicsExtractor c, int x, int y, int w, String label, String value, int accent) {
        c.fill(x, y, x + w, y + 42, 0xAA090914);
        c.fill(x, y, x + w, y + 2, accent);
        WbText.drawClipped(c, font, label, x + 10, y + 9, w - 20, WbTheme.TEXT_DIM);
        WbText.drawClipped(c, font, value, x + 10, y + 24, w - 20, WbTheme.TEXT);
    }

    private void drawProgress(GuiGraphicsExtractor c, int x, int y, int w, double progress, int accent) {
        int clamped = Math.max(0, Math.min(w, (int) Math.round(w * progress)));
        c.fill(x, y, x + w, y + 8, 0x66000000);
        c.fill(x, y, x + clamped, y + 8, accent);
    }

    private void line(GuiGraphicsExtractor c, int x, int y, String label, String value) {
        WbText.drawClipped(c, font, label + ":", x, y, 116, WbTheme.TEXT_MUTED);
        WbText.drawClipped(c, font, value, x + 118, y, 360, WbTheme.TEXT);
    }

    private static String millis(long ms) {
        if (ms < 1000L) return ms + "ms";
        return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0D);
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String localizeDetail(String value) {
        if (value == null || value.isBlank()) return "";
        return value.startsWith("worldbinder.") ? Lang.string(value) : value;
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

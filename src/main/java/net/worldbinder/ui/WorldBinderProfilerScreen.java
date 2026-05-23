package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.profiling.StorageProfiler;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.util.Lang;
import net.worldbinder.storage.StorageStage;

import java.util.Map;

public final class WorldBinderProfilerScreen extends Screen {
    private final Screen parent;

    public WorldBinderProfilerScreen(Screen parent) {
        super(Lang.text("worldbinder.profiler.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Lang.text("worldbinder.gui.back"), b -> onClose())
                .bounds(Math.max(18, width - 108), Math.max(18, height - 32), 90, 22)
                .tooltip(Tooltip.create(Lang.text("worldbinder.profiler.back.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Lang.text("worldbinder.section.map"), b -> {
                    minecraft.setScreen(new WorldBinderMapScreen(this));
                })
                .bounds(18, Math.max(18, height - 32), 90, 22)
                .tooltip(Tooltip.create(Lang.text("worldbinder.profiler.map.tooltip")))
                .build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF005050C);
        int panelW = Math.max(240, Math.min(860, width - 24));
        int panelH = Math.max(180, Math.min(500, height - 48));
        int left = Math.max(12, (width - panelW) / 2);
        int top = 18;
        drawPanel(context, left, top, panelW, panelH);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.profiler.header"), width / 2, top + 14, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.profiler.subheader"), width / 2, top + 32, 0xFFBDB6D9);

        SceneCaptureService capture = WorldBinderClient.capture();
        boolean narrow = panelW < 560;
        int cardW = narrow ? panelW - 52 : (panelW - 78) / 2;
        drawCard(context, left + 26, top + 64, cardW, 164, Lang.string("worldbinder.profiler.live_capture"));
        line(context, left + 44, top + 96, Lang.string("worldbinder.profiler.mode"), capture.modeName());
        line(context, left + 44, top + 118, Lang.string("worldbinder.profiler.blocks"), Integer.toString(capture.capturedBlocks()));
        line(context, left + 44, top + 140, Lang.string("worldbinder.profiler.entities"), Integer.toString(capture.capturedEntities()));
        line(context, left + 44, top + 162, Lang.string("worldbinder.profiler.chunks"), Lang.string("worldbinder.profiler.chunks_value", capture.scannedChunks(), capture.partialChunks(), capture.queuedChunks()));
        line(context, left + 44, top + 184, Lang.string("worldbinder.profiler.queue_eta"), capture.estimatedFinishText() + "");
        line(context, left + 44, top + 206, Lang.string("worldbinder.profiler.throttle"), capture.adaptiveThrottlePercent() + "%");

        StorageProgress progress = StorageFlow.progress();
        drawCard(context, narrow ? left + 26 : left + 52 + cardW, narrow ? top + 238 : top + 64, cardW, 164, Lang.string("worldbinder.profiler.storage_flow"));
        int storageX = narrow ? left + 44 : left + 70 + cardW;
        int storageY = narrow ? top + 270 : top + 96;
        line(context, storageX, storageY, Lang.string("worldbinder.profiler.stage"), progress.stage().label());
        line(context, storageX, storageY + 22, Lang.string("worldbinder.profiler.detail"), shorten(progress.detail(), 42));
        line(context, storageX, storageY + 44, Lang.string("worldbinder.profiler.progress"), (int) (progress.progress() * 100.0D) + "%");
        line(context, storageX, storageY + 66, Lang.string("worldbinder.profiler.elapsed"), millis(progress.elapsedMillis()));
        line(context, storageX, storageY + 88, Lang.string("worldbinder.profiler.completed"), Integer.toString(StorageProfiler.completedJobs()));
        line(context, storageX, storageY + 110, Lang.string("worldbinder.profiler.failed"), Integer.toString(StorageProfiler.failedJobs()));

        drawCard(context, left + 26, narrow ? top + 412 : top + 246, panelW - 52, Math.max(110, panelH - (narrow ? 430 : 260)), Lang.string("worldbinder.profiler.stage_timings"));
        int x = left + 44;
        int y = narrow ? top + 444 : top + 278;
        Map<StorageStage, Long> times = StorageProfiler.stageTimesSnapshot();
        long max = 1L;
        for (Long value : times.values()) max = Math.max(max, value == null ? 0L : value);
        int row = 0;
        for (StorageStage stage : StorageStage.values()) {
            if (stage == StorageStage.IDLE) continue;
            long value = times.getOrDefault(stage, 0L);
            int yy = y + row * 18;
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(stage.label()), x, yy, stage == StorageProfiler.currentStage() ? 0xFFFF55FF : 0xFFE6E6F0);
            int barX = x + 210;
            int barW = Math.max(40, panelW - 360);
            int fill = (int) (barW * (value / (double) max));
            context.fill(barX, yy + 3, barX + barW, yy + 10, 0x55000000);
            context.fill(barX, yy + 3, barX + fill, yy + 10, stage == StorageProfiler.currentStage() ? 0xFFFF55FF : 0xFF55FFAA);
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(millis(value)), barX + barW + 12, yy, 0xFFBDB6D9);
            row++;
            if (row > 8) break;
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawPanel(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xEE090914);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xCC121225);
        c.fill(x, y, x + w, y + 3, 0xFFFF55FF);
        c.fill(x, y + h - 3, x + w, y + h, 0xFF5E03FC);
    }

    private void drawCard(GuiGraphicsExtractor c, int x, int y, int w, int h, String title) {
        c.fill(x, y, x + w, y + h, 0xAA090914);
        c.fill(x, y, x + w, y + 1, 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawTextWithShadow(c, font, Component.literal(title), x + 12, y + 10, 0xFFFF55FF);
    }

    private void line(GuiGraphicsExtractor c, int x, int y, String label, String value) {
        net.worldbinder.util.GuiText.drawTextWithShadow(c, font, Component.literal("§8" + label + ": §f" + value), x, y, 0xFFE6E6F0);
    }

    private static String millis(long ms) {
        if (ms < 1000L) return ms + "ms";
        return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0D);
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }
}

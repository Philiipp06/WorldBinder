package net.worldbinder.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorldBinderHud {
    private static final int STATUS_WIDTH = 520;
    private static final int STATUS_FULL_HEIGHT = 76;
    private static final int STATUS_STORAGE_HEIGHT = 58;
    private static final int STATUS_DETAIL_HEIGHT = 42;
    private static final RadarRenderCache RADAR_CACHE = new RadarRenderCache();

    private WorldBinderHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("worldbinder", "hud"), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            if (client.gui.hud.isHidden()) {
                WorldBinderNotifications.visible(0);
                return;
            }
            SceneCaptureService capture = WorldBinderClient.capture();
            WorldBinderConfig config = WorldBinder.config();
            int bossbarBottom = 0;
            if (config.showBossbarOverlay && OperationStatus.visible()) {
                bossbarBottom = drawBossbar(context, client, capture);
            }
            boolean showRadar = capture != null && capture.isRoamingCapture() && config.showChunkRadar;
            RadarLayout radarLayout = showRadar ? layoutChunkRadar(client, config) : null;
            WorldBinderNotificationOverlay.draw(
                    context,
                    client,
                    bossbarBottom,
                    radarLayout == null ? 0 : radarLayout.y(),
                    radarLayout == null ? 0 : radarLayout.y() + radarLayout.panelHeight()
            );
            if (showRadar) {
                drawChunkRadar(context, client, capture, radarLayout);
            }
        });
    }

    private static int drawBossbar(net.minecraft.client.gui.GuiGraphicsExtractor context, Minecraft client, SceneCaptureService capture) {
        Font renderer = client.font;
        WorldBinderConfig config = WorldBinder.config();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        StorageProgress storage = StorageFlow.progress();
        boolean storageMode = storage.isRunning();
        boolean captureMode = capture != null && capture.isCapturing();
        int logicalHeight = storageMode ? STATUS_STORAGE_HEIGHT : captureMode ? STATUS_FULL_HEIGHT : STATUS_DETAIL_HEIGHT;
        float scale = statusScale(screenWidth, screenHeight, config, logicalHeight);
        int physicalWidth = Math.max(1, Math.round(STATUS_WIDTH * scale));
        int physicalHeight = Math.max(1, Math.round(logicalHeight * scale));
        int x = WorldBinderWidgetLayout.positionX(screenWidth, physicalWidth, config.bossbarWidgetXPercent);
        int y = WorldBinderWidgetLayout.positionY(screenHeight, physicalHeight, config.bossbarWidgetYPercent);

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        try {
            drawBossbarContents(context, renderer, config, capture, storage, storageMode, captureMode, logicalHeight);
        } finally {
            context.pose().popMatrix();
        }
        return y + physicalHeight;
    }

    private static void drawBossbarContents(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            Font renderer,
            WorldBinderConfig config,
            SceneCaptureService capture,
            StorageProgress storage,
            boolean storageMode,
            boolean captureMode,
            int logicalHeight
    ) {
        double shownProgress = storageMode ? storage.progress() : OperationStatus.progress();
        int fill = (int) ((STATUS_WIDTH - 4) * Math.max(0.0D, Math.min(1.0D, shownProgress)));
        context.fill(3, 4, STATUS_WIDTH + 3, logicalHeight + 4, withAlpha(config.bossbarBackgroundColor, 0x88));
        context.fill(0, 0, STATUS_WIDTH, logicalHeight, withAlpha(config.bossbarBackgroundColor, 0xE6));
        context.fill(0, 0, STATUS_WIDTH, 18, config.bossbarBackgroundColor);
        context.fill(2, 2, 2 + fill, 16, storageMode || OperationStatus.active() ? config.bossbarAccentColor : WbTheme.OK);
        context.fill(0, 0, STATUS_WIDTH, 1, config.bossbarAccentColor);
        context.fill(0, 17, STATUS_WIDTH, 18, WbTheme.INFO);

        if (storageMode) {
            GuiText.drawCenteredTextWithShadow(context, renderer, Lang.text("worldbinder.storage.title"), STATUS_WIDTH / 2, 5, WbTheme.TEXT);
            GuiText.drawCenteredTextWithShadow(
                    context,
                    renderer,
                    Component.literal(shorten(storage.stage().label() + " • " + (int) (storage.progress() * 100.0D) + "%", 76)),
                    STATUS_WIDTH / 2,
                    28,
                    WbTheme.TEXT_SOFT
            );
            GuiText.drawCenteredTextWithShadow(
                    context,
                    renderer,
                    Lang.text("worldbinder.storage.elapsed_remaining", StorageProgress.formatMillis(storage.elapsedMillis()), storage.etaText()),
                    STATUS_WIDTH / 2,
                    44,
                    WbTheme.TEXT_DIM
            );
            return;
        }

        GuiText.drawCenteredTextWithShadow(context, renderer, Component.literal(shorten(OperationStatus.title(), 76)), STATUS_WIDTH / 2, 5, WbTheme.TEXT);
        if (!captureMode) {
            GuiText.drawCenteredTextWithShadow(context, renderer, Component.literal(shorten(OperationStatus.detail(), 78)), STATUS_WIDTH / 2, 25, WbTheme.TEXT_SOFT);
            return;
        }

        GuiText.drawCenteredTextWithShadow(context, renderer, Lang.text(capture.isPaused() ? "worldbinder.common.paused" : "worldbinder.hud.downloading"), STATUS_WIDTH / 2, 25, WbTheme.TEXT_SOFT);
        int done = capture.scannedChunks();
        int scanning = capture.partialChunks();
        int queued = capture.queuedChunks();
        int total = Math.max(1, done + scanning + queued);
        int gap = 8;
        int meterW = (STATUS_WIDTH - gap * 2) / 3;
        drawMeter(context, renderer, 0, 43, meterW, Lang.string("worldbinder.hud.meter.chunks"), done + "/" + total, done / (double) total, WbTheme.OK);
        drawMeter(context, renderer, meterW + gap, 43, meterW, Lang.string("worldbinder.hud.meter.entities"), Integer.toString(capture.capturedEntities()), Math.min(1.0D, capture.capturedEntities() / 1000.0D), config.bossbarAccentColor);
        drawMeter(context, renderer, (meterW + gap) * 2, 43, meterW, Lang.string("worldbinder.hud.meter.queue"), Integer.toString(queued), Math.min(1.0D, queued / 512.0D), queued > 512 ? WbTheme.ERROR : WbTheme.WARN);
    }

    private static float statusScale(int screenWidth, int screenHeight, WorldBinderConfig config, int logicalHeight) {
        float requested = hudScale(screenWidth, screenHeight) * config.effectiveBossbarScalePercent() / 100.0F;
        float fit = Math.min(
                (screenWidth - WorldBinderWidgetLayout.SCREEN_MARGIN * 2) / (float) STATUS_WIDTH,
                (screenHeight - WorldBinderWidgetLayout.SCREEN_MARGIN * 2) / (float) logicalHeight
        );
        return Math.max(0.2F, Math.min(requested, fit));
    }

    private static void drawMeter(net.minecraft.client.gui.GuiGraphicsExtractor context, Font renderer, int x, int y, int w, String label, String value, double progress, int color) {
        context.fill(x, y, x + w, y + 24, 0x77000000);
        context.fill(x, y, x + w, y + 1, WbTheme.ACCENT_SOFT);
        int fill = (int) ((w - 6) * Math.max(0.0D, Math.min(1.0D, progress)));
        context.fill(x + 3, y + 15, x + w - 3, y + 20, 0x33000000);
        context.fill(x + 3, y + 15, x + 3 + fill, y + 20, color);
        String text = shorten(label + ": §f" + value, Math.max(8, (w - 10) / 6));
        net.worldbinder.util.GuiText.drawTextWithShadow(context, renderer, Component.literal(text), x + 5, y + 4, 0xFFBDB6D9);
    }

    public static WidgetBounds previewBounds(Minecraft client, WorldBinderConfig config, WorldBinderWidgetLayout.Widget widget) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int width;
        int height;
        if (widget == WorldBinderWidgetLayout.Widget.STATUS) {
            float scale = statusScale(screenW, screenH, config, STATUS_FULL_HEIGHT);
            width = Math.max(1, Math.round(STATUS_WIDTH * scale));
            height = Math.max(1, Math.round(STATUS_FULL_HEIGHT * scale));
        } else if (widget == WorldBinderWidgetLayout.Widget.RADAR) {
            RadarLayout radar = layoutChunkRadar(client, config);
            width = radar.panelWidth();
            height = radar.panelHeight();
        } else {
            width = WorldBinderNotificationOverlay.preferredWidth(config, screenW, screenH);
            height = WorldBinderNotificationOverlay.previewHeight(config, screenW, screenH);
        }
        int x = WorldBinderWidgetLayout.positionX(screenW, width, WorldBinderWidgetLayout.xPercent(config, widget));
        int y = WorldBinderWidgetLayout.positionY(screenH, height, WorldBinderWidgetLayout.yPercent(config, widget));
        return new WidgetBounds(x, y, width, height);
    }

    public static void drawWidgetPreview(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            Minecraft client,
            WorldBinderConfig config,
            WorldBinderWidgetLayout.Widget widget
    ) {
        WidgetBounds bounds = previewBounds(client, config, widget);
        if (widget == WorldBinderWidgetLayout.Widget.STATUS) {
            drawBossbarPreview(context, client.font, config, bounds);
        } else if (widget == WorldBinderWidgetLayout.Widget.RADAR) {
            drawRadarPreview(context, client, config, bounds);
        } else {
            WorldBinderNotificationOverlay.drawPreview(context, client.font, config, bounds.x(), bounds.y(), bounds.width());
        }
    }

    private static void drawBossbarPreview(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            Font font,
            WorldBinderConfig config,
            WidgetBounds bounds
    ) {
        float scale = bounds.width() / (float) STATUS_WIDTH;
        context.pose().pushMatrix();
        context.pose().translate(bounds.x(), bounds.y());
        context.pose().scale(scale, scale);
        try {
            context.fill(3, 4, STATUS_WIDTH + 3, STATUS_FULL_HEIGHT + 4, 0x77000000);
            context.fill(0, 0, STATUS_WIDTH, STATUS_FULL_HEIGHT, withAlpha(config.bossbarBackgroundColor, 0xE6));
            context.fill(0, 0, STATUS_WIDTH, 18, config.bossbarBackgroundColor);
            context.fill(2, 2, 2 + Math.round((STATUS_WIDTH - 4) * 0.42F), 16, config.bossbarAccentColor);
            context.fill(0, 0, STATUS_WIDTH, 2, config.bossbarAccentColor);
            GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.widget_editor.preview.status_title"), STATUS_WIDTH / 2, 5, WbTheme.TEXT);
            GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.widget_editor.preview.status_message"), STATUS_WIDTH / 2, 25, WbTheme.TEXT_SOFT);
            int gap = 8;
            int meterW = (STATUS_WIDTH - gap * 2) / 3;
            drawMeter(context, font, 0, 45, meterW, Lang.string("worldbinder.hud.meter.chunks"), "84/120", 0.7D, WbTheme.OK);
            drawMeter(context, font, meterW + gap, 45, meterW, Lang.string("worldbinder.hud.meter.entities"), "12", 0.35D, config.bossbarAccentColor);
            drawMeter(context, font, (meterW + gap) * 2, 45, meterW, Lang.string("worldbinder.hud.meter.queue"), "36", 0.3D, WbTheme.WARN);
        } finally {
            context.pose().popMatrix();
        }
    }

    private static void drawRadarPreview(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            Minecraft client,
            WorldBinderConfig config,
            WidgetBounds bounds
    ) {
        RadarLayout layout = layoutChunkRadar(client, config);
        int panelW = layout.logicalWidth();
        int panelH = layout.logicalHeight();
        int gridSize = layout.size();
        int cell = layout.cell();
        int grid = layout.grid();
        float scale = bounds.width() / (float) panelW;

        context.pose().pushMatrix();
        context.pose().translate(bounds.x(), bounds.y());
        context.pose().scale(scale, scale);
        try {
            context.fill(3, 4, panelW + 3, panelH + 4, 0x77000000);
            context.fill(0, 0, panelW, panelH, config.chunkRadarBackgroundColor);
            context.fill(0, 0, panelW, 2, config.chunkRadarAccentColor);
            context.fill(0, 20, panelW, 21, 0x5534485A);
            GuiText.drawTextWithShadow(context, client.font, Component.translatable("worldbinder.widget_editor.preview.radar_title"), 7, 6, WbTheme.TEXT);

            int gridX = (panelW - grid) / 2;
            int gridY = 25;
            context.fill(gridX - 3, gridY - 3, gridX + grid + 3, gridY + grid + 3, 0xAA060A10);
            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    int color = ((row + col) % 4 == 0) ? 0xAA24506D : 0xAA2D6B4E;
                    int cellX = gridX + col * cell;
                    int cellY = gridY + row * cell;
                    context.fill(cellX, cellY, cellX + cell - 1, cellY + cell - 1, color);
                }
            }
            int center = gridSize / 2;
            int centerX = gridX + center * cell;
            int centerY = gridY + center * cell;
            context.fill(centerX, centerY + cell / 2, centerX + cell - 1, centerY + cell / 2 + 1, config.chunkRadarAccentColor);
            context.fill(centerX + cell / 2, centerY, centerX + cell / 2 + 1, centerY + cell - 1, config.chunkRadarAccentColor);
        } finally {
            context.pose().popMatrix();
        }
    }

    private static RadarLayout layoutChunkRadar(Minecraft client, WorldBinderConfig config) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int requestedSize = Math.max(1, config.chunkRadarSize | 1);
        int size = cappedOddSize(requestedSize, config.effectiveRadarMaxRenderedChunks());
        int cell = Math.max(2, Math.min(16, config.chunkRadarCellSize));
        int grid = size * cell;
        int logicalW = Math.max(92, grid + 22);
        int logicalH = grid + 38;
        float requestedScale = hudScale(screenW, screenH) * config.effectiveChunkRadarScalePercent() / 100.0F;
        float fitScale = Math.min(
                (screenW - WorldBinderWidgetLayout.SCREEN_MARGIN * 2) / (float) logicalW,
                (screenH - WorldBinderWidgetLayout.SCREEN_MARGIN * 2) / (float) logicalH
        );
        float scale = Math.max(0.2F, Math.min(requestedScale, fitScale));
        int panelW = Math.max(1, Math.round(logicalW * scale));
        int panelH = Math.max(1, Math.round(logicalH * scale));
        int x = WorldBinderWidgetLayout.positionX(screenW, panelW, config.chunkRadarWidgetXPercent);
        int y = WorldBinderWidgetLayout.positionY(screenH, panelH, config.chunkRadarWidgetYPercent);
        return new RadarLayout(x, y, panelW, panelH, logicalW, logicalH, scale, requestedSize, size, cell, grid);
    }

    private static void drawChunkRadar(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            Minecraft client,
            SceneCaptureService capture,
            RadarLayout layout
    ) {
        WorldBinderConfig config = WorldBinder.config();
        int x = layout.x();
        int y = layout.y();
        int panelW = layout.logicalWidth();
        int panelH = layout.logicalHeight();
        int requestedSize = layout.requestedSize();
        int size = layout.size();
        int cell = layout.cell();
        int grid = layout.grid();

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(layout.scale(), layout.scale());
        try {
            context.fill(3, 4, panelW + 3, panelH + 4, 0x88000000);
            context.fill(0, 0, panelW, panelH, config.chunkRadarBackgroundColor);
            context.fill(1, 1, panelW - 1, panelH - 1, 0x332B3A4A);
            context.fill(0, 0, panelW, 2, config.chunkRadarAccentColor);
            context.fill(0, 20, panelW, 21, 0x5534485A);

            int playerChunkX = client.player.blockPosition().getX() >> 4;
            int playerChunkZ = client.player.blockPosition().getZ() >> 4;
            String title = Lang.string("worldbinder.hud.chunk_title", playerChunkX, playerChunkZ);
            if (size != requestedSize) {
                title += " • capped";
            }
            net.worldbinder.util.GuiText.drawTextWithShadow(context, client.font, Component.literal(shorten(title, Math.max(10, (panelW - 14) / 6))), 7, 6, WbTheme.TEXT);

            RadarRenderFrame frame = RADAR_CACHE.frame(capture, config, playerChunkX, playerChunkZ, size, cell);
            int center = size / 2;
            int gridX = (panelW - grid) / 2;
            int gridY = 25;
            context.fill(gridX - 3, gridY - 3, gridX + grid + 3, gridY + grid + 3, 0xBB060A10);
            for (RadarCell radarCell : frame.cells) {
                int px = gridX + (radarCell.dx + center) * cell;
                int py = gridY + (radarCell.dz + center) * cell;
                drawRadarCell(context, radarCell, px, py, cell, frame.detailCells, config.radarLayerMode);
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    private static float hudScale(int screenWidth, int screenHeight) {
        float scale = Math.min(screenWidth / 960.0F, screenHeight / 540.0F);
        return Math.max(0.55F, Math.min(1.0F, scale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static int cappedOddSize(int requestedSize, int maxRenderedChunks) {
        if ((long) requestedSize * (long) requestedSize <= maxRenderedChunks) {
            return requestedSize;
        }
        int size = Math.max(1, (int) Math.floor(Math.sqrt(Math.max(1, maxRenderedChunks))));
        if ((size & 1) == 0) {
            size--;
        }
        return Math.max(1, size);
    }

    private static void drawRadarCell(net.minecraft.client.gui.GuiGraphicsExtractor context, RadarCell cellData, int x, int y, int cell, int detailCells, WorldBinderConfig.MapLayerMode mode) {
        context.fill(x, y, x + cell - 1, y + cell - 1, cellData.baseColor);

        if (mode != WorldBinderConfig.MapLayerMode.CHUNKS_ONLY && detailCells > 1 && cellData.detailColors != null) {
            for (int z = 0; z < detailCells; z++) {
                for (int lx = 0; lx < detailCells; lx++) {
                    int px1 = x + lx * cell / detailCells;
                    int py1 = y + z * cell / detailCells;
                    int px2 = x + (lx + 1) * cell / detailCells;
                    int py2 = y + (z + 1) * cell / detailCells;
                    context.fill(px1, py1, Math.min(x + cell - 1, px2), Math.min(y + cell - 1, py2), cellData.detailColors[z * detailCells + lx]);
                }
            }
        }

        if (mode != WorldBinderConfig.MapLayerMode.MAP_ONLY || cellData.player) {
            context.fill(x, y, x + cell - 1, y + 1, cellData.borderColor);
            context.fill(x, y + cell - 2, x + cell - 1, y + cell - 1, cellData.borderColor);
            context.fill(x, y, x + 1, y + cell - 1, cellData.borderColor);
            context.fill(x + cell - 2, y, x + cell - 1, y + cell - 1, cellData.borderColor);
        }
        if (cellData.player) {
            int c = WbTheme.ACCENT_RIGHT;
            int mid = Math.max(1, cell / 2);
            context.fill(x, y + mid, x + cell - 1, y + mid + 1, c);
            context.fill(x + mid, y, x + mid + 1, y + cell - 1, c);
        }
    }

    private static ChunkCaptureStatus statusOf(ChunkSnapshot snapshot, boolean saved, boolean scanning, boolean queued, boolean failed) {
        if (failed || (snapshot != null && snapshot.exportError)) return ChunkCaptureStatus.FAILED;
        if (saved) return ChunkCaptureStatus.DONE;
        if (scanning) return snapshot != null && snapshot.effectiveStatus() == ChunkCaptureStatus.PARTIAL ? ChunkCaptureStatus.PARTIAL : ChunkCaptureStatus.SCANNING;
        if (queued) return ChunkCaptureStatus.QUEUED;
        return snapshot == null ? ChunkCaptureStatus.UNKNOWN : snapshot.effectiveStatus();
    }

    private static int statusFill(ChunkCaptureStatus status) {
        return switch (status) {
            case DONE -> 0xAA2D6B4E;
            case SCANNING -> 0xAA24506D;
            case PARTIAL, RECOVERY -> 0xAA6B5A21;
            case QUEUED -> 0xAA67441F;
            case FAILED -> 0xAA642330;
            case UNKNOWN -> 0xAA202A3A;
        };
    }

    private static int statusBorder(ChunkCaptureStatus status) {
        return switch (status) {
            case DONE -> WbTheme.OK;
            case SCANNING -> WbTheme.INFO;
            case PARTIAL, RECOVERY -> WbTheme.WARN;
            case QUEUED -> WbTheme.ACCENT_RIGHT;
            case FAILED -> WbTheme.ERROR;
            case UNKNOWN -> 0x775B6B80;
        };
    }

    private static int chooseDetailCells(WorldBinderConfig config, int renderedChunks, int cell, boolean reduced) {
        if (reduced) {
            return 1;
        }
        WorldBinderConfig.RadarDetailMode mode = config.radarDetailMode == null ? WorldBinderConfig.RadarDetailMode.AUTO : config.radarDetailMode;
        return switch (mode) {
            case LOW -> 1;
            case MEDIUM -> cell >= 10 && renderedChunks <= 169 ? 2 : 1;
            case HIGH -> cell >= 12 && renderedChunks <= 121 ? 4 : cell >= 10 ? 2 : 1;
            case AUTO -> {
                if (cell < 12 || renderedChunks > 121) yield 1;
                if (cell >= 24 && renderedChunks <= 49) yield 4;
                yield 2;
            }
        };
    }

    private static boolean shouldReduceDetail(SceneCaptureService capture, WorldBinderConfig config) {
        if (!config.effectiveAdaptiveThrottleEnabled()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        int fps = client == null ? config.targetFps : Math.max(1, client.getFps());
        return capture.renderingQualityReduced() || fps < Math.max(1, config.targetFps) * 0.75D;
    }

    private static int[] buildDetailColors(ChunkSnapshot snapshot, int cells, int fallback) {
        if (snapshot == null || cells <= 1 || !snapshot.hasSnapshot) {
            return null;
        }
        int[] colors = new int[cells * cells];
        int scale = 16 / cells;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int sampleX = Math.min(15, x * scale + scale / 2);
                int sampleZ = Math.min(15, z * scale + scale / 2);
                colors[z * cells + x] = snapshot.hasSample(sampleX, sampleZ) ? snapshot.colorAt(sampleX, sampleZ) : fallback;
            }
        }
        return colors;
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static final class RadarRenderCache {
        private long nextRefreshMillis;
        private long revision = Long.MIN_VALUE;
        private int playerChunkX = Integer.MIN_VALUE;
        private int playerChunkZ = Integer.MIN_VALUE;
        private int size = -1;
        private int cell = -1;
        private int detailCells = -1;
        private WorldBinderConfig.MapLayerMode layerMode;
        private WorldBinderConfig.RadarDetailMode detailMode;
        private RadarRenderFrame frame = new RadarRenderFrame(List.of(), 1);

        private RadarRenderFrame frame(SceneCaptureService capture, WorldBinderConfig config, int playerChunkX, int playerChunkZ, int size, int cell) {
            long now = System.currentTimeMillis();
            long currentRevision = capture.mapDataRevision();
            int renderedChunks = size * size;
            int currentDetailCells = chooseDetailCells(config, renderedChunks, cell, shouldReduceDetail(capture, config));
            WorldBinderConfig.MapLayerMode currentLayerMode = config.radarLayerMode == null ? WorldBinderConfig.MapLayerMode.BOTH : config.radarLayerMode;
            WorldBinderConfig.RadarDetailMode currentDetailMode = config.radarDetailMode == null ? WorldBinderConfig.RadarDetailMode.AUTO : config.radarDetailMode;
            if (now < nextRefreshMillis
                    && revision == currentRevision
                    && this.playerChunkX == playerChunkX
                    && this.playerChunkZ == playerChunkZ
                    && this.size == size
                    && this.cell == cell
                    && this.detailCells == currentDetailCells
                    && this.layerMode == currentLayerMode
                    && this.detailMode == currentDetailMode) {
                return frame;
            }

            nextRefreshMillis = now + config.effectiveRadarUpdateIntervalMillis();
            revision = currentRevision;
            this.playerChunkX = playerChunkX;
            this.playerChunkZ = playerChunkZ;
            this.size = size;
            this.cell = cell;
            this.detailCells = currentDetailCells;
            this.layerMode = currentLayerMode;
            this.detailMode = currentDetailMode;
            this.frame = build(capture, currentLayerMode, playerChunkX, playerChunkZ, size, currentDetailCells);
            return frame;
        }

        private RadarRenderFrame build(SceneCaptureService capture, WorldBinderConfig.MapLayerMode mode, int playerChunkX, int playerChunkZ, int size, int detailCells) {
            Set<Long> chunks = capture.downloadedChunksView();
            Set<Long> partial = capture.partialChunksView();
            Set<Long> queued = capture.queuedChunksView();
            Set<Long> failed = capture.failedChunksView();
            Map<Long, ChunkSnapshot> snapshots = capture.chunkSnapshotsView();
            int center = size / 2;
            List<RadarCell> cells = new ArrayList<>(size * size);
            for (int dz = -center; dz <= center; dz++) {
                for (int dx = -center; dx <= center; dx++) {
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    long key = ChunkPos.pack(chunkX, chunkZ);
                    ChunkSnapshot snapshot = snapshots.get(key);
                    ChunkCaptureStatus status = statusOf(snapshot, chunks.contains(key), partial.contains(key), queued.contains(key), failed.contains(key));
                    boolean player = dx == 0 && dz == 0;
                    int statusColor = statusFill(status);
                    int base = mode == WorldBinderConfig.MapLayerMode.MAP_ONLY ? 0x55101018 : statusColor;
                    if (snapshot != null && mode != WorldBinderConfig.MapLayerMode.CHUNKS_ONLY) {
                        base = snapshot.averageColor();
                    }
                    cells.add(new RadarCell(dx, dz, player, base, statusBorder(status), buildDetailColors(snapshot, detailCells, base)));
                }
            }
            return new RadarRenderFrame(cells, detailCells);
        }
    }

    private record RadarRenderFrame(List<RadarCell> cells, int detailCells) {
    }

    private record RadarCell(int dx, int dz, boolean player, int baseColor, int borderColor, int[] detailColors) {
    }

    private record RadarLayout(
            int x,
            int y,
            int panelWidth,
            int panelHeight,
            int logicalWidth,
            int logicalHeight,
            float scale,
            int requestedSize,
            int size,
            int cell,
            int grid
    ) {
    }

    public record WidgetBounds(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }
}

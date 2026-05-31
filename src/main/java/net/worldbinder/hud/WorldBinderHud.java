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
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorldBinderHud {
    private static final RadarRenderCache RADAR_CACHE = new RadarRenderCache();

    private WorldBinderHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("worldbinder", "hud"), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) {
                return;
            }
            SceneCaptureService capture = WorldBinderClient.capture();
            if (WorldBinder.config().showBossbarOverlay && OperationStatus.visible()) {
                drawBossbar(context, client, capture);
            }
            if (capture != null && capture.isRoamingCapture() && WorldBinder.config().chunkRadarRightAligned) {
                drawChunkRadar(context, client, capture);
            }
        });
    }

    private static void drawBossbar(net.minecraft.client.gui.GuiGraphicsExtractor context, Minecraft client, SceneCaptureService capture) {
        Font renderer = client.font;
        int requestedScale = Math.max(1, WorldBinder.config().bossbarScalePercent);
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        float responsiveScale = hudScale(screenWidth, screenHeight);
        int maxWidth = Math.max(170, screenWidth - 32);
        int designWidth = Math.round(520 * responsiveScale * requestedScale / 100.0F);
        int barWidth = clamp(designWidth, Math.min(260, maxWidth), maxWidth);
        boolean verySmall = screenWidth < 740 || screenHeight < 270;
        if (verySmall) {
            barWidth = Math.min(barWidth, Math.max(170, screenWidth - 48));
        }
        int x = (screenWidth - barWidth) / 2;
        int y = Math.max(4, Math.round(WorldBinder.config().bossbarOffsetY * responsiveScale));
        StorageProgress storage = StorageFlow.progress();
        boolean storageMode = storage.isRunning();
        boolean captureMode = capture != null && capture.isCapturing();
        boolean compact = verySmall || barWidth < 360 || screenHeight < 170;
        int overlayHeight = storageMode ? compact ? 44 : 58 : captureMode && !compact ? 76 : 42;
        y = Math.min(y, Math.max(4, screenHeight - overlayHeight - 4));

        double shownProgress = storageMode ? storage.progress() : OperationStatus.progress();
        int fill = (int) ((barWidth - 4) * Math.max(0.0D, Math.min(1.0D, shownProgress)));

        if (storageMode) {
            context.fill(x - 5, y - 5, x + barWidth + 5, y + overlayHeight, 0x8810182A);
            context.fill(x, y, x + barWidth, y + 18, 0xFF1A1128);
            context.fill(x + 2, y + 2, x + 2 + fill, y + 16, 0xFFFF55FF);
            context.fill(x, y, x + barWidth, y + 1, 0xFFFF55FF);
            net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, renderer, Lang.text("worldbinder.storage.title"), screenWidth / 2, y + 5, 0xFFFFFFFF);
            net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, renderer, Component.literal(shorten(storage.stage().label() + " • " + (int) (storage.progress() * 100.0D) + "%", Math.max(18, barWidth / 6))), screenWidth / 2, y + 28, 0xFFE6E6F0);
            if (!compact) {
                net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, renderer, Lang.text("worldbinder.storage.elapsed_remaining", StorageProgress.formatMillis(storage.elapsedMillis()), storage.etaText()), screenWidth / 2, y + 44, 0xFFBDB6D9);
            }
            return;
        }

        context.fill(x - 5, y - 5, x + barWidth + 5, y + overlayHeight, 0x88080810);
        context.fill(x, y, x + barWidth, y + 18, 0xFF1A1128);
        context.fill(x + 2, y + 2, x + 2 + fill, y + 16, OperationStatus.active() ? 0xFFFF55FF : 0xFF55FFAA);
        context.fill(x, y, x + barWidth, y + 1, 0xFFFF55FF);
        context.fill(x, y + 17, x + barWidth, y + 18, 0xFF5E03FC);

        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, renderer, Component.literal(shorten(OperationStatus.title(), Math.max(20, barWidth / 6))), screenWidth / 2, y + 5, 0xFFFFFFFF);
        if (captureMode) {
            net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, renderer, Lang.text(capture.isPaused() ? "worldbinder.common.paused" : "worldbinder.hud.downloading"), screenWidth / 2, y + 25, 0xFFE6E6F0);
            if (!compact) {
                int chipY = y + 43;
                int done = capture.scannedChunks();
                int scanning = capture.partialChunks();
                int queued = capture.queuedChunks();
                int total = Math.max(1, done + scanning + queued);
                int gap = 8;
                int meterW = Math.max(64, (barWidth - gap * 2) / 3);
                drawMeter(context, renderer, x, chipY, meterW, Lang.string("worldbinder.hud.meter.chunks"), done + "/" + total, done / (double) total, 0xFF55FFAA);
                drawMeter(context, renderer, x + meterW + gap, chipY, meterW, Lang.string("worldbinder.hud.meter.entities"), Integer.toString(capture.capturedEntities()), Math.min(1.0D, capture.capturedEntities() / 1000.0D), 0xFFFF55FF);
                drawMeter(context, renderer, x + (meterW + gap) * 2, chipY, meterW, Lang.string("worldbinder.hud.meter.queue"), Integer.toString(queued), Math.min(1.0D, queued / 512.0D), queued > 512 ? 0xFFFF5555 : 0xFFFFD166);
            }
        } else {
            net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, renderer, Component.literal(shorten(OperationStatus.detail(), Math.max(24, barWidth / 6))), screenWidth / 2, y + 25, 0xFFE6E6F0);
        }
    }

    private static void drawMeter(net.minecraft.client.gui.GuiGraphicsExtractor context, Font renderer, int x, int y, int w, String label, String value, double progress, int color) {
        context.fill(x, y, x + w, y + 24, 0x77000000);
        context.fill(x, y, x + w, y + 1, 0x885E03FC);
        int fill = (int) ((w - 6) * Math.max(0.0D, Math.min(1.0D, progress)));
        context.fill(x + 3, y + 15, x + w - 3, y + 20, 0x33000000);
        context.fill(x + 3, y + 15, x + 3 + fill, y + 20, color);
        String text = shorten(label + ": §f" + value, Math.max(8, (w - 10) / 6));
        net.worldbinder.util.GuiText.drawTextWithShadow(context, renderer, Component.literal(text), x + 5, y + 4, 0xFFBDB6D9);
    }

    private static void drawChunkRadar(net.minecraft.client.gui.GuiGraphicsExtractor context, Minecraft client, SceneCaptureService capture) {
        WorldBinderConfig config = WorldBinder.config();
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        float responsiveScale = hudScale(screenW, screenH);
        int requestedSize = Math.max(1, config.chunkRadarSize | 1);
        int size = cappedOddSize(requestedSize, config.effectiveRadarMaxRenderedChunks());
        int requestedCell = Math.max(1, Math.round(config.chunkRadarCellSize * Math.max(1, config.chunkRadarScalePercent) / 100.0F * responsiveScale));
        int maxPanelW = Math.max(64, Math.min(screenW / 3, 210));
        int maxPanelH = Math.max(64, Math.min(screenH / 3, 210));
        int maxGrid = Math.max(24, Math.min(maxPanelW - 18, maxPanelH - 34));
        int cell = Math.max(1, Math.min(requestedCell, maxGrid / Math.max(1, size)));
        while (size > 3 && size * cell > maxGrid) {
            size -= 2;
        }
        int grid = size * cell;
        int panelW = Math.max(72, grid + 18);
        int panelH = grid + 34;
        int x = config.chunkRadarRightAligned
                ? screenW - panelW - Math.round(config.chunkRadarOffsetX * responsiveScale)
                : Math.round(config.chunkRadarOffsetX * responsiveScale);
        int y = Math.round(config.chunkRadarOffsetY * responsiveScale);
        int bossbarClearance = OperationStatus.visible() ? Math.min(screenH - panelH - 4, Math.round(92 * responsiveScale)) : 4;
        y = Math.max(y, bossbarClearance);
        x = Math.max(4, Math.min(screenW - panelW - 4, x));
        y = Math.max(4, Math.min(screenH - panelH - 4, y));

        context.fill(x, y, x + panelW, y + panelH, 0xAA10182A);
        context.fill(x, y, x + panelW, y + 2, 0xFFFF55FF);

        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        String title = Lang.string("worldbinder.hud.chunk_title", playerChunkX, playerChunkZ);
        if (size != requestedSize) {
            title += " • capped";
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, client.font, Component.literal(shorten(title, Math.max(8, (panelW - 12) / 6))), x + 7, y + 6, 0xFFFFFFFF);

        RadarRenderFrame frame = RADAR_CACHE.frame(capture, config, playerChunkX, playerChunkZ, size, cell);
        int center = size / 2;
        int gridX = x + 8;
        int gridY = y + 22;
        for (RadarCell radarCell : frame.cells) {
            int px = gridX + (radarCell.dx + center) * cell;
            int py = gridY + (radarCell.dz + center) * cell;
            drawRadarCell(context, radarCell, px, py, cell, frame.detailCells, config.radarLayerMode);
        }
    }

    private static float hudScale(int screenWidth, int screenHeight) {
        float scale = Math.min(screenWidth / 960.0F, screenHeight / 540.0F);
        return Math.max(0.55F, Math.min(1.0F, scale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
            int c = 0xFFFF55FF;
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
            case DONE -> 0x66336644;
            case SCANNING -> 0x66305666;
            case PARTIAL, RECOVERY -> 0x665A4A10;
            case QUEUED -> 0x665A3A10;
            case FAILED -> 0x663C1010;
            case UNKNOWN -> 0x55303A4E;
        };
    }

    private static int statusBorder(ChunkCaptureStatus status) {
        return switch (status) {
            case DONE -> 0xFF55FFAA;
            case SCANNING -> 0xFF55A7FF;
            case PARTIAL, RECOVERY -> 0xFFFFE066;
            case QUEUED -> 0xFFFFD166;
            case FAILED -> 0xFFFF5555;
            case UNKNOWN -> 0x55444466;
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
}

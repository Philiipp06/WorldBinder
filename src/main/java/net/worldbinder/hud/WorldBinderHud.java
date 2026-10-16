package net.worldbinder.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.render.MapData;
import net.worldbinder.render.MapMarkers;
import net.worldbinder.render.MapRegionTextures;
import net.worldbinder.render.MapStatusOverlay;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;

import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

public final class WorldBinderHud {
    private static final int STATUS_WIDTH = 520;
    private static final int STATUS_FULL_HEIGHT = 76;
    private static final int STATUS_STORAGE_HEIGHT = 58;
    private static final int STATUS_DETAIL_HEIGHT = 42;
    private static final int RADAR_HEADER = 22;
    private static final int RADAR_FOOTER = 14;
    private static final MapStatusOverlay RADAR_STATUS = new MapStatusOverlay();
    private static long radarSyncSignature = Long.MIN_VALUE;
    private static long radarStatsMillis;
    private static int radarDoneInView;
    private static int radarKnownInView;

    private WorldBinderHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("worldbinder", "hud"), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            if (net.worldbinder.compat.ClientCompat.screen(client) == null) {
                WbTheme.refresh();
            }
            if (net.worldbinder.compat.ClientCompat.hudHidden(client)) {
                WorldBinderNotifications.visible(0);
                return;
            }
            SceneCaptureService capture = WorldBinderClient.capture();
            WorldBinderConfig config = WorldBinder.config();
            WidgetBounds statusBounds = null;
            if (config.showBossbarOverlay && OperationStatus.visible()) {
                statusBounds = drawBossbar(context, client, capture);
            }
            boolean showRadar = capture != null && config.showChunkRadar
                    && (capture.isRoamingCapture() || config.radarShowOutsideCapture);
            RadarLayout radarLayout = showRadar ? layoutChunkRadar(client, config) : null;
            WidgetBounds radarBounds = radarLayout == null ? null : new WidgetBounds(
                    radarLayout.x(), radarLayout.y(), radarLayout.panelWidth(), radarLayout.panelHeight());
            WidgetBounds movementBounds = config.showMovementBadges && MovementHud.hasActive(config)
                    ? MovementHud.bounds(client, config, false) : null;
            WorldBinderNotificationOverlay.draw(context, client, statusBounds, radarBounds, movementBounds);
            if (showRadar) {
                drawChunkRadar(context, client, capture, radarLayout);
            }
            MovementHud.draw(context, client, config, false);
        });
    }

    // ------------------------------------------------------------------ status bar

    private static WidgetBounds drawBossbar(GuiGraphicsExtractor context, Minecraft client, SceneCaptureService capture) {
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
        return new WidgetBounds(x, y, physicalWidth, physicalHeight);
    }

    private static void drawStatusFrame(GuiGraphicsExtractor context, Font font, WorldBinderConfig config, int height,
                                        WbIcon icon, String title, double progress, boolean active) {
        int accent = 0xFF000000 | (config.bossbarAccentColor & 0xFFFFFF);
        int background = config.bossbarBackgroundColor;
        WbChrome.softShadow(context, 0, 0, STATUS_WIDTH, height, 0x80);
        WbChrome.rounded(context, 0, 0, STATUS_WIDTH, height, WbMotion.mix(background, accent, 0.35F));
        WbChrome.roundedGradient(context, 1, 1, STATUS_WIDTH - 2, height - 2,
                WbMotion.mix(withAlpha(background, 0xF0), accent, 0.08F), withAlpha(background, 0xE6));
        WbChrome.rounded(context, 6, 4, 16, 16, WbTheme.withAlpha(accent, 0x30));
        icon.draw(context, 8, 6, 2, accent);
        context.text(font, Component.literal(shorten(title, 60)).withStyle(ChatFormatting.BOLD), 28, 8, WbTheme.TEXT, true);
        String percent = Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * 100.0D) + "%";
        context.text(font, percent, STATUS_WIDTH - 10 - font.width(percent), 8, WbMotion.mix(accent, WbTheme.TEXT, 0.4F), true);
        int barX = 8;
        int barW = STATUS_WIDTH - 16;
        int fill = (int) Math.round((barW - 2) * Math.max(0.0D, Math.min(1.0D, progress)));
        WbChrome.drawProgressTrack(context, barX, 22, barW, 5, fill, active ? accent : WbTheme.OK);
        if (active) {
            WbChrome.drawProgressShimmer(context, barX, 22, Math.max(4, fill + 2), 5, accent);
        }
    }

    private static void drawBossbarContents(
            GuiGraphicsExtractor context,
            Font renderer,
            WorldBinderConfig config,
            SceneCaptureService capture,
            StorageProgress storage,
            boolean storageMode,
            boolean captureMode,
            int logicalHeight
    ) {
        double shownProgress = storageMode ? storage.progress() : OperationStatus.progress();
        if (storageMode) {
            drawStatusFrame(context, renderer, config, logicalHeight, WbIcon.SAVE, Lang.string("worldbinder.storage.title"), shownProgress, true);
            WbText.drawClipped(context, renderer, storage.stage().label(), 10, 33, STATUS_WIDTH / 2, WbTheme.TEXT_SOFT);
            String eta = Lang.string("worldbinder.storage.elapsed_remaining", StorageProgress.formatMillis(storage.elapsedMillis()), storage.etaText());
            context.text(renderer, eta, STATUS_WIDTH - 10 - renderer.width(eta), 33, WbTheme.TEXT_DIM, true);
            return;
        }
        boolean active = OperationStatus.active();
        drawStatusFrame(context, renderer, config, logicalHeight, captureMode ? WbIcon.CAPTURE : WbIcon.INFO,
                OperationStatus.title(), captureMode ? captureProgress(capture) : shownProgress, active || captureMode);
        if (!captureMode) {
            WbText.drawClipped(context, renderer, OperationStatus.detail(), 10, 31, STATUS_WIDTH - 20, WbTheme.TEXT_SOFT);
            return;
        }
        int done = capture.scannedChunks();
        int scanning = capture.partialChunks();
        int queued = capture.queuedChunks();
        int total = Math.max(1, done + scanning + queued);
        boolean paused = capture.isPaused();
        int stateColor = paused ? WbTheme.WARN : WbTheme.OK;
        float pulse = paused ? 1.0F : WbMotion.pulse(1400L);
        WbChrome.fillCircle(context, 14, 37, 3, WbMotion.mix(stateColor, 0xFFFFFFFF, pulse * 0.3F));
        context.text(renderer, Lang.string(paused ? "worldbinder.common.paused" : "worldbinder.hud.downloading"), 22, 33, WbTheme.TEXT_SOFT, true);
        String eta = Lang.string("worldbinder.common.eta") + ": " + capture.estimatedFinishText();
        context.text(renderer, eta, STATUS_WIDTH - 10 - renderer.width(eta), 33, WbTheme.TEXT_DIM, true);
        int gap = 6;
        int meterW = (STATUS_WIDTH - 16 - gap * 2) / 3;
        drawMeter(context, renderer, 8, 46, meterW, WbIcon.CHUNK, Lang.string("worldbinder.hud.meter.chunks"), done + "/" + total, done / (double) total, WbTheme.OK);
        drawMeter(context, renderer, 8 + meterW + gap, 46, meterW, WbIcon.ENTITY, Lang.string("worldbinder.hud.meter.entities"), Integer.toString(capture.capturedEntities()),
                Math.min(1.0D, capture.capturedEntities() / 1000.0D), 0xFF000000 | (config.bossbarAccentColor & 0xFFFFFF));
        drawMeter(context, renderer, 8 + (meterW + gap) * 2, 46, meterW, WbIcon.RECOVERY, Lang.string("worldbinder.hud.meter.queue"), Integer.toString(queued),
                Math.min(1.0D, queued / 512.0D), queued > 512 ? WbTheme.ERROR : WbTheme.WARN);
    }

    private static double captureProgress(SceneCaptureService capture) {
        int done = capture.scannedChunks();
        int total = Math.max(1, done + capture.partialChunks() + capture.queuedChunks());
        return done / (double) total;
    }

    private static float statusScale(int screenWidth, int screenHeight, WorldBinderConfig config, int logicalHeight) {
        float requested = hudScale(screenWidth, screenHeight) * config.effectiveBossbarScalePercent() / 100.0F;
        float fit = Math.min(
                (screenWidth - WorldBinderWidgetLayout.SCREEN_MARGIN * 2) / (float) STATUS_WIDTH,
                (screenHeight - WorldBinderWidgetLayout.SCREEN_MARGIN * 2) / (float) logicalHeight
        );
        return Math.max(0.2F, Math.min(requested, fit));
    }

    private static void drawMeter(GuiGraphicsExtractor context, Font renderer, int x, int y, int w, WbIcon icon, String label, String value, double progress, int color) {
        WbChrome.rounded(context, x, y, w, 24, 0x55000000);
        icon.draw(context, x + 5, y + 4, 1, color);
        WbText.drawClipped(context, renderer, label, x + 16, y + 4, w - 22 - renderer.width(value), WbTheme.TEXT_MUTED);
        context.text(renderer, value, x + w - 6 - renderer.width(value), y + 4, WbTheme.TEXT, true);
        WbChrome.drawProgressTrack(context, x + 5, y + 15, w - 10, 5, (int) Math.round((w - 12) * Math.max(0.0D, Math.min(1.0D, progress))), color);
    }

    // ------------------------------------------------------------------ widget editor previews

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
        } else if (widget == WorldBinderWidgetLayout.Widget.MOVEMENT) {
            return MovementHud.bounds(client, config, true);
        } else {
            width = WorldBinderNotificationOverlay.preferredWidth(config, screenW, screenH);
            height = WorldBinderNotificationOverlay.previewHeight(config, screenW, screenH);
        }
        int x = WorldBinderWidgetLayout.positionX(screenW, width, WorldBinderWidgetLayout.xPercent(config, widget));
        int y = WorldBinderWidgetLayout.positionY(screenH, height, WorldBinderWidgetLayout.yPercent(config, widget));
        return new WidgetBounds(x, y, width, height);
    }

    public static void drawWidgetPreview(GuiGraphicsExtractor context, Minecraft client, WorldBinderConfig config,
                                         WorldBinderWidgetLayout.Widget widget) {
        WidgetBounds bounds = previewBounds(client, config, widget);
        if (widget == WorldBinderWidgetLayout.Widget.STATUS) {
            drawBossbarPreview(context, client.font, config, bounds);
        } else if (widget == WorldBinderWidgetLayout.Widget.RADAR) {
            RadarLayout layout = layoutChunkRadar(client, config);
            RadarLayout placed = new RadarLayout(bounds.x(), bounds.y(), layout.panelWidth(), layout.panelHeight(), layout.logicalWidth(),
                    layout.logicalHeight(), layout.scale(), layout.size(), layout.cell(), layout.grid());
            drawRadar(context, client, WorldBinderClient.capture(), placed, config, true);
        } else if (widget == WorldBinderWidgetLayout.Widget.MOVEMENT) {
            MovementHud.draw(context, client, config, true);
        } else {
            WorldBinderNotificationOverlay.drawPreview(context, client.font, config, bounds.x(), bounds.y(), bounds.width());
        }
    }

    private static void drawBossbarPreview(GuiGraphicsExtractor context, Font font, WorldBinderConfig config, WidgetBounds bounds) {
        float scale = bounds.width() / (float) STATUS_WIDTH;
        context.pose().pushMatrix();
        context.pose().translate(bounds.x(), bounds.y());
        context.pose().scale(scale, scale);
        try {
            drawStatusFrame(context, font, config, STATUS_FULL_HEIGHT, WbIcon.CAPTURE,
                    Lang.string("worldbinder.widget_editor.preview.status_title"), 0.62D, true);
            WbChrome.fillCircle(context, 14, 37, 3, WbTheme.OK);
            context.text(font, Lang.string("worldbinder.widget_editor.preview.status_message"), 22, 33, WbTheme.TEXT_SOFT, true);
            int gap = 6;
            int meterW = (STATUS_WIDTH - 16 - gap * 2) / 3;
            drawMeter(context, font, 8, 46, meterW, WbIcon.CHUNK, Lang.string("worldbinder.hud.meter.chunks"), "84/120", 0.7D, WbTheme.OK);
            drawMeter(context, font, 8 + meterW + gap, 46, meterW, WbIcon.ENTITY, Lang.string("worldbinder.hud.meter.entities"), "12", 0.35D,
                    0xFF000000 | (config.bossbarAccentColor & 0xFFFFFF));
            drawMeter(context, font, 8 + (meterW + gap) * 2, 46, meterW, WbIcon.RECOVERY, Lang.string("worldbinder.hud.meter.queue"), "36", 0.3D, WbTheme.WARN);
        } finally {
            context.pose().popMatrix();
        }
    }

    // ------------------------------------------------------------------ chunk radar

    private static RadarLayout layoutChunkRadar(Minecraft client, WorldBinderConfig config) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int requestedSize = Math.max(3, config.chunkRadarSize | 1);
        int size = cappedOddSize(requestedSize, config.effectiveRadarMaxRenderedChunks());
        int cell = Math.max(2, Math.min(16, config.chunkRadarCellSize));
        int grid = size * cell;
        int logicalW = Math.max(118, grid + 16);
        int logicalH = RADAR_HEADER + grid + 8 + (config.radarShowCoordinates ? RADAR_FOOTER : 0);
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
        return new RadarLayout(x, y, panelW, panelH, logicalW, logicalH, scale, size, cell, grid);
    }

    private static void drawChunkRadar(GuiGraphicsExtractor context, Minecraft client, SceneCaptureService capture, RadarLayout layout) {
        drawRadar(context, client, capture, layout, WorldBinder.config(), false);
    }

    /**
     * Texture-based radar: shares the live map's region textures, follows the player smoothly with sub-pixel
     * precision, optionally rotates with the view and shows status tint, capture radius, entities and a
     * player arrow. In the widget editor without a world it shows a decorative placeholder.
     */
    private static void drawRadar(GuiGraphicsExtractor context, Minecraft client, SceneCaptureService capture, RadarLayout layout,
                                  WorldBinderConfig config, boolean preview) {
        Font font = client.font;
        int panelW = layout.logicalWidth();
        int panelH = layout.logicalHeight();
        int grid = layout.grid();
        int accent = 0xFF000000 | (config.chunkRadarAccentColor & 0xFFFFFF);
        int background = config.chunkRadarBackgroundColor;
        boolean live = client.player != null && client.level != null && capture != null;

        context.pose().pushMatrix();
        context.pose().translate(layout.x(), layout.y());
        context.pose().scale(layout.scale(), layout.scale());
        try {
            WbChrome.softShadow(context, 0, 0, panelW, panelH, 0x80);
            WbChrome.rounded(context, 0, 0, panelW, panelH, WbMotion.mix(background, accent, 0.35F));
            WbChrome.roundedGradient(context, 1, 1, panelW - 2, panelH - 2, WbMotion.mix(background, accent, 0.08F), background);
            WbIcon.MAP.draw(context, 7, 7, 1, accent);
            int gridX = (panelW - grid) / 2;
            int gridY = RADAR_HEADER;
            if (!live) {
                context.text(font, Component.translatable("worldbinder.widget_editor.preview.radar_title").withStyle(ChatFormatting.BOLD), 18, 6, WbTheme.TEXT, true);
                drawRadarPlaceholder(context, gridX, gridY, grid, layout.cell(), accent);
                if (config.radarShowCoordinates) {
                    context.text(font, "X 128  Y 64  Z -256", 7, gridY + grid + 6, WbTheme.TEXT_DIM, false);
                }
                return;
            }
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            double px = client.player.xo + (client.player.getX() - client.player.xo) * partial;
            double pz = client.player.zo + (client.player.getZ() - client.player.zo) * partial;
            float yaw = client.player.getViewYRot(partial);
            int playerChunkX = client.player.blockPosition().getX() >> 4;
            int playerChunkZ = client.player.blockPosition().getZ() >> 4;
            Map<Long, ChunkSnapshot> snapshots = capture.chunkSnapshotsView();
            LongFunction<ChunkSnapshot> lookup = MapData.lookup(snapshots);
            updateRadarStats(capture, playerChunkX, playerChunkZ, layout.size());

            String title = Lang.string("worldbinder.hud.chunk_title", playerChunkX, playerChunkZ);
            String coverage = radarKnownInView == 0 ? "" : Math.round(radarDoneInView * 100.0F / Math.max(1, layout.size() * layout.size())) + "%";
            WbText.drawClipped(context, font, Component.literal(title).withStyle(ChatFormatting.BOLD), 18, 7,
                    panelW - 26 - font.width(coverage), WbTheme.TEXT);
            if (!coverage.isEmpty()) {
                context.text(font, coverage, panelW - 7 - font.width(coverage), 7, WbMotion.mix(WbTheme.OK, WbTheme.TEXT, 0.3F), true);
            }

            context.fill(gridX - 1, gridY - 1, gridX + grid + 1, gridY + grid + 1, WbTheme.withAlpha(accent, 0x70));
            context.fill(gridX, gridY, gridX + grid, gridY + grid, 0xFF0B1016);
            double zoom = layout.cell() / 16.0D;
            double centerX = gridX + grid / 2.0D;
            double centerY = gridY + grid / 2.0D;
            double reach = grid / 2.0D / zoom * (config.radarRotate ? 1.45D : 1.0D);
            int minChunkX = (int) Math.floor((px - reach) / 16.0D) - 1;
            int maxChunkX = (int) Math.floor((px + reach) / 16.0D) + 1;
            int minChunkZ = (int) Math.floor((pz - reach) / 16.0D) - 1;
            int maxChunkZ = (int) Math.floor((pz + reach) / 16.0D) + 1;
            int minRegionX = Math.floorDiv(minChunkX, MapRegionTextures.REGION_CHUNKS);
            int maxRegionX = Math.floorDiv(maxChunkX, MapRegionTextures.REGION_CHUNKS);
            int minRegionZ = Math.floorDiv(minChunkZ, MapRegionTextures.REGION_CHUNKS);
            int maxRegionZ = Math.floorDiv(maxChunkZ, MapRegionTextures.REGION_CHUNKS);
            WorldBinderConfig.MapLayerMode mode = config.radarLayerMode == null ? WorldBinderConfig.MapLayerMode.BOTH : config.radarLayerMode;
            boolean terrain = mode != WorldBinderConfig.MapLayerMode.CHUNKS_ONLY;
            MapRegionTextures textures = MapRegionTextures.shared();
            long signature = capture.mapDataRevision() * 31L + net.worldbinder.render.MapExplorer.get().revision() * 17L
                    + minChunkX * 7919L + minChunkZ * 104729L + (config.mapHillshade ? 1 : 0);
            if (terrain && signature != radarSyncSignature) {
                radarSyncSignature = signature;
                textures.sync(lookup, minChunkX, minChunkZ, maxChunkX, maxChunkZ, config.mapHillshade);
            }
            if (terrain) {
                textures.paint(lookup, 1_500_000L);
            }
            boolean strong = mode == WorldBinderConfig.MapLayerMode.CHUNKS_ONLY;
            boolean statusLayer = mode != WorldBinderConfig.MapLayerMode.MAP_ONLY;
            if (statusLayer) {
                Set<Long> done = capture.downloadedChunksView();
                Set<Long> partialSet = capture.partialChunksView();
                Set<Long> queued = capture.queuedChunksView();
                Set<Long> failed = capture.failedChunksView();
                RADAR_STATUS.update(minRegionX, minRegionZ, maxRegionX, maxRegionZ, capture.mapDataRevision() * 3L + (strong ? 1 : 0) + WbTheme.ACCENT,
                        key -> radarStatusColor(snapshots.get(key), done.contains(key), partialSet.contains(key), queued.contains(key), failed.contains(key), strong));
            }

            context.enableScissor(gridX, gridY, gridX + grid, gridY + grid);
            try {
                context.pose().pushMatrix();
                context.pose().translate((float) centerX, (float) centerY);
                if (config.radarRotate) {
                    context.pose().rotate((float) Math.toRadians(-(yaw + 180.0F)));
                }
                double originX = -px * zoom;
                double originY = -pz * zoom;
                if (terrain) {
                    textures.drawTerrain(context, originX, originY, zoom, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
                }
                if (statusLayer) {
                    RADAR_STATUS.draw(context, originX, originY, zoom, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
                }
                WorldBinderConfig.RadarDetailMode detail = config.radarDetailMode == null ? WorldBinderConfig.RadarDetailMode.AUTO : config.radarDetailMode;
                if (detail != WorldBinderConfig.RadarDetailMode.LOW && layout.cell() >= 6) {
                    int lineColor = detail == WorldBinderConfig.RadarDetailMode.HIGH ? 0x30FFFFFF : 0x18FFFFFF;
                    for (int cx = minChunkX; cx <= maxChunkX + 1; cx++) {
                        int lx = (int) Math.round(originX + cx * 16 * zoom);
                        context.fill(lx, (int) Math.round(originY + minChunkZ * 16 * zoom), lx + 1, (int) Math.round(originY + (maxChunkZ + 1) * 16 * zoom), lineColor);
                    }
                    for (int cz = minChunkZ; cz <= maxChunkZ + 1; cz++) {
                        int ly = (int) Math.round(originY + cz * 16 * zoom);
                        context.fill((int) Math.round(originX + minChunkX * 16 * zoom), ly, (int) Math.round(originX + (maxChunkX + 1) * 16 * zoom), ly + 1, lineColor);
                    }
                }
                if (capture.isRoamingCapture()) {
                    int radius = config.effectiveRoamingRadiusChunks();
                    int x1 = (int) Math.round(originX + (playerChunkX - radius) * 16 * zoom);
                    int y1 = (int) Math.round(originY + (playerChunkZ - radius) * 16 * zoom);
                    int x2 = (int) Math.round(originX + (playerChunkX + radius + 1) * 16 * zoom);
                    int y2 = (int) Math.round(originY + (playerChunkZ + radius + 1) * 16 * zoom);
                    int color = WbTheme.withAlpha(WbTheme.OK, 0x90);
                    context.fill(x1, y1, x2, y1 + 1, color);
                    context.fill(x1, y2 - 1, x2, y2, color);
                    context.fill(x1, y1, x1 + 1, y2, color);
                    context.fill(x2 - 1, y1, x2, y2, color);
                }
                if (config.radarShowEntities) {
                    for (Entity entity : client.level.entitiesForRendering()) {
                        if (entity == client.player) continue;
                        MapMarkers.Kind kind = MapMarkers.kind(entity);
                        if (!MapMarkers.visible(kind, config)) continue;
                        double ex = (entity.xo + (entity.getX() - entity.xo) * partial - px) * zoom;
                        double ez = (entity.zo + (entity.getZ() - entity.zo) * partial - pz) * zoom;
                        if (Math.abs(ex) > reach * zoom || Math.abs(ez) > reach * zoom) continue;
                        if (entity instanceof Player) {
                            WbChrome.fillCircle(context, (int) Math.round(ex), (int) Math.round(ez), 3, 0xE0050709);
                            WbChrome.fillCircle(context, (int) Math.round(ex), (int) Math.round(ez), 2, kind.color);
                        } else {
                            MapMarkers.drawEntity(context, (int) Math.round(ex), (int) Math.round(ez), kind, layout.cell() >= 8 ? 2 : 1);
                        }
                    }
                }
                context.pose().popMatrix();
                MapMarkers.drawPlayerArrow(context, (float) centerX, (float) centerY, config.radarRotate ? -180.0F : yaw, 0.85F, accent, true);
            } finally {
                context.disableScissor();
            }
            drawRadarNorth(context, font, gridX, gridY, grid, config.radarRotate ? yaw : 180.0F);
            if (config.radarShowCoordinates) {
                String coords = "X " + client.player.blockPosition().getX() + "  Y " + client.player.blockPosition().getY()
                        + "  Z " + client.player.blockPosition().getZ();
                WbText.drawClipped(context, font, coords, 7, gridY + grid + 6, panelW - 14 - 12, WbTheme.TEXT_MUTED);
                String facing = facing(yaw);
                context.text(font, facing, panelW - 7 - font.width(facing), gridY + grid + 6, accent, true);
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    private static void updateRadarStats(SceneCaptureService capture, int playerChunkX, int playerChunkZ, int size) {
        long now = System.currentTimeMillis();
        if (now < radarStatsMillis) {
            return;
        }
        radarStatsMillis = now + 500L;
        Set<Long> done = capture.downloadedChunksView();
        Map<Long, ChunkSnapshot> snapshots = capture.chunkSnapshotsView();
        int center = size / 2;
        int doneCount = 0;
        int known = 0;
        for (int dz = -center; dz <= center; dz++) {
            for (int dx = -center; dx <= center; dx++) {
                long key = ChunkPos.pack(playerChunkX + dx, playerChunkZ + dz);
                if (done.contains(key)) doneCount++;
                if (snapshots.containsKey(key)) known++;
            }
        }
        radarDoneInView = doneCount;
        radarKnownInView = known;
    }

    private static int radarStatusColor(ChunkSnapshot snapshot, boolean saved, boolean scanning, boolean queued, boolean failed, boolean strong) {
        ChunkCaptureStatus status = statusOf(snapshot, saved, scanning, queued, failed);
        int alpha = strong ? 0xD0 : 0x60;
        return switch (status) {
            case DONE -> strong ? WbTheme.withAlpha(0xFF3F8F67, alpha) : 0;
            case SCANNING -> WbTheme.withAlpha(WbTheme.INFO, alpha);
            case PARTIAL, RECOVERY -> WbTheme.withAlpha(WbTheme.WARN, alpha);
            case QUEUED -> WbTheme.withAlpha(WbTheme.ACCENT_RIGHT, alpha);
            case FAILED -> WbTheme.withAlpha(WbTheme.ERROR, strong ? alpha : 0x90);
            case UNKNOWN -> 0;
        };
    }

    private static void drawRadarNorth(GuiGraphicsExtractor context, Font font, int gridX, int gridY, int grid, float yaw) {
        double angle = Math.toRadians(-(yaw + 180.0F));
        double r = grid / 2.0D - 7;
        double dirX = Math.sin(angle);
        double dirY = -Math.cos(angle);
        int nx = (int) Math.round(gridX + grid / 2.0D + dirX * r);
        int ny = (int) Math.round(gridY + grid / 2.0D + dirY * r);
        WbChrome.fillCircle(context, nx, ny, 5, 0xD0101720);
        WbChrome.circleOutline(context, nx, ny, 5, WbTheme.withAlpha(WbTheme.ERROR, 0xC0));
        context.text(font, "N", nx - font.width("N") / 2 + 1, ny - 3, WbTheme.TEXT, false);
    }

    private static String facing(float yaw) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int index = Math.floorMod(Math.round(yaw / 45.0F), 8);
        return names[index];
    }

    private static void drawRadarPlaceholder(GuiGraphicsExtractor context, int x, int y, int grid, int cell, int accent) {
        context.fill(x - 1, y - 1, x + grid + 1, y + grid + 1, WbTheme.withAlpha(accent, 0x70));
        int[] palette = {0xFF4F7A3A, 0xFF5E8A44, 0xFF3D6A9C, 0xFF7A7F86, 0xFF8A6F4A};
        int cells = Math.max(1, grid / Math.max(2, cell));
        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                int noise = Math.abs((row * 73856093) ^ (col * 19349663)) % 11;
                int color = noise < 5 ? palette[0] : noise < 8 ? palette[1] : noise < 9 ? palette[2] : noise < 10 ? palette[3] : palette[4];
                context.fill(x + col * cell, y + row * cell, x + (col + 1) * cell, y + (row + 1) * cell, color);
            }
        }
        MapMarkers.drawPlayerArrow(context, x + grid / 2.0F, y + grid / 2.0F, 135.0F, 0.85F, accent, true);
    }

    private static ChunkCaptureStatus statusOf(ChunkSnapshot snapshot, boolean saved, boolean scanning, boolean queued, boolean failed) {
        if (failed || (snapshot != null && snapshot.exportError)) return ChunkCaptureStatus.FAILED;
        if (saved) return ChunkCaptureStatus.DONE;
        if (scanning) return snapshot != null && snapshot.effectiveStatus() == ChunkCaptureStatus.PARTIAL ? ChunkCaptureStatus.PARTIAL : ChunkCaptureStatus.SCANNING;
        if (queued) return ChunkCaptureStatus.QUEUED;
        return snapshot == null ? ChunkCaptureStatus.UNKNOWN : snapshot.effectiveStatus();
    }

    private static float hudScale(int screenWidth, int screenHeight) {
        float scale = Math.min(screenWidth / 960.0F, screenHeight / 540.0F);
        return Math.max(0.55F, Math.min(1.0F, scale));
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

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private record RadarLayout(int x, int y, int panelWidth, int panelHeight, int logicalWidth, int logicalHeight,
                               float scale, int size, int cell, int grid) {
    }

    public record WidgetBounds(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }
}

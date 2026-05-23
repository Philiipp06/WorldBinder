package net.worldbinder.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.render.ChunkMapTileCache;
import net.worldbinder.util.Lang;

import java.util.Map;
import java.util.Set;

public final class WorldBinderMapScreen extends Screen {
    private final Screen parent;
    private int zoom = 2;
    private int panChunkX = 0;
    private int panChunkZ = 0;
    private boolean followPlayer = true;
    private long selectedChunk = Long.MIN_VALUE;
    private boolean filterMissing;
    private boolean filterIncomplete;
    private boolean filterEntities;
    private boolean filterBlockEntities;
    private Button missingFilterButton;
    private Button incompleteFilterButton;
    private Button entitiesFilterButton;
    private Button blockEntitiesFilterButton;
    private EditBox goX;
    private EditBox goZ;
    private final ChunkMapTileCache tileCache = new ChunkMapTileCache();
    private long nextMapDataRefreshMillis;
    private long nextPanelRefreshMillis;
    private long cachedMapRevision = Long.MIN_VALUE;
    private long cachedPanelRevision = Long.MIN_VALUE;
    private Set<Long> cachedDone = Set.of();
    private Set<Long> cachedPartial = Set.of();
    private Set<Long> cachedQueued = Set.of();
    private Set<Long> cachedFailed = Set.of();
    private Map<Long, ChunkSnapshot> cachedSnapshots = Map.of();
    private int cachedSavedCount;
    private int cachedScanningCount;
    private int cachedQueuedCount;
    private int cachedErrorCount;
    private long cachedInspectorKey = Long.MIN_VALUE;
    private ChunkSnapshot cachedInspectorSnapshot;
    private String cachedInspectorStatus = Lang.string("worldbinder.map.unknown");
    private double cachedInspectorQuality;
    private String cachedQueueDiagnostics = "";

    public WorldBinderMapScreen(Screen parent) {
        super(Component.translatable("worldbinder.map.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boolean compact = width < 1120;
        int rightW = width >= 900 ? 184 : 0;
        int right = rightW > 0 ? width - rightW - 18 : width - 180;
        int row1 = 42;
        int row2 = compact ? 66 : 42;
        addRenderableWidget(button(18, row1, 84, Lang.string("worldbinder.map.follow"), Lang.text("worldbinder.map.tooltip.follow"), b -> followPlayer = true));
        addRenderableWidget(button(108, row1, 78, Lang.string("worldbinder.map.origin"), Lang.text("worldbinder.map.tooltip.origin"), b -> jumpToOrigin()));
        missingFilterButton = filterButton(192, row1, 86, Lang.string("worldbinder.map.missing"), Lang.text("worldbinder.map.tooltip.missing"), b -> { filterMissing = !filterMissing; updateFilterButtons(); }); addRenderableWidget(missingFilterButton);
        incompleteFilterButton = filterButton(284, row1, 104, Lang.string("worldbinder.map.incomplete"), Lang.text("worldbinder.map.tooltip.incomplete"), b -> { filterIncomplete = !filterIncomplete; updateFilterButtons(); }); addRenderableWidget(incompleteFilterButton);
        int secondX = compact ? 18 : 394;
        entitiesFilterButton = filterButton(secondX, row2, 94, Lang.string("worldbinder.common.entities"), Lang.text("worldbinder.map.tooltip.entities"), b -> { filterEntities = !filterEntities; updateFilterButtons(); }); addRenderableWidget(entitiesFilterButton);
        blockEntitiesFilterButton = filterButton(secondX + 100, row2, 124, Lang.string("worldbinder.map.blockentities"), Lang.text("worldbinder.map.tooltip.blockentities"), b -> { filterBlockEntities = !filterBlockEntities; updateFilterButtons(); }); addRenderableWidget(blockEntitiesFilterButton);
        addRenderableWidget(button(secondX + 232, row2, 96, Lang.string("worldbinder.map.view_value", modeLabel(WorldBinder.config().f10MapLayerMode)), Lang.text("worldbinder.map.tooltip.view"), b -> {
            WorldBinder.config().f10MapLayerMode = nextMode(WorldBinder.config().f10MapLayerMode);
            WorldBinder.config().save();
            b.setMessage(Component.literal(Lang.string("worldbinder.map.view_value", modeLabel(WorldBinder.config().f10MapLayerMode))));
        }));

        if (rightW > 0) {
            goX = new EditBox(font, right, 50, 78, 20, Lang.text("worldbinder.map.chunk_x"));
            goX.setHint(Lang.text("worldbinder.map.chunk_x"));
            goZ = new EditBox(font, right + 86, 50, 78, 20, Lang.text("worldbinder.map.chunk_z"));
            goZ.setHint(Lang.text("worldbinder.map.chunk_z"));
            addRenderableWidget(goX);
            addRenderableWidget(goZ);
            addRenderableWidget(button(right, 76, 78, Lang.string("worldbinder.map.go"), Lang.text("worldbinder.map.tooltip.go"), b -> jumpToFields()));
            addRenderableWidget(button(right + 86, 76, 78, Lang.string("worldbinder.map.player"), Lang.text("worldbinder.map.tooltip.player"), b -> followPlayer = true));
        } else {
            goX = new EditBox(font, Math.max(18, width - 178), 72, 78, 20, Lang.text("worldbinder.map.chunk_x"));
            goX.setHint(Lang.text("worldbinder.map.chunk_x"));
            goZ = new EditBox(font, Math.max(18, width - 92), 72, 78, 20, Lang.text("worldbinder.map.chunk_z"));
            goZ.setHint(Lang.text("worldbinder.map.chunk_z"));
            addRenderableWidget(goX);
            addRenderableWidget(goZ);
        }
        addRenderableWidget(button(width - 104, height - 30, 86, Lang.string("worldbinder.gui.back"), Lang.text("worldbinder.gui.back"), b -> onClose()));
        addRenderableWidget(button(18, height - 30, 110, Lang.string("worldbinder.map.queue_rescan"), Lang.text("worldbinder.map.tooltip.rescan"), b -> queueSelectedRescan()));
        addRenderableWidget(button(136, height - 30, 96, Lang.string("worldbinder.map.clear_filters"), Lang.text("worldbinder.map.tooltip.clear_filters"), b -> clearFilters()));
        updateFilterButtons();
    }

    private Button button(int x, int y, int w, String label, Component tooltip, Button.OnPress action) {
        return Button.builder(Component.literal(label), action).bounds(x, y, w, 20).tooltip(Tooltip.create(tooltip)).build();
    }

    private Button filterButton(int x, int y, int w, String label, Component tooltip, Button.OnPress action) {
        return Button.builder(Component.literal(filterButtonLabel(label, false)), action).bounds(x, y, w, 20).tooltip(Tooltip.create(tooltip)).build();
    }

    private void updateFilterButtons() {
        if (missingFilterButton != null) missingFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.map.missing"), filterMissing)));
        if (incompleteFilterButton != null) incompleteFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.map.incomplete"), filterIncomplete)));
        if (entitiesFilterButton != null) entitiesFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.common.entities"), filterEntities)));
        if (blockEntitiesFilterButton != null) blockEntitiesFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.map.blockentities"), filterBlockEntities)));
    }

    private String filterButtonLabel(String label, boolean active) {
        return (active ? "§a" + Lang.string("worldbinder.common.on") + " " : "§7" + Lang.string("worldbinder.common.off") + " ") + label;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoom = Math.max(1, Math.min(12, zoom + (verticalAmount > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (event.button() == 0) {
            followPlayer = false;
            int chunkPixels = Math.max(16, 16 * zoom);
            panChunkX -= (int) Math.round(offsetX / Math.max(1.0D, chunkPixels));
            panChunkZ -= (int) Math.round(offsetY / Math.max(1.0D, chunkPixels));
            return true;
        }
        return super.mouseDragged(event, offsetX, offsetY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        long hovered = chunkAt(event.x(), event.y());
        if (hovered != Long.MIN_VALUE) {
            selectedChunk = hovered;
            if (event.button() == 1) {
                WorldBinderClient.capture().queueChunkForRescan((int) hovered, (int) (hovered >> 32));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF005050C);
        SceneCaptureService capture = WorldBinderClient.capture();
        boolean reducedUiDetail = shouldReduceUiDetail(capture);
        refreshMapDataIfNeeded(capture, reducedUiDetail);
        Set<Long> done = cachedDone;
        Set<Long> partial = cachedPartial;
        Set<Long> queued = cachedQueued;
        Set<Long> failed = cachedFailed;
        Map<Long, ChunkSnapshot> snapshots = cachedSnapshots;
        Minecraft mc = Minecraft.getInstance();
        int playerChunkX = mc.player == null ? 0 : mc.player.blockPosition().getX() >> 4;
        int playerChunkZ = mc.player == null ? 0 : mc.player.blockPosition().getZ() >> 4;
        if (followPlayer) {
            panChunkX = playerChunkX;
            panChunkZ = playerChunkZ;
        }

        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.map.header"), width / 2, 14, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.literal(Lang.string("worldbinder.map.help_prefix") + modeLabel(WorldBinder.config().f10MapLayerMode) + (reducedUiDetail ? " • " + Lang.string("worldbinder.map.ui_reduced") : "")), width / 2, 29, reducedUiDetail ? 0xFFFFD166 : 0xFFBDB6D9);
        drawTopFilterStatus(context);

        int leftPanelW = leftPanelWidth();
        int rightPanelW = rightPanelWidth();
        int mapX = mapX(leftPanelW);
        int mapY = mapY();
        int mapW = mapWidth(leftPanelW, rightPanelW, mapX);
        int mapH = mapHeight(mapY);
        context.fill(mapX - 8, mapY - 8, mapX + mapW + 8, mapY + mapH + 8, 0xAA080810);
        context.fill(mapX - 8, mapY - 8, mapX + mapW + 8, mapY - 5, 0xFFFF55FF);
        context.fill(mapX - 8, mapY + mapH + 5, mapX + mapW + 8, mapY + mapH + 8, 0xFF5E03FC);

        int chunkPixels = Math.max(16, 16 * zoom);
        int chunksX = Math.max(3, mapW / chunkPixels);
        int chunksZ = Math.max(3, mapH / chunkPixels);
        int radiusX = chunksX / 2;
        int radiusZ = chunksZ / 2;
        Long hoveredKey = null;
        int hoveredX = 0;
        int hoveredZ = 0;

        for (int dz = -radiusZ; dz <= radiusZ; dz++) {
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                int cx = panChunkX + dx;
                int cz = panChunkZ + dz;
                long key = ChunkPos.pack(cx, cz);
                ChunkSnapshot snapshot = snapshots.get(key);
                ChunkCaptureStatus status = statusOf(key, snapshot, done, partial, queued, failed, cx == playerChunkX && cz == playerChunkZ);
                if (!passesFilter(key, snapshot, done, partial, queued, failed)) continue;
                int chunkX = mapX + (dx + radiusX) * chunkPixels;
                int chunkY = mapY + (dz + radiusZ) * chunkPixels;
                drawChunk(context, snapshot, chunkX, chunkY, zoom, fallbackChunkColor(status), WorldBinder.config().f10MapLayerMode, reducedUiDetail);
                boolean playerChunk = cx == playerChunkX && cz == playerChunkZ;
                boolean importantBorder = playerChunk || key == selectedChunk || status == ChunkCaptureStatus.DONE || status == ChunkCaptureStatus.PARTIAL || status == ChunkCaptureStatus.FAILED || zoom >= 3;
                if (importantBorder && (WorldBinder.config().f10MapLayerMode != WorldBinderConfig.MapLayerMode.MAP_ONLY || playerChunk || key == selectedChunk)) {
                    drawChunkBorder(context, chunkX, chunkY, chunkPixels, playerChunk ? Lang.string("worldbinder.map.player") : statusLabel(status), key == selectedChunk);
                }
                if (cx == playerChunkX && cz == playerChunkZ) {
                    drawPlayerCross(context, chunkX, chunkY, chunkPixels);
                }
                if (mouseX >= chunkX && mouseX < chunkX + chunkPixels && mouseY >= chunkY && mouseY < chunkY + chunkPixels) {
                    hoveredKey = key;
                    hoveredX = cx;
                    hoveredZ = cz;
                }
            }
        }

        refreshPanelsIfNeeded(capture, hoveredKey != null ? hoveredKey : selectedChunk, snapshots, done, partial, queued, failed);
        if (leftPanelW > 0) {
            drawLegend(context, 16, mapY + 6);
            drawCoveragePanel(context, 16, mapY + 118);
            drawFilterPanel(context, 16, Math.min(height - 150, mapY + 260));
        }
        if (rightPanelW > 0) {
            drawInspectorPanel(context, width - rightPanelW - 18, 106, hoveredKey != null ? hoveredKey : selectedChunk, hoveredKey != null ? hoveredX : (int) selectedChunk, hoveredKey != null ? hoveredZ : (int) (selectedChunk >> 32));
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.center_line", panChunkX, panChunkZ, playerChunkX, playerChunkZ, zoom, followPlayer ? Lang.string("worldbinder.map.following") : Lang.string("worldbinder.map.free_pan")), mapX, mapY + mapH + 20, 0xFFE6E6F0);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private long chunkAt(double mouseX, double mouseY) {
        int leftPanelW = leftPanelWidth();
        int rightPanelW = rightPanelWidth();
        int mapX = mapX(leftPanelW);
        int mapY = mapY();
        int mapW = mapWidth(leftPanelW, rightPanelW, mapX);
        int mapH = mapHeight(mapY);
        int chunkPixels = Math.max(16, 16 * zoom);
        int chunksX = Math.max(3, mapW / chunkPixels);
        int chunksZ = Math.max(3, mapH / chunkPixels);
        int radiusX = chunksX / 2;
        int radiusZ = chunksZ / 2;
        if (mouseX < mapX || mouseY < mapY || mouseX >= mapX + mapW || mouseY >= mapY + mapH) return Long.MIN_VALUE;
        int gridX = (int) ((mouseX - mapX) / chunkPixels) - radiusX;
        int gridZ = (int) ((mouseY - mapY) / chunkPixels) - radiusZ;
        return ChunkPos.pack(panChunkX + gridX, panChunkZ + gridZ);
    }



    private int leftPanelWidth() {
        return width >= 760 ? Math.min(170, Math.max(130, width / 7)) : 0;
    }

    private int rightPanelWidth() {
        return width >= 900 ? 184 : 0;
    }

    private int mapX(int leftPanelW) {
        return leftPanelW > 0 ? leftPanelW + 18 : 18;
    }

    private int mapY() {
        return width < 1120 ? 102 : 76;
    }

    private int mapWidth(int leftPanelW, int rightPanelW, int mapX) {
        int reservedRight = rightPanelW > 0 ? rightPanelW + 36 : 18;
        return Math.max(180, width - mapX - reservedRight);
    }

    private int mapHeight(int mapY) {
        return Math.max(120, height - mapY - 54);
    }
    private boolean shouldReduceUiDetail(SceneCaptureService capture) {
        if (!WorldBinder.config().effectiveAdaptiveThrottleEnabled()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        int fps = mc == null ? WorldBinder.config().targetFps : Math.max(1, mc.getFps());
        return capture.renderingQualityReduced() || fps < Math.max(1, WorldBinder.config().targetFps) * 0.75D || cachedSnapshots.size() > 50_000;
    }

    private void refreshMapDataIfNeeded(SceneCaptureService capture, boolean reducedUiDetail) {
        long now = System.currentTimeMillis();
        long revision = capture.mapDataRevision();
        if (now < nextMapDataRefreshMillis && revision == cachedMapRevision) {
            return;
        }
        long interval = Math.max(50L, WorldBinder.config().effectiveMaxUiWorkMs() * (reducedUiDetail ? 40L : 20L));
        nextMapDataRefreshMillis = now + Math.min(reducedUiDetail ? 250L : 125L, interval); // Heavy map data is capped; rendering still follows the screen FPS.
        cachedMapRevision = revision;
        cachedDone = capture.downloadedChunksView();
        cachedPartial = capture.partialChunksView();
        cachedQueued = capture.queuedChunksView();
        cachedFailed = capture.failedChunksView();
        cachedSnapshots = capture.chunkSnapshotsView();
    }

    private void refreshPanelsIfNeeded(SceneCaptureService capture, long inspectorKey, Map<Long, ChunkSnapshot> snapshots, Set<Long> done, Set<Long> partial, Set<Long> queued, Set<Long> failed) {
        long now = System.currentTimeMillis();
        long revision = capture.mapDataRevision();
        if (now < nextPanelRefreshMillis && inspectorKey == cachedInspectorKey && revision == cachedPanelRevision) {
            return;
        }
        nextPanelRefreshMillis = now + 350L;
        cachedPanelRevision = revision;
        cachedSavedCount = done.size();
        cachedScanningCount = partial.size();
        cachedQueuedCount = queued.size();
        cachedErrorCount = failed.size();
        cachedQueueDiagnostics = capture.queueDiagnosticsLine();
        cachedInspectorKey = inspectorKey;
        cachedInspectorSnapshot = inspectorKey == Long.MIN_VALUE ? null : snapshots.get(inspectorKey);
        cachedInspectorStatus = inspectorKey == Long.MIN_VALUE ? Lang.string("worldbinder.map.unknown") : statusLabel(statusOf(inspectorKey, cachedInspectorSnapshot, done, partial, queued, failed, false));
        cachedInspectorQuality = cachedInspectorSnapshot == null ? 0.0D : cachedInspectorSnapshot.qualityScore(expectedHeight());
    }

    private boolean passesFilter(long key, ChunkSnapshot snapshot, Set<Long> done, Set<Long> partial, Set<Long> queued, Set<Long> failed) {
        ChunkCaptureStatus status = statusOf(key, snapshot, done, partial, queued, failed, false);
        if (filterMissing && status != ChunkCaptureStatus.UNKNOWN) return false;
        if (filterIncomplete && !isIncomplete(status)) return false;
        if (filterEntities && (snapshot == null || snapshot.entityCount <= 0)) return false;
        return !filterBlockEntities || (snapshot != null && snapshot.blockEntityCount > 0);
    }

    private ChunkCaptureStatus statusOf(long key, ChunkSnapshot snapshot, Set<Long> done, Set<Long> partial, Set<Long> queued, Set<Long> failed, boolean player) {
        if (player) return ChunkCaptureStatus.DONE;
        if (failed.contains(key) || (snapshot != null && snapshot.exportError)) return ChunkCaptureStatus.FAILED;
        if (partial.contains(key)) return activeScanLabel(snapshot);
        if (queued.contains(key)) return ChunkCaptureStatus.QUEUED;
        if (done.contains(key)) return ChunkCaptureStatus.DONE;
        return snapshot == null ? ChunkCaptureStatus.UNKNOWN : snapshot.effectiveStatus();
    }

    private ChunkCaptureStatus activeScanLabel(ChunkSnapshot snapshot) {
        if (snapshot != null && snapshot.effectiveStatus() == ChunkCaptureStatus.PARTIAL) {
            return ChunkCaptureStatus.PARTIAL;
        }
        return ChunkCaptureStatus.SCANNING;
    }

    private boolean isIncomplete(ChunkCaptureStatus status) {
        return status == ChunkCaptureStatus.QUEUED
                || status == ChunkCaptureStatus.SCANNING
                || status == ChunkCaptureStatus.PARTIAL
                || status == ChunkCaptureStatus.FAILED
                || status == ChunkCaptureStatus.RECOVERY;
    }

    private String statusLabel(ChunkCaptureStatus status) {
        return switch (status == null ? ChunkCaptureStatus.UNKNOWN : status) {
            case UNKNOWN -> Lang.string("worldbinder.map.unknown");
            case QUEUED -> Lang.string("worldbinder.common.queued");
            case SCANNING -> Lang.string("worldbinder.common.scanning");
            case DONE -> Lang.string("worldbinder.common.done");
            case PARTIAL -> Lang.string("worldbinder.common.partial");
            case FAILED -> Lang.string("worldbinder.common.error");
            case RECOVERY -> Lang.string("worldbinder.section.recovery");
        };
    }

    private String filterStatusLine() {
        if (!filterMissing && !filterIncomplete && !filterEntities && !filterBlockEntities) {
            return Lang.string("worldbinder.map.filters_none");
        }
        StringBuilder builder = new StringBuilder(Lang.string("worldbinder.map.filters_prefix"));
        if (filterMissing) builder.append(" ").append(Lang.string("worldbinder.map.missing"));
        if (filterIncomplete) builder.append(" ").append(Lang.string("worldbinder.map.incomplete"));
        if (filterEntities) builder.append(" ").append(Lang.string("worldbinder.common.entities"));
        if (filterBlockEntities) builder.append(" ").append(Lang.string("worldbinder.map.blockentities"));
        return builder.toString();
    }

    private void drawTopFilterStatus(GuiGraphicsExtractor context) {
        String line = filterStatusLine();
        int textWidth = font.width(line);
        int x = (width - textWidth) / 2 - 10;
        int y = 52;
        int color = hasActiveFilters() ? 0xAA15301F : 0xAA10182A;
        int accent = hasActiveFilters() ? 0xFF55FFAA : 0xFF5E03FC;
        context.fill(x, y, x + textWidth + 20, y + 18, color);
        context.fill(x, y, x + textWidth + 20, y + 2, accent);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(line), x + 10, y + 6, hasActiveFilters() ? 0xFF55FFAA : 0xFFBDB6D9);
    }

    private boolean hasActiveFilters() {
        return filterMissing || filterIncomplete || filterEntities || filterBlockEntities;
    }

    private void drawFilterPanel(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 142, y + 98, hasActiveFilters() ? 0xAA102018 : 0xAA080810);
        context.fill(x, y, x + 142, y + 2, hasActiveFilters() ? 0xFF55FFAA : 0xFF5E03FC);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(hasActiveFilters() ? Lang.string("worldbinder.map.filters_active") : Lang.string("worldbinder.map.filters_none")), x + 10, y + 10, hasActiveFilters() ? 0xFF55FFAA : 0xFFFF55FF);
        filterLine(context, x + 10, y + 28, Lang.string("worldbinder.map.missing"), filterMissing);
        filterLine(context, x + 10, y + 42, Lang.string("worldbinder.map.incomplete"), filterIncomplete);
        filterLine(context, x + 10, y + 56, Lang.string("worldbinder.common.entities"), filterEntities);
        filterLine(context, x + 10, y + 70, Lang.string("worldbinder.map.blockentities"), filterBlockEntities);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.clear_hint"), x + 10, y + 84, 0xFF8F86B8);
    }

    private void filterLine(GuiGraphicsExtractor context, int x, int y, String label, boolean active) {
        if (active) {
            context.fill(x - 4, y - 1, x + 118, y + 11, 0x331AFF88);
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal((active ? "§a" + Lang.string("worldbinder.common.on") + "  " : "§7" + Lang.string("worldbinder.common.off") + " ") + "§f" + label), x, y, active ? 0xFF55FFAA : 0xFF8F86B8);
    }

    private void drawLegend(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 142, y + 100, 0xAA080810);
        context.fill(x, y, x + 142, y + 2, 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.legend"), x + 10, y + 10, 0xFFFF55FF);
        legend(context, x + 10, y + 28, 0xFF55FFAA, Lang.string("worldbinder.common.done"));
        legend(context, x + 10, y + 42, 0xFFFFE066, Lang.string("worldbinder.common.partial"));
        legend(context, x + 10, y + 56, 0xFFFFA12B, Lang.string("worldbinder.common.queued"));
        legend(context, x + 10, y + 70, 0xFF55A7FF, Lang.string("worldbinder.common.scanning"));
        legend(context, x + 10, y + 84, 0xFFFF5555, Lang.string("worldbinder.map.error_missing"));
    }

    private void legend(GuiGraphicsExtractor context, int x, int y, int color, String text) {
        context.fill(x, y + 2, x + 8, y + 10, color);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(text), x + 14, y, 0xFFE6E6F0);
    }

    private void drawCoveragePanel(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 142, y + 132, 0xAA080810);
        context.fill(x, y, x + 142, y + 2, 0xFF5E03FC);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.coverage"), x + 10, y + 10, 0xFFFF55FF);
        int total = Math.max(1, cachedSavedCount + cachedScanningCount + cachedQueuedCount);
        meter(context, x + 10, y + 30, 118, Lang.string("worldbinder.common.saved"), cachedSavedCount, total, 0xFF55FFAA);
        meter(context, x + 10, y + 52, 118, Lang.string("worldbinder.common.scanning"), cachedScanningCount, total, 0xFF55A7FF);
        meter(context, x + 10, y + 74, 118, Lang.string("worldbinder.common.queued"), cachedQueuedCount, total, 0xFFFFA12B);
        meter(context, x + 10, y + 96, 118, Lang.string("worldbinder.common.errors"), cachedErrorCount, Math.max(1, cachedSnapshots.size()), 0xFFFF5555);
    }

    private void meter(GuiGraphicsExtractor context, int x, int y, int w, String label, int value, int total, int color) {
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(label + " §f" + value), x, y, 0xFFBDB6D9);
        context.fill(x, y + 11, x + w, y + 15, 0x66000000);
        context.fill(x, y + 11, x + (int) (w * Math.min(1.0D, value / (double) total)), y + 15, color);
    }

    private void drawInspectorPanel(GuiGraphicsExtractor context, int x, int y, long key, int cx, int cz) {
        int w = 184;
        int h = WorldBinder.config().queueDebugDiagnostics ? 262 : 208;
        context.fill(x, y, x + w, y + h, 0xDD080810);
        context.fill(x, y, x + w, y + 2, 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.inspector"), x + 10, y + 10, 0xFFFF55FF);
        if (key == Long.MIN_VALUE) {
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.inspector.empty"), x + 10, y + 34, 0xFFBDB6D9);
            return;
        }
        ChunkSnapshot snapshot = cachedInspectorSnapshot;
        String status = cachedInspectorStatus;
        int yy = y + 32;
        line(context, x, yy, Lang.string("worldbinder.map.chunk"), cx + " / " + cz); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.common.status"), status); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.common.blocks"), snapshot == null ? "0" : Lang.string("worldbinder.map.scanned_blocks", snapshot.scannedBlocks)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.common.entities"), snapshot == null ? "0" : Integer.toString(snapshot.entityCount)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.blockentities"), snapshot == null ? "0" : Integer.toString(snapshot.blockEntityCount)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.biomes"), snapshot == null ? Lang.string("worldbinder.map.unknown") : (snapshot.hasBiomeData ? Lang.string("worldbinder.common.yes") : Lang.string("worldbinder.common.no"))); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.light"), snapshot == null ? Lang.string("worldbinder.map.unknown") : (snapshot.lightEstimated ? Lang.string("worldbinder.map.estimated") : Lang.string("worldbinder.common.saved"))); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.snapshot"), snapshot == null ? Lang.string("worldbinder.common.no") : (snapshot.hasSnapshot ? Lang.string("worldbinder.common.yes") : Lang.string("worldbinder.common.no"))); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.last_scanned"), snapshot == null ? Lang.string("worldbinder.common.never") : snapshot.lastScannedText()); yy += 16;
        if (WorldBinder.config().queueDebugDiagnostics && snapshot != null) {
            line(context, x, yy, Lang.string("worldbinder.map.queued_by"), valueOrDash(snapshot.queueSource)); yy += 16;
            line(context, x, yy, Lang.string("worldbinder.map.reason"), valueOrDash(snapshot.queueReason)); yy += 16;
            line(context, x, yy, Lang.string("worldbinder.common.queue"), cachedQueueDiagnostics); yy += 16;
            line(context, x, yy, Lang.string("worldbinder.map.history"), valueOrDash(snapshot.stateHistory)); yy += 16;
        }
        yy += 2;
        double quality = cachedInspectorQuality;
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.quality", (int) (quality * 100.0D)), x + 10, yy, 0xFFBDB6D9);
        context.fill(x + 10, yy + 12, x + w - 10, yy + 17, 0x66000000);
        context.fill(x + 10, yy + 12, x + 10 + (int) ((w - 20) * quality), yy + 17, quality > 0.9D ? 0xFF55FFAA : quality > 0.55D ? 0xFFFFE066 : 0xFFFF5555);
    }

    private void line(GuiGraphicsExtractor context, int x, int y, String label, String value) {
        String text = label + ": §f" + value;
        if (text.length() > 42) {
            text = text.substring(0, 39) + "...";
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(text), x + 10, y, 0xFFBDB6D9);
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void drawChunk(GuiGraphicsExtractor context, ChunkSnapshot snapshot, int x, int y, int zoom, int fallbackColor, WorldBinderConfig.MapLayerMode mode, boolean reducedUiDetail) {
        int chunkPixels = Math.max(16, 16 * zoom);
        int base = mode == WorldBinderConfig.MapLayerMode.MAP_ONLY ? 0x33111118 : fallbackColor;
        context.fill(x, y, x + chunkPixels, y + chunkPixels, base);
        if (snapshot == null || mode == WorldBinderConfig.MapLayerMode.CHUNKS_ONLY) {
            return;
        }
        // Keep the F10 map useful even when UI detail is reduced: the tile cache already
        // chooses a cheap LOD for small chunks, while still showing actual captured terrain.
        tileCache.draw(context, ChunkPos.pack(snapshot.chunkX, snapshot.chunkZ), snapshot, x, y, chunkPixels);
    }

    private void drawChunkBorder(GuiGraphicsExtractor context, int x, int y, int size, String status, boolean selected) {
        int color;
        if (status.equals(Lang.string("worldbinder.common.done"))) {
            color = 0xFF55FFAA;
        } else if (status.equals(Lang.string("worldbinder.common.partial"))) {
            color = 0xFFFFE066;
        } else if (status.equals(Lang.string("worldbinder.common.queued"))) {
            color = 0xFFFFA12B;
        } else if (status.equals(Lang.string("worldbinder.common.scanning"))) {
            color = 0xFF55A7FF;
        } else if (status.equals(Lang.string("worldbinder.map.player"))) {
            color = 0xFFFF55FF;
        } else if (status.equals(Lang.string("worldbinder.common.error"))) {
            color = 0xFFFF5555;
        } else {
            color = 0xFF555566;
        }
        if (selected) color = 0xFFFFFFFF;
        int t = size < 32 ? 1 : 2;
        context.fill(x, y, x + size, y + t, color);
        context.fill(x, y + size - t, x + size, y + size, color);
        context.fill(x, y, x + t, y + size, color);
        context.fill(x + size - t, y, x + size, y + size, color);
    }

    private void drawPlayerCross(GuiGraphicsExtractor context, int x, int y, int size) {
        int color = 0xFFFF55FF;
        int mid = size / 2;
        context.fill(x, y + mid - 1, x + size, y + mid + 1, color);
        context.fill(x + mid - 1, y, x + mid + 1, y + size, color);
        int t = size < 32 ? 1 : 2;
        context.fill(x, y, x + size, y + t, color);
        context.fill(x, y + size - t, x + size, y + size, color);
        context.fill(x, y, x + t, y + size, color);
        context.fill(x + size - t, y, x + size, y + size, color);
    }

    private static WorldBinderConfig.MapLayerMode nextMode(WorldBinderConfig.MapLayerMode mode) {
        return switch (mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode) {
            case BOTH -> WorldBinderConfig.MapLayerMode.CHUNKS_ONLY;
            case CHUNKS_ONLY -> WorldBinderConfig.MapLayerMode.MAP_ONLY;
            case MAP_ONLY -> WorldBinderConfig.MapLayerMode.BOTH;
        };
    }

    private static String modeLabel(WorldBinderConfig.MapLayerMode mode) {
        return switch (mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode) {
            case BOTH -> Lang.string("worldbinder.config.map_mode.both");
            case CHUNKS_ONLY -> Lang.string("worldbinder.config.map_mode.chunks");
            case MAP_ONLY -> Lang.string("worldbinder.config.map_mode.map");
        };
    }

    private int fallbackChunkColor(ChunkCaptureStatus status) {
        return switch (status == null ? ChunkCaptureStatus.UNKNOWN : status) {
            case FAILED -> 0x663C1010;
            case SCANNING -> 0x66305666;
            case PARTIAL, RECOVERY -> 0x665A4A10;
            case QUEUED -> 0x332A1F08;
            case DONE -> 0x3320402F;
            case UNKNOWN -> 0x16111122;
        };
    }

    private int expectedHeight() {
        return WorldBinder.config().effectiveCaptureHeight();
    }

    private void jumpToFields() {
        try {
            panChunkX = Integer.parseInt(goX.getValue().trim());
            panChunkZ = Integer.parseInt(goZ.getValue().trim());
            followPlayer = false;
        } catch (NumberFormatException ignored) {
        }
    }

    private void jumpToOrigin() {
        ChunkSnapshot first = WorldBinderClient.capture().chunkSnapshotsView().values().stream().findFirst().orElse(null);
        if (first != null) {
            panChunkX = first.chunkX;
            panChunkZ = first.chunkZ;
            followPlayer = false;
        }
    }

    private void queueSelectedRescan() {
        if (selectedChunk != Long.MIN_VALUE) {
            WorldBinderClient.capture().queueChunkForRescan((int) selectedChunk, (int) (selectedChunk >> 32));
        }
    }

    private void clearFilters() {
        filterMissing = false;
        filterIncomplete = false;
        filterEntities = false;
        filterBlockEntities = false;
        updateFilterButtons();
    }
}

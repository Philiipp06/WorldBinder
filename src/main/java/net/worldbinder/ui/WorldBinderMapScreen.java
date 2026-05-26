package net.worldbinder.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
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
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTooltips;

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
    private boolean filtersOpen;
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
    private String cachedInspectorStatus = "Unknown";
    private double cachedInspectorQuality;
    private String cachedQueueDiagnostics = "";

    public WorldBinderMapScreen(Screen parent) {
        super(Component.translatable("worldbinder.map.title"));
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
        missingFilterButton = null;
        incompleteFilterButton = null;
        entitiesFilterButton = null;
        blockEntitiesFilterButton = null;

        int margin = mapOuterMargin();
        int gap = 6;
        int top = controlsTop();
        int rowH = 22;
        int buttonW = width < 520 ? 64 : width < 760 ? 72 : 82;
        int x = margin;
        int y = top;
        int rightLimit = width - margin;

        Button follow = button(x, y, buttonW, "Follow", Component.literal("Keep the map centered on your current chunk"), b -> followPlayer = true);
        addRenderableWidget(follow);
        x += buttonW + gap;
        Button player = button(x, y, buttonW, "Player", Component.literal("Jump back to your current chunk"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                panChunkX = mc.player.blockPosition().getX() >> 4;
                panChunkZ = mc.player.blockPosition().getZ() >> 4;
                followPlayer = true;
            }
        });
        addRenderableWidget(player);
        x += buttonW + gap;
        if (x + buttonW > rightLimit) {
            x = margin;
            y += rowH + 6;
        }
        addRenderableWidget(button(x, y, buttonW, "Origin", Component.literal("Jump to the first captured chunk"), b -> jumpToOrigin()));
        x += buttonW + gap;

        int viewW = Math.min(Math.max(buttonW + 20, 104), Math.max(72, rightLimit - x));
        if (x + viewW > rightLimit) {
            x = margin;
            y += rowH + 6;
            viewW = Math.min(Math.max(buttonW + 20, 104), rightLimit - x);
        }
        addRenderableWidget(button(x, y, viewW, "View: " + modeLabel(WorldBinder.config().f10MapLayerMode), Component.literal("Switch between terrain, chunk status and combined view"), b -> {
            WorldBinder.config().f10MapLayerMode = nextMode(WorldBinder.config().f10MapLayerMode);
            WorldBinder.config().save();
            b.setMessage(Component.literal("View: " + modeLabel(WorldBinder.config().f10MapLayerMode)));
        }));
        x += viewW + gap;

        int filterW = Math.min(Math.max(buttonW + 26, 100), Math.max(72, rightLimit - x));
        if (x + filterW > rightLimit) {
            x = margin;
            y += rowH + 6;
            filterW = Math.min(Math.max(buttonW + 26, 100), rightLimit - x);
        }
        addRenderableWidget(button(x, y, filterW, filtersOpen ? "Filters ▲" : "Filters ▼", Component.literal("Open chunk filters"), b -> {
            filtersOpen = !filtersOpen;
            rebuildWidgets();
        }));

        int goY = y;
        int goW = 68;
        int goTotalW = goW * 2 + 72 + gap * 2;
        int goXStart = rightLimit - goTotalW;
        if (width >= 650 && goXStart > x + filterW + 12) {
            goX = new EditBox(font, goXStart, goY, goW, 20, Component.literal("Chunk X"));
            goX.setHint(Component.literal("Chunk X"));
            goZ = new EditBox(font, goXStart + goW + gap, goY, goW, 20, Component.literal("Chunk Z"));
            goZ.setHint(Component.literal("Chunk Z"));
            addRenderableWidget(goX);
            addRenderableWidget(goZ);
            addRenderableWidget(button(goXStart + goW * 2 + gap * 2, goY, 72, "Go", Component.literal("Jump to chunk X/Z"), b -> jumpToFields()));
        }

        if (filtersOpen) {
            int filterY = filterControlsY();
            int fw = Math.max(82, Math.min(118, (width - margin * 2 - gap * 3) / 4));
            int fx = margin;
            missingFilterButton = filterButton(fx, filterY, fw, "Missing", Component.literal("Only show chunks that are not captured yet"), b -> { filterMissing = !filterMissing; updateFilterButtons(); });
            addRenderableWidget(missingFilterButton);
            fx += fw + gap;
            incompleteFilterButton = filterButton(fx, filterY, fw, "Incomplete", Component.literal("Only show queued, partial or failed chunks"), b -> { filterIncomplete = !filterIncomplete; updateFilterButtons(); });
            addRenderableWidget(incompleteFilterButton);
            fx += fw + gap;
            if (fx + fw <= rightLimit) {
                entitiesFilterButton = filterButton(fx, filterY, fw, "Entities", Component.literal("Only show chunks containing captured entities"), b -> { filterEntities = !filterEntities; updateFilterButtons(); });
                addRenderableWidget(entitiesFilterButton);
                fx += fw + gap;
            }
            if (fx + fw <= rightLimit) {
                blockEntitiesFilterButton = filterButton(fx, filterY, fw, "BlockEntities", Component.literal("Only show chunks containing block entities"), b -> { filterBlockEntities = !filterBlockEntities; updateFilterButtons(); });
                addRenderableWidget(blockEntitiesFilterButton);
            }
        }

        int footerY = Math.max(controlsTop() + 28, height - 30);
        int footerW = Math.max(78, Math.min(130, (width - margin * 2 - gap * 2) / 3));
        addRenderableWidget(button(margin, footerY, footerW, "Queue rescan", Component.literal("Rescan selected chunk"), b -> queueSelectedRescan()));
        addRenderableWidget(button(margin + footerW + gap, footerY, footerW, "Clear filters", Component.literal("Disable all filters"), b -> clearFilters()));
        addRenderableWidget(button(width - margin - Math.min(86, footerW), footerY, Math.min(86, footerW), "Back", Component.literal("Back"), b -> onClose()));
        updateFilterButtons();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        init();
    }

    private Button button(int x, int y, int w, String label, Component tooltip, Button.OnPress action) {
        return WbTooltips.register(Button.builder(Component.literal(label), action).bounds(x, y, w, 20).build(), tooltip);
    }

    private Button filterButton(int x, int y, int w, String label, Component tooltip, Button.OnPress action) {
        return WbTooltips.register(Button.builder(Component.literal(filterButtonLabel(label, false)), action).bounds(x, y, w, 20).build(), tooltip);
    }

    private void updateFilterButtons() {
        if (missingFilterButton != null) missingFilterButton.setMessage(Component.literal(filterButtonLabel("Missing", filterMissing)));
        if (incompleteFilterButton != null) incompleteFilterButton.setMessage(Component.literal(filterButtonLabel("Incomplete", filterIncomplete)));
        if (entitiesFilterButton != null) entitiesFilterButton.setMessage(Component.literal(filterButtonLabel("Entities", filterEntities)));
        if (blockEntitiesFilterButton != null) blockEntitiesFilterButton.setMessage(Component.literal(filterButtonLabel("BlockEntities", filterBlockEntities)));
    }

    private String filterButtonLabel(String label, boolean active) {
        return (active ? "§aON " : "§7OFF ") + label;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoom = Math.max(1, Math.min(12, zoom + (verticalAmount > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        MouseButtonEvent virtualEvent = WbLayout.virtualMouseEvent(event, width, height);
        if (virtualEvent.button() == 0) {
            offsetX = uiScale.toVirtualDelta(offsetX);
            offsetY = uiScale.toVirtualDelta(offsetY);
            followPlayer = false;
            int chunkPixels = Math.max(16, 16 * zoom);
            panChunkX -= (int) Math.round(offsetX / Math.max(1.0D, chunkPixels));
            panChunkZ -= (int) Math.round(offsetY / Math.max(1.0D, chunkPixels));
            return true;
        }
        return super.mouseDragged(virtualEvent, offsetX, offsetY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = WbLayout.virtualMouseEvent(event, width, height);
        int realWidth = width;
        int realHeight = height;
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
        long hovered = chunkAt(virtualEvent.x(), virtualEvent.y());
        if (hovered != Long.MIN_VALUE) {
            selectedChunk = hovered;
            if (virtualEvent.button() == 1) {
                WorldBinderClient.capture().queueChunkForRescan((int) hovered, (int) (hovered >> 32));
                return true;
            }
        }
        return super.mouseClicked(virtualEvent, doubleClick);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, 0xF005050C);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
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

        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.literal("◆ WorldBinder Map ◆"), width / 2, 14, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.literal("Drag • Scroll • Right click rescan • View " + modeLabel(WorldBinder.config().f10MapLayerMode) + (reducedUiDetail ? " • UI detail reduced" : "")), width / 2, 29, reducedUiDetail ? 0xFFFFD166 : 0xFFBDB6D9);
        drawMapStatusChips(context);

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
        int chunksX = visibleChunkCount(mapW, chunkPixels);
        int chunksZ = visibleChunkCount(mapH, chunkPixels);
        int radiusX = chunksX / 2;
        int radiusZ = chunksZ / 2;
        Long hoveredKey = null;
        int hoveredX = 0;
        int hoveredZ = 0;

        context.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
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
                    drawChunkBorder(context, chunkX, chunkY, chunkPixels, playerChunk ? "Player" : statusLabel(status), key == selectedChunk);
                }
                if (cx == playerChunkX && cz == playerChunkZ) {
                    drawPlayerCross(context, chunkX, chunkY, chunkPixels);
                }
                if (virtualMouseX >= chunkX && virtualMouseX < chunkX + chunkPixels && virtualMouseY >= chunkY && virtualMouseY < chunkY + chunkPixels) {
                    hoveredKey = key;
                    hoveredX = cx;
                    hoveredZ = cz;
                }
            }
        }
        context.disableScissor();

        refreshPanelsIfNeeded(capture, hoveredKey != null ? hoveredKey : selectedChunk, snapshots, done, partial, queued, failed);
        if (leftPanelW > 0) {
            drawLegend(context, 16, mapY + 6);
            drawCoveragePanel(context, 16, mapY + 118);
            drawFilterPanel(context, 16, Math.min(height - 150, mapY + 260));
        }
        if (rightPanelW > 0) {
            drawInspectorPanel(context, width - rightPanelW - 18, 106, hoveredKey != null ? hoveredKey : selectedChunk, hoveredKey != null ? hoveredX : (int) selectedChunk, hoveredKey != null ? hoveredZ : (int) (selectedChunk >> 32));
        }
        if (hoveredKey != null) {
            drawChunkTooltip(context, virtualMouseX, virtualMouseY, hoveredKey, hoveredX, hoveredZ, snapshots, done, partial, queued, failed);
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Center §f" + panChunkX + ", " + panChunkZ + " §7• Player §d" + playerChunkX + ", " + playerChunkZ + " §7• Zoom §f" + zoom + "x §7• " + (followPlayer ? "Following" : "Free pan")), mapX, mapY + mapH + 20, 0xFFE6E6F0);
        super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
    }


    private void drawChunkTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, long key, int cx, int cz, Map<Long, ChunkSnapshot> snapshots, Set<Long> done, Set<Long> partial, Set<Long> queued, Set<Long> failed) {
        ChunkSnapshot snapshot = snapshots.get(key);
        ChunkCaptureStatus status = statusOf(key, snapshot, done, partial, queued, failed, false);
        String[] lines = new String[]{
                "Chunk: " + cx + " / " + cz,
                "Status: " + statusLabel(status),
                "Blocks: " + (snapshot == null ? "0" : snapshot.savedBlocks + " / " + snapshot.scannedBlocks),
                "Entities: " + (snapshot == null ? "0" : Integer.toString(snapshot.entityCount)),
                "Block entities: " + (snapshot == null ? "0" : Integer.toString(snapshot.blockEntityCount))
        };
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(line));
        }
        w += 20;
        int h = 18 + lines.length * 12;
        int x = Math.min(width - w - 8, mouseX + 14);
        int y = Math.min(height - h - 8, mouseY + 14);
        context.fill(x, y, x + w, y + h, 0xEE080810);
        context.fill(x, y, x + w, y + 2, status == ChunkCaptureStatus.DONE ? 0xFF55FFAA : status == ChunkCaptureStatus.FAILED ? 0xFFFF5555 : 0xFFFF55FF);
        for (int i = 0; i < lines.length; i++) {
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(lines[i]), x + 10, y + 10 + i * 12, i == 1 ? 0xFFE6E6F0 : 0xFFBDB6D9);
        }
    }

    private long chunkAt(double mouseX, double mouseY) {
        int leftPanelW = leftPanelWidth();
        int rightPanelW = rightPanelWidth();
        int mapX = mapX(leftPanelW);
        int mapY = mapY();
        int mapW = mapWidth(leftPanelW, rightPanelW, mapX);
        int mapH = mapHeight(mapY);
        int chunkPixels = Math.max(16, 16 * zoom);
        int chunksX = visibleChunkCount(mapW, chunkPixels);
        int chunksZ = visibleChunkCount(mapH, chunkPixels);
        int radiusX = chunksX / 2;
        int radiusZ = chunksZ / 2;
        if (mouseX < mapX || mouseY < mapY || mouseX >= mapX + mapW || mouseY >= mapY + mapH) return Long.MIN_VALUE;
        int gridX = (int) ((mouseX - mapX) / chunkPixels) - radiusX;
        int gridZ = (int) ((mouseY - mapY) / chunkPixels) - radiusZ;
        return ChunkPos.pack(panChunkX + gridX, panChunkZ + gridZ);
    }



    private int mapOuterMargin() {
        return width < 520 ? 12 : 18;
    }

    private int controlsTop() {
        return height < 390 ? 42 : 52;
    }

    private int toolbarRows() {
        if (width < 360) {
            return 4;
        }
        if (width < 520) {
            return 3;
        }
        if (width < 760) {
            return 2;
        }
        return 1;
    }

    private int statusY() {
        return controlsTop() + toolbarRows() * 28 + 8;
    }

    private int statusChipRows() {
        int available = Math.max(1, width - mapOuterMargin() * 2);
        int fullWidth = 0;
        String[] labels = {"Saved", "Partial", "Queued", "Errors"};
        int[] values = {cachedSavedCount, cachedScanningCount, cachedQueuedCount, cachedErrorCount};
        for (int i = 0; i < labels.length; i++) {
            fullWidth += chipWidth(labels[i], values[i]) + (i == 0 ? 0 : 6);
        }
        if (hasActiveFilters()) {
            fullWidth += chipWidth(filterStatusLine().replace("Filters:", "Filter"), -1) + 6;
        }
        return fullWidth > available ? 2 : 1;
    }

    private int filterControlsY() {
        return statusY() + statusChipRows() * 20 + 6;
    }

    private int leftPanelWidth() {
        return width >= 1040 && height >= 520 ? Math.min(160, Math.max(126, width / 9)) : 0;
    }

    private int rightPanelWidth() {
        return width >= 1220 && height >= 520 ? 184 : 0;
    }

    private int mapX(int leftPanelW) {
        return leftPanelW > 0 ? leftPanelW + mapOuterMargin() : mapOuterMargin();
    }

    private int mapY() {
        return filterControlsY() + (filtersOpen ? 28 : 0) + 8;
    }

    private int mapWidth(int leftPanelW, int rightPanelW, int mapX) {
        int reservedRight = rightPanelW > 0 ? rightPanelW + mapOuterMargin() * 2 : mapOuterMargin();
        return Math.max(80, width - mapX - reservedRight);
    }

    private int mapHeight(int mapY) {
        return Math.max(70, height - mapY - 62);
    }

    private static int visibleChunkCount(int pixels, int chunkPixels) {
        int count = Math.max(3, pixels / Math.max(1, chunkPixels));
        if (count % 2 == 0) {
            count--;
        }
        return Math.max(3, count);
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
        cachedInspectorStatus = inspectorKey == Long.MIN_VALUE ? "Unknown" : statusLabel(statusOf(inspectorKey, cachedInspectorSnapshot, done, partial, queued, failed, false));
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
            case UNKNOWN -> "Unknown";
            case QUEUED -> "Queued";
            case SCANNING -> "Scanning";
            case DONE -> "Done";
            case PARTIAL -> "Partial";
            case FAILED -> "Error";
            case RECOVERY -> "Recovery";
        };
    }

    private String filterStatusLine() {
        if (!filterMissing && !filterIncomplete && !filterEntities && !filterBlockEntities) {
            return "Filters: none";
        }
        StringBuilder builder = new StringBuilder("Filters:");
        if (filterMissing) builder.append(" Missing");
        if (filterIncomplete) builder.append(" Incomplete");
        if (filterEntities) builder.append(" Entities");
        if (filterBlockEntities) builder.append(" BlockEntities");
        return builder.toString();
    }


    private void drawMapStatusChips(GuiGraphicsExtractor context) {
        int y = statusY();
        int x = mapOuterMargin();
        int startX = x;
        int right = width - mapOuterMargin();
        String[] labels = {"Saved", "Partial", "Queued", "Errors"};
        int[] values = {cachedSavedCount, cachedScanningCount, cachedQueuedCount, cachedErrorCount};
        int[] accents = {0xFF55FFAA, 0xFFFFE066, 0xFFFFA12B, 0xFFFF5555};
        for (int i = 0; i < labels.length; i++) {
            int chipWidth = chipWidth(labels[i], values[i]);
            if (x > startX && x + chipWidth > right) {
                x = startX;
                y += 20;
            }
            x = chip(context, x, y, labels[i], values[i], accents[i]) + 6;
        }
        if (hasActiveFilters()) {
            String label = filterStatusLine().replace("Filters:", "Filter");
            int chipWidth = chipWidth(label, -1);
            if (x > startX && x + chipWidth > right) {
                x = startX;
                y += 20;
            }
            chip(context, x, y, label, -1, 0xFF55A7FF);
        }
    }

    private int chipWidth(String label, int value) {
        String text = value < 0 ? label : label + " " + value;
        return font.width(text) + 18;
    }

    private int chip(GuiGraphicsExtractor context, int x, int y, String label, int value, int accent) {
        String text = value < 0 ? label : label + " " + value;
        int w = font.width(text) + 18;
        context.fill(x, y, x + w, y + 18, 0xAA080810);
        context.fill(x, y, x + 3, y + 18, accent);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(text), x + 9, y + 5, 0xFFE6E6F0);
        return x + w;
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
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Filter panel"), x + 10, y + 10, hasActiveFilters() ? 0xFF55FFAA : 0xFFFF55FF);
        filterLine(context, x + 10, y + 28, "Missing", filterMissing);
        filterLine(context, x + 10, y + 42, "Incomplete", filterIncomplete);
        filterLine(context, x + 10, y + 56, "Entities", filterEntities);
        filterLine(context, x + 10, y + 70, "BlockEntities", filterBlockEntities);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Clear button resets all"), x + 10, y + 84, 0xFF8F86B8);
    }

    private void filterLine(GuiGraphicsExtractor context, int x, int y, String label, boolean active) {
        if (active) {
            context.fill(x - 4, y - 1, x + 118, y + 11, 0x331AFF88);
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal((active ? "§aON  " : "§7OFF ") + "§f" + label), x, y, active ? 0xFF55FFAA : 0xFF8F86B8);
    }

    private void drawLegend(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 142, y + 100, 0xAA080810);
        context.fill(x, y, x + 142, y + 2, 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Legend"), x + 10, y + 10, 0xFFFF55FF);
        legend(context, x + 10, y + 28, 0xFF55FFAA, "Done");
        legend(context, x + 10, y + 42, 0xFFFFE066, "Partial");
        legend(context, x + 10, y + 56, 0xFFFFA12B, "Queued");
        legend(context, x + 10, y + 70, 0xFF55A7FF, "Scanning");
        legend(context, x + 10, y + 84, 0xFFFF5555, "Error / missing");
    }

    private void legend(GuiGraphicsExtractor context, int x, int y, int color, String text) {
        context.fill(x, y + 2, x + 8, y + 10, color);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(text), x + 14, y, 0xFFE6E6F0);
    }

    private void drawCoveragePanel(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 142, y + 132, 0xAA080810);
        context.fill(x, y, x + 142, y + 2, 0xFF5E03FC);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Coverage"), x + 10, y + 10, 0xFFFF55FF);
        int total = Math.max(1, cachedSavedCount + cachedScanningCount + cachedQueuedCount);
        meter(context, x + 10, y + 30, 118, "Saved", cachedSavedCount, total, 0xFF55FFAA);
        meter(context, x + 10, y + 52, 118, "Scanning", cachedScanningCount, total, 0xFF55A7FF);
        meter(context, x + 10, y + 74, 118, "Queued", cachedQueuedCount, total, 0xFFFFA12B);
        meter(context, x + 10, y + 96, 118, "Errors", cachedErrorCount, Math.max(1, cachedSnapshots.size()), 0xFFFF5555);
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
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Chunk Inspector"), x + 10, y + 10, 0xFFFF55FF);
        if (key == Long.MIN_VALUE) {
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Hover or right-click a chunk."), x + 10, y + 34, 0xFFBDB6D9);
            return;
        }
        ChunkSnapshot snapshot = cachedInspectorSnapshot;
        String status = cachedInspectorStatus;
        int yy = y + 32;
        line(context, x, yy, "Chunk", cx + " / " + cz); yy += 16;
        line(context, x, yy, "Status", status); yy += 16;
        line(context, x, yy, "Blocks", snapshot == null ? "0" : snapshot.scannedBlocks + " scanned"); yy += 16;
        line(context, x, yy, "Entities", snapshot == null ? "0" : Integer.toString(snapshot.entityCount)); yy += 16;
        line(context, x, yy, "BlockEntities", snapshot == null ? "0" : Integer.toString(snapshot.blockEntityCount)); yy += 16;
        line(context, x, yy, "Biomes", snapshot == null ? "Unknown" : (snapshot.hasBiomeData ? "Yes" : "No")); yy += 16;
        line(context, x, yy, "Light", snapshot == null ? "Unknown" : (snapshot.lightEstimated ? "Estimated" : "Saved")); yy += 16;
        line(context, x, yy, "Snapshot", snapshot == null ? "No" : (snapshot.hasSnapshot ? "Yes" : "No")); yy += 16;
        line(context, x, yy, "Last scanned", snapshot == null ? "never" : snapshot.lastScannedText()); yy += 16;
        if (WorldBinder.config().queueDebugDiagnostics && snapshot != null) {
            line(context, x, yy, "Queued by", valueOrDash(snapshot.queueSource)); yy += 16;
            line(context, x, yy, "Reason", valueOrDash(snapshot.queueReason)); yy += 16;
            line(context, x, yy, "Queue", cachedQueueDiagnostics); yy += 16;
            line(context, x, yy, "History", valueOrDash(snapshot.stateHistory)); yy += 16;
        }
        yy += 2;
        double quality = cachedInspectorQuality;
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Quality §f" + (int) (quality * 100.0D) + "%"), x + 10, yy, 0xFFBDB6D9);
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
        if (snapshot != null && mode != WorldBinderConfig.MapLayerMode.CHUNKS_ONLY) {
            tileCache.draw(context, ChunkPos.pack(snapshot.chunkX, snapshot.chunkZ), snapshot, x, y, chunkPixels);
        }
        if (chunkPixels >= 24) {
            context.fill(x, y, x + chunkPixels, y + 1, 0x33111122);
            context.fill(x, y, x + 1, y + chunkPixels, 0x33111122);
        }
        if (snapshot != null && snapshot.effectiveStatus() != ChunkCaptureStatus.UNKNOWN) {
            int quality = (int) Math.min(chunkPixels, Math.max(2, chunkPixels * snapshot.qualityScore(expectedHeight())));
            context.fill(x, y + chunkPixels - 2, x + quality, y + chunkPixels, snapshot.isDone() ? 0xFF55FFAA : 0xFFFFE066);
        }
    }

    private void drawChunkBorder(GuiGraphicsExtractor context, int x, int y, int size, String status, boolean selected) {
        int color = switch (status) {
            case "Done" -> 0xFF55FFAA;
            case "Partial" -> 0xFFFFE066;
            case "Queued" -> 0xFFFFA12B;
            case "Scanning" -> 0xFF55A7FF;
            case "Player" -> 0xFFFF55FF;
            case "Error" -> 0xFFFF5555;
            default -> 0xFF555566;
        };
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
            case BOTH -> "Both";
            case CHUNKS_ONLY -> "Chunks";
            case MAP_ONLY -> "Map";
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

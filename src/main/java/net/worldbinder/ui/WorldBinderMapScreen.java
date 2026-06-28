package net.worldbinder.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
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
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.Chat;
import net.worldbinder.util.Lang;
import org.lwjgl.glfw.GLFW;

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
    private String cachedInspectorStatus = "";
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

        Button follow = button(x, y, buttonW, Lang.string("worldbinder.map.follow"), Component.translatable("worldbinder.map.tooltip.follow"), b -> followPlayer = true);
        addRenderableWidget(follow);
        x += buttonW + gap;
        Button player = button(x, y, buttonW, Lang.string("worldbinder.map.player"), Component.translatable("worldbinder.map.tooltip.player"), b -> {
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
        addRenderableWidget(button(x, y, buttonW, Lang.string("worldbinder.map.origin"), Component.translatable("worldbinder.map.tooltip.origin"), b -> jumpToOrigin()));
        x += buttonW + gap;

        int viewW = Math.min(Math.max(buttonW + 20, 104), Math.max(72, rightLimit - x));
        if (x + viewW > rightLimit) {
            x = margin;
            y += rowH + 6;
            viewW = Math.min(Math.max(buttonW + 20, 104), rightLimit - x);
        }
        addRenderableWidget(button(x, y, viewW, Lang.string("worldbinder.map.view_value", modeLabel(WorldBinder.config().f10MapLayerMode)), Component.translatable("worldbinder.map.tooltip.view"), b -> {
            WorldBinder.config().f10MapLayerMode = nextMode(WorldBinder.config().f10MapLayerMode);
            WorldBinder.config().save();
            b.setMessage(Component.literal(Lang.string("worldbinder.map.view_value", modeLabel(WorldBinder.config().f10MapLayerMode))));
        }));
        x += viewW + gap;

        int filterW = Math.min(Math.max(buttonW + 26, 100), Math.max(72, rightLimit - x));
        if (x + filterW > rightLimit) {
            x = margin;
            y += rowH + 6;
            filterW = Math.min(Math.max(buttonW + 26, 100), rightLimit - x);
        }
        addRenderableWidget(button(x, y, filterW, Lang.string(filtersOpen ? "worldbinder.map.filters_toggle_open" : "worldbinder.map.filters_toggle_closed"), Component.translatable("worldbinder.map.tooltip.filters"), b -> {
            filtersOpen = !filtersOpen;
            rebuildWidgets();
        }));

        int goY = y;
        int goW = 68;
        int goTotalW = goW * 2 + 72 + gap * 2;
        int goXStart = rightLimit - goTotalW;
        if (width >= 650 && goXStart > x + filterW + 12) {
            goX = new EditBox(font, goXStart, goY, goW, 20, Lang.text("worldbinder.map.chunk_x"));
            goX.setHint(Lang.text("worldbinder.map.chunk_x"));
            goZ = new EditBox(font, goXStart + goW + gap, goY, goW, 20, Lang.text("worldbinder.map.chunk_z"));
            goZ.setHint(Lang.text("worldbinder.map.chunk_z"));
            addRenderableWidget(goX);
            addRenderableWidget(goZ);
            addRenderableWidget(button(goXStart + goW * 2 + gap * 2, goY, 72, Lang.string("worldbinder.map.go"), Component.translatable("worldbinder.map.tooltip.go"), b -> jumpToFields()));
        }

        if (filtersOpen) {
            int filterY = filterControlsY();
            int fw = Math.max(82, Math.min(118, (width - margin * 2 - gap * 3) / 4));
            int fx = margin;
            missingFilterButton = filterButton(fx, filterY, fw, Lang.string("worldbinder.map.missing"), Component.translatable("worldbinder.map.tooltip.missing"), b -> { filterMissing = !filterMissing; updateFilterButtons(); });
            addRenderableWidget(missingFilterButton);
            fx += fw + gap;
            incompleteFilterButton = filterButton(fx, filterY, fw, Lang.string("worldbinder.map.incomplete"), Component.translatable("worldbinder.map.tooltip.incomplete"), b -> { filterIncomplete = !filterIncomplete; updateFilterButtons(); });
            addRenderableWidget(incompleteFilterButton);
            fx += fw + gap;
            if (fx + fw <= rightLimit) {
                entitiesFilterButton = filterButton(fx, filterY, fw, Lang.string("worldbinder.map.entities"), Component.translatable("worldbinder.map.tooltip.entities"), b -> { filterEntities = !filterEntities; updateFilterButtons(); });
                addRenderableWidget(entitiesFilterButton);
                fx += fw + gap;
            }
            if (fx + fw <= rightLimit) {
                blockEntitiesFilterButton = filterButton(fx, filterY, fw, Lang.string("worldbinder.map.blockentities"), Component.translatable("worldbinder.map.tooltip.blockentities"), b -> { filterBlockEntities = !filterBlockEntities; updateFilterButtons(); });
                addRenderableWidget(blockEntitiesFilterButton);
            }
        }

        int footerY = Math.max(controlsTop() + 28, height - 30);
        int footerW = Math.max(78, Math.min(130, (width - margin * 2 - gap * 2) / 3));
        addRenderableWidget(button(margin, footerY, footerW, Lang.string("worldbinder.map.queue_rescan"), Component.translatable("worldbinder.map.tooltip.rescan"), b -> queueSelectedRescan()));
        addRenderableWidget(button(margin + footerW + gap, footerY, footerW, Lang.string("worldbinder.map.clear_filters"), Component.translatable("worldbinder.map.tooltip.clear_filters"), b -> clearFilters()));
        addRenderableWidget(button(width - margin - Math.min(86, footerW), footerY, Math.min(86, footerW), Lang.string("worldbinder.gui.back"), Component.translatable("worldbinder.tooltip.config.back"), b -> onClose()));
        updateFilterButtons();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        init();
    }

    private Button button(int x, int y, int w, String label, Component tooltip, Button.OnPress action) {
        return WbButton.create(x, y, w, 20, label, tooltip, action);
    }

    private Button filterButton(int x, int y, int w, String label, Component tooltip, Button.OnPress action) {
        return WbButton.create(x, y, w, 20, filterButtonLabel(label, false), tooltip, action);
    }

    private void updateFilterButtons() {
        if (missingFilterButton != null) missingFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.map.missing"), filterMissing)));
        if (incompleteFilterButton != null) incompleteFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.map.incomplete"), filterIncomplete)));
        if (entitiesFilterButton != null) entitiesFilterButton.setMessage(Component.literal(filterButtonLabel(Lang.string("worldbinder.map.entities"), filterEntities)));
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
                WorldBinderClient.capture().queueChunkForRescan(chunkXFromKey(hovered), chunkZFromKey(hovered));
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
    public boolean keyPressed(KeyEvent event) {
        if (getFocused() == goX || getFocused() == goZ) {
            return super.keyPressed(event);
        }
        boolean copyPressed = event.key() == GLFW.GLFW_KEY_C
                && (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (copyPressed && selectedChunk != Long.MIN_VALUE) {
            copySelectedChunkCenter();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
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
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
        WbChrome.drawBackdrop(context, width, height);
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

        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.map.title"), width / 2, 14, WbTheme.TEXT);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.literal(Lang.string("worldbinder.map.help_prefix") + modeLabel(WorldBinder.config().f10MapLayerMode) + (reducedUiDetail ? " / " + Lang.string("worldbinder.map.ui_reduced") : "")), width / 2, 29, reducedUiDetail ? WbTheme.WARN : WbTheme.TEXT_MUTED);
        drawMapStatusChips(context);

        int leftPanelW = leftPanelWidth();
        int rightPanelW = rightPanelWidth();
        int mapX = mapX(leftPanelW);
        int mapY = mapY();
        int mapW = mapWidth(leftPanelW, rightPanelW, mapX);
        int mapH = mapHeight(mapY);
        WbChrome.drawInset(context, mapX - 8, mapY - 8, mapW + 16, mapH + 16, WbTheme.ACCENT, true);

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
                    drawChunkBorder(context, chunkX, chunkY, chunkPixels, status, playerChunk, key == selectedChunk);
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
            long inspectorKey = hoveredKey != null ? hoveredKey : selectedChunk;
            drawInspectorPanel(context, width - rightPanelW - 18, 106, inspectorKey, hoveredKey != null ? hoveredX : chunkXFromKey(inspectorKey), hoveredKey != null ? hoveredZ : chunkZFromKey(inspectorKey));
        }
        if (hoveredKey != null) {
            drawChunkTooltip(context, virtualMouseX, virtualMouseY, hoveredKey, hoveredX, hoveredZ, snapshots, done, partial, queued, failed);
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(Lang.string("worldbinder.map.center_line", panChunkX, panChunkZ, playerChunkX, playerChunkZ, zoom, followPlayer ? Lang.string("worldbinder.map.following") : Lang.string("worldbinder.map.free_pan"))), mapX, mapY + mapH + 20, 0xFFE6E6F0);
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
        int exactChunkX = chunkXFromKey(key);
        int exactChunkZ = chunkZFromKey(key);
        ChunkCaptureStatus status = statusOf(key, snapshot, done, partial, queued, failed, false);
        int centerX = chunkMiddleBlock(exactChunkX);
        int centerZ = chunkMiddleBlock(exactChunkZ);
        String[] lines = new String[]{
                Lang.string("worldbinder.map.tooltip.chunk_line", exactChunkX, exactChunkZ),
                Lang.string("worldbinder.map.tooltip.center_line", centerX, centerZ),
                Lang.string("worldbinder.map.tooltip.copy_center"),
                Lang.string("worldbinder.map.tooltip.status_line", statusLabel(status)),
                Lang.string("worldbinder.map.tooltip.blocks_line", snapshot == null ? "0" : snapshot.savedBlocks + " / " + snapshot.scannedBlocks),
                Lang.string("worldbinder.map.tooltip.entities_line", snapshot == null ? "0" : Integer.toString(snapshot.entityCount)),
                Lang.string("worldbinder.map.tooltip.blockentities_line", snapshot == null ? "0" : Integer.toString(snapshot.blockEntityCount))
        };
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(line));
        }
        w += 20;
        int h = 18 + lines.length * 12;
        int x = Math.min(width - w - 8, mouseX + 14);
        int y = Math.min(height - h - 8, mouseY + 14);
        int accent = status == ChunkCaptureStatus.DONE ? WbTheme.OK : status == ChunkCaptureStatus.FAILED ? WbTheme.ERROR : WbTheme.ACCENT;
        WbChrome.drawInset(context, x, y, w, h, accent, true);
        for (int i = 0; i < lines.length; i++) {
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(lines[i]), x + 10, y + 10 + i * 12, i == 1 ? 0xFFE6E6F0 : 0xFFBDB6D9);
        }
    }

    private static int chunkXFromKey(long key) {
        return key == Long.MIN_VALUE ? 0 : (int) key;
    }

    private static int chunkZFromKey(long key) {
        return key == Long.MIN_VALUE ? 0 : (int) (key >> 32);
    }

    private static int chunkMinBlock(int chunk) {
        return chunk << 4;
    }

    private static int chunkMiddleBlock(int chunk) {
        return (chunk << 4) + 8;
    }

    private static int chunkMaxBlock(int chunk) {
        return (chunk << 4) + 15;
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
        String[] labels = {
                Lang.string("worldbinder.map.saved"),
                Lang.string("worldbinder.map.partial"),
                Lang.string("worldbinder.map.queued"),
                Lang.string("worldbinder.map.errors")
        };
        int[] values = {cachedSavedCount, cachedScanningCount, cachedQueuedCount, cachedErrorCount};
        for (int i = 0; i < labels.length; i++) {
            fullWidth += chipWidth(labels[i], values[i]) + (i == 0 ? 0 : 6);
        }
        if (hasActiveFilters()) {
            fullWidth += chipWidth(filterStatusLine(), -1) + 6;
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
        cachedInspectorStatus = inspectorKey == Long.MIN_VALUE ? Lang.string("worldbinder.map.status.unknown") : statusLabel(statusOf(inspectorKey, cachedInspectorSnapshot, done, partial, queued, failed, false));
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
            case UNKNOWN -> Lang.string("worldbinder.map.status.unknown");
            case QUEUED -> Lang.string("worldbinder.map.status.queued");
            case SCANNING -> Lang.string("worldbinder.map.status.scanning");
            case DONE -> Lang.string("worldbinder.map.status.done");
            case PARTIAL -> Lang.string("worldbinder.map.status.partial");
            case FAILED -> Lang.string("worldbinder.map.status.error");
            case RECOVERY -> Lang.string("worldbinder.map.status.recovery");
        };
    }

    private String filterStatusLine() {
        if (!filterMissing && !filterIncomplete && !filterEntities && !filterBlockEntities) {
            return Lang.string("worldbinder.map.filters_none");
        }
        StringBuilder builder = new StringBuilder(Lang.string("worldbinder.map.filters_prefix"));
        if (filterMissing) builder.append(' ').append(Lang.string("worldbinder.map.missing"));
        if (filterIncomplete) builder.append(' ').append(Lang.string("worldbinder.map.incomplete"));
        if (filterEntities) builder.append(' ').append(Lang.string("worldbinder.map.entities"));
        if (filterBlockEntities) builder.append(' ').append(Lang.string("worldbinder.map.blockentities"));
        return builder.toString();
    }


    private void drawMapStatusChips(GuiGraphicsExtractor context) {
        int y = statusY();
        int x = mapOuterMargin();
        int startX = x;
        int right = width - mapOuterMargin();
        String[] labels = {
                Lang.string("worldbinder.map.saved"),
                Lang.string("worldbinder.map.partial"),
                Lang.string("worldbinder.map.queued"),
                Lang.string("worldbinder.map.errors")
        };
        int[] values = {cachedSavedCount, cachedScanningCount, cachedQueuedCount, cachedErrorCount};
        int[] accents = {WbTheme.OK, WbTheme.WARN, WbTheme.ACCENT_RIGHT, WbTheme.ERROR};
        for (int i = 0; i < labels.length; i++) {
            int chipWidth = chipWidth(labels[i], values[i]);
            if (x > startX && x + chipWidth > right) {
                x = startX;
                y += 20;
            }
            x = chip(context, x, y, labels[i], values[i], accents[i]) + 6;
        }
        if (hasActiveFilters()) {
            String label = filterStatusLine();
            int chipWidth = chipWidth(label, -1);
            if (x > startX && x + chipWidth > right) {
                x = startX;
                y += 20;
            }
            chip(context, x, y, label, -1, WbTheme.INFO);
        }
    }

    private int chipWidth(String label, int value) {
        String text = value < 0 ? label : label + " " + value;
        return font.width(text) + 18;
    }

    private int chip(GuiGraphicsExtractor context, int x, int y, String label, int value, int accent) {
        String text = value < 0 ? label : label + " " + value;
        int w = font.width(text) + 18;
        WbChrome.drawInset(context, x, y, w, 18, accent, true);
        WbText.drawClipped(context, font, text, x + 9, y + 5, w - 18, WbTheme.TEXT_SOFT);
        return x + w;
    }

    private void drawTopFilterStatus(GuiGraphicsExtractor context) {
        String line = filterStatusLine();
        int textWidth = font.width(line);
        int x = (width - textWidth) / 2 - 10;
        int y = 52;
        int color = hasActiveFilters() ? 0xAA15301F : 0xAA10182A;
        int accent = hasActiveFilters() ? WbTheme.OK : WbTheme.ACCENT;
        context.fill(x, y, x + textWidth + 20, y + 18, color);
        context.fill(x, y, x + textWidth + 20, y + 2, accent);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(line), x + 10, y + 6, hasActiveFilters() ? WbTheme.OK : WbTheme.TEXT_MUTED);
    }

    private boolean hasActiveFilters() {
        return filterMissing || filterIncomplete || filterEntities || filterBlockEntities;
    }

    private void drawFilterPanel(GuiGraphicsExtractor context, int x, int y) {
        WbChrome.drawCard(context, font, x, y, 142, 98, Lang.text("worldbinder.map.filter_panel"), hasActiveFilters() ? WbTheme.OK : WbTheme.ACCENT, false);
        filterLine(context, x + 10, y + 28, Lang.string("worldbinder.map.missing"), filterMissing);
        filterLine(context, x + 10, y + 42, Lang.string("worldbinder.map.incomplete"), filterIncomplete);
        filterLine(context, x + 10, y + 56, Lang.string("worldbinder.map.entities"), filterEntities);
        filterLine(context, x + 10, y + 70, Lang.string("worldbinder.map.blockentities"), filterBlockEntities);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.clear_hint"), x + 10, y + 84, WbTheme.TEXT_DIM);
    }

    private void filterLine(GuiGraphicsExtractor context, int x, int y, String label, boolean active) {
        if (active) {
            context.fill(x - 4, y - 1, x + 118, y + 11, WbTheme.ACCENT_MUTED);
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal((active ? "§a" + Lang.string("worldbinder.common.on") + "  " : "§7" + Lang.string("worldbinder.common.off") + " ") + "§f" + label), x, y, active ? WbTheme.OK : WbTheme.TEXT_DIM);
    }

    private void drawLegend(GuiGraphicsExtractor context, int x, int y) {
        WbChrome.drawCard(context, font, x, y, 142, 100, Lang.text("worldbinder.map.legend"), WbTheme.ACCENT, false);
        legend(context, x + 10, y + 28, WbTheme.OK, Lang.string("worldbinder.map.status.done"));
        legend(context, x + 10, y + 42, WbTheme.WARN, Lang.string("worldbinder.map.status.partial"));
        legend(context, x + 10, y + 56, WbTheme.ACCENT_RIGHT, Lang.string("worldbinder.map.status.queued"));
        legend(context, x + 10, y + 70, WbTheme.INFO, Lang.string("worldbinder.map.status.scanning"));
        legend(context, x + 10, y + 84, WbTheme.ERROR, Lang.string("worldbinder.map.error_missing"));
    }

    private void legend(GuiGraphicsExtractor context, int x, int y, int color, String text) {
        context.fill(x, y + 2, x + 8, y + 10, color);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(text), x + 14, y, WbTheme.TEXT_SOFT);
    }

    private void drawCoveragePanel(GuiGraphicsExtractor context, int x, int y) {
        WbChrome.drawCard(context, font, x, y, 142, 132, Lang.text("worldbinder.map.coverage"), WbTheme.INFO, false);
        int total = Math.max(1, cachedSavedCount + cachedScanningCount + cachedQueuedCount);
        meter(context, x + 10, y + 30, 118, Lang.string("worldbinder.map.saved"), cachedSavedCount, total, WbTheme.OK);
        meter(context, x + 10, y + 52, 118, Lang.string("worldbinder.map.status.scanning"), cachedScanningCount, total, WbTheme.INFO);
        meter(context, x + 10, y + 74, 118, Lang.string("worldbinder.map.queued"), cachedQueuedCount, total, WbTheme.ACCENT_RIGHT);
        meter(context, x + 10, y + 96, 118, Lang.string("worldbinder.map.errors"), cachedErrorCount, Math.max(1, cachedSnapshots.size()), WbTheme.ERROR);
    }

    private void meter(GuiGraphicsExtractor context, int x, int y, int w, String label, int value, int total, int color) {
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(label + " §f" + value), x, y, WbTheme.TEXT_MUTED);
        WbChrome.drawProgressTrack(context, x, y + 11, w, 5, (int) (w * Math.min(1.0D, value / (double) total)), color);
    }

    private void drawInspectorPanel(GuiGraphicsExtractor context, int x, int y, long key, int cx, int cz) {
        int w = 184;
        int h = WorldBinder.config().queueDebugDiagnostics ? 294 : 240;
        WbChrome.drawCard(context, font, x, y, w, h, Lang.text("worldbinder.map.inspector"), WbTheme.ACCENT, false);
        if (key == Long.MIN_VALUE) {
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.inspector.empty"), x + 10, y + 34, WbTheme.TEXT_MUTED);
            return;
        }
        ChunkSnapshot snapshot = cachedInspectorSnapshot;
        String status = cachedInspectorStatus;
        int yy = y + 32;
        line(context, x, yy, Lang.string("worldbinder.map.chunk"), cx + " / " + cz); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.block_x"), chunkMinBlock(cx) + " .. " + chunkMaxBlock(cx)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.block_z"), chunkMinBlock(cz) + " .. " + chunkMaxBlock(cz)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.status"), status); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.blocks"), snapshot == null ? "0" : Lang.string("worldbinder.map.scanned_blocks", snapshot.scannedBlocks)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.entities"), snapshot == null ? "0" : Integer.toString(snapshot.entityCount)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.blockentities"), snapshot == null ? "0" : Integer.toString(snapshot.blockEntityCount)); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.biomes"), snapshot == null ? Lang.string("worldbinder.map.status.unknown") : (snapshot.hasBiomeData ? Lang.string("worldbinder.common.yes") : Lang.string("worldbinder.common.no"))); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.light"), snapshot == null ? Lang.string("worldbinder.map.status.unknown") : (snapshot.lightEstimated ? Lang.string("worldbinder.map.estimated") : Lang.string("worldbinder.common.saved"))); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.snapshot"), snapshot == null ? Lang.string("worldbinder.common.no") : (snapshot.hasSnapshot ? Lang.string("worldbinder.common.yes") : Lang.string("worldbinder.common.no"))); yy += 16;
        line(context, x, yy, Lang.string("worldbinder.map.last_scanned"), snapshot == null ? Lang.string("worldbinder.common.never") : snapshot.lastScannedText()); yy += 16;
        if (WorldBinder.config().queueDebugDiagnostics && snapshot != null) {
            line(context, x, yy, Lang.string("worldbinder.map.queued_by"), valueOrDash(snapshot.queueSource)); yy += 16;
            line(context, x, yy, Lang.string("worldbinder.map.reason"), valueOrDash(snapshot.queueReason)); yy += 16;
            line(context, x, yy, Lang.string("worldbinder.map.queue"), cachedQueueDiagnostics); yy += 16;
            line(context, x, yy, Lang.string("worldbinder.map.history"), valueOrDash(snapshot.stateHistory)); yy += 16;
        }
        yy += 2;
        double quality = cachedInspectorQuality;
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.map.quality", (int) (quality * 100.0D)), x + 10, yy, WbTheme.TEXT_MUTED);
        WbChrome.drawProgressTrack(context, x + 10, yy + 12, w - 20, 5, (int) ((w - 20) * quality), quality > 0.9D ? WbTheme.OK : quality > 0.55D ? WbTheme.WARN : WbTheme.ERROR);
    }

    private void line(GuiGraphicsExtractor context, int x, int y, String label, String value) {
        String text = label + ": §f" + value;
        if (text.length() > 42) {
            text = text.substring(0, 39) + "...";
        }
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal(text), x + 10, y, WbTheme.TEXT_MUTED);
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void drawChunk(GuiGraphicsExtractor context, ChunkSnapshot snapshot, int x, int y, int zoom, int fallbackColor, WorldBinderConfig.MapLayerMode mode, boolean reducedUiDetail) {
        int chunkPixels = Math.max(16, 16 * zoom);
        int base = mode == WorldBinderConfig.MapLayerMode.MAP_ONLY ? 0x55202A36 : fallbackColor;
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
            context.fill(x, y + chunkPixels - 2, x + quality, y + chunkPixels, snapshot.isDone() ? WbTheme.OK : WbTheme.WARN);
        }
    }

    private void drawChunkBorder(GuiGraphicsExtractor context, int x, int y, int size, ChunkCaptureStatus status, boolean player, boolean selected) {
        int color = player ? WbTheme.ACCENT : switch (status == null ? ChunkCaptureStatus.UNKNOWN : status) {
            case DONE -> WbTheme.OK;
            case PARTIAL, RECOVERY -> WbTheme.WARN;
            case QUEUED -> WbTheme.ACCENT_RIGHT;
            case SCANNING -> WbTheme.INFO;
            case FAILED -> WbTheme.ERROR;
            case UNKNOWN -> 0xFF555566;
        };
        if (selected) color = 0xFFFFFFFF;
        int t = size < 32 ? 1 : 2;
        context.fill(x, y, x + size, y + t, color);
        context.fill(x, y + size - t, x + size, y + size, color);
        context.fill(x, y, x + t, y + size, color);
        context.fill(x + size - t, y, x + size, y + size, color);
    }

    private void drawPlayerCross(GuiGraphicsExtractor context, int x, int y, int size) {
        int color = WbTheme.ACCENT;
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
            case FAILED -> 0x8846222C;
            case SCANNING -> 0x88304F68;
            case PARTIAL, RECOVERY -> 0x88705E24;
            case QUEUED -> 0x7751381A;
            case DONE -> 0x66315B48;
            case UNKNOWN -> 0x44202A3A;
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
            WorldBinderClient.capture().queueChunkForRescan(chunkXFromKey(selectedChunk), chunkZFromKey(selectedChunk));
        }
    }

    private void copySelectedChunkCenter() {
        int x = chunkMiddleBlock(chunkXFromKey(selectedChunk));
        int z = chunkMiddleBlock(chunkZFromKey(selectedChunk));
        int y = 80;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            y = mc.player.blockPosition().getY();
        }
        String coordinate = x + " " + y + " " + z;
        if (mc != null) {
            mc.keyboardHandler.setClipboard(coordinate);
        }
        Chat.info(Lang.string("worldbinder.chat.copied_chunk_center", coordinate));
    }

    private void clearFilters() {
        filterMissing = false;
        filterIncomplete = false;
        filterEntities = false;
        filterBlockEntities = false;
        updateFilterButtons();
    }
}

package net.worldbinder.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.render.MapData;
import net.worldbinder.render.MapExplorer;
import net.worldbinder.render.MapExporter;
import net.worldbinder.render.MapMarkers;
import net.worldbinder.render.MapRegionTextures;
import net.worldbinder.render.MapStatusOverlay;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbSounds;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.ui.component.WbTransition;
import net.worldbinder.util.Chat;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * WorldBinder live map (F10). Terrain is rendered from cached region textures with optional relief shading,
 * the capture status is a second texture layer, and all panels only refresh when the map data changes.
 * Explore mode adds loaded chunks without capturing, entities can be shown as markers, and the map can be
 * exported as a PNG or as region tiles.
 */
public final class WorldBinderMapScreen extends Screen {
    private static final double MIN_ZOOM = 0.5D;
    private static final double MAX_ZOOM = 16.0D;
    private static final int MARGIN = 12;
    private static final int TOOLBAR_Y = 10;
    private static final int TOOLBAR_H = 22;
    private static final int SIDEBAR_W = 212;
    private static final int DROPDOWN_ROW = 22;
    private static final MapStatusOverlay STATUS_OVERLAY = new MapStatusOverlay();

    private enum Dropdown { NONE, LAYERS, FILTERS, EXPORT }

    private enum StatusFilter {
        MISSING("worldbinder.map.filter.missing", WbIcon.SEARCH),
        DONE("worldbinder.map.filter.done", WbIcon.CHECK),
        PROGRESS("worldbinder.map.filter.progress", WbIcon.RECOVERY),
        FAILED("worldbinder.map.filter.failed", WbIcon.CLOSE);

        final String key;
        final WbIcon icon;

        StatusFilter(String key, WbIcon icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    private enum ContentFilter {
        ENTITIES("worldbinder.map.filter.entities", WbIcon.ENTITY),
        BLOCK_ENTITIES("worldbinder.map.filter.block_entities", WbIcon.CHEST),
        LOW_QUALITY("worldbinder.map.filter.low_quality", WbIcon.WARNING),
        RECENT("worldbinder.map.filter.recent", WbIcon.STAR),
        EXPLORED_ONLY("worldbinder.map.filter.explored_only", WbIcon.GLOBE);

        final String key;
        final WbIcon icon;

        ContentFilter(String key, WbIcon icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    private static final EnumSet<StatusFilter> STATUS_FILTERS = EnumSet.noneOf(StatusFilter.class);
    private static final EnumSet<ContentFilter> CONTENT_FILTERS = EnumSet.noneOf(ContentFilter.class);
    private static boolean filterInvert;
    private static boolean filterHide;
    private static boolean exportWithStatus;

    private final Screen parent;
    private double camX;
    private double camZ;
    private double targetCamX;
    private double targetCamZ;
    private double zoom;
    private double targetZoom;
    private boolean followPlayer = true;
    private boolean cameraInitialized;
    private boolean dragging;
    private double dragDistance;
    private long selectedChunk = Long.MIN_VALUE;
    private Dropdown dropdown = Dropdown.NONE;
    private int dropdownX;
    private EditBox goField;
    private long lastFrameNanos;
    private long footerMessageMillis;
    private Component footerMessage;
    private int footerAccent = WbTheme.OK;
    private boolean exporting;

    private long nextDataRefreshMillis;
    private long cachedRevision = Long.MIN_VALUE;
    private long cachedExplorerRevision = Long.MIN_VALUE;
    private Set<Long> cachedDone = Set.of();
    private Set<Long> cachedPartial = Set.of();
    private Set<Long> cachedQueued = Set.of();
    private Set<Long> cachedFailed = Set.of();
    private Map<Long, ChunkSnapshot> cachedSnapshots = Map.of();
    private LongFunction<ChunkSnapshot> lookup = key -> null;
    private int[] cachedBounds;
    private int statDone;
    private int statPartial;
    private int statQueued;
    private int statFailed;
    private int statKnown;
    private int statWithEntities;
    private int statWithBlockEntities;
    private long lastSyncSignature = Long.MIN_VALUE;
    private final Map<MapMarkers.Kind, Integer> nearbyEntities = new EnumMap<>(MapMarkers.Kind.class);

    private int hoverBlockX;
    private int hoverBlockZ;
    private boolean hoverValid;
    private Entity hoveredEntity;

    public WorldBinderMapScreen(Screen parent) {
        super(Component.translatable("worldbinder.map.title"));
        this.parent = parent;
        double stored = WorldBinder.config().mapZoom;
        this.zoom = this.targetZoom = clampZoom(Double.isFinite(stored) ? stored : 2.0D);
    }

    // ------------------------------------------------------------------ layout

    private boolean sidebarVisible() {
        return WorldBinder.config().mapShowSidebar && width >= 640;
    }

    private int mapX() {
        return MARGIN;
    }

    private int mapY() {
        return TOOLBAR_Y + TOOLBAR_H + 10;
    }

    private int mapW() {
        return Math.max(120, width - MARGIN * 2 - (sidebarVisible() ? SIDEBAR_W + 10 : 0));
    }

    private int mapH() {
        return Math.max(90, height - mapY() - 30);
    }

    private int sidebarX() {
        return width - MARGIN - SIDEBAR_W;
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

    private Button toolbarButton(int x, String key, String tooltipKey, WbIcon icon, boolean labeled, int labeledWidth, Button.OnPress action) {
        Button button = WbButton.create(x, TOOLBAR_Y, labeled ? labeledWidth : 24, TOOLBAR_H, Lang.text(key), Lang.text(tooltipKey), action);
        return labeled ? WbButton.icon(button, icon) : WbButton.iconOnly(button, icon);
    }

    private void initScaled() {
        String pendingGo = goField == null ? "" : goField.getValue();
        WorldBinderConfig config = WorldBinder.config();
        boolean wideLabels = width >= 880;
        boolean labels = width >= 700;
        int x = MARGIN;

        Button follow = toolbarButton(x, "worldbinder.map.follow", "worldbinder.map.tooltip.follow", WbIcon.FOLLOW, wideLabels, 72, b -> {
            followPlayer = !followPlayer;
            rebuild();
        });
        WbButton.tab(follow, WbTheme.ACCENT, followPlayer);
        WbTooltips.hint(follow, Component.literal("F"));
        addRenderableWidget(follow);
        x += follow.getWidth() + 4;

        Button center = toolbarButton(x, "worldbinder.map.player", "worldbinder.map.tooltip.player", WbIcon.TARGET, wideLabels, 72, b -> centerOnPlayer(true));
        WbTooltips.hint(center, Component.literal("Space"));
        addRenderableWidget(center);
        x += center.getWidth() + 4;

        Button origin = toolbarButton(x, "worldbinder.map.origin", "worldbinder.map.tooltip.origin", WbIcon.HOME, wideLabels, 66, b -> jumpToOrigin());
        addRenderableWidget(origin);
        x += origin.getWidth() + 10;

        Button zoomOut = WbButton.iconOnly(WbButton.create(x, TOOLBAR_Y, 22, TOOLBAR_H, Lang.text("worldbinder.map.zoom_out"),
                Lang.text("worldbinder.map.tooltip.zoom_out"), b -> zoomBy(-1, mapX() + mapW() / 2.0, mapY() + mapH() / 2.0)), WbIcon.MINUS);
        WbTooltips.hint(zoomOut, Component.literal("-"));
        addRenderableWidget(zoomOut);
        zoomLabelX = x + 22;
        x += 22 + 44;
        Button zoomIn = WbButton.iconOnly(WbButton.create(x, TOOLBAR_Y, 22, TOOLBAR_H, Lang.text("worldbinder.map.zoom_in"),
                Lang.text("worldbinder.map.tooltip.zoom_in"), b -> zoomBy(1, mapX() + mapW() / 2.0, mapY() + mapH() / 2.0)), WbIcon.PLUS);
        WbTooltips.hint(zoomIn, Component.literal("+"));
        addRenderableWidget(zoomIn);
        x += 22 + 10;

        Button layers = toolbarButton(x, "worldbinder.map.layers", "worldbinder.map.tooltip.layers", WbIcon.LAYERS, labels, 84, b -> toggleDropdown(Dropdown.LAYERS));
        WbButton.dropdown(layers, WbTheme.INFO, dropdown == Dropdown.LAYERS);
        addRenderableWidget(layers);
        int layersX = x;
        x += layers.getWidth() + 4;

        Button filters = toolbarButton(x, "worldbinder.map.filters", "worldbinder.map.tooltip.filters", WbIcon.FILTER, labels, 84, b -> toggleDropdown(Dropdown.FILTERS));
        WbButton.dropdown(filters, activeFilterCount() > 0 ? WbTheme.OK : WbTheme.INFO, dropdown == Dropdown.FILTERS);
        if (activeFilterCount() > 0 && labels) {
            WbButton.badge(filters, Integer.toString(activeFilterCount()));
        }
        addRenderableWidget(filters);
        int filtersX = x;
        x += filters.getWidth() + 4;

        Button explore = toolbarButton(x, "worldbinder.map.explore", "worldbinder.map.tooltip.explore", WbIcon.GLOBE, labels, 84, b -> {
            WorldBinder.config().mapExploreMode = !WorldBinder.config().mapExploreMode;
            WorldBinder.config().save();
            footer(Component.translatable(WorldBinder.config().mapExploreMode ? "worldbinder.map.explore_on" : "worldbinder.map.explore_off"), WbTheme.INFO);
            rebuild();
        });
        WbButton.tab(explore, WbTheme.OK, config.mapExploreMode);
        WbTooltips.hint(explore, Component.literal("E"));
        addRenderableWidget(explore);
        x += explore.getWidth() + 4;

        Button export = toolbarButton(x, "worldbinder.map.export", "worldbinder.map.tooltip.export", WbIcon.DOWNLOAD, labels, 84, b -> toggleDropdown(Dropdown.EXPORT));
        WbButton.dropdown(export, WbTheme.ACCENT_RIGHT, dropdown == Dropdown.EXPORT);
        export.active = !exporting;
        addRenderableWidget(export);
        int exportX = x;
        x += export.getWidth() + 10;

        int right = width - MARGIN;
        Button close = WbButton.icon(WbButton.quiet(WbButton.create(right - 68, TOOLBAR_Y, 68, TOOLBAR_H, Lang.text("worldbinder.gui.back"),
                Lang.text("worldbinder.tooltip.config.back"), b -> onClose())), WbIcon.BACK);
        WbTooltips.hint(close, Component.literal("Esc"));
        addRenderableWidget(close);
        right -= 72;
        if (width >= 640) {
            Button sidebar = WbButton.iconOnly(WbButton.create(right - 22, TOOLBAR_Y, 22, TOOLBAR_H, Lang.text("worldbinder.map.sidebar"),
                    Lang.text("worldbinder.map.tooltip.sidebar"), b -> {
                        WorldBinder.config().mapShowSidebar = !WorldBinder.config().mapShowSidebar;
                        rebuild();
                    }), WbIcon.EYE);
            WbButton.tab(sidebar, WbTheme.ACCENT, config.mapShowSidebar);
            WbTooltips.hint(sidebar, Component.literal("H"));
            addRenderableWidget(sidebar);
            right -= 26;
        }
        int goW = 30;
        int fieldW = Math.min(110, right - goW - 4 - x);
        if (fieldW >= 64) {
            goField = new WbEditBox(font, right - goW - 4 - fieldW, TOOLBAR_Y, fieldW, TOOLBAR_H);
            goField.setBordered(false);
            goField.setMaxLength(32);
            goField.setHint(Lang.text("worldbinder.map.go_hint"));
            goField.setValue(pendingGo);
            goField.setTextColor(WbTheme.TEXT_SOFT);
            WbTooltips.register(goField, Lang.text("worldbinder.map.tooltip.go_field"));
            addRenderableWidget(goField);
            addRenderableWidget(WbButton.iconOnly(WbButton.primary(WbButton.create(right - goW, TOOLBAR_Y, goW, TOOLBAR_H, Lang.text("worldbinder.map.go"),
                    Lang.text("worldbinder.map.tooltip.go"), b -> jumpToField())), WbIcon.CHEVRON_RIGHT));
        } else {
            goField = null;
        }

        if (dropdown == Dropdown.LAYERS) {
            addLayerDropdown(layersX);
        } else if (dropdown == Dropdown.FILTERS) {
            addFilterDropdown(filtersX);
        } else if (dropdown == Dropdown.EXPORT) {
            addExportDropdown(exportX);
        }

        if (sidebarVisible()) {
            addSidebarActions();
        }
    }

    private int zoomLabelX;

    private int[] dropdownBounds() {
        int y = TOOLBAR_Y + TOOLBAR_H + 4;
        return switch (dropdown) {
            case LAYERS, FILTERS -> new int[] {dropdownX, y, 380, 10 + 14 + 6 * DROPDOWN_ROW + 2};
            case EXPORT -> new int[] {dropdownX, y, 230, 10 + 4 * DROPDOWN_ROW};
            case NONE -> new int[] {0, 0, 0, 0};
        };
    }

    private void addLayerDropdown(int x) {
        dropdownX = Math.max(MARGIN, Math.min(x, width - MARGIN - 380));
        WorldBinderConfig config = WorldBinder.config();
        int top = TOOLBAR_Y + TOOLBAR_H + 4 + 5 + 14;
        int colW = 180;
        int left = dropdownX + 6;
        int rightCol = dropdownX + 6 + colW + 8;
        WorldBinderConfig.MapLayerMode[] modes = WorldBinderConfig.MapLayerMode.values();
        WbIcon[] modeIcons = {WbIcon.LAYERS, WbIcon.GRID, WbIcon.MAP};
        for (int i = 0; i < modes.length; i++) {
            WorldBinderConfig.MapLayerMode mode = modes[i];
            Button button = WbButton.create(left, top + i * DROPDOWN_ROW, colW, 20, Component.literal(modeLabel(mode)),
                    Lang.text("worldbinder.map.tooltip.view"), b -> {
                        WorldBinder.config().f10MapLayerMode = mode;
                        rebuild();
                    });
            addRenderableWidget(WbButton.icon(WbButton.tab(button, WbTheme.INFO, effectiveMode() == mode), modeIcons[i]));
        }
        int row = modes.length;
        addRenderableWidget(toggleRow(left, top + row++ * DROPDOWN_ROW, colW, "worldbinder.config.map.hillshade", "worldbinder.tooltip.config.map.hillshade",
                config.mapHillshade, v -> config.mapHillshade = v));
        addRenderableWidget(toggleRow(left, top + row++ * DROPDOWN_ROW, colW, "worldbinder.config.map.grid", "worldbinder.tooltip.config.map.grid",
                config.mapShowGrid, v -> config.mapShowGrid = v));
        addRenderableWidget(toggleRow(left, top + row * DROPDOWN_ROW, colW, "worldbinder.config.map.status", "worldbinder.tooltip.config.map.status",
                config.mapShowStatusOverlay, v -> config.mapShowStatusOverlay = v));

        int r = 0;
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.PLAYER, "worldbinder.config.map.players", config.mapShowPlayers, v -> config.mapShowPlayers = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.HOSTILE, "worldbinder.map.layer.hostile", config.mapShowHostile, v -> config.mapShowHostile = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.PASSIVE, "worldbinder.map.layer.passive", config.mapShowPassive, v -> config.mapShowPassive = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.ITEM, "worldbinder.map.layer.items", config.mapShowItems, v -> config.mapShowItems = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.OTHER, "worldbinder.map.layer.other", config.mapShowOtherEntities, v -> config.mapShowOtherEntities = v));
        addRenderableWidget(toggleRow(rightCol, top + r * DROPDOWN_ROW, colW, "worldbinder.map.layer.limit", "worldbinder.map.tooltip.limit",
                config.mapLimitPanning, v -> config.mapLimitPanning = v));
    }

    private void addFilterDropdown(int x) {
        dropdownX = Math.max(MARGIN, Math.min(x, width - MARGIN - 380));
        int top = TOOLBAR_Y + TOOLBAR_H + 4 + 5 + 14;
        int colW = 180;
        int left = dropdownX + 6;
        int rightCol = dropdownX + 6 + colW + 8;
        int row = 0;
        for (StatusFilter filter : StatusFilter.values()) {
            Button button = filterRow(left, top + row++ * DROPDOWN_ROW, colW, filter.key, filter.key + ".tooltip", filter.icon,
                    STATUS_FILTERS.contains(filter), v -> {
                        if (v) STATUS_FILTERS.add(filter); else STATUS_FILTERS.remove(filter);
                    });
            String count = statusCount(filter);
            if (count != null) WbButton.badge(button, count);
            addRenderableWidget(button);
        }
        row = 0;
        for (ContentFilter filter : ContentFilter.values()) {
            Button button = filterRow(rightCol, top + row++ * DROPDOWN_ROW, colW, filter.key, filter.key + ".tooltip", filter.icon,
                    CONTENT_FILTERS.contains(filter), v -> {
                        if (v) CONTENT_FILTERS.add(filter); else CONTENT_FILTERS.remove(filter);
                    });
            String count = contentCount(filter);
            if (count != null) WbButton.badge(button, count);
            addRenderableWidget(button);
        }
        int actionY = top + 5 * DROPDOWN_ROW + 2;
        int third = (380 - 12 - 8) / 3;
        addRenderableWidget(toggleRow(left, actionY, third, "worldbinder.map.filter.invert", "worldbinder.map.filter.invert.tooltip", filterInvert, v -> filterInvert = v));
        addRenderableWidget(toggleRow(left + third + 4, actionY, third, "worldbinder.map.filter.hide", "worldbinder.map.filter.hide.tooltip", filterHide, v -> filterHide = v));
        addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(left + (third + 4) * 2, actionY, third, 20, Lang.text("worldbinder.map.clear_filters"),
                Lang.text("worldbinder.map.tooltip.clear_filters"), b -> clearFilters())), WbIcon.RESET));
    }

    private void addExportDropdown(int x) {
        dropdownX = Math.max(MARGIN, Math.min(x, width - MARGIN - 230));
        int top = TOOLBAR_Y + TOOLBAR_H + 4 + 5;
        int w = 218;
        int left = dropdownX + 6;
        addRenderableWidget(WbButton.icon(WbButton.create(left, top, w, 20, Lang.text("worldbinder.map.export.full"),
                Lang.text("worldbinder.map.export.full.tooltip"), b -> startExport(MapExporter.Mode.FULL)), WbIcon.MAP));
        addRenderableWidget(WbButton.icon(WbButton.create(left, top + DROPDOWN_ROW, w, 20, Lang.text("worldbinder.map.export.tiles"),
                Lang.text("worldbinder.map.export.tiles.tooltip"), b -> startExport(MapExporter.Mode.TILES)), WbIcon.GRID));
        addRenderableWidget(toggleRow(left, top + DROPDOWN_ROW * 2, w, "worldbinder.map.export.status", "worldbinder.map.export.status.tooltip",
                exportWithStatus, v -> exportWithStatus = v));
        addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(left, top + DROPDOWN_ROW * 3, w, 20, Lang.text("worldbinder.map.export.folder"),
                Lang.text("worldbinder.map.export.folder.tooltip"), b -> {
                    java.nio.file.Path folder = WorldBinderPaths.RECOVERY_ROOT.resolve("map_exports");
                    try {
                        java.nio.file.Files.createDirectories(folder);
                    } catch (java.io.IOException ignored) {
                        // PathOpener reports missing folders itself.
                    }
                    net.worldbinder.util.PathOpener.open(folder);
                })), WbIcon.FOLDER));
    }

    private Button toggleRow(int x, int y, int w, String key, String tooltipKey, boolean value, java.util.function.Consumer<Boolean> setter) {
        Button button = WbButton.create(x, y, w, 20, Lang.text(key), Lang.text(tooltipKey), b -> {
            boolean next = !WbButton.isSelected(b);
            setter.accept(next);
            WbButton.selected(b, next);
            lastSyncSignature = Long.MIN_VALUE;
        });
        return WbButton.toggle(button, WbTheme.ACCENT, value);
    }

    private Button markerRow(int x, int y, int w, MapMarkers.Kind kind, String key, boolean value, java.util.function.Consumer<Boolean> setter) {
        Button button = toggleRow(x, y, w, key, "worldbinder.map.tooltip.marker", value, setter);
        WbButton.style(button, WbButton.Style.TOGGLE, kind.color, value);
        Integer count = nearbyEntities.get(kind);
        if (count != null && count > 0) {
            WbButton.badge(button, Integer.toString(count));
        }
        return button;
    }

    private Button filterRow(int x, int y, int w, String key, String tooltipKey, WbIcon icon, boolean value, java.util.function.Consumer<Boolean> setter) {
        Button button = WbButton.create(x, y, w, 20, Lang.text(key), Lang.text(tooltipKey), b -> {
            boolean next = !WbButton.isSelected(b);
            setter.accept(next);
            WbButton.selected(b, next);
        });
        return WbButton.icon(WbButton.tab(button, WbTheme.OK, value), icon);
    }

    private int selectedCardY() {
        return mapY() + 230;
    }

    private void addSidebarActions() {
        if (selectedChunk == Long.MIN_VALUE) {
            return;
        }
        int x = sidebarX() + 10;
        int w = SIDEBAR_W - 20;
        int y = selectedCardY() + 150;
        if (y + 22 > mapY() + mapH()) {
            return;
        }
        int bw = (w - 8) / 3;
        addRenderableWidget(WbButton.icon(WbButton.create(x, y, bw, 20, Lang.text("worldbinder.map.action.rescan"),
                Lang.text("worldbinder.map.tooltip.rescan"), b -> queueSelectedRescan()), WbIcon.RECOVERY));
        addRenderableWidget(WbButton.icon(WbButton.create(x + bw + 4, y, bw, 20, Lang.text("worldbinder.map.action.copy"),
                Lang.text("worldbinder.map.tooltip.copy"), b -> copySelectedChunkCenter()), WbIcon.COPY));
        addRenderableWidget(WbButton.icon(WbButton.create(x + (bw + 4) * 2, y, bw, 20, Lang.text("worldbinder.map.action.center"),
                Lang.text("worldbinder.map.tooltip.center"), b -> centerOnChunk(selectedChunk)), WbIcon.TARGET));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void toggleDropdown(Dropdown target) {
        dropdown = dropdown == target ? Dropdown.NONE : target;
        rebuild();
    }

    // ------------------------------------------------------------------ camera

    private static double clampZoom(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    private void zoomBy(int direction, double anchorX, double anchorY) {
        double next = clampZoom(targetZoom * (direction > 0 ? 1.35D : 1.0D / 1.35D));
        if (next == targetZoom) {
            return;
        }
        double centerX = mapX() + mapW() / 2.0;
        double centerY = mapY() + mapH() / 2.0;
        double anchorBlockX = targetCamX + (anchorX - centerX) / targetZoom;
        double anchorBlockZ = targetCamZ + (anchorY - centerY) / targetZoom;
        targetZoom = next;
        if (!followPlayer) {
            targetCamX = anchorBlockX - (anchorX - centerX) / next;
            targetCamZ = anchorBlockZ - (anchorY - centerY) / next;
        }
        WbSounds.sliderTick();
    }

    private void updateCamera() {
        long now = System.nanoTime();
        long elapsed = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
        lastFrameNanos = now;
        Minecraft mc = Minecraft.getInstance();
        if (!cameraInitialized) {
            cameraInitialized = true;
            if (mc.player != null) {
                camX = targetCamX = mc.player.getX();
                camZ = targetCamZ = mc.player.getZ();
            } else if (cachedBounds != null) {
                camX = targetCamX = (cachedBounds[0] + cachedBounds[2] + 1) * 8.0;
                camZ = targetCamZ = (cachedBounds[1] + cachedBounds[3] + 1) * 8.0;
            }
        }
        if (followPlayer && mc.player != null) {
            targetCamX = mc.player.getX();
            targetCamZ = mc.player.getZ();
        } else {
            clampTarget();
        }
        boolean smooth = WorldBinder.config().mapSmoothCamera && !WbMotion.reduced();
        if (!smooth || dragging) {
            camX = targetCamX;
            camZ = targetCamZ;
            zoom = targetZoom;
            if (!smooth) return;
        }
        float seconds = Math.min(0.05F, elapsed / 1_000_000_000.0F);
        double blend = 1.0D - Math.exp(-14.0D * seconds);
        camX += (targetCamX - camX) * blend;
        camZ += (targetCamZ - camZ) * blend;
        double zoomBlend = 1.0D - Math.exp(-16.0D * seconds);
        zoom = Math.exp(Math.log(zoom) + (Math.log(targetZoom) - Math.log(zoom)) * zoomBlend);
        if (Math.abs(zoom - targetZoom) < 0.0005D) zoom = targetZoom;
    }

    /** Keeps the camera center inside the known map area (plus a margin) so you cannot pan into the void. */
    private void clampTarget() {
        if (!WorldBinder.config().mapLimitPanning) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double minX;
        double minZ;
        double maxX;
        double maxZ;
        if (cachedBounds != null) {
            minX = cachedBounds[0] * 16.0;
            minZ = cachedBounds[1] * 16.0;
            maxX = (cachedBounds[2] + 1) * 16.0;
            maxZ = (cachedBounds[3] + 1) * 16.0;
        } else if (mc.player != null) {
            minX = maxX = mc.player.getX();
            minZ = maxZ = mc.player.getZ();
        } else {
            return;
        }
        if (mc.player != null) {
            minX = Math.min(minX, mc.player.getX());
            maxX = Math.max(maxX, mc.player.getX());
            minZ = Math.min(minZ, mc.player.getZ());
            maxZ = Math.max(maxZ, mc.player.getZ());
        }
        double margin = 48.0 + Math.min(mapW(), mapH()) / 4.0 / Math.max(MIN_ZOOM, targetZoom);
        targetCamX = Math.max(minX - margin, Math.min(maxX + margin, targetCamX));
        targetCamZ = Math.max(minZ - margin, Math.min(maxZ + margin, targetCamZ));
    }

    private void centerOnPlayer(boolean enableFollow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        targetCamX = mc.player.getX();
        targetCamZ = mc.player.getZ();
        if (enableFollow && !followPlayer) {
            followPlayer = true;
            rebuild();
        }
    }

    private void centerOnChunk(long key) {
        if (key == Long.MIN_VALUE) {
            return;
        }
        setFollow(false);
        targetCamX = chunkX(key) * 16 + 8;
        targetCamZ = chunkZ(key) * 16 + 8;
    }

    private void setFollow(boolean value) {
        if (followPlayer != value) {
            followPlayer = value;
            rebuild();
        }
    }

    private double blockAtScreenX(double screenX) {
        return camX + (screenX - (mapX() + mapW() / 2.0)) / zoom;
    }

    private double blockAtScreenY(double screenY) {
        return camZ + (screenY - (mapY() + mapH() / 2.0)) / zoom;
    }

    private double screenXOfBlock(double blockX) {
        return mapX() + mapW() / 2.0 + (blockX - camX) * zoom;
    }

    private double screenYOfBlock(double blockZ) {
        return mapY() + mapH() / 2.0 + (blockZ - camZ) * zoom;
    }

    private boolean insideMap(double x, double y) {
        return x >= mapX() && y >= mapY() && x < mapX() + mapW() && y < mapY() + mapH();
    }

    // ------------------------------------------------------------------ data

    private void refreshData(SceneCaptureService capture) {
        long now = System.currentTimeMillis();
        long revision = capture.mapDataRevision();
        long explorerRevision = MapExplorer.get().revision();
        if (revision == cachedRevision && explorerRevision == cachedExplorerRevision && now < nextDataRefreshMillis) {
            return;
        }
        nextDataRefreshMillis = now + 250L;
        boolean dataChanged = revision != cachedRevision || explorerRevision != cachedExplorerRevision;
        cachedRevision = revision;
        cachedExplorerRevision = explorerRevision;
        cachedDone = capture.downloadedChunksView();
        cachedPartial = capture.partialChunksView();
        cachedQueued = capture.queuedChunksView();
        cachedFailed = capture.failedChunksView();
        cachedSnapshots = capture.chunkSnapshotsView();
        lookup = MapData.lookup(cachedSnapshots);
        statDone = cachedDone.size();
        statPartial = cachedPartial.size();
        statQueued = cachedQueued.size();
        statFailed = cachedFailed.size();
        statKnown = Math.max(cachedSnapshots.size(), statDone + statPartial);
        if (dataChanged) {
            cachedBounds = MapData.bounds(cachedSnapshots);
            int withEntities = 0;
            int withBlockEntities = 0;
            for (ChunkSnapshot snapshot : cachedSnapshots.values()) {
                if (snapshot.entityCount > 0) withEntities++;
                if (snapshot.blockEntityCount > 0) withBlockEntities++;
            }
            statWithEntities = withEntities;
            statWithBlockEntities = withBlockEntities;
        }
        lastSyncSignature = Long.MIN_VALUE;
    }

    private ChunkCaptureStatus statusOf(long key) {
        ChunkSnapshot snapshot = cachedSnapshots.get(key);
        if (cachedFailed.contains(key) || (snapshot != null && snapshot.exportError)) return ChunkCaptureStatus.FAILED;
        if (cachedPartial.contains(key)) {
            return snapshot != null && snapshot.effectiveStatus() == ChunkCaptureStatus.PARTIAL ? ChunkCaptureStatus.PARTIAL : ChunkCaptureStatus.SCANNING;
        }
        if (cachedQueued.contains(key)) return ChunkCaptureStatus.QUEUED;
        if (cachedDone.contains(key)) return ChunkCaptureStatus.DONE;
        return snapshot == null ? ChunkCaptureStatus.UNKNOWN : snapshot.effectiveStatus();
    }

    private boolean explored(long key) {
        return !cachedSnapshots.containsKey(key) && MapExplorer.get().snapshot(key) != null;
    }

    private boolean passesFilter(long key, ChunkCaptureStatus status) {
        ChunkSnapshot snapshot = cachedSnapshots.get(key);
        boolean ok = true;
        if (!STATUS_FILTERS.isEmpty()) {
            boolean any = false;
            for (StatusFilter filter : STATUS_FILTERS) {
                any |= switch (filter) {
                    case MISSING -> status == ChunkCaptureStatus.UNKNOWN;
                    case DONE -> status == ChunkCaptureStatus.DONE;
                    case PROGRESS -> status == ChunkCaptureStatus.QUEUED || status == ChunkCaptureStatus.SCANNING
                            || status == ChunkCaptureStatus.PARTIAL || status == ChunkCaptureStatus.RECOVERY;
                    case FAILED -> status == ChunkCaptureStatus.FAILED;
                };
            }
            ok = any;
        }
        for (ContentFilter filter : CONTENT_FILTERS) {
            ok &= switch (filter) {
                case ENTITIES -> snapshot != null && snapshot.entityCount > 0;
                case BLOCK_ENTITIES -> snapshot != null && snapshot.blockEntityCount > 0;
                case LOW_QUALITY -> snapshot != null && snapshot.qualityScore(WorldBinder.config().effectiveCaptureHeight()) < 0.55D;
                case RECENT -> snapshot != null && System.currentTimeMillis() - snapshot.lastScannedAtMillis < 120_000L;
                case EXPLORED_ONLY -> explored(key);
            };
        }
        return filterInvert != ok;
    }

    private int activeFilterCount() {
        return STATUS_FILTERS.size() + CONTENT_FILTERS.size();
    }

    private String statusCount(StatusFilter filter) {
        return switch (filter) {
            case MISSING -> null;
            case DONE -> Integer.toString(statDone);
            case PROGRESS -> Integer.toString(statPartial + statQueued);
            case FAILED -> Integer.toString(statFailed);
        };
    }

    private String contentCount(ContentFilter filter) {
        return switch (filter) {
            case ENTITIES -> Integer.toString(statWithEntities);
            case BLOCK_ENTITIES -> Integer.toString(statWithBlockEntities);
            case EXPLORED_ONLY -> MapExplorer.get().size() > 0 ? Integer.toString(MapExplorer.get().size()) : null;
            default -> null;
        };
    }

    private WorldBinderConfig.MapLayerMode effectiveMode() {
        WorldBinderConfig.MapLayerMode mode = WorldBinder.config().f10MapLayerMode;
        return mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode;
    }

    private int statusOverlayColor(long key) {
        ChunkCaptureStatus status = statusOf(key);
        boolean chunksOnly = effectiveMode() == WorldBinderConfig.MapLayerMode.CHUNKS_ONLY;
        if (activeFilterCount() > 0 || filterInvert) {
            boolean pass = passesFilter(key, status);
            if (!pass) {
                return filterHide ? 0xFF0B1016 : 0xC0070A0E;
            }
            if (lookup.apply(key) == null) {
                return 0x50FFFFFF;
            }
        }
        if (effectiveMode() == WorldBinderConfig.MapLayerMode.MAP_ONLY || !WorldBinder.config().mapShowStatusOverlay && !chunksOnly) {
            return 0;
        }
        return statusTint(status, chunksOnly);
    }

    private static int statusTint(ChunkCaptureStatus status, boolean strong) {
        int alpha = strong ? 0xD0 : 0x58;
        return switch (status) {
            case DONE -> strong ? WbTheme.withAlpha(0xFF3F8F67, alpha) : 0;
            case PARTIAL, RECOVERY -> WbTheme.withAlpha(WbTheme.WARN, alpha);
            case QUEUED -> WbTheme.withAlpha(WbTheme.ACCENT_RIGHT, alpha);
            case SCANNING -> WbTheme.withAlpha(WbTheme.INFO, strong ? alpha : 0x70);
            case FAILED -> WbTheme.withAlpha(WbTheme.ERROR, strong ? alpha : 0x88);
            case UNKNOWN -> 0;
        };
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        boolean transition = WbTransition.begin(context, this, realWidth, realHeight);
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int vmx = uiScale.toVirtualX(mouseX);
        int vmy = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, WbTheme.BACKDROP);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        List<String> hoverLines = null;
        String hoverTitle = null;
        int hoverAccent = WbTheme.ACCENT;
        try {
            WbChrome.drawBackdrop(context, width, height);
            SceneCaptureService capture = WorldBinderClient.capture();
            refreshData(capture);
            updateCamera();

            drawToolbarChrome(context);
            drawMap(context, capture, vmx, vmy);
            if (sidebarVisible()) {
                drawSidebar(context, capture);
            }
            drawFooter(context);

            if (dropdown != Dropdown.NONE) {
                context.nextStratum();
                drawDropdownChrome(context);
            }
            super.extractRenderState(context, vmx, vmy, delta);

            if (!dragging && dropdown == Dropdown.NONE && insideMap(vmx, vmy)) {
                if (hoveredEntity != null) {
                    MapMarkers.Kind kind = MapMarkers.kind(hoveredEntity);
                    hoverTitle = hoveredEntity.getName().getString();
                    hoverLines = entityDetails(hoveredEntity, kind);
                    hoverAccent = kind.color;
                } else if (hoverValid) {
                    long key = ChunkPos.pack(Math.floorDiv(hoverBlockX, 16), Math.floorDiv(hoverBlockZ, 16));
                    ChunkCaptureStatus status = statusOf(key);
                    hoverTitle = Lang.string("worldbinder.map.tooltip.chunk_line", chunkX(key), chunkZ(key));
                    hoverLines = hoverDetails(key, status);
                    hoverAccent = statusColor(status);
                }
            }
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        if (hoverLines != null) {
            WbTooltips.drawCard(context, font, hoverTitle, hoverLines, hoverAccent, mouseX, mouseY, realWidth, realHeight);
        } else {
            WbTooltips.showHovered(this, context, font, vmx, vmy, mouseX, mouseY);
        }
        WbTransition.end(context, transition);
        WbTransition.veil(context, this, realWidth, realHeight);
    }

    private void drawDropdownChrome(GuiGraphicsExtractor context) {
        int[] b = dropdownBounds();
        int accent = dropdown == Dropdown.EXPORT ? WbTheme.ACCENT_RIGHT : dropdown == Dropdown.FILTERS ? WbTheme.OK : WbTheme.INFO;
        WbChrome.softShadow(context, b[0], b[1], b[2], b[3], 0xB0);
        WbChrome.rounded(context, b[0], b[1], b[2], b[3], WbMotion.mix(WbTheme.panelBorder(), accent, 0.4F));
        WbChrome.roundedGradient(context, b[0] + 1, b[1] + 1, b[2] - 2, b[3] - 2, WbTheme.PANEL_TOP, WbTheme.PANEL_INNER);
        context.fill(b[0] + 2, b[1] + 1, b[0] + b[2] - 2, b[1] + 2, accent);
        if (dropdown == Dropdown.LAYERS || dropdown == Dropdown.FILTERS) {
            String left = Lang.string(dropdown == Dropdown.LAYERS ? "worldbinder.map.layer.header_map" : "worldbinder.map.filter.header_status");
            String right = Lang.string(dropdown == Dropdown.LAYERS ? "worldbinder.map.layer.header_markers" : "worldbinder.map.filter.header_content");
            context.text(font, Component.literal(left).withStyle(ChatFormatting.BOLD), b[0] + 10, b[1] + 7, WbTheme.TEXT_MUTED, false);
            context.text(font, Component.literal(right).withStyle(ChatFormatting.BOLD), b[0] + 10 + 188, b[1] + 7, WbTheme.TEXT_MUTED, false);
            context.fill(b[0] + 190, b[1] + 8, b[0] + 191, b[1] + b[3] - (dropdown == Dropdown.FILTERS ? 30 : 6), WbTheme.divider());
            if (dropdown == Dropdown.FILTERS) {
                context.fill(b[0] + 8, b[1] + b[3] - 28, b[0] + b[2] - 8, b[1] + b[3] - 27, WbTheme.divider());
            }
        }
    }

    private void drawToolbarChrome(GuiGraphicsExtractor context) {
        String zoomText = zoomLabel();
        WbChrome.rounded(context, zoomLabelX + 2, TOOLBAR_Y + 3, 40, TOOLBAR_H - 6, 0x40000000);
        context.text(font, zoomText, zoomLabelX + 22 - font.width(zoomText) / 2, TOOLBAR_Y + 7, WbTheme.TEXT_SOFT, false);
        if (goField != null) {
            WbEditBox field = (WbEditBox) goField;
            WbChrome.drawField(context, field.chromeX(), field.chromeY(), field.chromeWidth(), field.chromeHeight(), WbTheme.ACCENT,
                    goField.isFocused(), false);
        }
    }

    private String zoomLabel() {
        return zoom >= 1.0D ? String.format(Locale.ROOT, "%.1fx", zoom) : String.format(Locale.ROOT, "1:%d", Math.round(1.0D / zoom));
    }

    private void drawMap(GuiGraphicsExtractor context, SceneCaptureService capture, int mouseX, int mouseY) {
        WorldBinderConfig config = WorldBinder.config();
        int mx = mapX();
        int my = mapY();
        int mw = mapW();
        int mh = mapH();
        WbChrome.softShadow(context, mx, my, mw, mh, 0x80);
        WbChrome.rounded(context, mx - 1, my - 1, mw + 2, mh + 2, WbMotion.mix(WbTheme.panelBorder(), WbTheme.ACCENT, 0.25F));
        context.fill(mx, my, mx + mw, my + mh, 0xFF0B1016);

        double originX = mx + mw / 2.0 - camX * zoom;
        double originY = my + mh / 2.0 - camZ * zoom;
        int minChunkX = (int) Math.floor(blockAtScreenX(mx) / 16.0) - 1;
        int maxChunkX = (int) Math.floor(blockAtScreenX(mx + mw) / 16.0) + 1;
        int minChunkZ = (int) Math.floor(blockAtScreenY(my) / 16.0) - 1;
        int maxChunkZ = (int) Math.floor(blockAtScreenY(my + mh) / 16.0) + 1;
        int minRegionX = Math.floorDiv(minChunkX, MapRegionTextures.REGION_CHUNKS);
        int maxRegionX = Math.floorDiv(maxChunkX, MapRegionTextures.REGION_CHUNKS);
        int minRegionZ = Math.floorDiv(minChunkZ, MapRegionTextures.REGION_CHUNKS);
        int maxRegionZ = Math.floorDiv(maxChunkZ, MapRegionTextures.REGION_CHUNKS);

        MapRegionTextures textures = MapRegionTextures.shared();
        boolean terrain = effectiveMode() != WorldBinderConfig.MapLayerMode.CHUNKS_ONLY;
        long syncSignature = cachedRevision * 31L + cachedExplorerRevision * 17L + minChunkX * 7919L + minChunkZ * 104729L
                + maxChunkX * 31L + maxChunkZ + (config.mapHillshade ? 1 : 0);
        if (terrain && syncSignature != lastSyncSignature) {
            lastSyncSignature = syncSignature;
            textures.sync(lookup, minChunkX, minChunkZ, maxChunkX, maxChunkZ, config.mapHillshade);
        }
        if (terrain) {
            textures.paint(lookup, config.effectiveMaxUiWorkMs() * 1_000_000L);
        }
        long filterSignature = STATUS_FILTERS.hashCode() * 31L + CONTENT_FILTERS.hashCode() * 17L + (filterInvert ? 3 : 0) + (filterHide ? 5 : 0);
        long statusSignature = cachedRevision * 131L + filterSignature * 7L + effectiveMode().ordinal() * 16L
                + (config.mapShowStatusOverlay ? 64 : 0) + WbTheme.ACCENT
                + (CONTENT_FILTERS.contains(ContentFilter.EXPLORED_ONLY) || !STATUS_FILTERS.isEmpty() ? cachedExplorerRevision * 1009L : 0L)
                + (CONTENT_FILTERS.contains(ContentFilter.RECENT) ? System.currentTimeMillis() / 5000L : 0L);
        STATUS_OVERLAY.update(minRegionX, minRegionZ, maxRegionX, maxRegionZ, statusSignature, this::statusOverlayColor);

        context.enableScissor(mx, my, mx + mw, my + mh);
        try {
            drawBackgroundGrid(context, originX, originY, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
            if (terrain) {
                textures.drawTerrain(context, originX, originY, zoom, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
            }
            STATUS_OVERLAY.draw(context, originX, originY, zoom, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
            if (config.mapShowGrid) {
                drawGrid(context, minChunkX, minChunkZ, maxChunkX, maxChunkZ, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
            }
            drawCaptureRadius(context, capture);

            hoverValid = insideMap(mouseX, mouseY);
            if (hoverValid) {
                hoverBlockX = (int) Math.floor(blockAtScreenX(mouseX));
                hoverBlockZ = (int) Math.floor(blockAtScreenY(mouseY));
                if (!dragging) {
                    drawChunkOutline(context, Math.floorDiv(hoverBlockX, 16), Math.floorDiv(hoverBlockZ, 16), 0x90FFFFFF, 1);
                }
            }
            if (selectedChunk != Long.MIN_VALUE) {
                float pulse = WbMotion.pulse(1400L);
                drawChunkOutline(context, chunkX(selectedChunk), chunkZ(selectedChunk), WbMotion.mix(WbTheme.ACCENT, 0xFFFFFFFF, pulse), 2);
            }
            drawEntities(context, mouseX, mouseY);
            drawLocalPlayer(context);
            drawCompass(context, mx + mw - 26, my + 8);
            drawScaleBar(context, mx + 10, my + mh - 20);
            int pending = textures.pendingChunks();
            int pillY = my + 8;
            if (pending > 0 && terrain) {
                WbChrome.drawPill(context, font, mx + 8, pillY, Lang.string("worldbinder.map.rendering", pending), WbTheme.INFO, WbIcon.PERFORMANCE);
                pillY += 18;
            }
            if (activeFilterCount() > 0) {
                WbChrome.drawPill(context, font, mx + 8, pillY, Lang.string("worldbinder.map.filters_active", activeFilterCount()), WbTheme.OK, WbIcon.FILTER);
                pillY += 18;
            }
            if (config.mapExploreMode) {
                float pulse = WbMotion.pulse(2000L);
                WbChrome.drawPill(context, font, mx + 8, pillY, Lang.string("worldbinder.map.exploring", MapExplorer.get().size()),
                        WbMotion.mix(WbTheme.OK, 0xFFFFFFFF, pulse * 0.2F), WbIcon.GLOBE);
            }
            if (cachedSnapshots.isEmpty() && cachedDone.isEmpty() && cachedQueued.isEmpty() && MapExplorer.get().size() == 0) {
                drawEmptyState(context, mx, my, mw, mh);
            }
        } finally {
            context.disableScissor();
        }
        WbChrome.roundedOutline(context, mx - 1, my - 1, mw + 2, mh + 2, WbTheme.withAlpha(WbTheme.ACCENT, 0x40));
    }

    private void drawBackgroundGrid(GuiGraphicsExtractor context, double originX, double originY,
                                    int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        double regionSize = MapRegionTextures.REGION_BLOCKS * zoom;
        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                if (((rx + rz) & 1) == 0) {
                    continue;
                }
                int x1 = (int) Math.floor(originX + rx * regionSize);
                int y1 = (int) Math.floor(originY + rz * regionSize);
                context.fill(x1, y1, (int) Math.floor(originX + (rx + 1) * regionSize), (int) Math.floor(originY + (rz + 1) * regionSize), 0x0CFFFFFF);
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor context, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                          int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        int top = mapY();
        int bottom = mapY() + mapH();
        int left = mapX();
        int right = mapX() + mapW();
        double chunkPixels = zoom * 16.0D;
        if (chunkPixels >= 6.0D) {
            int alpha = (int) Math.min(0x22, (chunkPixels - 6.0D) * 3.0D + 6.0D);
            int color = alpha << 24 | 0xFFFFFF;
            for (int cx = minChunkX; cx <= maxChunkX + 1; cx++) {
                int x = (int) Math.round(screenXOfBlock(cx * 16.0));
                context.fill(x, top, x + 1, bottom, color);
            }
            for (int cz = minChunkZ; cz <= maxChunkZ + 1; cz++) {
                int y = (int) Math.round(screenYOfBlock(cz * 16.0));
                context.fill(left, y, right, y + 1, color);
            }
        }
        int regionColor = 0x38FFFFFF;
        for (int rx = minRegionX; rx <= maxRegionX + 1; rx++) {
            int x = (int) Math.round(screenXOfBlock(rx * (double) MapRegionTextures.REGION_BLOCKS));
            context.fill(x, top, x + 1, bottom, regionColor);
        }
        for (int rz = minRegionZ; rz <= maxRegionZ + 1; rz++) {
            int y = (int) Math.round(screenYOfBlock(rz * (double) MapRegionTextures.REGION_BLOCKS));
            context.fill(left, y, right, y + 1, regionColor);
        }
    }

    private void drawChunkOutline(GuiGraphicsExtractor context, int cx, int cz, int color, int thickness) {
        int x1 = (int) Math.round(screenXOfBlock(cx * 16.0));
        int y1 = (int) Math.round(screenYOfBlock(cz * 16.0));
        int x2 = (int) Math.round(screenXOfBlock(cx * 16.0 + 16.0));
        int y2 = (int) Math.round(screenYOfBlock(cz * 16.0 + 16.0));
        if (x2 - x1 < 3) {
            x2 = x1 + 3;
            y2 = y1 + 3;
        }
        context.fill(x1, y1, x2, y1 + thickness, color);
        context.fill(x1, y2 - thickness, x2, y2, color);
        context.fill(x1, y1, x1 + thickness, y2, color);
        context.fill(x2 - thickness, y1, x2, y2, color);
    }

    private void drawCaptureRadius(GuiGraphicsExtractor context, SceneCaptureService capture) {
        Minecraft mc = Minecraft.getInstance();
        if (capture == null || !capture.isRoamingCapture() || mc.player == null) {
            return;
        }
        int radius = WorldBinder.config().effectiveRoamingRadiusChunks();
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int x1 = (int) Math.round(screenXOfBlock((pcx - radius) * 16.0));
        int y1 = (int) Math.round(screenYOfBlock((pcz - radius) * 16.0));
        int x2 = (int) Math.round(screenXOfBlock((pcx + radius + 1) * 16.0));
        int y2 = (int) Math.round(screenYOfBlock((pcz + radius + 1) * 16.0));
        float pulse = WbMotion.pulse(2200L);
        int color = WbTheme.withAlpha(WbTheme.OK, 0x60 + Math.round(0x50 * pulse));
        int dash = 6;
        for (int x = x1; x < x2; x += dash * 2) {
            context.fill(x, y1, Math.min(x + dash, x2), y1 + 1, color);
            context.fill(x, y2 - 1, Math.min(x + dash, x2), y2, color);
        }
        for (int y = y1; y < y2; y += dash * 2) {
            context.fill(x1, y, x1 + 1, Math.min(y + dash, y2), color);
            context.fill(x2 - 1, y, x2, Math.min(y + dash, y2), color);
        }
    }

    private void drawEntities(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        hoveredEntity = null;
        nearbyEntities.clear();
        if (mc.level == null) {
            return;
        }
        WorldBinderConfig config = WorldBinder.config();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double bestDistance = 25.0D;
        int radius = zoom >= 4.0D ? 3 : 2;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) {
                continue;
            }
            MapMarkers.Kind kind = MapMarkers.kind(entity);
            nearbyEntities.merge(kind, 1, Integer::sum);
            if (!MapMarkers.visible(kind, config)) {
                continue;
            }
            double ex = entity.xo + (entity.getX() - entity.xo) * partial;
            double ez = entity.zo + (entity.getZ() - entity.zo) * partial;
            int sx = (int) Math.round(screenXOfBlock(ex));
            int sy = (int) Math.round(screenYOfBlock(ez));
            if (!insideMap(sx, sy)) {
                continue;
            }
            if (entity instanceof Player other) {
                MapMarkers.drawPlayerArrow(context, sx, sy, other.getViewYRot(partial), 0.7F, MapMarkers.Kind.PLAYER.color, false);
                if (zoom >= 1.0D) {
                    String name = other.getName().getString();
                    int w = font.width(name) + 8;
                    WbChrome.rounded(context, sx - w / 2, sy - 20, w, 11, 0xC0000000);
                    context.text(font, name, sx - font.width(name) / 2, sy - 18, WbTheme.TEXT, false);
                }
            } else {
                MapMarkers.drawEntity(context, sx, sy, kind, radius);
            }
            double dx = sx - mouseX;
            double dy = sy - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                hoveredEntity = entity;
            }
        }
        if (hoveredEntity != null) {
            int sx = (int) Math.round(screenXOfBlock(hoveredEntity.getX()));
            int sy = (int) Math.round(screenYOfBlock(hoveredEntity.getZ()));
            WbChrome.circleOutline(context, sx, sy, radius + 4, 0xE0FFFFFF);
        }
    }

    private void drawLocalPlayer(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = mc.player.xo + (mc.player.getX() - mc.player.xo) * partial;
        double pz = mc.player.zo + (mc.player.getZ() - mc.player.zo) * partial;
        float sx = (float) screenXOfBlock(px);
        float sy = (float) screenYOfBlock(pz);
        MapMarkers.drawPlayerArrow(context, sx, sy, mc.player.getViewYRot(partial), 1.0F, WbTheme.ACCENT, true);
    }

    private void drawCompass(GuiGraphicsExtractor context, int x, int y) {
        WbChrome.rounded(context, x, y, 18, 24, 0xB0101720);
        WbChrome.roundedOutline(context, x, y, 18, 24, WbTheme.withAlpha(WbTheme.ACCENT, 0x80));
        WbIcon.COMPASS.draw(context, x + 6, y + 3, 1, WbTheme.ERROR);
        context.text(font, "N", x + 9 - font.width("N") / 2, y + 13, WbTheme.TEXT, false);
    }

    private void drawScaleBar(GuiGraphicsExtractor context, int x, int y) {
        double[] steps = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048};
        double blocks = steps[0];
        for (double step : steps) {
            blocks = step;
            if (step * zoom >= 48.0D) {
                break;
            }
        }
        int pixels = (int) Math.round(blocks * zoom);
        String label = Lang.string("worldbinder.map.scale_blocks", (int) blocks);
        int w = Math.max(pixels, font.width(label)) + 12;
        WbChrome.rounded(context, x - 4, y - 12, w, 20, 0xA0080C12);
        context.fill(x, y + 2, x + pixels, y + 4, WbTheme.TEXT);
        context.fill(x, y - 1, x + 1, y + 4, WbTheme.TEXT);
        context.fill(x + pixels - 1, y - 1, x + pixels, y + 4, WbTheme.TEXT);
        context.text(font, label, x, y - 10, WbTheme.TEXT_SOFT, false);
    }

    private void drawEmptyState(GuiGraphicsExtractor context, int mx, int my, int mw, int mh) {
        int w = Math.min(340, mw - 40);
        int h = 86;
        int x = mx + (mw - w) / 2;
        int y = my + (mh - h) / 2;
        WbChrome.drawCardSurface(context, x, y, w, h, WbTheme.ACCENT, false, false);
        WbIcon.MAP.draw(context, x + 14, y + 12, 2, WbTheme.ACCENT);
        context.text(font, Component.literal(Lang.string("worldbinder.map.empty_title")).withStyle(ChatFormatting.BOLD), x + 36, y + 14, WbTheme.TEXT, true);
        WbText.drawWrapped(context, font, Lang.string("worldbinder.map.empty_hint_explore"), x + 14, y + 34, w - 28, WbTheme.TEXT_SOFT, 4);
    }

    // ------------------------------------------------------------------ sidebar

    private void drawSidebar(GuiGraphicsExtractor context, SceneCaptureService capture) {
        int x = sidebarX();
        int y = mapY();
        int w = SIDEBAR_W;
        int bottom = mapY() + mapH();

        int cardH = 138;
        WbChrome.drawCardSurface(context, x, y, w, cardH, WbTheme.ACCENT, false, false);
        cardTitle(context, x, y, WbIcon.MAP, Lang.string("worldbinder.map.coverage"));
        int total = Math.max(1, statDone + statPartial + statQueued + statFailed);
        int rowY = y + 26;
        statRow(context, x + 10, rowY, w - 20, Lang.string("worldbinder.map.saved"), statDone, total, WbTheme.OK); rowY += 20;
        statRow(context, x + 10, rowY, w - 20, Lang.string("worldbinder.map.partial"), statPartial, total, WbTheme.WARN); rowY += 20;
        statRow(context, x + 10, rowY, w - 20, Lang.string("worldbinder.map.queued"), statQueued, total, WbTheme.ACCENT_RIGHT); rowY += 20;
        statRow(context, x + 10, rowY, w - 20, Lang.string("worldbinder.map.errors"), statFailed, total, WbTheme.ERROR);
        double areaKm2 = statDone * 256.0D / 1_000_000.0D;
        WbText.drawClipped(context, font, Lang.string("worldbinder.map.area", String.format(Locale.ROOT, "%.2f", areaKm2), statKnown),
                x + 10, y + cardH - 26, w - 20, WbTheme.TEXT_DIM);
        String explored = Lang.string("worldbinder.map.explored_count", MapExplorer.get().size());
        WbIcon.GLOBE.draw(context, x + 10, y + cardH - 13, 1, WorldBinder.config().mapExploreMode ? WbTheme.OK : WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, explored, x + 21, y + cardH - 13, w - 31, WbTheme.TEXT_DIM);

        int cursorY = y + cardH + 8;
        int cursorH = 76;
        WbChrome.drawCardSurface(context, x, cursorY, w, cursorH, WbTheme.INFO, false, false);
        cardTitle(context, x, cursorY, WbIcon.TARGET, Lang.string("worldbinder.map.cursor"));
        if (hoverValid) {
            long key = ChunkPos.pack(Math.floorDiv(hoverBlockX, 16), Math.floorDiv(hoverBlockZ, 16));
            String height = heightAt(lookup.apply(key), hoverBlockX, hoverBlockZ);
            infoRow(context, x + 10, cursorY + 26, w - 20, Lang.string("worldbinder.map.position"), hoverBlockX + " / " + height + " / " + hoverBlockZ);
            infoRow(context, x + 10, cursorY + 40, w - 20, Lang.string("worldbinder.map.chunk"), chunkX(key) + ", " + chunkZ(key)
                    + "  (r " + Math.floorDiv(chunkX(key), 32) + ", " + Math.floorDiv(chunkZ(key), 32) + ")");
            statusRow(context, x + 10, cursorY + 56, statusOf(key), explored(key));
        } else {
            entityCounts(context, x + 10, cursorY + 28, w - 20);
        }

        int selY = selectedCardY();
        int selH = Math.min(176, bottom - selY);
        if (selH < 60) {
            return;
        }
        WbChrome.drawCardSurface(context, x, selY, w, selH, selectedChunk == Long.MIN_VALUE ? WbTheme.PANEL_BORDER : WbTheme.ACCENT, false, selectedChunk != Long.MIN_VALUE);
        cardTitle(context, x, selY, WbIcon.CHUNK, Lang.string("worldbinder.map.inspector"));
        if (selectedChunk == Long.MIN_VALUE) {
            WbText.drawWrapped(context, font, Lang.string("worldbinder.map.inspector.empty"), x + 10, selY + 28, w - 20, WbTheme.TEXT_DIM, 4);
            return;
        }
        ChunkSnapshot snapshot = cachedSnapshots.get(selectedChunk);
        ChunkCaptureStatus status = statusOf(selectedChunk);
        int cx = chunkX(selectedChunk);
        int cz = chunkZ(selectedChunk);
        int line = selY + 26;
        infoRow(context, x + 10, line, w - 20, Lang.string("worldbinder.map.chunk"), cx + ", " + cz); line += 13;
        infoRow(context, x + 10, line, w - 20, Lang.string("worldbinder.map.blocks_range"), (cx << 4) + ".." + ((cx << 4) + 15) + " / " + (cz << 4) + ".." + ((cz << 4) + 15)); line += 13;
        statusRow(context, x + 10, line, status, explored(selectedChunk)); line += 15;
        infoRow(context, x + 10, line, w - 20, Lang.string("worldbinder.map.blocks"), snapshot == null ? "0" : snapshot.savedBlocks + " / " + snapshot.scannedBlocks); line += 13;
        infoRow(context, x + 10, line, w - 20, Lang.string("worldbinder.map.entities"), snapshot == null ? "0" : Integer.toString(snapshot.entityCount)); line += 13;
        infoRow(context, x + 10, line, w - 20, Lang.string("worldbinder.map.blockentities"), snapshot == null ? "0" : Integer.toString(snapshot.blockEntityCount)); line += 13;
        infoRow(context, x + 10, line, w - 20, Lang.string("worldbinder.map.last_scanned"), snapshot == null ? Lang.string("worldbinder.common.never") : snapshot.lastScannedText()); line += 15;
        double quality = snapshot == null ? 0.0D : snapshot.qualityScore(WorldBinder.config().effectiveCaptureHeight());
        context.text(font, Lang.string("worldbinder.map.quality", (int) Math.round(quality * 100.0D)), x + 10, line, WbTheme.TEXT_MUTED, false);
        WbChrome.drawProgressTrack(context, x + 10, line + 11, w - 20, 6, (int) Math.round((w - 22) * quality),
                quality > 0.9D ? WbTheme.OK : quality > 0.55D ? WbTheme.WARN : WbTheme.ERROR);
        if (WorldBinder.config().queueDebugDiagnostics) {
            WbText.drawClipped(context, font, capture.queueDiagnosticsLine(), x + 10, selY + selH + 4, w - 20, WbTheme.TEXT_DIM);
        }
    }

    private void entityCounts(GuiGraphicsExtractor context, int x, int y, int w) {
        WbText.drawClipped(context, font, Lang.string("worldbinder.map.nearby_entities"), x, y, w, WbTheme.TEXT_DIM);
        int cx = x;
        int cy = y + 16;
        for (MapMarkers.Kind kind : new MapMarkers.Kind[] {MapMarkers.Kind.HOSTILE, MapMarkers.Kind.PASSIVE, MapMarkers.Kind.ITEM, MapMarkers.Kind.PLAYER}) {
            int count = nearbyEntities.getOrDefault(kind, 0);
            MapMarkers.drawEntity(context, cx + 3, cy + 4, kind, 3);
            String text = Integer.toString(count);
            context.text(font, text, cx + 10, cy, WbTheme.TEXT_SOFT, false);
            cx += 14 + font.width(text) + 10;
            if (cx > x + w - 30) {
                cx = x;
                cy += 14;
            }
        }
        WbText.drawClipped(context, font, Lang.string("worldbinder.map.cursor_hint_short"), x, y + 34, w, WbTheme.TEXT_DIM);
    }

    private void cardTitle(GuiGraphicsExtractor context, int x, int y, WbIcon icon, String title) {
        icon.draw(context, x + 10, y + 9, 1, WbTheme.ACCENT);
        context.text(font, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 22, y + 8, WbTheme.TEXT, true);
        WbChrome.drawDivider(context, x + 8, y + 20, SIDEBAR_W - 16);
    }

    private void statRow(GuiGraphicsExtractor context, int x, int y, int w, String label, int value, int total, int color) {
        WbChrome.rounded(context, x, y + 1, 7, 7, color);
        context.text(font, label, x + 11, y, WbTheme.TEXT_SOFT, false);
        String text = Integer.toString(value);
        context.text(font, text, x + w - font.width(text), y, WbTheme.TEXT, false);
        WbChrome.drawProgressTrack(context, x + 11, y + 10, w - 11, 4, (int) Math.round((w - 13) * Math.min(1.0D, value / (double) total)), color);
    }

    private void infoRow(GuiGraphicsExtractor context, int x, int y, int w, String label, String value) {
        int labelW = Math.min(64, font.width(label) + 6);
        WbText.drawClipped(context, font, label, x, y, labelW, WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, value, x + labelW, y, w - labelW, WbTheme.TEXT_SOFT);
    }

    private void statusRow(GuiGraphicsExtractor context, int x, int y, ChunkCaptureStatus status, boolean explored) {
        int width = WbChrome.drawPill(context, font, x, y - 3, statusLabel(status), statusColor(status), statusIcon(status));
        if (explored) {
            WbChrome.drawPill(context, font, x + width + 4, y - 3, Lang.string("worldbinder.map.status.explored"), WbTheme.OK, WbIcon.GLOBE);
        }
    }

    private static String heightAt(ChunkSnapshot snapshot, int blockX, int blockZ) {
        if (snapshot == null || snapshot.heights == null || snapshot.heights.length != 256) {
            return "?";
        }
        int height = snapshot.heights[(blockZ & 15) * 16 + (blockX & 15)];
        return height == Integer.MIN_VALUE ? "?" : Integer.toString(height);
    }

    private void drawFooter(GuiGraphicsExtractor context) {
        int y = height - 22;
        int x = MARGIN;
        if (exporting) {
            WbChrome.drawPill(context, font, x, y - 2, Lang.string("worldbinder.map.exporting"), WbTheme.ACCENT_RIGHT, WbIcon.DOWNLOAD);
        } else if (footerMessage != null && System.currentTimeMillis() - footerMessageMillis < 3200L) {
            WbChrome.drawPill(context, font, x, y - 2, WbText.ellipsize(font, footerMessage.getString(), width - 360), footerAccent, WbIcon.CHECK);
        } else if (hoverValid) {
            String coords = Lang.string("worldbinder.map.footer_coords", hoverBlockX, hoverBlockZ,
                    Math.floorDiv(hoverBlockX, 16), Math.floorDiv(hoverBlockZ, 16));
            WbIcon.TARGET.draw(context, x, y + 1, 1, WbTheme.ACCENT);
            context.text(font, coords, x + 12, y, WbTheme.TEXT_SOFT, false);
        } else {
            Minecraft mc = Minecraft.getInstance();
            String line = mc.player == null ? Lang.string("worldbinder.map.footer_no_player")
                    : Lang.string("worldbinder.map.footer_player", mc.player.blockPosition().getX(), mc.player.blockPosition().getY(), mc.player.blockPosition().getZ());
            WbIcon.PLAYER.draw(context, x, y + 1, 1, WbTheme.ACCENT);
            context.text(font, line, x + 12, y, WbTheme.TEXT_MUTED, false);
        }
        if (width >= 700) {
            int hx = width - MARGIN;
            hx = hint(context, hx, y, Lang.string("worldbinder.map.hint_rescan"), Lang.string("worldbinder.map.key_right_click"));
            hx = hint(context, hx - 10, y, Lang.string("worldbinder.map.hint_zoom"), Lang.string("worldbinder.map.key_scroll"));
            hint(context, hx - 10, y, Lang.string("worldbinder.map.hint_pan"), Lang.string("worldbinder.map.key_drag"));
        }
    }

    private int hint(GuiGraphicsExtractor context, int right, int y, String text, String key) {
        int textW = font.width(text);
        int keyW = Math.max(14, font.width(key) + 8);
        int x = right - textW - keyW - 4;
        WbChrome.drawKeycap(context, font, x, y - 2, key);
        context.text(font, text, x + keyW + 4, y, WbTheme.TEXT_DIM, false);
        return x;
    }

    private List<String> hoverDetails(long key, ChunkCaptureStatus status) {
        ChunkSnapshot snapshot = cachedSnapshots.get(key);
        List<String> details = new ArrayList<>();
        details.add(Lang.string("worldbinder.map.tooltip.status_line", statusLabel(status))
                + (explored(key) ? "  ·  " + Lang.string("worldbinder.map.status.explored") : ""));
        details.add(Lang.string("worldbinder.map.tooltip.block_line", hoverBlockX, heightAt(lookup.apply(key), hoverBlockX, hoverBlockZ), hoverBlockZ));
        if (snapshot == null) {
            details.add(Lang.string("worldbinder.map.tooltip.not_recorded"));
        } else {
            details.add(Lang.string("worldbinder.map.tooltip.blocks_line", snapshot.savedBlocks + " / " + snapshot.scannedBlocks));
            details.add(Lang.string("worldbinder.map.tooltip.entities_line", snapshot.entityCount)
                    + "  ·  " + Lang.string("worldbinder.map.tooltip.blockentities_line", snapshot.blockEntityCount));
        }
        details.add("");
        details.add(Lang.string("worldbinder.map.tooltip.click_hint"));
        return details;
    }

    private List<String> entityDetails(Entity entity, MapMarkers.Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        List<String> details = new ArrayList<>();
        details.add(Lang.string("worldbinder.map.entity.kind", Lang.string("worldbinder.map.entity.kind." + kind.name().toLowerCase(Locale.ROOT)),
                entity.getType().getDescription().getString()));
        details.add(Lang.string("worldbinder.map.tooltip.block_line", (int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ())));
        if (mc.player != null) {
            details.add(Lang.string("worldbinder.map.entity.distance", Math.round(mc.player.distanceTo(entity))));
        }
        return details;
    }

    private static int statusColor(ChunkCaptureStatus status) {
        return switch (status == null ? ChunkCaptureStatus.UNKNOWN : status) {
            case DONE -> WbTheme.OK;
            case PARTIAL, RECOVERY -> WbTheme.WARN;
            case QUEUED -> WbTheme.ACCENT_RIGHT;
            case SCANNING -> WbTheme.INFO;
            case FAILED -> WbTheme.ERROR;
            case UNKNOWN -> WbTheme.TEXT_DIM;
        };
    }

    private static WbIcon statusIcon(ChunkCaptureStatus status) {
        return switch (status == null ? ChunkCaptureStatus.UNKNOWN : status) {
            case DONE -> WbIcon.CHECK;
            case PARTIAL, RECOVERY -> WbIcon.WARNING;
            case QUEUED -> WbIcon.CHUNK;
            case SCANNING -> WbIcon.SEARCH;
            case FAILED -> WbIcon.CLOSE;
            case UNKNOWN -> WbIcon.INFO;
        };
    }

    private static String statusLabel(ChunkCaptureStatus status) {
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

    private static String modeLabel(WorldBinderConfig.MapLayerMode mode) {
        return switch (mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode) {
            case BOTH -> Lang.string("worldbinder.config.map_mode.both");
            case CHUNKS_ONLY -> Lang.string("worldbinder.config.map_mode.chunks");
            case MAP_ONLY -> Lang.string("worldbinder.config.map_mode.map");
        };
    }

    private static int chunkX(long key) {
        return key == Long.MIN_VALUE ? 0 : (int) key;
    }

    private static int chunkZ(long key) {
        return key == Long.MIN_VALUE ? 0 : (int) (key >> 32);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        double vx = uiScale.toVirtualX(mouseX);
        double vy = uiScale.toVirtualY(mouseY);
        int realWidth = width;
        int realHeight = height;
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
            if (insideMap(vx, vy) && dropdown == Dropdown.NONE && verticalAmount != 0) {
                zoomBy(verticalAmount > 0 ? 1 : -1, vx, vy);
                return true;
            }
            return super.mouseScrolled(vx, vy, horizontalAmount, verticalAmount);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = WbLayout.virtualMouseEvent(event, width, height);
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
            double vx = virtualEvent.x();
            double vy = virtualEvent.y();
            if (dropdown != Dropdown.NONE) {
                int[] b = dropdownBounds();
                boolean insidePanel = vx >= b[0] && vx <= b[0] + b[2] && vy >= b[1] && vy <= b[1] + b[3];
                if (!insidePanel && vy > TOOLBAR_Y + TOOLBAR_H) {
                    dropdown = Dropdown.NONE;
                    rebuild();
                    WbSounds.dropdown(false);
                    return true;
                }
            }
            if (super.mouseClicked(virtualEvent, doubleClick)) {
                return true;
            }
            if (insideMap(vx, vy)) {
                long key = ChunkPos.pack(Math.floorDiv((int) Math.floor(blockAtScreenX(vx)), 16),
                        Math.floorDiv((int) Math.floor(blockAtScreenY(vy)), 16));
                if (virtualEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    selectedChunk = key;
                    WorldBinderClient.capture().queueChunkForRescan(chunkX(key), chunkZ(key));
                    footer(Component.translatable("worldbinder.map.rescan_queued", chunkX(key), chunkZ(key)), WbTheme.OK);
                    WbSounds.mapPing();
                    rebuild();
                    return true;
                }
                if (virtualEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    dragging = true;
                    dragDistance = 0;
                    if (doubleClick) {
                        centerOnChunk(key);
                    }
                    return true;
                }
            }
            return false;
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        if (dragging) {
            double dx = uiScale.toVirtualDelta(offsetX);
            double dy = uiScale.toVirtualDelta(offsetY);
            dragDistance += Math.abs(dx) + Math.abs(dy);
            if (dragDistance > 2.0D) {
                if (followPlayer) {
                    followPlayer = false;
                    rebuild();
                }
                targetCamX -= dx / zoom;
                targetCamZ -= dy / zoom;
            }
            return true;
        }
        return super.mouseDragged(WbLayout.virtualMouseEvent(event, width, height), uiScale.toVirtualDelta(offsetX), uiScale.toVirtualDelta(offsetY));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = WbLayout.virtualMouseEvent(event, width, height);
        if (dragging) {
            dragging = false;
            if (dragDistance <= 2.0D) {
                int realWidth = width;
                int realHeight = height;
                WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
                width = uiScale.virtualWidth();
                height = uiScale.virtualHeight();
                try {
                    long key = ChunkPos.pack(Math.floorDiv((int) Math.floor(blockAtScreenX(virtualEvent.x())), 16),
                            Math.floorDiv((int) Math.floor(blockAtScreenY(virtualEvent.y())), 16));
                    selectedChunk = selectedChunk == key ? Long.MIN_VALUE : key;
                    WbSounds.click();
                    rebuild();
                } finally {
                    width = realWidth;
                    height = realHeight;
                }
            }
            return true;
        }
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (goField != null && getFocused() == goField) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                jumpToField();
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.isCopy() && selectedChunk != Long.MIN_VALUE) {
            copySelectedChunkCenter();
            return true;
        }
        int key = event.key();
        double step = 48.0D / zoom;
        if (key == InputConstants.KEY_W || key == InputConstants.KEY_UP) { pan(0, -step); return true; }
        if (key == InputConstants.KEY_S || key == InputConstants.KEY_DOWN) { pan(0, step); return true; }
        if (key == InputConstants.KEY_A || key == InputConstants.KEY_LEFT) { pan(-step, 0); return true; }
        if (key == InputConstants.KEY_D || key == InputConstants.KEY_RIGHT) { pan(step, 0); return true; }
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        double cx = MARGIN + (uiScale.virtualWidth() - MARGIN * 2) / 2.0;
        double cy = uiScale.virtualHeight() / 2.0;
        if (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD) { zoomBy(1, cx, cy); return true; }
        if (key == InputConstants.KEY_MINUS || key == 333 /* GLFW_KEY_KP_SUBTRACT */) { zoomBy(-1, cx, cy); return true; }
        if (key == InputConstants.KEY_SPACE) { centerOnPlayer(true); return true; }
        if (key == InputConstants.KEY_F) { followPlayer = !followPlayer; rebuild(); return true; }
        if (key == InputConstants.KEY_G) { WorldBinder.config().mapShowGrid = !WorldBinder.config().mapShowGrid; WbSounds.toggle(WorldBinder.config().mapShowGrid); return true; }
        if (key == InputConstants.KEY_H) { WorldBinder.config().mapShowSidebar = !WorldBinder.config().mapShowSidebar; rebuild(); return true; }
        if (key == InputConstants.KEY_E) {
            WorldBinder.config().mapExploreMode = !WorldBinder.config().mapExploreMode;
            WbSounds.toggle(WorldBinder.config().mapExploreMode);
            rebuild();
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            WbTooltips.noteKeyboardNavigation();
        }
        return super.keyPressed(event);
    }

    private void pan(double dx, double dz) {
        if (followPlayer) {
            followPlayer = false;
            rebuild();
        }
        targetCamX += dx;
        targetCamZ += dz;
    }

    @Override
    public void onClose() {
        WorldBinder.config().mapZoom = targetZoom;
        WorldBinder.config().save();
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }

    // ------------------------------------------------------------------ actions

    private void footer(Component message, int accent) {
        footerMessage = message;
        footerAccent = accent;
        footerMessageMillis = System.currentTimeMillis();
    }

    private void startExport(MapExporter.Mode mode) {
        if (exporting) {
            return;
        }
        if (cachedSnapshots.isEmpty() && MapExplorer.get().size() == 0) {
            footer(Component.translatable("worldbinder.map.export.empty"), WbTheme.WARN);
            WbSounds.warning();
            return;
        }
        exporting = true;
        dropdown = Dropdown.NONE;
        rebuild();
        java.util.function.LongToIntFunction status = exportWithStatus ? key -> statusTint(statusOf(key), false) : null;
        MapExporter.export(cachedSnapshots, mode, WorldBinder.config().mapHillshade, status).whenComplete((result, error) ->
                Minecraft.getInstance().execute(() -> {
                    exporting = false;
                    if (error == null) {
                        footer(Component.translatable("worldbinder.map.export.done", result.files(), result.chunks()), WbTheme.OK);
                        Chat.info(Lang.string("worldbinder.map.export.done_chat", result.path().toAbsolutePath()));
                        WbSounds.success();
                        net.worldbinder.util.PathOpener.open(result.path());
                    } else {
                        footer(Component.translatable("worldbinder.map.export.failed"), WbTheme.ERROR);
                        Chat.error(Lang.string("worldbinder.map.export.failed"));
                        WorldBinder.LOGGER.warn("Live map export failed", error);
                        WbSounds.error();
                    }
                    if (minecraft != null && net.worldbinder.compat.ClientCompat.screen(minecraft) == this) {
                        rebuild();
                    }
                }));
    }

    private void jumpToField() {
        if (goField == null) {
            return;
        }
        String[] parts = goField.getValue().trim().replace(',', ' ').split("\\s+");
        try {
            double x;
            double z;
            if (parts.length >= 3) {
                x = Double.parseDouble(parts[0]);
                z = Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                x = Double.parseDouble(parts[0]);
                z = Double.parseDouble(parts[1]);
            } else {
                WbSounds.error();
                return;
            }
            setFollow(false);
            targetCamX = x;
            targetCamZ = z;
            WbSounds.mapPing();
        } catch (NumberFormatException ignored) {
            WbSounds.error();
        }
    }

    private void jumpToOrigin() {
        ChunkSnapshot first = WorldBinderClient.capture().chunkSnapshotsView().values().stream().findFirst().orElse(null);
        if (first != null) {
            setFollow(false);
            targetCamX = first.chunkX * 16 + 8;
            targetCamZ = first.chunkZ * 16 + 8;
        } else if (cachedBounds != null) {
            setFollow(false);
            targetCamX = (cachedBounds[0] + cachedBounds[2] + 1) * 8.0;
            targetCamZ = (cachedBounds[1] + cachedBounds[3] + 1) * 8.0;
        }
    }

    private void queueSelectedRescan() {
        if (selectedChunk != Long.MIN_VALUE) {
            WorldBinderClient.capture().queueChunkForRescan(chunkX(selectedChunk), chunkZ(selectedChunk));
            footer(Component.translatable("worldbinder.map.rescan_queued", chunkX(selectedChunk), chunkZ(selectedChunk)), WbTheme.OK);
            WbSounds.mapPing();
        }
    }

    private void copySelectedChunkCenter() {
        int x = (chunkX(selectedChunk) << 4) + 8;
        int z = (chunkZ(selectedChunk) << 4) + 8;
        int y = 80;
        Minecraft mc = Minecraft.getInstance();
        String height = heightAt(lookup.apply(selectedChunk), x, z);
        if (!height.equals("?")) {
            y = Integer.parseInt(height) + 1;
        } else if (mc.player != null) {
            y = mc.player.blockPosition().getY();
        }
        String coordinate = x + " " + y + " " + z;
        mc.keyboardHandler.setClipboard(coordinate);
        footer(Component.translatable("worldbinder.chat.copied_chunk_center", coordinate), WbTheme.OK);
        Chat.info(Lang.string("worldbinder.chat.copied_chunk_center", coordinate));
    }

    private void clearFilters() {
        STATUS_FILTERS.clear();
        CONTENT_FILTERS.clear();
        filterInvert = false;
        filterHide = false;
        rebuild();
    }
}

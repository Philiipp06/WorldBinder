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
import net.worldbinder.render.Map3DView;
import net.worldbinder.render.MapExporter3D;
import net.worldbinder.render.MapRenderer3D;
import net.worldbinder.render.MapVoxels;
import net.worldbinder.render.MapWaypoints;
import net.worldbinder.render.BlockMapColors;
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

        Button view3d = toolbarButton(x, "worldbinder.map.view3d", "worldbinder.map.tooltip.view3d", WbIcon.CUBE, labels, 56, b -> toggle3d());
        WbButton.tab(view3d, WbTheme.ACCENT_RIGHT, config.map3d);
        WbTooltips.hint(view3d, Component.literal("V"));
        addRenderableWidget(view3d);
        x += view3d.getWidth() + 4;

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
        if (config.map3d) {
            add3dControls();
        }
    }

    /** Small control cluster at the bottom right of the 3D view: rotate, tilt, height and reset. */
    private void add3dControls() {
        WorldBinderConfig config = WorldBinder.config();
        int size = 20;
        int gap = 3;
        int count = 6;
        int x = mapX() + mapW() - 8 - count * size - (count - 1) * gap;
        int y = mapY() + mapH() - 8 - size;
        Button left = WbButton.iconOnly(WbButton.create(x, y, size, size, Lang.text("worldbinder.map.3d.rotate_left"),
                Lang.text("worldbinder.map.3d.rotate_left.tooltip"), b -> rotate3d(-45.0D)), WbIcon.ROTATE);
        WbTooltips.hint(left, Component.literal(","));
        addRenderableWidget(left);
        x += size + gap;
        Button right = WbButton.iconOnly(WbButton.create(x, y, size, size, Lang.text("worldbinder.map.3d.rotate_right"),
                Lang.text("worldbinder.map.3d.rotate_right.tooltip"), b -> rotate3d(45.0D)), WbIcon.CHEVRON_RIGHT);
        WbTooltips.hint(right, Component.literal("."));
        addRenderableWidget(right);
        x += size + gap;
        Button flatter = WbButton.iconOnly(WbButton.create(x, y, size, size, Lang.text("worldbinder.map.3d.tilt_down"),
                Lang.text("worldbinder.map.3d.tilt_down.tooltip"), b -> tilt3d(-10.0D)), WbIcon.TILT);
        WbTooltips.hint(flatter, Component.literal("PgDn"));
        addRenderableWidget(flatter);
        x += size + gap;
        Button steeper = WbButton.iconOnly(WbButton.create(x, y, size, size, Lang.text("worldbinder.map.3d.tilt_up"),
                Lang.text("worldbinder.map.3d.tilt_up.tooltip"), b -> tilt3d(10.0D)), WbIcon.CHEVRON_DOWN);
        WbTooltips.hint(steeper, Component.literal("PgUp"));
        addRenderableWidget(steeper);
        x += size + gap;
        Button height = WbButton.iconOnly(WbButton.create(x, y, size, size, Lang.text("worldbinder.map.3d.height"),
                Lang.text("worldbinder.map.3d.height.tooltip", config.map3dHeightPercent), b -> cycle3dHeight()), WbIcon.MOUNTAIN);
        addRenderableWidget(height);
        x += size + gap;
        Button reset = WbButton.iconOnly(WbButton.create(x, y, size, size, Lang.text("worldbinder.map.3d.reset"),
                Lang.text("worldbinder.map.3d.reset.tooltip"), b -> reset3d()), WbIcon.RESET);
        addRenderableWidget(reset);
    }

    private int zoomLabelX;

    private int[] dropdownBounds() {
        int y = TOOLBAR_Y + TOOLBAR_H + 4;
        return switch (dropdown) {
            case LAYERS -> new int[] {dropdownX, y, 380, 10 + 14 + 8 * DROPDOWN_ROW + 2};
            case FILTERS -> new int[] {dropdownX, y, 380, 10 + 14 + 6 * DROPDOWN_ROW + 2};
            case EXPORT -> new int[] {dropdownX, y, 230, 10 + 6 * DROPDOWN_ROW + 6};
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
        addRenderableWidget(toggleRow(left, top + row++ * DROPDOWN_ROW, colW, "worldbinder.config.map.status", "worldbinder.tooltip.config.map.status",
                config.mapShowStatusOverlay, v -> config.mapShowStatusOverlay = v));
        WorldBinderConfig.MapBlockColors colors = config.mapBlockColors;
        addRenderableWidget(WbButton.icon(WbButton.create(left, top + row * DROPDOWN_ROW, colW, 20,
                Lang.text("worldbinder.map.layer.block_colors", Lang.string("worldbinder.map.block_colors." + colors.name().toLowerCase(Locale.ROOT))),
                Lang.text("worldbinder.map.layer.block_colors.tooltip"), b -> cycleBlockColors()), WbIcon.PALETTE));

        int r = 0;
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.PLAYER, "worldbinder.config.map.players", config.mapShowPlayers, v -> config.mapShowPlayers = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.HOSTILE, "worldbinder.map.layer.hostile", config.mapShowHostile, v -> config.mapShowHostile = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.PASSIVE, "worldbinder.map.layer.passive", config.mapShowPassive, v -> config.mapShowPassive = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.ITEM, "worldbinder.map.layer.items", config.mapShowItems, v -> config.mapShowItems = v));
        addRenderableWidget(markerRow(rightCol, top + r++ * DROPDOWN_ROW, colW, MapMarkers.Kind.OTHER, "worldbinder.map.layer.other", config.mapShowOtherEntities, v -> config.mapShowOtherEntities = v));
        Button waypoints = toggleRow(rightCol, top + r++ * DROPDOWN_ROW, colW, "worldbinder.map.layer.waypoints", "worldbinder.map.layer.waypoints.tooltip",
                config.mapShowWaypoints, v -> config.mapShowWaypoints = v);
        int waypointCount = MapWaypoints.current().size();
        if (waypointCount > 0) WbButton.badge(waypoints, Integer.toString(waypointCount));
        addRenderableWidget(waypoints);
        addRenderableWidget(WbButton.icon(toggleRow(rightCol, top + r++ * DROPDOWN_ROW, colW, "worldbinder.map.layer.invisible", "worldbinder.map.layer.invisible.tooltip",
                config.mapShowInvisibleBlocks, v -> {
                    config.mapShowInvisibleBlocks = v;
                    config.save();
                    MapVoxels.get().clear();
                    MapExplorer.get().clear();
                    Map3DView.shared().invalidate();
                }), WbIcon.EYE));
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
        addRenderableWidget(WbButton.icon(WbButton.create(left, top + DROPDOWN_ROW * 3 + 6, w, 20, Lang.text("worldbinder.map.export.model"),
                Lang.text("worldbinder.map.export.model.tooltip"), b -> startExport3d(MapExporter3D.Mode.MODEL)), WbIcon.CUBE));
        addRenderableWidget(WbButton.icon(WbButton.create(left, top + DROPDOWN_ROW * 4 + 6, w, 20, Lang.text("worldbinder.map.export.render"),
                Lang.text("worldbinder.map.export.render.tooltip"), b -> startExport3d(MapExporter3D.Mode.RENDER)), WbIcon.MOUNTAIN));
        addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(left, top + DROPDOWN_ROW * 5 + 6, w, 20, Lang.text("worldbinder.map.export.folder"),
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

    // ------------------------------------------------------------------ 3D view

    private double base3dY = Double.NaN;
    private boolean rotating;
    private double renderResolution = 1.0D;
    private WorldBinderConfig.MapWaypoint hoveredWaypoint;
    private boolean rightClickShift;

    private static final long TRANSITION_NANOS = 700_000_000L;
    private long transitionStart;
    private boolean transitionToFlat;

    /** 0 = flat 2D map, 1 = full 3D view; animated while switching. */
    private double transition3d() {
        if (transitionStart == 0L) {
            return 1.0D;
        }
        double t = Math.min(1.0D, (System.nanoTime() - transitionStart) / (double) TRANSITION_NANOS);
        double eased = t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3) / 2.0D;
        if (t >= 1.0D) {
            transitionStart = 0L;
            if (transitionToFlat) {
                WorldBinder.config().map3d = false;
                WorldBinder.config().save();
                Map3DView.shared().reset();
                rebuild();
                return 0.0D;
            }
            return 1.0D;
        }
        return transitionToFlat ? 1.0D - eased : eased;
    }

    private boolean transitioning() {
        return transitionStart != 0L;
    }

    private MapRenderer3D.Camera camera3d() {
        WorldBinderConfig config = WorldBinder.config();
        if (Double.isNaN(base3dY)) {
            Minecraft mc = Minecraft.getInstance();
            base3dY = mc.player != null ? mc.player.getY() : 64.0D;
        }
        double t = transition3d();
        double yaw = config.map3dYaw > 180.0D ? config.map3dYaw - 360.0D : config.map3dYaw;
        yaw *= t;
        double pitch = 90.0D + (config.map3dPitch - 90.0D) * t;
        double height = config.map3dHeightPercent / 100.0D * (0.05D + 0.95D * t);
        return new MapRenderer3D.Camera(camX, camZ, base3dY, yaw, pitch, zoom, height);
    }

    private long pivotKey = Long.MIN_VALUE;
    private double pivotHeight = Double.NaN;

    /**
     * Keeps the rotation pivot at the typical terrain height around the view center (median of the surrounding
     * columns, so single towers or holes do not shift the whole landscape), smoothly.
     */
    private void updateBase3d() {
        int bx = (int) Math.floor(camX);
        int bz = (int) Math.floor(camZ);
        long key = (((long) (bx >> 3)) * 73856093L) ^ (((long) (bz >> 3)) * 19349663L) ^ (cachedRevision * 31L + cachedExplorerRevision);
        if (key != pivotKey) {
            pivotKey = key;
            int radius = (int) Math.max(24, Math.min(160, Math.min(mapW(), mapH()) / 3.0D / Math.max(0.25D, zoom)));
            int stride = Math.max(2, radius / 12);
            List<Integer> samples = new ArrayList<>();
            for (int z = bz - radius; z <= bz + radius; z += stride) {
                for (int x = bx - radius; x <= bx + radius; x += stride) {
                    ChunkSnapshot snapshot = lookup.apply(ChunkPos.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
                    if (snapshot == null || snapshot.heights == null || snapshot.heights.length != 256) continue;
                    int value = snapshot.heights[Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16)];
                    if (value != Integer.MIN_VALUE) samples.add(value);
                }
            }
            if (samples.isEmpty()) {
                pivotHeight = Double.NaN;
            } else {
                samples.sort(null);
                pivotHeight = samples.get(samples.size() / 2) + 1;
            }
        }
        double target = pivotHeight;
        if (Double.isNaN(target)) {
            Minecraft mc = Minecraft.getInstance();
            target = mc.player != null ? mc.player.getY() : Double.isNaN(base3dY) ? 64.0D : base3dY;
        }
        if (Double.isNaN(base3dY)) {
            base3dY = target;
        } else {
            base3dY += (target - base3dY) * 0.10D;
        }
        if (Math.abs(target - base3dY) < 0.05D) base3dY = target;
    }

    /** Draws the 3D view; returns false (drawing nothing) until the first frame is ready, so the 2D map shows meanwhile. */
    private boolean drawMap3d(GuiGraphicsExtractor context, int mouseX, int mouseY, int mx, int my, int mw, int mh) {
        WorldBinderConfig config = WorldBinder.config();
        updateBase3d();
        MapVoxels.get().demand();
        Map3DView view = Map3DView.shared();
        long dataSignature = cachedRevision * 31L + cachedExplorerRevision * 17L + BlockMapColors.revision() * 7L + MapVoxels.get().revision() * 13L;
        MapRenderer3D.Camera camera = camera3d();
        if (!view.hasFrame()) {
            view.draw(context, cachedSnapshots, camera, mx, my, mw, mh, renderResolution, dataSignature, config.mapHillshade, true);
            return false;
        }
        context.fillGradient(mx, my, mx + mw, my + mh, 0xFF22303F, 0xFF0B1016);
        context.enableScissor(mx, my, mx + mw, my + mh);
        try {
            view.draw(context, cachedSnapshots, camera, mx, my, mw, mh, renderResolution, dataSignature, config.mapHillshade, transitioning());
            hoverValid = false;
            if (insideMap(mouseX, mouseY)) {
                int[] hit = view.hit(mouseX - mx, mouseY - my, mw, mh);
                if (hit != null) {
                    hoverValid = true;
                    hoverBlockX = hit[0];
                    hoverBlockZ = hit[1];
                }
            }
            if (selectedChunk != Long.MIN_VALUE) {
                drawChunkOutline3d(context, view, chunkX(selectedChunk), chunkZ(selectedChunk), WbMotion.mix(WbTheme.ACCENT, 0xFFFFFFFF, WbMotion.pulse(1400L)));
            }
            if (hoverValid && !dragging && !rotating) {
                drawChunkOutline3d(context, view, Math.floorDiv(hoverBlockX, 16), Math.floorDiv(hoverBlockZ, 16), 0x90FFFFFF);
            }
            drawEntities3d(context, view, mouseX, mouseY, mx, my, mw, mh);
            drawWaypoints(context, mouseX, mouseY, true);
            drawLocalPlayer3d(context, view, mx, my, mw, mh);
            MapRenderer3D.Camera shown = view.shownCamera();
            drawCompass3d(context, mx + mw - 30, my + 8, shown == null ? config.map3dYaw : shown.yawDegrees());
            int pillY = my + 8;
            WbChrome.drawPill(context, font, mx + 8, pillY, Lang.string("worldbinder.map.3d.badge", Math.round(config.map3dYaw), Math.round(config.map3dPitch),
                    config.map3dHeightPercent), WbTheme.ACCENT_RIGHT, WbIcon.CUBE);
            pillY += 18;
            if (config.mapExploreMode) {
                WbChrome.drawPill(context, font, mx + 8, pillY, Lang.string("worldbinder.map.exploring", MapExplorer.get().size()), WbTheme.OK, WbIcon.GLOBE);
            }
            if (cachedSnapshots.isEmpty() && MapExplorer.get().size() == 0 && MapVoxels.get().size() == 0) {
                drawEmptyState(context, mx, my, mw, mh);
            }
        } finally {
            context.disableScissor();
        }
        WbChrome.roundedOutline(context, mx - 1, my - 1, mw + 2, mh + 2, WbTheme.withAlpha(WbTheme.ACCENT_RIGHT, 0x50));
        return true;
    }

    private void drawChunkOutline3d(GuiGraphicsExtractor context, Map3DView view, int cx, int cz, int color) {
        double y = heightAtBlock(cx * 16 + 8, cz * 16 + 8) + 1.0D;
        double[][] world = {{cx * 16.0, cz * 16.0}, {cx * 16.0 + 16, cz * 16.0}, {cx * 16.0 + 16, cz * 16.0 + 16}, {cx * 16.0, cz * 16.0 + 16}};
        double[][] corners = new double[4][];
        for (int i = 0; i < 4; i++) {
            corners[i] = view.project(world[i][0], y, world[i][1], mapW(), mapH());
            if (corners[i] == null) return;
        }
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % 4];
            drawLine(context, mapX() + a[0], mapY() + a[1], mapX() + b[0], mapY() + b[1], color);
        }
    }

    private void drawLine(GuiGraphicsExtractor context, double x0, double y0, double x1, double y1, int color) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        if (steps > 4000) return;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int x = (int) Math.round(x0 + (x1 - x0) * t);
            int y = (int) Math.round(y0 + (y1 - y0) * t);
            context.fill(x, y, x + 1, y + 1, color);
        }
    }

    private double heightAtBlock(int x, int z) {
        ChunkSnapshot snapshot = lookup.apply(ChunkPos.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (snapshot != null && snapshot.heights != null && snapshot.heights.length == 256) {
            int value = snapshot.heights[Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16)];
            if (value != Integer.MIN_VALUE) return value;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) return mc.player.getY() - 1.0D;
        return Double.isNaN(base3dY) ? 64.0D : base3dY - 1.0D;
    }

    private void drawEntities3d(GuiGraphicsExtractor context, Map3DView view, int mouseX, int mouseY, int mx, int my, int mw, int mh) {
        Minecraft mc = Minecraft.getInstance();
        hoveredEntity = null;
        nearbyEntities.clear();
        if (mc.level == null) {
            return;
        }
        WorldBinderConfig config = WorldBinder.config();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double best = 25.0D;
        int radius = zoom >= 4.0D ? 3 : 2;
        MapRenderer3D.Camera shown = view.shownCamera();
        float viewYaw = shown == null ? 0.0F : (float) shown.yawDegrees();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            MapMarkers.Kind kind = MapMarkers.kind(entity);
            nearbyEntities.merge(kind, 1, Integer::sum);
            if (!MapMarkers.visible(kind, config)) continue;
            double ex = entity.xo + (entity.getX() - entity.xo) * partial;
            double ey = entity.yo + (entity.getY() - entity.yo) * partial;
            double ez = entity.zo + (entity.getZ() - entity.zo) * partial;
            double[] foot = view.project(ex, ey, ez, mw, mh);
            double[] body = view.project(ex, ey + entity.getBbHeight() * 0.5D, ez, mw, mh);
            if (foot == null || body == null) continue;
            int fx = (int) Math.round(mx + foot[0]);
            int fy = (int) Math.round(my + foot[1]);
            int bx = (int) Math.round(mx + body[0]);
            int by = (int) Math.round(my + body[1]);
            if (!insideMap(bx, by)) continue;
            boolean hidden = view.depthAt(foot[0], foot[1] - 1, mw, mh) < foot[2] - 0.6D;
            if (!hidden) {
                context.fill(fx - 2, fy, fx + 3, fy + 1, 0x60000000);
            }
            if (entity instanceof Player other) {
                MapMarkers.drawPlayerArrow(context, bx, by, other.getViewYRot(partial) - viewYaw, 0.7F,
                        hidden ? WbTheme.withAlpha(MapMarkers.Kind.PLAYER.color, 0x90) : MapMarkers.Kind.PLAYER.color, false);
                String name = other.getName().getString();
                int w = font.width(name) + 8;
                WbChrome.rounded(context, bx - w / 2, by - 20, w, 11, 0xC0000000);
                context.text(font, name, bx - font.width(name) / 2, by - 18, WbTheme.TEXT, false);
            } else if (hidden) {
                WbChrome.circleOutline(context, bx, by, radius, WbTheme.withAlpha(kind.color, 0xB0));
            } else {
                MapMarkers.drawEntity(context, bx, by, kind, radius);
            }
            double dx = bx - mouseX;
            double dy = by - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                hoveredEntity = entity;
            }
        }
    }

    /**
     * The local player stands on the terrain: a ground shadow and the navigation arrow lying flat on the ground
     * (squashed by the view tilt). When terrain covers the player, a dimmed outline and a short beacon show where.
     */
    private void drawLocalPlayer3d(GuiGraphicsExtractor context, Map3DView view, int mx, int my, int mw, int mh) {
        Minecraft mc = Minecraft.getInstance();
        MapRenderer3D.Camera shown = view.shownCamera();
        if (mc.player == null || shown == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = mc.player.xo + (mc.player.getX() - mc.player.xo) * partial;
        double py = mc.player.yo + (mc.player.getY() - mc.player.yo) * partial;
        double pz = mc.player.zo + (mc.player.getZ() - mc.player.zo) * partial;
        double[] foot = view.project(px, py, pz, mw, mh);
        if (foot == null) return;
        float fx = (float) (mx + foot[0]);
        float fy = (float) (my + foot[1]);
        boolean hidden = view.depthAt(foot[0], foot[1] - 1, mw, mh) < foot[2] - 0.6D;
        float tilt = (float) Math.max(0.35D, Math.sin(Math.toRadians(shown.pitchDegrees())));
        float yaw = mc.player.getViewYRot(partial) - (float) shown.yawDegrees();
        if (hidden) {
            int color = WbTheme.withAlpha(WbTheme.ACCENT, 0xA0);
            context.fill(Math.round(fx), Math.round(fy) - 14, Math.round(fx) + 1, Math.round(fy), color);
            WbChrome.circleOutline(context, Math.round(fx), Math.round(fy) - 17, 3, color);
        }
        context.pose().pushMatrix();
        context.pose().translate(fx, fy);
        context.pose().scale(1.0F, tilt);
        WbChrome.fillCircle(context, 1, 2, 8, 0x50000000);
        MapMarkers.drawPlayerArrow(context, 0.0F, 0.0F, yaw, 0.9F, hidden ? WbTheme.withAlpha(WbTheme.ACCENT, 0x90) : WbTheme.ACCENT, true);
        context.pose().popMatrix();
    }

    private void drawCompass3d(GuiGraphicsExtractor context, int x, int y, double yaw) {
        int size = 22;
        int cx = x + size / 2;
        int cy = y + size / 2;
        WbChrome.fillCircle(context, cx, cy, size / 2, 0xC0101720);
        WbChrome.circleOutline(context, cx, cy, size / 2, WbTheme.withAlpha(WbTheme.ACCENT, 0x90));
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(i * 90.0D - yaw);
            int lx = (int) Math.round(cx + Math.sin(angle) * 7.0D);
            int ly = (int) Math.round(cy - Math.cos(angle) * 7.0D);
            if (i == 0) {
                context.text(font, Component.literal("N").withStyle(ChatFormatting.BOLD), lx - 2, ly - 4, WbTheme.ERROR, false);
            } else {
                context.fill(lx - 1, ly - 1, lx + 1, ly + 1, WbTheme.TEXT_SOFT);
            }
        }
    }

    private void toggle3d() {
        WorldBinderConfig config = WorldBinder.config();
        boolean entering = !config.map3d || transitionToFlat && transitioning();
        boolean animate = !WbMotion.reduced();
        if (entering) {
            config.map3d = true;
            config.save();
            transitionToFlat = false;
            transitionStart = animate ? System.nanoTime() : 0L;
        } else if (animate) {
            transitionToFlat = true;
            transitionStart = System.nanoTime();
        } else {
            config.map3d = false;
            config.save();
            Map3DView.shared().reset();
        }
        Map3DView.shared().invalidate();
        WbSounds.toggle(entering);
        footer(Component.translatable(entering ? "worldbinder.map.3d.on" : "worldbinder.map.3d.off"), WbTheme.ACCENT_RIGHT);
        rebuild();
    }

    private void rotate3d(double degrees) {
        WorldBinderConfig config = WorldBinder.config();
        config.map3dYaw = (((config.map3dYaw + degrees) % 360.0D) + 360.0D) % 360.0D;
        WbSounds.sliderTick();
    }

    private void tilt3d(double degrees) {
        WorldBinderConfig config = WorldBinder.config();
        config.map3dPitch = Math.max(20.0D, Math.min(89.0D, config.map3dPitch + degrees));
        WbSounds.sliderTick();
    }

    private void cycle3dHeight() {
        WorldBinderConfig config = WorldBinder.config();
        int[] steps = {50, 100, 150, 200, 300};
        int next = steps[0];
        for (int step : steps) {
            if (step > config.map3dHeightPercent) {
                next = step;
                break;
            }
        }
        config.map3dHeightPercent = next;
        footer(Component.translatable("worldbinder.map.3d.height_set", next), WbTheme.ACCENT_RIGHT);
        WbSounds.click();
        rebuild();
    }

    private void reset3d() {
        WorldBinderConfig config = WorldBinder.config();
        config.map3dYaw = 45.0D;
        config.map3dPitch = 50.0D;
        config.map3dHeightPercent = 100;
        WbSounds.click();
        rebuild();
    }

    private void cycleBlockColors() {
        WorldBinderConfig config = WorldBinder.config();
        WorldBinderConfig.MapBlockColors[] values = WorldBinderConfig.MapBlockColors.values();
        config.mapBlockColors = values[(config.mapBlockColors.ordinal() + 1) % values.length];
        config.save();
        BlockMapColors.invalidate();
        // explored chunks are sampled again with the new colors; captured chunks keep their recorded colors
        MapExplorer.get().clear();
        lastSyncSignature = Long.MIN_VALUE;
        footer(Component.translatable("worldbinder.map.block_colors_set",
                Lang.string("worldbinder.map.block_colors." + config.mapBlockColors.name().toLowerCase(Locale.ROOT))), WbTheme.INFO);
        WbSounds.click();
        rebuild();
    }

    private void startExport3d(MapExporter3D.Mode mode) {
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
        MapRenderer3D.Camera camera = camera3d();
        MapExporter3D.export(cachedSnapshots, mode, camera, mapW(), mapH(), WorldBinder.config().mapHillshade).whenComplete((result, error) ->
                Minecraft.getInstance().execute(() -> {
                    exporting = false;
                    if (error == null) {
                        footer(Component.translatable(mode == MapExporter3D.Mode.MODEL ? "worldbinder.map.export.model_done" : "worldbinder.map.export.render_done",
                                result.chunks()), WbTheme.OK);
                        Chat.info(Lang.string("worldbinder.map.export.done_chat", result.path().toAbsolutePath()));
                        WbSounds.success();
                        net.worldbinder.util.PathOpener.open(result.path().getParent());
                    } else {
                        footer(Component.translatable("worldbinder.map.export.failed"), WbTheme.ERROR);
                        Chat.error(Lang.string("worldbinder.map.export.failed"));
                        WorldBinder.LOGGER.warn("Live map 3D export failed", error);
                        WbSounds.error();
                    }
                    if (minecraft != null && net.worldbinder.compat.ClientCompat.screen(minecraft) == this) {
                        rebuild();
                    }
                }));
    }

    // ------------------------------------------------------------------ waypoints

    private double[] waypointScreen(WorldBinderConfig.MapWaypoint point, boolean view3d) {
        if (view3d) {
            double[] p = Map3DView.shared().project(point.x + 0.5D, point.y, point.z + 0.5D, mapW(), mapH());
            return p == null ? null : new double[] {mapX() + p[0], mapY() + p[1]};
        }
        return new double[] {screenXOfBlock(point.x + 0.5D), screenYOfBlock(point.z + 0.5D)};
    }

    private void drawWaypoints(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean view3d) {
        hoveredWaypoint = null;
        if (!WorldBinder.config().mapShowWaypoints) {
            return;
        }
        double best = 64.0D;
        int left = mapX() + 8;
        int top = mapY() + 8;
        int right = mapX() + mapW() - 8;
        int bottom = mapY() + mapH() - 8;
        for (WorldBinderConfig.MapWaypoint point : MapWaypoints.current()) {
            double[] p = waypointScreen(point, view3d);
            if (p == null) continue;
            int x = (int) Math.round(p[0]);
            int y = (int) Math.round(p[1]);
            boolean inside = x >= left && x <= right && y >= top + 24 && y <= bottom;
            if (!inside) {
                // edge marker pointing towards the off-screen waypoint
                int ex = Math.max(left, Math.min(right, x));
                int ey = Math.max(top, Math.min(bottom, y));
                WbChrome.fillCircle(context, ex, ey, 4, 0xD0050709);
                WbChrome.fillCircle(context, ex, ey, 3, point.color);
                continue;
            }
            float pulse = point.death ? WbMotion.pulse(1600L) : 0.0F;
            int stem = view3d ? 10 : 6;
            int headY = y - stem - 4;
            context.fill(x, y - stem, x + 1, y, 0xC0000000 | (point.color & 0xFFFFFF));
            context.fill(x - 2, y, x + 3, y + 1, 0x70000000);
            WbChrome.fillCircle(context, x, headY, 6, 0xE0050709);
            WbChrome.fillCircle(context, x, headY, 5, WbMotion.mix(point.color, 0xFFFFFFFF, pulse * 0.35F));
            (point.death ? WbIcon.CLOSE : WbIcon.STAR).draw(context, x - 3, headY - 3, 1, 0xFF0B1016);
            String label = MapWaypoints.label(point);
            if (zoom >= 0.75D || view3d) {
                int w = font.width(label) + 8;
                WbChrome.rounded(context, x - w / 2, headY - 18, w, 11, 0xC0000000);
                context.text(font, label, x - font.width(label) / 2, headY - 16, WbTheme.TEXT, false);
            }
            double dx = x - mouseX;
            double dy = headY - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                hoveredWaypoint = point;
            }
        }
        if (hoveredWaypoint != null) {
            hoveredEntity = null;
        }
    }

    private List<String> waypointDetails(WorldBinderConfig.MapWaypoint point) {
        List<String> details = new ArrayList<>();
        details.add(Lang.string("worldbinder.map.tooltip.block_line", point.x, point.y, point.z));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = Math.sqrt(mc.player.distanceToSqr(point.x + 0.5D, point.y, point.z + 0.5D));
            details.add(Lang.string("worldbinder.map.entity.distance", Math.round(distance)));
        }
        details.add("");
        details.add(Lang.string("worldbinder.map.waypoint.remove_hint"));
        return details;
    }

    private void addWaypointAtHover() {
        Minecraft mc = Minecraft.getInstance();
        int x;
        int z;
        if (hoverValid) {
            x = hoverBlockX;
            z = hoverBlockZ;
        } else if (mc.player != null) {
            x = mc.player.getBlockX();
            z = mc.player.getBlockZ();
        } else {
            return;
        }
        int y = (int) Math.floor(heightAtBlock(x, z)) + 1;
        WorldBinderConfig.MapWaypoint point = MapWaypoints.add(x, y, z);
        if (point == null) {
            footer(Component.translatable("worldbinder.map.waypoint.limit", MapWaypoints.MAX_PER_WORLD), WbTheme.WARN);
            WbSounds.warning();
            return;
        }
        WorldBinder.config().mapShowWaypoints = true;
        footer(Component.translatable("worldbinder.map.waypoint.added", point.name, x, y, z), WbTheme.OK);
        WbSounds.mapPing();
    }

    /** Right click on the map: removes a hovered waypoint, Shift places one, otherwise queues a chunk rescan. */
    private void rightClickMap(double vx, double vy, boolean shift) {
        if (hoveredWaypoint != null) {
            String label = MapWaypoints.label(hoveredWaypoint);
            MapWaypoints.remove(hoveredWaypoint);
            hoveredWaypoint = null;
            footer(Component.translatable("worldbinder.map.waypoint.removed", label), WbTheme.WARN);
            WbSounds.click();
            return;
        }
        if (shift) {
            addWaypointAtHover();
            return;
        }
        long key = chunkUnderCursor(vx, vy);
        if (key == Long.MIN_VALUE) {
            return;
        }
        selectedChunk = key;
        WorldBinderClient.capture().queueChunkForRescan(chunkX(key), chunkZ(key));
        footer(Component.translatable("worldbinder.map.rescan_queued", chunkX(key), chunkZ(key)), WbTheme.OK);
        WbSounds.mapPing();
        rebuild();
    }

    /** Chunk under a virtual mouse position (2D projection or 3D hit test), or {@link Long#MIN_VALUE}. */
    private long chunkUnderCursor(double vx, double vy) {
        if (WorldBinder.config().map3d) {
            int[] hit = Map3DView.shared().hit(vx - mapX(), vy - mapY(), mapW(), mapH());
            return hit == null ? Long.MIN_VALUE : ChunkPos.pack(Math.floorDiv(hit[0], 16), Math.floorDiv(hit[1], 16));
        }
        return ChunkPos.pack(Math.floorDiv((int) Math.floor(blockAtScreenX(vx)), 16), Math.floorDiv((int) Math.floor(blockAtScreenY(vy)), 16));
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
            renderResolution = Math.max(1.0D, Math.min(2.0D, uiScale.scale() * (minecraft == null ? 1 : minecraft.getWindow().getGuiScale())));
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
                if (hoveredWaypoint != null) {
                    hoverTitle = MapWaypoints.label(hoveredWaypoint);
                    hoverLines = waypointDetails(hoveredWaypoint);
                    hoverAccent = hoveredWaypoint.color;
                } else if (hoveredEntity != null) {
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
        if (dropdown == Dropdown.EXPORT) {
            int dividerY = b[1] + 5 + DROPDOWN_ROW * 3 + 1;
            context.fill(b[0] + 8, dividerY, b[0] + b[2] - 8, dividerY + 1, WbTheme.divider());
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
        if (config.map3d && drawMap3d(context, mouseX, mouseY, mx, my, mw, mh)) {
            return;
        }

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
            drawWaypoints(context, mouseX, mouseY, false);
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
            hx = WorldBinder.config().map3d
                    ? hint(context, hx, y, Lang.string("worldbinder.map.hint_rotate"), Lang.string("worldbinder.map.key_right_drag"))
                    : hint(context, hx, y, Lang.string("worldbinder.map.hint_rescan"), Lang.string("worldbinder.map.key_right_click"));
            hx = hint(context, hx - 10, y, Lang.string("worldbinder.map.hint_waypoint"), Lang.string("worldbinder.map.key_waypoint"));
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
                if (WorldBinder.config().map3d) {
                    zoomBy(verticalAmount > 0 ? 1 : -1, mapX() + mapW() / 2.0, mapY() + mapH() / 2.0);
                } else {
                    zoomBy(verticalAmount > 0 ? 1 : -1, vx, vy);
                }
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
                long key = chunkUnderCursor(vx, vy);
                if (virtualEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    if (WorldBinder.config().map3d) {
                        // right drag rotates and tilts; a plain right click acts on release
                        rotating = true;
                        dragDistance = 0;
                        rightClickShift = virtualEvent.hasShiftDown();
                        return true;
                    }
                    rightClickMap(vx, vy, virtualEvent.hasShiftDown());
                    return true;
                }
                if (virtualEvent.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
                    addWaypointAtHover();
                    return true;
                }
                if (virtualEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    dragging = true;
                    dragDistance = 0;
                    if (doubleClick && key != Long.MIN_VALUE) {
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
        if (rotating) {
            double dx = uiScale.toVirtualDelta(offsetX);
            double dy = uiScale.toVirtualDelta(offsetY);
            dragDistance += Math.abs(dx) + Math.abs(dy);
            if (dragDistance > 2.0D) {
                WorldBinderConfig config = WorldBinder.config();
                config.map3dYaw = (((config.map3dYaw + dx * 0.5D) % 360.0D) + 360.0D) % 360.0D;
                config.map3dPitch = Math.max(20.0D, Math.min(89.0D, config.map3dPitch - dy * 0.35D));
            }
            return true;
        }
        if (dragging) {
            double dx = uiScale.toVirtualDelta(offsetX);
            double dy = uiScale.toVirtualDelta(offsetY);
            dragDistance += Math.abs(dx) + Math.abs(dy);
            if (dragDistance > 2.0D) {
                if (followPlayer) {
                    followPlayer = false;
                    rebuild();
                }
                if (WorldBinder.config().map3d) {
                    double[] moved = MapRenderer3D.panTarget(new MapRenderer3D.Camera(targetCamX, targetCamZ, 0, WorldBinder.config().map3dYaw,
                            WorldBinder.config().map3dPitch, zoom, 1.0D), dx, dy);
                    targetCamX = moved[0];
                    targetCamZ = moved[1];
                } else {
                    targetCamX -= dx / zoom;
                    targetCamZ -= dy / zoom;
                }
            }
            return true;
        }
        return super.mouseDragged(WbLayout.virtualMouseEvent(event, width, height), uiScale.toVirtualDelta(offsetX), uiScale.toVirtualDelta(offsetY));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = WbLayout.virtualMouseEvent(event, width, height);
        if (rotating) {
            rotating = false;
            WorldBinder.config().save();
            if (dragDistance <= 2.0D) {
                int realWidth = width;
                int realHeight = height;
                WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
                width = uiScale.virtualWidth();
                height = uiScale.virtualHeight();
                try {
                    rightClickMap(virtualEvent.x(), virtualEvent.y(), rightClickShift);
                } finally {
                    width = realWidth;
                    height = realHeight;
                }
            }
            return true;
        }
        if (dragging) {
            dragging = false;
            if (dragDistance <= 2.0D) {
                int realWidth = width;
                int realHeight = height;
                WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
                width = uiScale.virtualWidth();
                height = uiScale.virtualHeight();
                try {
                    long key = chunkUnderCursor(virtualEvent.x(), virtualEvent.y());
                    if (key != Long.MIN_VALUE) {
                        selectedChunk = selectedChunk == key ? Long.MIN_VALUE : key;
                    }
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
        if (key == InputConstants.KEY_V) { toggle3d(); return true; }
        if (key == InputConstants.KEY_P) { addWaypointAtHover(); return true; }
        if (WorldBinder.config().map3d) {
            if (key == InputConstants.KEY_COMMA) { rotate3d(-15.0D); return true; }
            if (key == InputConstants.KEY_PERIOD) { rotate3d(15.0D); return true; }
            if (key == InputConstants.KEY_PAGEUP) { tilt3d(5.0D); return true; }
            if (key == InputConstants.KEY_PAGEDOWN) { tilt3d(-5.0D); return true; }
        }
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
        if (WorldBinder.config().map3d) {
            double yaw = Math.toRadians(WorldBinder.config().map3dYaw);
            targetCamX += dx * Math.cos(yaw) - dz * Math.sin(yaw);
            targetCamZ += dx * Math.sin(yaw) + dz * Math.cos(yaw);
            return;
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

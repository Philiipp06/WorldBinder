package net.worldbinder.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.GameRuleSettings;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.input.MovementKeys;
import net.worldbinder.movement.MovementProfile;
import net.worldbinder.movement.MovementSettings;
import net.worldbinder.movement.MovementTool;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbIntegerSlider;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbSounds;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.Lang;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorldBinderConfigScreen extends WorldBinderConfigScreenView {
    private static final String SAFE_RULES = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;"
            + "doInsomnia=false;doPatrolSpawning=false;doTraderSpawning=false;doWardenSpawning=false;"
            + "doFireTick=false;mobGriefing=false;disableRaids=true;keepInventory=true;randomTickSpeed=0";
    private static final String SHOWCASE_RULES = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;"
            + "doInsomnia=false;doPatrolSpawning=false;doTraderSpawning=false;doWardenSpawning=false;"
            + "doFireTick=false;mobGriefing=false;doTileDrops=false;doEntityDrops=false;randomTickSpeed=0";
    private static final long CONFIRM_WINDOW_MILLIS = 4000L;
    private static int rememberedMovementGroup;

    private long discardArmedMillis;
    private long resetArmedMillis;

    public WorldBinderConfigScreen(Screen parent) {
        this(parent, Tab.GENERAL, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen performance(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.PERFORMANCE, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen movement(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.MOVEMENT, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen general(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.GENERAL, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen hud(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.HUD, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen display(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.DISPLAY, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen safety(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen safetyExport(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.EXPORT, 0, copyConfig(WorldBinder.config()));
    }

    private WorldBinderConfigScreen(Screen parent, Tab tab, SafetyPage safetyPage, int scrollOffset, WorldBinderConfig draft) {
        super(parent, tab, safetyPage, scrollOffset,
                draft == null ? copyConfig(WorldBinder.config()) : draft);
        movementGroup = Math.max(0, Math.min(MOVEMENT_OPTIONS_GROUP, rememberedMovementGroup));
    }

    private static WorldBinderConfig copyConfig(WorldBinderConfig source) {
        WorldBinderConfig copy = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(source), WorldBinderConfig.class);
        return copy == null ? new WorldBinderConfig() : copy;
    }

    @Override
    protected boolean computeDirty() {
        try {
            return gameRules.isDirty() || !WorldBinder.GSON.toJson(draft).equals(WorldBinder.GSON.toJson(WorldBinder.config()));
        } catch (RuntimeException exception) {
            return false;
        }
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

    private void initScaled() {
        WorldBinderConfig config = draft;
        gameRules.load(config.gameRulesOverride);
        decorations.clear();

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);

        int contentX = contentAreaX(left, panelWidth);
        int contentBaseY = contentBaseY(top, panelWidth);
        int contentW = contentAreaWidth(panelWidth);
        int formX = contentX + 12;
        int formW = Math.max(MIN_CONTENT_WIDTH - 24, contentW - 24);
        int contentBottom = top + panelHeight - 48;
        maxScroll = Math.max(0, contentHeight(formW) - Math.max(80, contentBottom - contentBaseY));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int contentY = contentBaseY - scrollOffset;
        if (tab == Tab.GENERAL) initGeneral(config, formX, contentY, formW);
        if (tab == Tab.PERFORMANCE) initPerformance(config, formX, contentY, formW);
        if (tab == Tab.HUD) initHud(config, formX, contentY, formW);
        if (tab == Tab.SAFETY) initSafety(config, formX, contentY, formW);
        if (tab == Tab.MOVEMENT) initMovement(config, formX, contentY, formW);
        if (tab == Tab.DISPLAY) initDisplay(config, formX, contentY, formW);

        addFooter(left, top, panelWidth, panelHeight);
        addSettingsNavigation(left, top, panelWidth);
    }

    private void addFooter(int left, int top, int panelWidth, int panelHeight) {
        int buttonW = footerButtonWidth();
        int bottomY = top + panelHeight - 32;
        Button save = WbButton.icon(WbButton.primary(button(left + panelWidth - buttonW * 2 - 26, bottomY, buttonW, 22,
                "worldbinder.config.save", "worldbinder.tooltip.config.save", button -> {
                    if (saveConfig()) {
                        WbSounds.success();
                        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
                    }
                })), WbIcon.SAVE);
        WbTooltips.hint(save, Component.literal("Ctrl + S"));
        addRenderableWidget(save);
        boolean discardArmed = System.currentTimeMillis() - discardArmedMillis < CONFIRM_WINDOW_MILLIS;
        Button cancel = button(left + panelWidth - buttonW - 16, bottomY, buttonW, 22,
                discardArmed ? "worldbinder.config.discard_confirm" : "worldbinder.config.cancel",
                "worldbinder.tooltip.config.back", button -> requestClose());
        addRenderableWidget(discardArmed ? WbButton.danger(cancel) : WbButton.icon(WbButton.quiet(cancel), WbIcon.CLOSE));
        WbTooltips.hint(cancel, Component.literal("Esc"));

        boolean resetArmed = System.currentTimeMillis() - resetArmedMillis < CONFIRM_WINDOW_MILLIS;
        int resetW = footerResetWidth();
        Button reset = button(footerButtonsLeft(left, panelWidth), bottomY, resetW, 22,
                resetArmed ? "worldbinder.config.reset_confirm" : "worldbinder.config.reset_tab",
                "worldbinder.tooltip.config.reset_tab", button -> resetTab());
        addRenderableWidget(resetArmed ? WbButton.danger(reset) : WbButton.icon(WbButton.quiet(reset), WbIcon.RESET));
    }

    private void addSettingsNavigation(int left, int top, int panelWidth) {
        if (useSidebar(panelWidth)) {
            int tabX = left + 14;
            int tabY = top + (panelHeight() < 390 ? 62 : 64);
            int available = panelHeight() - (panelHeight() >= 390 ? 140 : 110);
            int step = Math.max(22, Math.min(30, available / Tab.values().length));
            int tabH = Math.min(24, step - 4);
            int tabW = settingsSidebarWidth(panelWidth) - 26;
            int index = 0;
            for (Tab target : Tab.values()) {
                Button navigation = WbButton.create(tabX, tabY + index * step, tabW, tabH,
                        Component.translatable(tabLabelKey(target)),
                        Component.translatable(tabDescriptionKey(target)),
                        button -> openSection(target, target == Tab.SAFETY ? safetyPage : SafetyPage.CORE));
                WbButton.icon(WbButton.tab(navigation, tabAccent(target), tab == target), tabIcon(target));
                if (target == Tab.MOVEMENT && draft.movement != null && draft.movement.enabledCount() > 0) {
                    WbButton.badge(navigation, Integer.toString(draft.movement.enabledCount()));
                }
                WbTooltips.hint(navigation, Component.literal("Ctrl + Tab"));
                addRenderableWidget(navigation);
                index++;
            }
            if (tab == Tab.SAFETY) {
                addSafetyNavigation(top, panelWidth);
            } else if (tab == Tab.MOVEMENT) {
                addMovementNavigation(top, panelWidth);
            }
            return;
        }

        int y = top + (compact() ? 42 : 52);
        int cols = navColumns(panelWidth);
        int gap = 6;
        int tabW = Math.max(72, (panelWidth - 36 - gap * (cols - 1)) / cols);
        int tabX = left + 18;
        int index = 0;
        for (Tab target : Tab.values()) {
            int col = index % cols;
            int row = index / cols;
            Button navigation = WbButton.create(tabX + col * (tabW + gap), y + row * 26, tabW, 22, Component.translatable(tabLabelKey(target)),
                    Component.translatable(tabDescriptionKey(target)),
                    button -> openSection(target, target == Tab.SAFETY ? safetyPage : SafetyPage.CORE));
            addRenderableWidget(WbButton.icon(WbButton.tab(navigation, tabAccent(target), tab == target), tabIcon(target)));
            index++;
        }
        if (!hasSubNavigation()) {
            return;
        }
        int subY = y + navRows(panelWidth) * 26 + 6;
        if (tab == Tab.MOVEMENT) {
            int groups = MOVEMENT_OPTIONS_GROUP + 1;
            int subCols = panelWidth < 430 ? 3 : groups;
            int buttonW = (panelWidth - 36 - gap * (subCols - 1)) / subCols;
            for (int i = 0; i < groups; i++) {
                addMovementGroupButton(tabX + (i % subCols) * (buttonW + gap), subY + (i / subCols) * 24, buttonW, i);
            }
            return;
        }
        int safetyCols = panelWidth < 430 ? 2 : 4;
        int safetyW = Math.max(90, (panelWidth - 36 - gap * (safetyCols - 1)) / safetyCols);
        int safetyIndex = 0;
        for (SafetyPage target : SafetyPage.values()) {
            int col = safetyIndex % safetyCols;
            int row = safetyIndex / safetyCols;
            Button navigation = WbButton.create(tabX + col * (safetyW + gap), subY + row * 24, safetyW, 20, Component.translatable(safetyLabelKey(target)),
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(safetyLabelKey(target))),
                    button -> openSection(Tab.SAFETY, target));
            addRenderableWidget(WbButton.icon(WbButton.tab(navigation, WbTheme.WARN, safetyPage == target), safetyIcon(target)));
            safetyIndex++;
        }
    }

    private void addSafetyNavigation(int top, int panelWidth) {
        int x = contentAreaX(WbLayout.left(width, panelWidth), panelWidth);
        int contentWidth = contentAreaWidth(panelWidth);
        int gap = 6;
        int cols = contentWidth < 400 ? 2 : SafetyPage.values().length;
        int buttonW = (contentWidth - gap * (cols - 1)) / cols;
        int index = 0;
        for (SafetyPage target : SafetyPage.values()) {
            Button navigation = WbButton.create(
                    x + (index % cols) * (buttonW + gap),
                    top + 59 + (index / cols) * 26,
                    buttonW,
                    22,
                    Component.translatable(safetyLabelKey(target)),
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(safetyLabelKey(target))),
                    button -> openSection(Tab.SAFETY, target)
            );
            addRenderableWidget(WbButton.icon(WbButton.tab(navigation, WbTheme.WARN, safetyPage == target), safetyIcon(target)));
            index++;
        }
    }

    private void addMovementNavigation(int top, int panelWidth) {
        int x = contentAreaX(WbLayout.left(width, panelWidth), panelWidth);
        int contentWidth = contentAreaWidth(panelWidth);
        int groups = MOVEMENT_OPTIONS_GROUP + 1;
        int cols = contentWidth < 400 ? 3 : groups;
        int gap = 6;
        int buttonW = (contentWidth - gap * (cols - 1)) / cols;
        for (int index = 0; index < groups; index++) {
            addMovementGroupButton(x + (index % cols) * (buttonW + gap),
                    top + 59 + (index / cols) * 26, buttonW, index);
        }
    }

    private void addMovementGroupButton(int x, int y, int width, int index) {
        boolean options = index >= MOVEMENT_OPTIONS_GROUP;
        String key = options ? "worldbinder.config.movement.group.options" : MovementTool.Category.values()[index].titleKey;
        Button navigation = WbButton.create(x, y, width, 22, Component.translatable(key),
                Component.translatable(options ? "worldbinder.config.movement.group.options.tooltip" : key + ".tooltip"), button -> {
                    if (applyFieldsToDraft()) {
                        movementGroup = index;
                        rememberedMovementGroup = index;
                        scrollOffset = 0;
                        pageChangedMillis = System.currentTimeMillis();
                        rebuildConfigWidgets();
                    }
                });
        int accent = options ? WbTheme.TEXT_MUTED : categoryAccent(MovementTool.Category.values()[index]);
        WbButton.icon(WbButton.tab(navigation, accent, movementGroup == index), categoryIcon(index));
        if (!options && width >= 120) {
            int enabled = 0;
            int total = 0;
            for (MovementTool tool : MovementTool.Category.values()[index].tools()) {
                total++;
                if (draft.movement.enabled(tool)) enabled++;
            }
            WbButton.badge(navigation, enabled + "/" + total);
        }
        addRenderableWidget(navigation);
    }

    private void openSection(Tab targetTab, SafetyPage targetPage) {
        if (!applyFieldsToDraft()) {
            return;
        }
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderConfigScreen(parent, targetTab, targetPage, 0, draft));
    }

    // ------------------------------------------------------------------ General

    private void initGeneral(WorldBinderConfig config, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        defaultName = field(x, y + 48, fullW, config.defaultArchiveName, 64, "worldbinder.tooltip.archive_name"); addContentWidget(defaultName);

        int targetW = Math.min(fullW, Math.max(180, w / 2));
        Button targetDropdown = WbButton.create(x, y + 100, targetW, 23,
                Component.translatable("worldbinder.config.target_dropdown_value", config.targetMinecraftVersion),
                Component.translatable("worldbinder.tooltip.target_version"), b -> {
                    if (!applyFieldsToDraft()) {
                        return;
                    }
                    net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderVersionPickerScreen(this,
                            config.targetMinecraftVersion, selected -> config.targetMinecraftVersion = selected));
                });
        addContentWidget(WbButton.icon(WbButton.dropdown(targetDropdown, WbTheme.ACCENT, false), WbIcon.GLOBE));

        int numericGap = 10;
        int numericW = (fullW - numericGap * 2) / 3;
        int numericY = y + 164;
        radiusChunks = field(x, numericY, numericW, Integer.toString(config.roamingRadiusChunks), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radius"); addContentWidget(radiusChunks);
        minY = field(x + numericW + numericGap, numericY, numericW, Integer.toString(config.captureMinY), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.y_min"); addContentWidget(minY);
        maxY = field(x + (numericW + numericGap) * 2, numericY, numericW, Integer.toString(config.captureMaxY), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.y_max"); addContentWidget(maxY);

        int gridY = y + 218;
        addToggleGrid(x, gridY, w, 2,
                toggleSpec("worldbinder.config.capture_entities", "worldbinder.tooltip.config.entities", () -> config.captureEntities, v -> config.captureEntities = v),
                toggleSpec("worldbinder.config.capture_block_entities", "worldbinder.tooltip.config.blockentities", () -> config.captureBlockEntities, v -> config.captureBlockEntities = v),
                toggleSpec("worldbinder.config.capture_air", "worldbinder.tooltip.config.air", () -> config.captureAir, v -> config.captureAir = v),
                toggleSpec("worldbinder.config.server_resource_pack", "worldbinder.tooltip.config.server_resource_pack", () -> config.includeServerResourcePack, v -> config.includeServerResourcePack = v),
                toggleSpec("worldbinder.config.gamerules", "worldbinder.tooltip.config.gamerules", () -> config.exportGameRules, v -> config.exportGameRules = v),
                toggleSpec("worldbinder.config.append_timestamp", "worldbinder.tooltip.config.append_timestamp", () -> config.appendTimestampToArchiveName, v -> config.appendTimestampToArchiveName = v));
    }

    // ------------------------------------------------------------------ Performance

    private void initPerformance(WorldBinderConfig config, int x, int y, int w) {
        int cols = w < 420 ? 2 : 4;
        int gap = 8;
        int presetW = Math.max(64, (w - gap * (cols - 1)) / cols);
        int presetY = y + 28;
        addContentWidget(preset(x, presetY, presetW, "worldbinder.gui.preset_safe", WorldBinderConfig.PerformancePreset.SAFE, "worldbinder.tooltip.preset_safe", WbIcon.SHIELD));
        addContentWidget(preset(x + (presetW + gap), presetY, presetW, "worldbinder.gui.preset_balanced", WorldBinderConfig.PerformancePreset.BALANCED, "worldbinder.tooltip.preset_balanced", WbIcon.STAR));
        addContentWidget(preset(x + (presetW + gap) * (cols == 2 ? 0 : 2), presetY + (cols == 2 ? 30 : 0), presetW, "worldbinder.gui.preset_fast", WorldBinderConfig.PerformancePreset.FAST, "worldbinder.tooltip.preset_fast", WbIcon.SPEED));
        addContentWidget(preset(x + (presetW + gap) * (cols == 2 ? 1 : 3), presetY + (cols == 2 ? 30 : 0), presetW, "worldbinder.gui.preset_extreme", WorldBinderConfig.PerformancePreset.EXTREME, "worldbinder.tooltip.preset_extreme", WbIcon.PERFORMANCE));

        int formY = y + (cols == 2 ? 96 : 78);
        int fieldGap = 10;
        int fieldW = (w - fieldGap * (cols - 1)) / cols;
        String[] tooltips = {
                "worldbinder.tooltip.config.blocks_per_tick", "worldbinder.tooltip.config.commands_per_tick",
                "worldbinder.tooltip.config.ms_budget", "worldbinder.tooltip.config.target_fps",
                "worldbinder.tooltip.config.new_chunks", "worldbinder.tooltip.config.queue_limit",
                "worldbinder.tooltip.config.hot_chunks", "worldbinder.tooltip.config.capture_ms",
                "worldbinder.tooltip.config.ui_ms", "worldbinder.tooltip.config.archive_ms"
        };
        int[] values = {
                config.blocksPerTick, config.commandsPerTick, config.tickBudgetMillis, config.targetFps,
                config.newChunksPerTick, config.chunkQueueLimit, config.hotChunksPerTick,
                config.maxCaptureWorkMs, config.maxUiWorkMs, config.maxArchiveWorkMs
        };
        EditBox[] fields = new EditBox[values.length];
        for (int i = 0; i < values.length; i++) {
            int fieldX = x + (i % cols) * (fieldW + fieldGap);
            int fieldY = formY + (i / cols) * 52 + 18;
            fields[i] = field(fieldX, fieldY, fieldW, Integer.toString(values[i]), NUMERIC_TEXT_MAX_LENGTH, tooltips[i]);
            addContentWidget(fields[i]);
        }
        blocksPerTick = fields[0]; commandsPerTick = fields[1]; budgetMs = fields[2]; targetFps = fields[3];
        newChunks = fields[4]; queueLimit = fields[5]; hotChunks = fields[6];
        maxCaptureWorkMs = fields[7]; maxUiWorkMs = fields[8]; maxArchiveWorkMs = fields[9];
    }

    // ------------------------------------------------------------------ HUD & Map

    private void initHud(WorldBinderConfig config, int x, int y, int w) {
        int editorW = Math.min(w, Math.max(220, w / 2));
        addContentWidget(WbButton.icon(WbButton.primary(button(x, y + 48, editorW, 24,
                "worldbinder.config.widget_editor", "worldbinder.tooltip.config.widget_editor",
                button -> {
                    if (applyFieldsToDraft()) {
                        net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderWidgetEditorScreen(this, draft));
                    }
                })), WbIcon.HUD));

        int gap = 12;
        int cols = w < 430 ? 2 : 4;
        int fieldW = Math.max(70, Math.min(112, (w - gap * (cols - 1)) / cols));
        int radarFieldY = y + 132;
        radarSize = field(x, radarFieldY, fieldW, Integer.toString(config.chunkRadarSize), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_size"); addContentWidget(radarSize);
        radarCell = field(x + fieldW + gap, radarFieldY, fieldW, Integer.toString(config.chunkRadarCellSize), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_cell"); addContentWidget(radarCell);
        int secondRowY = cols == 2 ? radarFieldY + 52 : radarFieldY;
        int secondRowX = cols == 2 ? x : x + (fieldW + gap) * 2;
        radarMaxChunks = field(secondRowX, secondRowY, fieldW, Integer.toString(config.radarMaxRenderedChunks), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_limit"); addContentWidget(radarMaxChunks);
        radarUpdateRate = field(secondRowX + fieldW + gap, secondRowY, fieldW, Integer.toString(config.radarUpdateRate), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_update_rate"); addContentWidget(radarUpdateRate);

        int mapY = y + (cols == 2 ? 244 : 196);
        int gridCols = Math.min(3, w < 300 ? 1 : w < 430 ? 2 : 3);
        int buttonRows = addButtonGrid(x, mapY + 30, w, 3,
                modeButton(0, 0, 100, "worldbinder.config.f10_view", () -> config.f10MapLayerMode, v -> config.f10MapLayerMode = v),
                modeButton(0, 0, 100, "worldbinder.config.radar_view", () -> config.radarLayerMode, v -> config.radarLayerMode = v),
                radarDetailButton(0, 0, 100, "worldbinder.config.radar_detail", () -> config.radarDetailMode, v -> config.radarDetailMode = v),
                toggle(0, 0, 100, 22, "worldbinder.config.markers", "worldbinder.tooltip.config.markers", () -> config.showWorldGizmos, v -> config.showWorldGizmos = v));
        addToggleGrid(x, mapY + 30 + buttonRows * 30, w, gridCols,
                toggleSpec("worldbinder.config.map.hillshade", "worldbinder.tooltip.config.map.hillshade", () -> config.mapHillshade, v -> config.mapHillshade = v),
                toggleSpec("worldbinder.config.map.grid", "worldbinder.tooltip.config.map.grid", () -> config.mapShowGrid, v -> config.mapShowGrid = v),
                toggleSpec("worldbinder.config.map.status", "worldbinder.tooltip.config.map.status", () -> config.mapShowStatusOverlay, v -> config.mapShowStatusOverlay = v),
                toggleSpec("worldbinder.config.map.players", "worldbinder.tooltip.config.map.players", () -> config.mapShowPlayers, v -> config.mapShowPlayers = v),
                toggleSpec("worldbinder.config.map.sidebar", "worldbinder.tooltip.config.map.sidebar", () -> config.mapShowSidebar, v -> config.mapShowSidebar = v),
                toggleSpec("worldbinder.config.map.smooth", "worldbinder.tooltip.config.map.smooth", () -> config.mapSmoothCamera, v -> config.mapSmoothCamera = v));

        int notificationY = mapY + hudMapSectionHeight(w);
        notificationDuration = field(x, notificationY + 32, fieldW, Integer.toString(config.notificationDurationPercent), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.notification_duration");
        addContentWidget(notificationDuration);
        int modeY = notificationY + 64;
        int modeW = Math.max(60, (w - 6) / 2);
        int modeIndex = 0;
        WbIcon[] modeIcons = {WbIcon.INFO, WbIcon.HUD, WbIcon.STAR, WbIcon.CLOSE};
        for (WorldBinderConfig.MessageMode mode : WorldBinderConfig.MessageMode.values()) {
            Button choice = WbButton.create(x + (modeIndex % 2) * (modeW + 6), modeY + (modeIndex / 2) * 26,
                    modeW, 22, Component.translatable("worldbinder.messages." + mode.name().toLowerCase(Locale.ROOT)),
                    Component.translatable("worldbinder.messages.tooltip"), b -> {
                        if (applyFieldsToDraft()) {
                            config.messageMode = mode;
                            rebuildConfigWidgets();
                        }
                    });
            addContentWidget(WbButton.icon(WbButton.tab(choice, WbTheme.INFO, config.effectiveMessageMode() == mode), modeIcons[modeIndex % modeIcons.length]));
            modeIndex++;
        }
    }

    // ------------------------------------------------------------------ Movement

    private int movementCardColumns(int w) {
        return w >= 520 ? 2 : 1;
    }

    private int movementGroupHeight(int w) {
        if (movementGroup >= MOVEMENT_OPTIONS_GROUP) {
            return 404;
        }
        List<MovementTool> tools = MovementTool.Category.values()[movementGroup].tools();
        int cols = movementCardColumns(w);
        int height = movementCardsTop(w);
        for (int i = 0; i < tools.size(); i += cols) {
            height += movementRowHeight(tools, i, cols) + 8;
        }
        return height + 8;
    }

    private static int movementCardsTop(int w) {
        return w >= 440 ? 112 : 162;
    }

    private static int movementRowHeight(List<MovementTool> tools, int start, int cols) {
        boolean adjustable = false;
        for (int i = start; i < Math.min(tools.size(), start + cols); i++) {
            adjustable |= tools.get(i).adjustable();
        }
        return adjustable ? 76 : 56;
    }

    private void initMovement(WorldBinderConfig config, int x, int y, int w) {
        if (config.movement == null) config.movement = new MovementSettings();
        if (movementGroup >= MOVEMENT_OPTIONS_GROUP) {
            initMovementOptions(config, x, y, w);
            return;
        }
        boolean wideHeader = w >= 440;
        section(x - 8, y + 22, w + 16, wideHeader ? 82 : 132, WbTheme.OK, WbIcon.POWER, "worldbinder.config.movement.quick",
                wideHeader ? "worldbinder.config.movement.quick.sub" : null);
        int masterW = wideHeader ? Math.max(150, Math.min(260, w / 2 - 4)) : w;
        addContentWidget(toggle(x, y + 44, masterW, 22, "worldbinder.config.movement.master", "worldbinder.tooltip.config.movement.master",
                () -> config.movementMasterEnabled, v -> config.movementMasterEnabled = v));
        int smallW = wideHeader ? Math.max(80, Math.min(120, (w - masterW - 16) / 2)) : (w - 6) / 2;
        int smallY = wideHeader ? y + 44 : y + 70;
        addContentWidget(WbButton.icon(WbButton.danger(button(x + w - smallW, smallY, smallW, 22, "worldbinder.config.movement.all_off",
                "worldbinder.tooltip.config.movement.all_off", b -> {
                    config.movement.disableAll();
                    rebuildConfigWidgets();
                })), WbIcon.STOP));
        addContentWidget(WbButton.icon(WbButton.quiet(button(x + w - smallW * 2 - 6, smallY, smallW, 22, "worldbinder.config.movement.reset_values",
                "worldbinder.tooltip.config.movement.reset_values", b -> {
                    config.movement.resetValues();
                    rebuildConfigWidgets();
                })), WbIcon.RESET));
        MovementProfile[] profiles = MovementProfile.values();
        int profileGap = 6;
        int profileCols = wideHeader ? profiles.length : 3;
        int profileW = (w - profileGap * (profileCols - 1)) / profileCols;
        int profileY = wideHeader ? y + 74 : y + 98;
        WbIcon[] profileIcons = {WbIcon.GLOBE, WbIcon.CHEST, WbIcon.PARKOUR, WbIcon.JESUS, WbIcon.ELYTRA_BOOST};
        for (int i = 0; i < profiles.length; i++) {
            MovementProfile profile = profiles[i];
            Button button = WbButton.create(x + (i % profileCols) * (profileW + profileGap), profileY + (i / profileCols) * 24, profileW, 20, Component.translatable(profile.key),
                    Component.translatable(profile.key + ".tooltip"), b -> {
                        profile.applyTo(config.movement);
                        config.movementMasterEnabled = true;
                        statusMessage = Component.translatable("worldbinder.config.movement.profile_applied", Component.translatable(profile.key));
                        statusMessageMillis = System.currentTimeMillis();
                        rebuildConfigWidgets();
                    });
            addContentWidget(WbButton.icon(button, profileIcons[i % profileIcons.length]));
        }

        List<MovementTool> tools = MovementTool.Category.values()[movementGroup].tools();
        int cols = movementCardColumns(w);
        int gap = 8;
        int cardW = (w + 16 - gap * (cols - 1)) / cols;
        int rowY = y + movementCardsTop(w);
        for (int i = 0; i < tools.size(); i += cols) {
            int rowH = movementRowHeight(tools, i, cols);
            for (int c = 0; c < cols && i + c < tools.size(); c++) {
                MovementTool tool = tools.get(i + c);
                int cardX = x - 8 + c * (cardW + gap);
                toolCard(cardX, rowY, cardW, rowH, tool);
                int hitH = tool.adjustable() ? 40 : rowH;
                Button toggle = toggle(cardX, rowY, cardW, hitH, tool.key(), tool.tooltipKey(),
                        () -> config.movement.enabled(tool), enabled -> config.movement.enabled(tool, enabled));
                WbButton.ghost(toggle);
                WbButton.style(toggle, WbButton.Style.TOGGLE, categoryAccent(tool.category), config.movement.enabled(tool));
                WbTooltips.register(toggle, Component.translatable(tool.key()), Component.translatable(tool.tooltipKey()));
                String bound = MovementKeys.boundKey(tool);
                WbTooltips.hint(toggle, bound == null ? Component.translatable("worldbinder.tooltip.hint.movement_key")
                        : Component.translatable("worldbinder.tooltip.hint.movement_bound", bound));
                addContentWidget(toggle);
                if (tool.adjustable()) {
                    WbIntegerSlider slider = new WbIntegerSlider(font, cardX + 10, rowY + 46, cardW - 20, 22,
                            (int) Math.round(tool.min * 10), (int) Math.round(tool.max * 10), (int) Math.round(config.movement.value(tool) * 10),
                            categoryAccent(tool.category), value -> config.movement.value(tool, value / 10.0),
                            value -> Component.translatable(tool.unit.key, formatToolValue(tool, value / 10.0)));
                    addContentWidget(WbTooltips.register(slider, Component.translatable(tool.key()), Component.translatable(tool.tooltipKey())));
                }
            }
            rowY += rowH + gap;
        }
    }

    private static String formatToolValue(MovementTool tool, double value) {
        if (tool.unit == MovementTool.Unit.GRAVITY) {
            return Math.round(value * 100) + "%";
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void initMovementOptions(WorldBinderConfig config, int x, int y, int w) {
        int cols = w < 430 ? 1 : 2;
        int sectionX = x - 8;
        int sectionW = w + 16;
        section(sectionX, y + 22, sectionW, 112, WbTheme.OK, WbIcon.POWER, "worldbinder.config.movement.options.behaviour", null);
        addToggleGrid(x, y + 44, w, cols,
                toggleSpec("worldbinder.config.movement.master", "worldbinder.tooltip.config.movement.master", () -> config.movementMasterEnabled, v -> config.movementMasterEnabled = v),
                toggleSpec("worldbinder.config.movement.feedback", "worldbinder.tooltip.config.movement.feedback", () -> config.movementToggleFeedback, v -> config.movementToggleFeedback = v),
                toggleSpec("worldbinder.config.movement.disable_servers", "worldbinder.tooltip.config.movement.disable_servers", () -> config.movementDisableOnServers, v -> config.movementDisableOnServers = v));
        textBlock(x, y + (cols == 1 ? 136 : 106), w, 2, Lang.string("worldbinder.config.movement.options.note"), WbTheme.TEXT_DIM);

        int hudY = y + (cols == 1 ? 170 : 146);
        section(sectionX, hudY, sectionW, 112, WbTheme.INFO, WbIcon.HUD, "worldbinder.config.movement.options.hud", null);
        addToggleGrid(x, hudY + 22, w, cols,
                toggleSpec("worldbinder.config.movement.show_badges", "worldbinder.tooltip.config.movement.show_badges", () -> config.showMovementBadges, v -> config.showMovementBadges = v),
                toggleSpec("worldbinder.config.movement.show_values", "worldbinder.tooltip.config.movement.show_values", () -> config.movementHudShowValues, v -> config.movementHudShowValues = v));
        int styleY = hudY + 22 + (cols == 1 ? 60 : 30);
        int halfW = cols == 1 ? w : (w - 8) / 2;
        Button style = WbButton.create(x, styleY, halfW, 22,
                Component.translatable("worldbinder.config.movement.hud_style", hudStyleLabel(config.effectiveMovementHudStyle())),
                Component.translatable("worldbinder.tooltip.config.movement.hud_style"), b -> {
                    WorldBinderConfig.MovementHudStyle[] styles = WorldBinderConfig.MovementHudStyle.values();
                    config.movementHudStyle = styles[(config.effectiveMovementHudStyle().ordinal() + 1) % styles.length];
                    b.setMessage(fit(Component.translatable("worldbinder.config.movement.hud_style", hudStyleLabel(config.movementHudStyle)), b.getWidth()));
                });
        addContentWidget(WbButton.icon(WbButton.dropdown(style, WbTheme.INFO, false), WbIcon.LAYERS));
        addContentWidget(WbButton.icon(button(cols == 1 ? x : x + halfW + 8, cols == 1 ? styleY + 30 : styleY, halfW, 22,
                "worldbinder.config.widget_editor", "worldbinder.tooltip.config.widget_editor", b -> {
                    if (applyFieldsToDraft()) {
                        net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderWidgetEditorScreen(this, draft));
                    }
                }), WbIcon.HUD));

        int keysY = hudY + (cols == 1 ? 160 : 124);
        section(sectionX, keysY, sectionW, 96, WbTheme.ACCENT, WbIcon.KEY, "worldbinder.config.movement.options.keys", null);
        textBlock(x, keysY + 24, w, 2, Lang.string("worldbinder.config.movement.options.keys_hint"), WbTheme.mutedText());
        addContentWidget(WbButton.icon(WbButton.primary(button(x, keysY + 56, Math.min(w, 240), 22,
                "worldbinder.config.movement.open_keybinds", "worldbinder.tooltip.config.movement.open_keybinds", b -> {
                    if (applyFieldsToDraft()) {
                        net.worldbinder.compat.ClientCompat.setScreen(minecraft,
                                new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, minecraft.options));
                    }
                })), WbIcon.KEY));
    }

    private static Component hudStyleLabel(WorldBinderConfig.MovementHudStyle style) {
        return Component.translatable("worldbinder.config.movement.hud_style." + style.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ Interface (Display)

    private void initDisplay(WorldBinderConfig config, int x, int y, int w) {
        int sectionX = x - 8;
        int sectionW = w + 16;
        int half = (w - 8) / 2;
        boolean narrow = w < 430;

        // Theme
        section(sectionX, y + 22, sectionW, 96, WbTheme.ACCENT, WbIcon.PALETTE, "worldbinder.config.display.theme", "worldbinder.config.display.theme.sub");
        WbTheme.Accent[] accents = WbTheme.Accent.values();
        int presetCount = accents.length - 1;
        int swatchCols = w < 640 ? 4 : presetCount;
        int swatchGap = 6;
        int swatchW = (w - swatchGap * (swatchCols - 1)) / swatchCols;
        for (int i = 0; i < presetCount; i++) {
            WbTheme.Accent accent = accents[i];
            Button swatch = WbButton.create(x + (i % swatchCols) * (swatchW + swatchGap), y + 44 + (i / swatchCols) * 24, swatchW, 20,
                    Component.translatable("worldbinder.config.display.accent." + accent.name().toLowerCase(Locale.ROOT)),
                    Component.translatable("worldbinder.tooltip.config.display.accent"), b -> {
                        config.uiAccent = accent;
                        rebuildConfigWidgets();
                    });
            addContentWidget(WbButton.icon(WbButton.tab(swatch, accent.primary, config.effectiveUiAccent() == accent), WbIcon.STAR));
        }
        int customY = y + 44 + ((presetCount + swatchCols - 1) / swatchCols) * 24 + 4;
        Button custom = WbButton.create(x, customY, Math.min(w, 220), 22,
                Component.translatable("worldbinder.config.display.custom_accent"),
                Component.translatable("worldbinder.tooltip.config.display.custom_accent"), b -> {
                    if (!applyFieldsToDraft()) return;
                    net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderColorPickerScreen(this,
                            Component.translatable("worldbinder.config.display.custom_accent"), config.uiCustomAccentColor, false, color -> {
                        config.uiCustomAccentColor = 0xFF000000 | (color & 0xFFFFFF);
                        config.uiAccent = WbTheme.Accent.CUSTOM;
                    }));
                });
        addContentWidget(WbButton.icon(WbButton.tab(custom, 0xFF000000 | (config.uiCustomAccentColor & 0xFFFFFF),
                config.effectiveUiAccent() == WbTheme.Accent.CUSTOM), WbIcon.PALETTE));
        int afterTheme = customY + 34;

        // Background & motion
        int motionY = afterTheme;
        section(sectionX, motionY, sectionW, narrow ? 170 : 116, WbTheme.ACCENT_DARK, WbIcon.MOTION, "worldbinder.config.display.motion_section", null);
        addContentWidget(slider(x, motionY + 22, narrow ? w : half, 20, 100, config.uiBackdropOpacityPercent, WbTheme.ACCENT_DARK,
                v -> config.uiBackdropOpacityPercent = v, v -> Component.translatable("worldbinder.config.display.backdrop", v + "%"),
                "worldbinder.tooltip.config.display.backdrop"));
        addContentWidget(slider(narrow ? x : x + half + 8, narrow ? motionY + 50 : motionY + 22, narrow ? w : half, 50, 200, config.uiAnimationSpeedPercent, WbTheme.ACCENT_DARK,
                v -> config.uiAnimationSpeedPercent = v, v -> Component.translatable("worldbinder.config.display.animation_speed", String.format(Locale.ROOT, "%.1fx", v / 100.0)),
                "worldbinder.tooltip.config.display.animation_speed"));
        int toggleY = motionY + (narrow ? 80 : 52);
        addToggleGrid(x, toggleY, w, narrow ? 2 : 2,
                toggleSpec("worldbinder.config.display.transitions", "worldbinder.tooltip.config.display.transitions", () -> config.uiScreenTransitions, v -> config.uiScreenTransitions = v),
                toggleSpec("worldbinder.config.display.motion", "worldbinder.tooltip.config.display.motion", () -> config.reducedUiMotion, v -> config.reducedUiMotion = v),
                toggleSpec("worldbinder.config.display.contrast", "worldbinder.tooltip.config.display.contrast", () -> config.highContrastUi, v -> config.highContrastUi = v),
                toggleSpec("worldbinder.config.display.icons", "worldbinder.tooltip.config.display.icons", () -> config.uiShowIcons, v -> config.uiShowIcons = v));
        int afterMotion = motionY + (narrow ? 180 : 126);

        // Sounds
        int soundY = afterMotion;
        section(sectionX, soundY, sectionW, narrow ? 140 : 110, WbTheme.OK, WbIcon.SOUND, "worldbinder.config.display.sounds", null);
        addToggleGrid(x, soundY + 22, w, narrow ? 2 : 3,
                toggleSpec("worldbinder.config.display.ui_sounds", "worldbinder.tooltip.config.display.ui_sounds", () -> config.uiSounds, v -> config.uiSounds = v),
                toggleSpec("worldbinder.config.display.hover_sounds", "worldbinder.tooltip.config.display.hover_sounds", () -> config.uiHoverSounds, v -> config.uiHoverSounds = v),
                toggleSpec("worldbinder.config.display.notification_sounds", "worldbinder.tooltip.config.display.notification_sounds", () -> config.notificationSounds, v -> config.notificationSounds = v));
        int volumeY = soundY + (narrow ? 82 : 52);
        addContentWidget(slider(x, volumeY, narrow ? w : half, 0, 200, config.uiSoundVolumePercent, WbTheme.OK,
                v -> config.uiSoundVolumePercent = v, v -> Component.translatable("worldbinder.config.display.volume", v + "%"),
                "worldbinder.tooltip.config.display.volume"));
        addContentWidget(WbButton.icon(WbButton.quiet(button(narrow ? x : x + half + 8, narrow ? volumeY + 28 : volumeY, narrow ? w : half, 22,
                "worldbinder.config.display.test_sound", "worldbinder.tooltip.config.display.test_sound", b -> WbSounds.success())), WbIcon.PLAY));
        int afterSounds = soundY + (narrow ? 150 : 120);

        // Tooltips
        int tooltipY = afterSounds;
        section(sectionX, tooltipY, sectionW, narrow ? 112 : 84, WbTheme.INFO, WbIcon.INFO, "worldbinder.config.display.tooltips", null);
        addToggleGrid(x, tooltipY + 22, w, 2,
                toggleSpec("worldbinder.config.display.tooltips_enabled", "worldbinder.tooltip.config.display.tooltips_enabled", () -> config.tooltipsEnabled, v -> config.tooltipsEnabled = v),
                toggleSpec("worldbinder.config.display.tooltip_hints", "worldbinder.tooltip.config.display.tooltip_hints", () -> config.tooltipShowHints, v -> config.tooltipShowHints = v));
        addContentWidget(slider(x, tooltipY + (narrow ? 82 : 52), narrow ? w : half, 0, 30, config.tooltipDelayMs / 50, WbTheme.INFO,
                v -> config.tooltipDelayMs = v * 50, v -> Component.translatable("worldbinder.config.display.tooltip_delay", (v * 50) + " ms"),
                "worldbinder.tooltip.config.display.tooltip_delay"));
        textBlock(x, tooltipY + (narrow ? 122 : 94), w, 2, Lang.string("worldbinder.config.display.apply"), WbTheme.TEXT_DIM);
    }

    private int displayHeight(int w) {
        boolean narrow = w < 430;
        int presetCount = WbTheme.Accent.values().length - 1;
        int swatchCols = w < 640 ? 4 : presetCount;
        int customY = 44 + ((presetCount + swatchCols - 1) / swatchCols) * 24 + 4;
        int afterTheme = customY + 34;
        int afterMotion = afterTheme + (narrow ? 180 : 126);
        int afterSounds = afterMotion + (narrow ? 150 : 120);
        return afterSounds + (narrow ? 150 : 122);
    }

    private WbIntegerSlider slider(int x, int y, int w, int min, int max, int value, int accent,
                                   java.util.function.IntConsumer setter, java.util.function.IntFunction<Component> label, String tooltipKey) {
        WbIntegerSlider slider = new WbIntegerSlider(font, x, y, w, 22, min, max, value, accent, setter, label);
        return WbTooltips.register(slider, Component.translatable(tooltipKey));
    }

    // ------------------------------------------------------------------ Safety

    private void initSafety(WorldBinderConfig config, int x, int y, int w) {
        int contentY = y + 28;
        if (safetyPage == SafetyPage.CORE) initSafetyCore(config, x, contentY, w);
        if (safetyPage == SafetyPage.EXPORT) initSafetyExport(config, x, contentY, w);
        if (safetyPage == SafetyPage.RESOURCE_PACK) initSafetyResourcePack(config, x, contentY, w);
        if (safetyPage == SafetyPage.GAMERULES) initGameRules(config, x, contentY, w);
    }

    private void initSafetyCore(WorldBinderConfig config, int x, int y, int w) {
        int labelW = labelWidth(w);
        recoverySeconds = field(x + labelW, y + 26, Math.min(90, w - labelW), Integer.toString(config.recoveryAutosaveSeconds), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.recovery_interval"); addContentWidget(recoverySeconds);
        addToggleGrid(x, y + 76, w, 3,
                toggleSpec("worldbinder.config.adaptive_throttle", "worldbinder.tooltip.config.adaptive", () -> config.adaptiveThrottle, v -> { config.adaptiveThrottle = v; config.adaptivePerformance = v; }),
                toggleSpec("worldbinder.config.server_safety", "worldbinder.tooltip.config.safety", () -> config.serverSafetyMode, v -> config.serverSafetyMode = v),
                toggleSpec("worldbinder.config.disconnect_autosave", "worldbinder.tooltip.config.disconnect", () -> config.autoSaveOnDisconnect, v -> config.autoSaveOnDisconnect = v),
                toggleSpec("worldbinder.config.crash_recovery", "worldbinder.tooltip.config.recovery", () -> config.crashRecovery, v -> config.crashRecovery = v),
                toggleSpec("worldbinder.config.delete_old_recovery", "worldbinder.tooltip.config.delete_old_recovery", () -> config.autoDeleteRecovery, v -> config.autoDeleteRecovery = v));
    }

    private void initSafetyExport(WorldBinderConfig config, int x, int y, int w) {
        addToggleGrid(x, y + 20, w, 3,
                toggleSpec("worldbinder.config.maps", "worldbinder.tooltip.config.maps", () -> config.exportMaps, v -> config.exportMaps = v),
                toggleSpec("worldbinder.config.stats", "worldbinder.tooltip.config.stats", () -> config.exportStats, v -> config.exportStats = v),
                toggleSpec("worldbinder.config.advancements", "worldbinder.tooltip.config.advancements", () -> config.exportAdvancements, v -> config.exportAdvancements = v),
                toggleSpec("worldbinder.config.zip", "worldbinder.tooltip.config.zip", () -> config.zipWorldExport, v -> config.zipWorldExport = v),
                toggleSpec("worldbinder.config.queue_diagnostics", "worldbinder.tooltip.config.queue_diagnostics", () -> config.queueDebugDiagnostics, v -> config.queueDebugDiagnostics = v));
    }

    private void initSafetyResourcePack(WorldBinderConfig config, int x, int y, int w) {
        int buttonW = Math.min(w, Math.max(220, w / 2));
        addContentWidget(resourcePackFallbackButton(x, y + 24, buttonW, config));
        addContentWidget(toggle(x, y + 58, buttonW, 22, "worldbinder.config.pack_fallback_warning", "worldbinder.tooltip.config.pack_fallback_warning", () -> config.showResourcePackFallbackWarning, v -> config.showResourcePackFallbackWarning = v));
        addContentWidget(reloadDisplayButton(x, y + 90, buttonW, config));
    }

    private Button reloadDisplayButton(int x, int y, int width, WorldBinderConfig config) {
        Button button = WbButton.create(x, y, width, 22,
                Component.translatable("worldbinder.config.reload_display.value", reloadDisplayLabel(config.reloadDisplayMode)),
                Component.translatable("worldbinder.tooltip.config.reload_display"), pressed -> {
                    config.reloadDisplayMode = config.reloadDisplayMode == WorldBinderConfig.ReloadDisplayMode.VANILLA
                            ? WorldBinderConfig.ReloadDisplayMode.HIDDEN : WorldBinderConfig.ReloadDisplayMode.VANILLA;
                    config.reloadDisplayConfigured = true;
                    pressed.setMessage(fit(Component.translatable("worldbinder.config.reload_display.value",
                            reloadDisplayLabel(config.reloadDisplayMode)), pressed.getWidth()));
                });
        return WbButton.icon(WbButton.dropdown(button, WbTheme.INFO, false), WbIcon.RECOVERY);
    }

    private static Component reloadDisplayLabel(WorldBinderConfig.ReloadDisplayMode mode) {
        WorldBinderConfig.ReloadDisplayMode effective = mode == null
                ? WorldBinderConfig.ReloadDisplayMode.HIDDEN : mode;
        return Component.translatable("worldbinder.config.reload_display." + effective.name().toLowerCase(Locale.ROOT));
    }

    private void initGameRules(WorldBinderConfig config, int x, int y, int w) {
        int categoryCols = w < 420 ? 2 : 4;
        int presetW = Math.max(72, (w - 16) / 3);
        addIfVisible(WbButton.icon(WbButton.tab(button(x, y + 54, presetW, 22, "worldbinder.config.rules.peaceful", "worldbinder.tooltip.rules.safe",
                b -> applyGameRulePreset(SAFE_RULES)), WbTheme.INFO, false), WbIcon.SHIELD), y + 54, 22);
        addIfVisible(WbButton.icon(WbButton.tab(button(x + presetW + 8, y + 54, presetW, 22, "worldbinder.config.rules.vanilla", "worldbinder.tooltip.rules.vanilla",
                b -> applyGameRulePreset(null)), WbTheme.ACCENT, false), WbIcon.GLOBE), y + 54, 22);
        addIfVisible(WbButton.icon(WbButton.tab(button(x + (presetW + 8) * 2, y + 54, presetW, 22, "worldbinder.config.rules.static", "worldbinder.tooltip.rules.showcase",
                b -> applyGameRulePreset(SHOWCASE_RULES)), WbTheme.WARN, false), WbIcon.EYE), y + 54, 22);

        int categoryY = y + 86;
        int categoryGap = 6;
        int categoryW = (w - categoryGap * (categoryCols - 1)) / categoryCols;
        for (int index = 0; index < 4; index++) {
            final int selected = index;
            String key = "worldbinder.gamerules.category." + index;
            Button categoryButton = WbButton.create(x + (index % categoryCols) * (categoryW + categoryGap),
                    categoryY + (index / categoryCols) * 26, categoryW, 22, Component.translatable(key),
                    Component.translatable(key), b -> {
                        if (applyFieldsToDraft()) {
                            gameRuleCategory = selected;
                            scrollOffset = 0;
                            pageChangedMillis = System.currentTimeMillis();
                            rebuildConfigWidgets();
                        }
                    });
            addIfVisible(WbButton.tab(categoryButton, WbTheme.WARN, gameRuleCategory == index),
                    categoryY + (index / categoryCols) * 26, 22);
        }

        int sliderY = categoryY + (categoryCols == 2 ? 62 : 36);
        addIfVisible(new WbIntegerSlider(font, x, sliderY, Math.min(w, 360), 22, 0, 64, gameRules.randomTickSpeed(),
                WbTheme.WARN, gameRules::setRandomTickSpeed,
                value -> Component.translatable("worldbinder.config.random_tick_speed", value)), sliderY, 22);

        int gridY = sliderY + 38;
        int cols = w < 420 ? 1 : 2;
        int gap = 8;
        int rowH = 22;
        int buttonW = Math.max(68, (w - gap * (cols - 1)) / cols);
        String[] rules = GameRuleSettings.rules(config.targetMinecraftVersion, gameRuleCategory);
        for (int i = 0; i < rules.length; i++) {
            String rule = rules[i];
            int col = i % cols;
            int row = i / cols;
            int by = gridY + row * (rowH + 6);
            addIfVisible(gameRuleButton(x + col * (buttonW + gap), by, buttonW, rule), by, rowH);
        }
    }

    private void applyGameRulePreset(String rules) {
        gameRules.load(rules);
        draft.gameRulesOverride = gameRules.serializeAndMarkClean();
        rebuildConfigWidgets();
    }

    // ------------------------------------------------------------------ helpers

    private <T extends AbstractWidget> T addContentWidget(T widget) {
        if (widget instanceof WbEditBox styled) {
            widget.visible = isInContentArea(styled.chromeY(), styled.chromeHeight());
        } else {
            widget.visible = isInContentArea(widget.getY(), widget.getHeight());
        }
        addRenderableWidget(widget);
        return widget;
    }

    private <T extends AbstractWidget> T addIfVisible(T widget, int y, int h) {
        widget.visible = isInContentArea(y, h);
        addRenderableWidget(widget);
        return widget;
    }

    private Button preset(int x, int y, int w, String labelKey, WorldBinderConfig.PerformancePreset preset, String tooltipKey, WbIcon icon) {
        Button button = WbButton.create(x, y, w, 22, Component.translatable(labelKey), Component.translatable(tooltipKey),
                b -> {
                    if (!applyFieldsToDraft()) {
                        return;
                    }
                    draft.applyPreset(preset);
                    net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderConfigScreen(parent, Tab.PERFORMANCE, SafetyPage.CORE, 0, draft));
                });
        return WbButton.icon(WbButton.tab(button, WbTheme.ACCENT_RIGHT, draft.performancePreset == preset), icon);
    }

    private Button resourcePackFallbackButton(int x, int y, int w, WorldBinderConfig config) {
        Button button = WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)),
                Component.translatable("worldbinder.tooltip.config.resource_pack_fallback"), b -> {
            config.resourcePackFallbackMode = nextFallbackMode(config.resourcePackFallbackMode);
            b.setMessage(fit(Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)), b.getWidth()));
        });
        return WbButton.icon(WbButton.dropdown(button, WbTheme.WARN, false), WbIcon.PALETTE);
    }

    private static WorldBinderConfig.ResourcePackFallbackMode nextFallbackMode(WorldBinderConfig.ResourcePackFallbackMode mode) {
        return switch (mode == null ? WorldBinderConfig.ResourcePackFallbackMode.LOWER_PROTOCOL_ONLY : mode) {
            case DISABLED -> WorldBinderConfig.ResourcePackFallbackMode.ENABLED;
            case ENABLED -> WorldBinderConfig.ResourcePackFallbackMode.LOWER_PROTOCOL_ONLY;
            case LOWER_PROTOCOL_ONLY -> WorldBinderConfig.ResourcePackFallbackMode.DISABLED;
        };
    }

    private static String fallbackLabel(WorldBinderConfig.ResourcePackFallbackMode mode) {
        return switch (mode == null ? WorldBinderConfig.ResourcePackFallbackMode.LOWER_PROTOCOL_ONLY : mode) {
            case DISABLED -> Lang.string("worldbinder.common.disabled");
            case ENABLED -> Lang.string("worldbinder.common.enabled");
            case LOWER_PROTOCOL_ONLY -> Lang.string("worldbinder.config.lower_protocol_only");
        };
    }

    private Button modeButton(int x, int y, int w, String label, ModeGetter getter, ModeSetter setter) {
        WorldBinderConfig.MapLayerMode current = getter.get();
        Button button = WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.mode_value", Component.translatable(label), modeLabel(current)),
                Component.translatable("worldbinder.tooltip.config.map_mode"), b -> {
            WorldBinderConfig.MapLayerMode next = nextMode(getter.get());
            setter.set(next);
            b.setMessage(fit(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), modeLabel(next)), b.getWidth()));
        });
        return WbButton.icon(WbButton.dropdown(button, WbTheme.INFO, false), WbIcon.LAYERS);
    }

    private static WorldBinderConfig.MapLayerMode nextMode(WorldBinderConfig.MapLayerMode mode) {
        return switch (mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode) {
            case BOTH -> WorldBinderConfig.MapLayerMode.CHUNKS_ONLY;
            case CHUNKS_ONLY -> WorldBinderConfig.MapLayerMode.MAP_ONLY;
            case MAP_ONLY -> WorldBinderConfig.MapLayerMode.BOTH;
        };
    }

    private Button radarDetailButton(int x, int y, int w, String label, RadarDetailGetter getter, RadarDetailSetter setter) {
        WorldBinderConfig.RadarDetailMode current = getter.get();
        Button button = WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.mode_value", Component.translatable(label), radarDetailLabel(current)),
                Component.translatable("worldbinder.tooltip.config.radar_detail"), b -> {
            WorldBinderConfig.RadarDetailMode next = nextRadarDetail(getter.get());
            setter.set(next);
            b.setMessage(fit(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), radarDetailLabel(next)), b.getWidth()));
        });
        return WbButton.icon(WbButton.dropdown(button, WbTheme.INFO, false), WbIcon.TARGET);
    }

    private static String modeLabel(WorldBinderConfig.MapLayerMode mode) {
        return switch (mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode) {
            case BOTH -> Lang.string("worldbinder.config.map_mode.both");
            case CHUNKS_ONLY -> Lang.string("worldbinder.config.map_mode.chunks");
            case MAP_ONLY -> Lang.string("worldbinder.config.map_mode.map");
        };
    }

    private static WorldBinderConfig.RadarDetailMode nextRadarDetail(WorldBinderConfig.RadarDetailMode mode) {
        return switch (mode == null ? WorldBinderConfig.RadarDetailMode.AUTO : mode) {
            case AUTO -> WorldBinderConfig.RadarDetailMode.LOW;
            case LOW -> WorldBinderConfig.RadarDetailMode.MEDIUM;
            case MEDIUM -> WorldBinderConfig.RadarDetailMode.HIGH;
            case HIGH -> WorldBinderConfig.RadarDetailMode.AUTO;
        };
    }

    private static String radarDetailLabel(WorldBinderConfig.RadarDetailMode mode) {
        return switch (mode == null ? WorldBinderConfig.RadarDetailMode.AUTO : mode) {
            case AUTO -> Lang.string("worldbinder.config.radar.auto");
            case LOW -> Lang.string("worldbinder.config.radar.low");
            case MEDIUM -> Lang.string("worldbinder.config.radar.medium");
            case HIGH -> Lang.string("worldbinder.config.radar.high");
        };
    }

    private Button gameRuleButton(int x, int y, int w, String rule) {
        boolean enabled = gameRules.value(rule);
        String label = Lang.string("gamerule." + rule);
        Button button = WbButton.create(x, y, w, 22, Component.literal(label + ": " + Lang.string(enabled ? "worldbinder.common.on" : "worldbinder.common.off")),
                Component.translatable("worldbinder.gamerules.rule_tooltip", Component.literal(rule)), b -> {
            boolean next = gameRules.toggle(rule);
            b.setMessage(fit(Component.literal(label + ": " + Lang.string(next ? "worldbinder.common.on" : "worldbinder.common.off")), b.getWidth()));
            WbButton.selected(b, next);
        });
        return WbButton.toggle(button, WbTheme.WARN, enabled);
    }

    private void addToggleGrid(int x, int y, int w, int requestedCols, ToggleSpec... specs) {
        int cols = Math.min(requestedCols, w < 300 ? 1 : w < 430 ? 2 : 3);
        int gap = 8;
        int buttonW = Math.max(88, (w - gap * (cols - 1)) / cols);
        for (int i = 0; i < specs.length; i++) {
            ToggleSpec spec = specs[i];
            int col = i % cols;
            int row = i / cols;
            addContentWidget(toggle(x + col * (buttonW + gap), y + row * 30, buttonW, 22, spec.labelKey, spec.tooltipKey, spec.getter, spec.setter));
        }
    }

    /** Lays buttons out in a grid and returns the number of rows used. */
    private int addButtonGrid(int x, int y, int w, int requestedCols, Button... buttons) {
        int cols = Math.min(requestedCols, w < 300 ? 1 : w < 430 ? 2 : 3);
        int gap = 8;
        int buttonW = Math.max(88, (w - gap * (cols - 1)) / cols);
        for (int i = 0; i < buttons.length; i++) {
            int col = i % cols;
            int row = i / cols;
            buttons[i].setX(x + col * (buttonW + gap));
            buttons[i].setY(y + row * 30);
            buttons[i].setWidth(buttonW);
            addContentWidget(buttons[i]);
        }
        return (buttons.length + cols - 1) / cols;
    }

    private ToggleSpec toggleSpec(String labelKey, String tooltipKey, BoolGetter getter, BoolSetter setter) {
        return new ToggleSpec(labelKey, tooltipKey, getter, setter);
    }

    private EditBox field(int x, int y, int w, String value, int maxLength) {
        EditBox f = new WbEditBox(font, x, y, Math.max(36, w), 22);
        f.setMaxLength(maxLength);
        f.setValue(value);
        f.setBordered(false);
        f.setTextColor(WbTheme.TEXT_SOFT);
        f.setTextColorUneditable(WbTheme.TEXT_DISABLED);
        f.setTextShadow(false);
        return f;
    }

    private EditBox field(int x, int y, int w, String value, int maxLength, String tooltipKey) {
        EditBox f = field(x, y, w, value, maxLength);
        return WbTooltips.register(f, Component.translatable(tooltipKey));
    }

    private Button button(int x, int y, int w, int h, String key, String tooltipKey, Button.OnPress action) {
        return WbButton.create(x, y, w, h, Component.translatable(key), Component.translatable(tooltipKey), action);
    }

    private Button toggle(int x, int y, int w, int h, String labelKey, String tooltipKey, BoolGetter getter, BoolSetter setter) {
        Button button = WbButton.create(x, y, w, h, toggleLabel(labelKey, getter.get()), Component.translatable(tooltipKey), b -> {
            boolean n = !getter.get();
            setter.set(n);
            b.setMessage(fit(toggleLabel(labelKey, n), b.getWidth()));
            WbButton.selected(b, n);
        });
        return WbButton.toggle(button, WbTheme.ACCENT, getter.get());
    }

    private Component toggleLabel(String labelKey, boolean enabled) {
        return Component.translatable("worldbinder.config.toggle_value", Component.translatable(labelKey), Component.translatable(enabled ? "worldbinder.common.on" : "worldbinder.common.off"));
    }

    private Component fit(Component label, int width) {
        return Component.literal(WbButton.fitLabel(label.getString(), width));
    }

    // ------------------------------------------------------------------ actions

    private boolean saveConfig() {
        if (!applyFieldsToDraft()) {
            WbSounds.error();
            return false;
        }
        WorldBinder.replaceConfig(draft);
        draft.save();
        WbTheme.refresh();
        return true;
    }

    private void requestClose() {
        applyFieldsToDraft();
        long now = System.currentTimeMillis();
        if (computeDirty() && now - discardArmedMillis >= CONFIRM_WINDOW_MILLIS) {
            discardArmedMillis = now;
            statusMessage = Component.translatable("worldbinder.config.discard_hint");
            statusMessageMillis = now;
            WbSounds.warning();
            rebuildConfigWidgets();
            return;
        }
        WbTheme.refresh();
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }

    private void resetTab() {
        long now = System.currentTimeMillis();
        if (now - resetArmedMillis >= CONFIRM_WINDOW_MILLIS) {
            resetArmedMillis = now;
            statusMessage = Component.translatable("worldbinder.config.reset_hint", Component.translatable(tabLabelKey(tab)));
            statusMessageMillis = now;
            WbSounds.warning();
            rebuildConfigWidgets();
            return;
        }
        resetArmedMillis = 0L;
        WorldBinderConfig defaults = new WorldBinderConfig();
        WorldBinderConfig c = draft;
        switch (tab) {
            case GENERAL -> {
                c.defaultArchiveName = defaults.defaultArchiveName;
                c.targetMinecraftVersion = defaults.targetMinecraftVersion;
                c.roamingRadiusChunks = defaults.roamingRadiusChunks;
                c.captureMinY = defaults.captureMinY;
                c.captureMaxY = defaults.captureMaxY;
                c.captureEntities = defaults.captureEntities;
                c.captureBlockEntities = defaults.captureBlockEntities;
                c.captureAir = defaults.captureAir;
                c.includeServerResourcePack = defaults.includeServerResourcePack;
                c.exportGameRules = defaults.exportGameRules;
                c.appendTimestampToArchiveName = defaults.appendTimestampToArchiveName;
            }
            case PERFORMANCE -> {
                c.applyPreset(WorldBinderConfig.PerformancePreset.BALANCED);
                c.targetFps = defaults.targetFps;
                c.maxUiWorkMs = defaults.maxUiWorkMs;
                c.maxCaptureWorkMs = defaults.maxCaptureWorkMs;
                c.maxArchiveWorkMs = defaults.maxArchiveWorkMs;
            }
            case HUD -> {
                c.chunkRadarSize = defaults.chunkRadarSize;
                c.chunkRadarCellSize = defaults.chunkRadarCellSize;
                c.radarMaxRenderedChunks = defaults.radarMaxRenderedChunks;
                c.radarUpdateRate = defaults.radarUpdateRate;
                c.f10MapLayerMode = defaults.f10MapLayerMode;
                c.radarLayerMode = defaults.radarLayerMode;
                c.radarDetailMode = defaults.radarDetailMode;
                c.showWorldGizmos = defaults.showWorldGizmos;
                c.notificationDurationPercent = defaults.notificationDurationPercent;
                c.messageMode = defaults.messageMode;
                c.mapHillshade = defaults.mapHillshade;
                c.mapShowGrid = defaults.mapShowGrid;
                c.mapShowStatusOverlay = defaults.mapShowStatusOverlay;
                c.mapShowPlayers = defaults.mapShowPlayers;
                c.mapShowSidebar = defaults.mapShowSidebar;
                c.mapSmoothCamera = defaults.mapSmoothCamera;
            }
            case SAFETY -> {
                c.recoveryAutosaveSeconds = defaults.recoveryAutosaveSeconds;
                c.adaptiveThrottle = defaults.adaptiveThrottle;
                c.adaptivePerformance = defaults.adaptivePerformance;
                c.serverSafetyMode = defaults.serverSafetyMode;
                c.autoSaveOnDisconnect = defaults.autoSaveOnDisconnect;
                c.crashRecovery = defaults.crashRecovery;
                c.autoDeleteRecovery = defaults.autoDeleteRecovery;
                c.exportMaps = defaults.exportMaps;
                c.exportStats = defaults.exportStats;
                c.exportAdvancements = defaults.exportAdvancements;
                c.zipWorldExport = defaults.zipWorldExport;
                c.queueDebugDiagnostics = defaults.queueDebugDiagnostics;
                c.resourcePackFallbackMode = defaults.resourcePackFallbackMode;
                c.showResourcePackFallbackWarning = defaults.showResourcePackFallbackWarning;
                c.gameRulesOverride = defaults.gameRulesOverride;
            }
            case MOVEMENT -> {
                c.movement = new MovementSettings();
                c.movementMasterEnabled = defaults.movementMasterEnabled;
                c.movementToggleFeedback = defaults.movementToggleFeedback;
                c.movementDisableOnServers = defaults.movementDisableOnServers;
                c.movementHudShowValues = defaults.movementHudShowValues;
                c.movementHudStyle = defaults.movementHudStyle;
                c.showMovementBadges = defaults.showMovementBadges;
            }
            case DISPLAY -> {
                c.uiAccent = defaults.uiAccent;
                c.uiCustomAccentColor = defaults.uiCustomAccentColor;
                c.uiBackdropOpacityPercent = defaults.uiBackdropOpacityPercent;
                c.uiAnimationSpeedPercent = defaults.uiAnimationSpeedPercent;
                c.uiScreenTransitions = defaults.uiScreenTransitions;
                c.uiShowIcons = defaults.uiShowIcons;
                c.uiSounds = defaults.uiSounds;
                c.uiSoundVolumePercent = defaults.uiSoundVolumePercent;
                c.uiHoverSounds = defaults.uiHoverSounds;
                c.notificationSounds = defaults.notificationSounds;
                c.tooltipsEnabled = defaults.tooltipsEnabled;
                c.tooltipDelayMs = defaults.tooltipDelayMs;
                c.tooltipShowHints = defaults.tooltipShowHints;
                c.highContrastUi = defaults.highContrastUi;
                c.reducedUiMotion = defaults.reducedUiMotion;
            }
        }
        statusMessage = Component.translatable("worldbinder.config.reset_done", Component.translatable(tabLabelKey(tab)));
        statusMessageMillis = now;
        pageChangedMillis = now;
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderConfigScreen(parent, tab, safetyPage, 0, c));
    }

    private boolean applyFieldsToDraft() {
        EditBox invalidField = firstInvalidNumericField();
        if (invalidField != null) {
            validationError = Component.translatable("worldbinder.config.validation.number");
            setFocused(invalidField);
            return false;
        }
        validationError = null;
        WorldBinderConfig config = draft;
        if (defaultName != null) config.defaultArchiveName = cleanName(defaultName.getValue(), config.defaultArchiveName);
        applyInt(radiusChunks, value -> config.roamingRadiusChunks = value);
        applyInt(minY, value -> config.captureMinY = value);
        applyInt(maxY, value -> config.captureMaxY = value);
        applyCustomInt(blocksPerTick, config.blocksPerTick, value -> config.blocksPerTick = value, config);
        applyCustomInt(commandsPerTick, config.commandsPerTick, value -> config.commandsPerTick = value, config);
        applyCustomInt(budgetMs, config.tickBudgetMillis, value -> config.tickBudgetMillis = value, config);
        applyCustomInt(newChunks, config.newChunksPerTick, value -> config.newChunksPerTick = value, config);
        applyCustomInt(queueLimit, config.chunkQueueLimit, value -> config.chunkQueueLimit = value, config);
        applyCustomInt(hotChunks, config.hotChunksPerTick, value -> config.hotChunksPerTick = value, config);
        applyInt(targetFps, value -> config.targetFps = value);
        applyInt(maxCaptureWorkMs, value -> config.maxCaptureWorkMs = value);
        applyInt(maxUiWorkMs, value -> config.maxUiWorkMs = value);
        applyInt(maxArchiveWorkMs, value -> config.maxArchiveWorkMs = value);
        applyInt(radarSize, value -> config.chunkRadarSize = value);
        applyInt(radarCell, value -> config.chunkRadarCellSize = value);
        applyInt(radarMaxChunks, value -> config.radarMaxRenderedChunks = value);
        applyInt(radarUpdateRate, value -> config.radarUpdateRate = value);
        applyInt(notificationDuration, value -> config.notificationDurationPercent = Math.max(40, Math.min(250, value)));
        applyInt(recoverySeconds, value -> config.recoveryAutosaveSeconds = value);
        if (gameRules.isDirty()) {
            config.gameRulesOverride = gameRules.serializeAndMarkClean();
        }
        return true;
    }

    private EditBox firstInvalidNumericField() {
        for (EditBox field : numericFields()) {
            if (field != null && parseInt(field.getValue()) == null) {
                return field;
            }
        }
        return null;
    }

    @Override
    protected boolean isInvalidNumericField(EditBox field) {
        if (field == null || parseInt(field.getValue()) != null) {
            return false;
        }
        for (EditBox numericField : numericFields()) {
            if (field == numericField) {
                return true;
            }
        }
        return false;
    }

    private EditBox[] numericFields() {
        return new EditBox[] {
                radiusChunks, minY, maxY,
                blocksPerTick, commandsPerTick, budgetMs, newChunks, queueLimit, hotChunks,
                targetFps, maxUiWorkMs, maxCaptureWorkMs, maxArchiveWorkMs,
                radarSize, radarCell, radarMaxChunks, radarUpdateRate,
                notificationDuration, recoverySeconds
        };
    }

    private static String cleanName(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static void applyCustomInt(EditBox field, int currentValue, IntSetter setter, WorldBinderConfig config) {
        Integer parsed = parseInt(field == null ? null : field.getValue());
        if (parsed != null && parsed != currentValue) {
            setter.set(parsed);
            config.performancePreset = WorldBinderConfig.PerformancePreset.CUSTOM;
        }
    }

    private static boolean applyInt(EditBox field, IntSetter setter) {
        Integer parsed = parseInt(field == null ? null : field.getValue());
        if (parsed == null) return false;
        setter.set(parsed);
        return true;
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    @Override
    protected void repositionElements() {
        applyFieldsToDraft();
        super.repositionElements();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(WbLayout.virtualMouseEvent(event, width, height), doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_TAB && event.hasControlDown()) {
            Tab[] tabs = Tab.values();
            int direction = event.hasShiftDown() ? -1 : 1;
            Tab next = tabs[Math.floorMod(tab.ordinal() + direction, tabs.length)];
            openSection(next, next == Tab.SAFETY ? safetyPage : SafetyPage.CORE);
            return true;
        }
        if (event.key() == InputConstants.KEY_S && event.hasControlDown()) {
            if (saveConfig()) {
                WbSounds.success();
                statusMessage = Component.translatable("worldbinder.config.saved_toast");
                statusMessageMillis = System.currentTimeMillis();
                dirty = false;
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_TAB || event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN) {
            WbTooltips.noteKeyboardNavigation();
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double offsetX, double offsetY) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        return super.mouseDragged(WbLayout.virtualMouseEvent(event, width, height), uiScale.toVirtualDelta(offsetX), uiScale.toVirtualDelta(offsetY));
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
        int step = compact() ? 18 : 28;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (verticalAmount < 0 ? step : -step)));
        if (before != scrollOffset) {
            if (!applyFieldsToDraft()) {
                scrollOffset = before;
                return true;
            }
            rebuildConfigWidgets();
            return true;
        }
        return super.mouseScrolled(virtualMouseX, virtualMouseY, horizontalAmount, verticalAmount);
    }

    private void rebuildConfigWidgets() {
        Map<String, ArrayDeque<WbButton.MotionState>> motion = new HashMap<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof Button button) {
                WbButton.MotionState state = WbButton.motionState(button);
                if (state != null) {
                    motion.computeIfAbsent(button.getMessage().getString(), key -> new ArrayDeque<>()).add(state);
                }
            }
        }
        setFocused(null);
        clearWidgets();
        init();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof Button button) {
                ArrayDeque<WbButton.MotionState> states = motion.get(button.getMessage().getString());
                if (states != null && !states.isEmpty()) {
                    WbButton.restoreMotion(button, states.removeFirst());
                }
            }
        }
    }

    @Override
    protected int contentHeight(int w) {
        if (tab == Tab.GENERAL) return 308;
        if (tab == Tab.PERFORMANCE) return (w < 420 ? 96 + 5 * 52 : 78 + 3 * 52) + 38;
        if (tab == Tab.HUD) return (w < 430 ? 244 : 196) + hudMapSectionHeight(w) + 132;
        if (tab == Tab.MOVEMENT) return movementGroupHeight(w);
        if (tab == Tab.DISPLAY) return displayHeight(w);
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.GAMERULES) {
            int cols = w < 420 ? 1 : 2;
            int rows = (GameRuleSettings.rules(draft.targetMinecraftVersion, gameRuleCategory).length + cols - 1) / cols;
            return (w < 420 ? 198 : 172) + rows * 28;
        }
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.CORE) return 180;
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.RESOURCE_PACK) return 198;
        if (tab == Tab.SAFETY) return 144;
        return 320;
    }

    @Override
    public void onClose() {
        requestClose();
    }

    private record ToggleSpec(String labelKey, String tooltipKey, BoolGetter getter, BoolSetter setter) {}

    @FunctionalInterface private interface IntSetter { void set(int value); }
    @FunctionalInterface private interface BoolGetter { boolean get(); }
    @FunctionalInterface private interface BoolSetter { void set(boolean value); }
    @FunctionalInterface private interface ModeGetter { WorldBinderConfig.MapLayerMode get(); }
    @FunctionalInterface private interface ModeSetter { void set(WorldBinderConfig.MapLayerMode value); }
    @FunctionalInterface private interface RadarDetailGetter { WorldBinderConfig.RadarDetailMode get(); }
    @FunctionalInterface private interface RadarDetailSetter { void set(WorldBinderConfig.RadarDetailMode value); }
}

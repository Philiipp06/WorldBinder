package net.worldbinder.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.GameRuleSettings;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.movement.MovementSettings;
import net.worldbinder.movement.MovementTool;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbIntegerSlider;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

public final class WorldBinderConfigScreen extends WorldBinderConfigScreenView {

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

    public static WorldBinderConfigScreen safety(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen safetyExport(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.EXPORT, 0, copyConfig(WorldBinder.config()));
    }

    private WorldBinderConfigScreen(Screen parent, Tab tab, SafetyPage safetyPage, int scrollOffset, WorldBinderConfig draft) {
        super(parent, tab, safetyPage, scrollOffset,
                draft == null ? copyConfig(WorldBinder.config()) : draft);
    }

    private static WorldBinderConfig copyConfig(WorldBinderConfig source) {
        WorldBinderConfig copy = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(source), WorldBinderConfig.class);
        return copy == null ? new WorldBinderConfig() : copy;
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

        int buttonW = compact() ? 76 : 92;
        int bottomY = top + panelHeight - 32;
        addRenderableWidget(WbButton.primary(button(left + panelWidth - buttonW * 2 - 26, bottomY, buttonW, 22, "worldbinder.config.save", "worldbinder.tooltip.config.save", button -> {
            if (saveConfig()) {
                minecraft.gui.setScreen(parent);
            }
        })));
        addRenderableWidget(WbButton.quiet(button(left + panelWidth - buttonW - 16, bottomY, buttonW, 22, "worldbinder.config.cancel", "worldbinder.tooltip.config.back", button -> minecraft.gui.setScreen(parent))));
        addSettingsNavigation(left, top, panelWidth);
    }

    private void addSettingsNavigation(int left, int top, int panelWidth) {
        if (useSidebar(panelWidth)) {
            int tabX = left + 16;
            int tabY = top + 82;
            int tabW = SETTINGS_SIDEBAR_WIDTH - 28;
            int index = 0;
            for (Tab target : Tab.values()) {
                Button navigation = WbButton.create(
                        tabX,
                        tabY + index * 40,
                        tabW,
                        32,
                        Component.translatable(tabLabelKey(target)),
                        Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(tabLabelKey(target))),
                        button -> openSection(target, target == Tab.SAFETY ? safetyPage : SafetyPage.CORE)
                );
                addRenderableWidget(WbButton.tab(navigation, tabAccent(target), tab == target));
                index++;
            }
            if (tab == Tab.SAFETY) {
                addSafetyNavigation(top, panelWidth);
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
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(tabLabelKey(target))),
                    button -> openSection(target, target == Tab.SAFETY ? safetyPage : SafetyPage.CORE));
            addRenderableWidget(WbButton.tab(navigation, tabAccent(target), tab == target));
            index++;
        }
        if (tab != Tab.SAFETY) {
            return;
        }
        int safetyY = y + navRows(panelWidth) * 26 + 6;
        int safetyCols = panelWidth < 430 ? 2 : 4;
        int safetyW = Math.max(90, (panelWidth - 36 - gap * (safetyCols - 1)) / safetyCols);
        int safetyIndex = 0;
        for (SafetyPage target : SafetyPage.values()) {
            int col = safetyIndex % safetyCols;
            int row = safetyIndex / safetyCols;
            Button navigation = WbButton.create(tabX + col * (safetyW + gap), safetyY + row * 24, safetyW, 20, Component.translatable(safetyLabelKey(target)),
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(safetyLabelKey(target))),
                    button -> openSection(Tab.SAFETY, target));
            addRenderableWidget(WbButton.tab(navigation, WbTheme.WARN, safetyPage == target));
            safetyIndex++;
        }
    }

    private void addSafetyNavigation(int top, int panelWidth) {
        int x = contentAreaX(WbLayout.left(width, panelWidth), panelWidth);
        int contentWidth = contentAreaWidth(panelWidth);
        int gap = 6;
        int buttonW = Math.max(90, (contentWidth - gap * (SafetyPage.values().length - 1)) / SafetyPage.values().length);
        int index = 0;
        for (SafetyPage target : SafetyPage.values()) {
            Button navigation = WbButton.create(
                    x + index * (buttonW + gap),
                    top + 59,
                    buttonW,
                    22,
                    Component.translatable(safetyLabelKey(target)),
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(safetyLabelKey(target))),
                    button -> openSection(Tab.SAFETY, target)
            );
            addRenderableWidget(WbButton.tab(navigation, WbTheme.WARN, safetyPage == target));
            index++;
        }
    }

    private void openSection(Tab targetTab, SafetyPage targetPage) {
        if (!applyFieldsToDraft()) {
            return;
        }
        minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, targetTab, targetPage, 0, draft));
    }

    private void initGeneral(WorldBinderConfig config, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        defaultName = field(x, y + 48, fullW, config.defaultArchiveName, 64); addContentWidget(defaultName);

        targetVersion = null;
        int targetW = Math.min(fullW, Math.max(180, w / 2));
        Button targetDropdown = WbButton.create(x, y + 100, targetW, 23,
                Component.translatable("worldbinder.config.target_dropdown_value", config.targetMinecraftVersion),
                Component.translatable("worldbinder.tooltip.target_version"), b -> {
                    if (!applyFieldsToDraft()) {
                        return;
                    }
                    targetVersionDropdownOpen = !targetVersionDropdownOpen;
                    rebuildConfigWidgets();
                });
        addContentWidget(WbButton.dropdown(targetDropdown, WbTheme.ACCENT, targetVersionDropdownOpen));
        if (targetVersionDropdownOpen) {
            addTargetVersionOptions(config, x, y + 128, fullW);
        }

        int targetDropdownOffset = targetVersionDropdownOpen ? targetVersionDropdownHeight(fullW) + 10 : 0;
        int numericW = Math.max(90, Math.min(130, (fullW - 48) / 3));
        int numericGap = Math.max(18, Math.min(28, (fullW - numericW * 3) / 2));
        int numericY = y + 164 + targetDropdownOffset;
        radiusChunks = field(x, numericY, numericW, Integer.toString(config.roamingRadiusChunks), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radiusChunks);
        minY = field(x + numericW + numericGap + 8, numericY, numericW, Integer.toString(config.captureMinY), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(minY);
        maxY = field(x + (numericW + numericGap) * 2 + 8, numericY, numericW, Integer.toString(config.captureMaxY), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(maxY);

        int gridY = y + 218 + targetDropdownOffset;
        addToggleGrid(x, gridY, w, 2,
                toggleSpec("worldbinder.config.capture_entities", "worldbinder.tooltip.config.entities", () -> config.captureEntities, v -> config.captureEntities = v),
                toggleSpec("worldbinder.config.capture_block_entities", "worldbinder.tooltip.config.blockentities", () -> config.captureBlockEntities, v -> config.captureBlockEntities = v),
                toggleSpec("worldbinder.config.capture_air", "worldbinder.tooltip.config.air", () -> config.captureAir, v -> config.captureAir = v),
                toggleSpec("worldbinder.config.server_resource_pack", "worldbinder.tooltip.config.server_resource_pack", () -> config.includeServerResourcePack, v -> config.includeServerResourcePack = v),
                toggleSpec("worldbinder.config.gamerules", "worldbinder.tooltip.config.gamerules", () -> config.exportGameRules, v -> config.exportGameRules = v),
                toggleSpec("worldbinder.config.append_timestamp", "worldbinder.tooltip.config.append_timestamp", () -> config.appendTimestampToArchiveName, v -> config.appendTimestampToArchiveName = v));
    }

    private void addTargetVersionOptions(WorldBinderConfig config, int x, int y, int w) {
        int cols = targetVersionColumns(w);
        int gap = 5;
        int optionW = Math.max(58, (w - gap * (cols - 1)) / cols);
        int i = 0;
        for (TargetMinecraftVersion.Entry entry : TargetMinecraftVersion.FINAL_RELEASES) {
            int col = i % cols;
            int row = i / cols;
            boolean selected = entry.name().equals(TargetMinecraftVersion.normalize(config.targetMinecraftVersion));
            Button option = WbButton.create(x + col * (optionW + gap), y + row * 22, optionW, 19, entry.name(),
                    Component.translatable("worldbinder.tooltip.target_version"), b -> {
                        if (!applyFieldsToDraft()) {
                            return;
                        }
                        config.targetMinecraftVersion = entry.name();
                        targetVersionDropdownOpen = false;
                        rebuildConfigWidgets();
                    });
            addContentWidget(WbButton.tab(option, WbTheme.ACCENT, selected));
            i++;
        }
    }

    private void initPerformance(WorldBinderConfig config, int x, int y, int w) {
        int cols = w < 420 ? 2 : 4;
        int gap = 8;
        int presetW = Math.max(64, (w - gap * (cols - 1)) / cols);
        int presetY = y + 28;
        addContentWidget(preset(x, presetY, presetW, "worldbinder.gui.preset_safe", WorldBinderConfig.PerformancePreset.SAFE, "worldbinder.tooltip.preset_safe"));
        addContentWidget(preset(x + (presetW + gap), presetY, presetW, "worldbinder.gui.preset_balanced", WorldBinderConfig.PerformancePreset.BALANCED, "worldbinder.tooltip.preset_balanced"));
        addContentWidget(preset(x + (presetW + gap) * (cols == 2 ? 0 : 2), presetY + (cols == 2 ? 30 : 0), presetW, "worldbinder.gui.preset_fast", WorldBinderConfig.PerformancePreset.FAST, "worldbinder.tooltip.preset_fast"));
        addContentWidget(preset(x + (presetW + gap) * (cols == 2 ? 1 : 3), presetY + (cols == 2 ? 30 : 0), presetW, "worldbinder.gui.preset_extreme", WorldBinderConfig.PerformancePreset.EXTREME, "worldbinder.tooltip.preset_extreme"));

        int formY = y + (cols == 2 ? 96 : 78);
        int fieldW = Math.max(62, Math.min(90, (w - 24) / 4));
        int fieldGap = Math.max(8, Math.min(24, (w - fieldW * 4) / 3));
        blocksPerTick = field(x, formY + 18, fieldW, Integer.toString(config.blocksPerTick), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.blocks_per_tick"); addContentWidget(blocksPerTick);
        commandsPerTick = field(x + (fieldW + fieldGap), formY + 18, fieldW, Integer.toString(config.commandsPerTick), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.commands_per_tick"); addContentWidget(commandsPerTick);
        budgetMs = field(x + (fieldW + fieldGap) * 2, formY + 18, fieldW, Integer.toString(config.tickBudgetMillis), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.ms_budget"); addContentWidget(budgetMs);
        targetFps = field(x + (fieldW + fieldGap) * 3, formY + 18, fieldW, Integer.toString(config.targetFps), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.target_fps"); addContentWidget(targetFps);
        newChunks = field(x, formY + 70, fieldW, Integer.toString(config.newChunksPerTick), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.new_chunks"); addContentWidget(newChunks);
        queueLimit = field(x + (fieldW + fieldGap), formY + 70, fieldW, Integer.toString(config.chunkQueueLimit), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.queue_limit"); addContentWidget(queueLimit);
        hotChunks = field(x + (fieldW + fieldGap) * 2, formY + 70, fieldW, Integer.toString(config.hotChunksPerTick), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.hot_chunks"); addContentWidget(hotChunks);
        maxCaptureWorkMs = field(x, formY + 122, fieldW, Integer.toString(config.maxCaptureWorkMs), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.capture_ms"); addContentWidget(maxCaptureWorkMs);
        maxUiWorkMs = field(x + (fieldW + fieldGap), formY + 122, fieldW, Integer.toString(config.maxUiWorkMs), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.ui_ms"); addContentWidget(maxUiWorkMs);
        maxArchiveWorkMs = field(x + (fieldW + fieldGap) * 2, formY + 122, fieldW, Integer.toString(config.maxArchiveWorkMs), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.archive_ms"); addContentWidget(maxArchiveWorkMs);
    }

    private void initHud(WorldBinderConfig config, int x, int y, int w) {
        int editorW = Math.min(w, Math.max(220, w / 2));
        addContentWidget(WbButton.primary(button(
                x,
                y + 48,
                editorW,
                24,
                "worldbinder.config.widget_editor",
                "worldbinder.tooltip.config.widget_editor",
                button -> {
                    if (applyFieldsToDraft()) {
                        minecraft.gui.setScreen(new WorldBinderWidgetEditorScreen(this, draft));
                    }
                }
        )));

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
        addButtonGrid(x, mapY + 30, w, 3,
                modeButton(0, 0, 100, "worldbinder.config.f10_view", () -> config.f10MapLayerMode, v -> config.f10MapLayerMode = v),
                modeButton(0, 0, 100, "worldbinder.config.radar_view", () -> config.radarLayerMode, v -> config.radarLayerMode = v),
                radarDetailButton(0, 0, 100, "worldbinder.config.radar_detail", () -> config.radarDetailMode, v -> config.radarDetailMode = v),
                toggle(0, 0, 100, 22, "worldbinder.config.markers", "worldbinder.tooltip.config.markers", () -> config.showWorldGizmos, v -> config.showWorldGizmos = v));

        int notificationY = mapY + 112;
        notificationDuration = field(x, notificationY + 32, fieldW, Integer.toString(config.notificationDurationPercent), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.notification_duration");
        addContentWidget(notificationDuration);
        int modeY = notificationY + 64;
        int modeW = Math.max(60, (w - 6) / 2);
        int modeIndex = 0;
        for (WorldBinderConfig.MessageMode mode : WorldBinderConfig.MessageMode.values()) {
            Button choice = WbButton.create(x + (modeIndex % 2) * (modeW + 6), modeY + (modeIndex / 2) * 26,
                    modeW, 22, Component.translatable("worldbinder.messages." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    Component.translatable("worldbinder.messages.tooltip"), b -> {
                        if (applyFieldsToDraft()) {
                            config.messageMode = mode;
                            rebuildConfigWidgets();
                        }
                    });
            addContentWidget(WbButton.tab(choice, WbTheme.INFO, config.effectiveMessageMode() == mode));
            modeIndex++;
        }
    }

    private void initMovement(WorldBinderConfig config, int x, int y, int w) {
        if (config.movement == null) config.movement = new MovementSettings();
        int rowY = y + 30;
        for (MovementTool tool : MovementTool.values()) {
            addContentWidget(toggle(x, rowY, w, 22, tool.key(), tool.key() + ".tooltip",
                    () -> config.movement.enabled(tool), enabled -> config.movement.enabled(tool, enabled)));
            if (tool.adjustable()) {
                WbIntegerSlider slider = new WbIntegerSlider(font, x, rowY + 25, w, 22,
                        (int)(tool.min * 10), (int)(tool.max * 10), (int)Math.round(config.movement.value(tool) * 10),
                        WbTheme.OK, value -> config.movement.value(tool, value / 10.0),
                        value -> Component.translatable(tool == MovementTool.STEP
                                ? "worldbinder.movement.blocks" : "worldbinder.movement.multiplier",
                                String.format(java.util.Locale.ROOT, "%.1f", value / 10.0)));
                addContentWidget(WbTooltips.register(slider, Component.translatable(tool.key() + ".tooltip")));
            }
            rowY += 58;
        }
    }

    private void initSafety(WorldBinderConfig config, int x, int y, int w) {
        int contentY = y + 28;
        if (safetyPage == SafetyPage.CORE) initSafetyCore(config, x, contentY, w);
        if (safetyPage == SafetyPage.EXPORT) initSafetyExport(config, x, contentY, w);
        if (safetyPage == SafetyPage.RESOURCE_PACK) initSafetyResourcePack(config, x, contentY, w);
        if (safetyPage == SafetyPage.GAMERULES) initGameRules(config, x, contentY, w);
    }

    private void initSafetyCore(WorldBinderConfig config, int x, int y, int w) {
        int labelW = labelWidth(w);
        recoverySeconds = field(x + labelW, y + 26, Math.min(90, w - labelW), Integer.toString(config.recoveryAutosaveSeconds), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(recoverySeconds);
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
    }

    private void initGameRules(WorldBinderConfig config, int x, int y, int w) {
        int presetW = Math.max(72, (w - 16) / 3);
        addIfVisible(WbButton.primary(button(x, y + 6, presetW, 20, "worldbinder.config.rules.peaceful", "worldbinder.tooltip.rules.safe",
                b -> applyGameRulePreset("doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;keepInventory=true;randomTickSpeed=0"))), y + 6, 20);
        addIfVisible(WbButton.style(button(x + presetW + 8, y + 6, presetW, 20, "worldbinder.config.rules.vanilla", "worldbinder.tooltip.rules.vanilla",
                b -> applyGameRulePreset("doDaylightCycle=true;doWeatherCycle=true;doMobSpawning=true;keepInventory=false;randomTickSpeed=3")), WbButton.Style.DEFAULT, WbTheme.INFO, false), y + 6, 20);
        addIfVisible(WbButton.style(button(x + (presetW + 8) * 2, y + 6, presetW, 20, "worldbinder.config.rules.static", "worldbinder.tooltip.rules.showcase",
                b -> applyGameRulePreset("doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;doFireTick=false;randomTickSpeed=0")), WbButton.Style.DEFAULT, WbTheme.WARN, false), y + 6, 20);

        int sliderY = y + 44;
        addIfVisible(new WbIntegerSlider(
                font,
                x,
                sliderY,
                Math.min(w, 360),
                22,
                0,
                64,
                gameRules.randomTickSpeed(),
                WbTheme.WARN,
                gameRules::setRandomTickSpeed,
                value -> Component.translatable("worldbinder.config.random_tick_speed", value)
        ), sliderY, 22);

        int gridY = y + 82;
        int cols = w < 420 ? 2 : 3;
        int gap = 8;
        int rowH = 22;
        int buttonW = Math.max(68, (w - gap * (cols - 1)) / cols);
        String[] rules = GameRuleSettings.rules();
        for (int i = 0; i < rules.length; i++) {
            String rule = rules[i];
            int col = i % cols;
            int row = i / cols;
            int by = gridY + row * (rowH + 6);
            addIfVisible(gameRuleButton(x + col * (buttonW + gap), by, buttonW, rule), by, rowH);
        }
    }

    private void applyGameRulePreset(String rules) {
        draft.gameRulesOverride = rules;
        gameRules.load(rules);
        rebuildConfigWidgets();
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addContentWidget(T widget) {
        if (widget instanceof WbEditBox styled) {
            widget.visible = isInContentArea(styled.chromeY(), styled.chromeHeight());
        } else {
            widget.visible = isInContentArea(widget.getY(), widget.getHeight());
        }
        addRenderableWidget(widget);
        return widget;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addIfVisible(T widget, int y, int h) {
        widget.visible = isInContentArea(y, h);
        addRenderableWidget(widget);
        return widget;
    }

    private Button preset(int x, int y, int w, String labelKey, WorldBinderConfig.PerformancePreset preset, String tooltipKey) {
        Button button = WbButton.create(x, y, w, 22, Component.translatable(labelKey), Component.translatable(tooltipKey),
                b -> {
                    if (!applyFieldsToDraft()) {
                        return;
                    }
                    draft.applyPreset(preset);
                    minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.PERFORMANCE, SafetyPage.CORE, 0, draft));
                });
        return WbButton.tab(button, WbTheme.ACCENT_RIGHT, draft.performancePreset == preset);
    }

    private Button resourcePackFallbackButton(int x, int y, int w, WorldBinderConfig config) {
        Button button = WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)),
                Component.translatable("worldbinder.tooltip.config.resource_pack_fallback"), b -> {
            config.resourcePackFallbackMode = nextFallbackMode(config.resourcePackFallbackMode);
            b.setMessage(fit(Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)), b.getWidth()));
        });
        return WbButton.dropdown(button, WbTheme.WARN, false);
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
        return WbButton.dropdown(button, WbTheme.INFO, false);
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
        return WbButton.dropdown(button, WbTheme.INFO, false);
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
        Button button = WbButton.create(x, y, w, 22, Component.literal(rule + " " + Lang.string(enabled ? "worldbinder.common.on" : "worldbinder.common.off")),
                Component.literal(rule), b -> {
            boolean next = gameRules.toggle(rule);
            b.setMessage(fit(Component.literal(rule + " " + Lang.string(next ? "worldbinder.common.on" : "worldbinder.common.off")), b.getWidth()));
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

    private void addButtonGrid(int x, int y, int w, int requestedCols, Button... buttons) {
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

    private boolean saveConfig() {
        if (!applyFieldsToDraft()) {
            return false;
        }
        WorldBinder.replaceConfig(draft);
        draft.save();
        return true;
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
        if (targetVersion != null) config.targetMinecraftVersion = TargetMinecraftVersion.normalize(targetVersion.getValue());
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
        setFocused(null);
        clearWidgets();
        init();
    }

    @Override
    protected int contentHeight(int w) {
        if (tab == Tab.GENERAL) return 308 + (targetVersionDropdownOpen ? targetVersionDropdownHeight(Math.max(120, Math.min(w, 520))) + 10 : 0);
        if (tab == Tab.PERFORMANCE) return 330;
        if (tab == Tab.HUD) return (w < 430 ? 500 : 452);
        if (tab == Tab.MOVEMENT) return 30 + MovementTool.values().length * 58;
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.GAMERULES) {
            int cols = w < 420 ? 2 : 3;
            int rows = (GameRuleSettings.rules().length + cols - 1) / cols;
            return 112 + rows * 28 - 6;
        }
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.CORE) return 180;
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.RESOURCE_PACK) return 164;
        if (tab == Tab.SAFETY) return 144;
        return 320;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
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

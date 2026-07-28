package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldBinderConfigScreen extends Screen {
    private static final int NUMERIC_TEXT_MAX_LENGTH = 16;
    private static final int MIN_CONTENT_WIDTH = 180;
    private static final int SETTINGS_SIDEBAR_WIDTH = 178;

    private enum Tab { GENERAL, PERFORMANCE, HUD, SAFETY }
    private enum SafetyPage { CORE, EXPORT, RESOURCE_PACK, GAMERULES }

    private final Screen parent;
    private final Tab tab;
    private final SafetyPage safetyPage;
    private final WorldBinderConfig draft;

    private EditBox defaultName;
    private EditBox targetVersion;
    private EditBox radiusChunks;
    private EditBox minY;
    private EditBox maxY;
    private EditBox blocksPerTick;
    private EditBox commandsPerTick;
    private EditBox budgetMs;
    private EditBox newChunks;
    private EditBox queueLimit;
    private EditBox hotChunks;
    private EditBox targetFps;
    private EditBox maxUiWorkMs;
    private EditBox maxCaptureWorkMs;
    private EditBox maxArchiveWorkMs;
    private EditBox radarSize;
    private EditBox radarCell;
    private EditBox radarMaxChunks;
    private EditBox radarUpdateRate;
    private EditBox notificationDuration;
    private EditBox recoverySeconds;
    private int randomTickSpeed;
    private final Map<String, Boolean> gameRuleValues = new LinkedHashMap<>();
    private final Map<String, String> unknownGameRuleValues = new LinkedHashMap<>();
    private boolean gameRulesDirty;
    private int scrollOffset;
    private int maxScroll;
    private boolean targetVersionDropdownOpen;
    private Component validationError;

    private static final String[] BOOLEAN_GAMERULES = {
            "announceAdvancements", "commandBlockOutput", "disableElytraMovementCheck", "disableRaids",
            "doDaylightCycle", "doEntityDrops", "doFireTick", "doImmediateRespawn", "doInsomnia",
            "doLimitedCrafting", "doMobLoot", "doMobSpawning", "doPatrolSpawning", "doTileDrops",
            "doTraderSpawning", "doVinesSpread", "doWardenSpawning", "doWeatherCycle", "drowningDamage",
            "fallDamage", "fireDamage", "forgiveDeadPlayers", "keepInventory", "logAdminCommands",
            "mobGriefing", "naturalRegeneration", "reducedDebugInfo", "sendCommandFeedback",
            "showDeathMessages", "spectatorsGenerateChunks", "universalAnger"
    };

    public WorldBinderConfigScreen(Screen parent) {
        this(parent, Tab.GENERAL, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
    }

    public static WorldBinderConfigScreen performance(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.PERFORMANCE, SafetyPage.CORE, 0, copyConfig(WorldBinder.config()));
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
        super(Component.translatable("worldbinder.config.title"));
        this.parent = parent;
        this.tab = tab;
        this.safetyPage = safetyPage;
        this.scrollOffset = Math.max(0, scrollOffset);
        this.draft = draft == null ? copyConfig(WorldBinder.config()) : draft;
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
        loadGameRules(config.gameRulesOverride);

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
                toggleSpec("worldbinder.config.delete_old_recovery", "worldbinder.tooltip.config.delete_old_recovery", () -> config.autoDeleteRecovery, v -> config.autoDeleteRecovery = v),
                toggleSpec("worldbinder.config.chat_feedback", "worldbinder.tooltip.config.chat", () -> config.showDetailedChatFeedback, v -> config.showDetailedChatFeedback = v));
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
        addIfVisible(new GameRuleSlider(x, sliderY, Math.min(w, 360), 22), sliderY, 22);

        int gridY = y + 82;
        int cols = w < 420 ? 2 : 3;
        int gap = 8;
        int rowH = 22;
        int buttonW = Math.max(68, (w - gap * (cols - 1)) / cols);
        for (int i = 0; i < BOOLEAN_GAMERULES.length; i++) {
            String rule = BOOLEAN_GAMERULES[i];
            int col = i % cols;
            int row = i / cols;
            int by = gridY + row * (rowH + 6);
            addIfVisible(gameRuleButton(x + col * (buttonW + gap), by, buttonW, rule), by, rowH);
        }
    }

    private void applyGameRulePreset(String rules) {
        draft.gameRulesOverride = rules;
        loadGameRules(rules);
        rebuildConfigWidgets();
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addContentWidget(T widget) {
        if (widget instanceof StyledEditBox styled) {
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

    private boolean isInContentArea(int y, int h) {
        return y >= contentClipTop() && y + h <= contentClipBottom();
    }

    private boolean isTextInContentArea(int y) {
        return y >= contentClipTop() && y + font.lineHeight <= contentClipBottom();
    }

    private int contentClipTop() {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int top = WbLayout.top(height, panelHeight);
        return contentBaseY(top, panelWidth) + 18;
    }

    private int contentClipBottom() {
        int panelHeight = panelHeight();
        int top = WbLayout.top(height, panelHeight);
        return top + panelHeight - 50;
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
        boolean enabled = gameRuleValues.getOrDefault(rule, defaultGameRule(rule));
        Button button = WbButton.create(x, y, w, 22, Component.literal(rule + " " + Lang.string(enabled ? "worldbinder.common.on" : "worldbinder.common.off")),
                Component.literal(rule), b -> {
            boolean next = !gameRuleValues.getOrDefault(rule, defaultGameRule(rule));
            gameRuleValues.put(rule, next);
            gameRulesDirty = true;
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
        EditBox f = new StyledEditBox(x, y, Math.max(36, w), 22);
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

    private int panelWidth() {
        return WbLayout.panelWidth(width);
    }

    private int panelHeight() {
        return WbLayout.panelHeight(height);
    }

    private boolean compact() {
        return WbLayout.compact(panelWidth(), panelHeight());
    }

    private boolean useSidebar(int panelWidth) {
        return panelWidth >= 700;
    }

    private int contentAreaX(int left, int panelWidth) {
        return useSidebar(panelWidth) ? left + SETTINGS_SIDEBAR_WIDTH + 20 : left + 24;
    }

    private int contentAreaWidth(int panelWidth) {
        int reserved = useSidebar(panelWidth) ? SETTINGS_SIDEBAR_WIDTH + 44 : 48;
        return Math.max(MIN_CONTENT_WIDTH, panelWidth - reserved);
    }

    private int contentBaseY(int top, int panelWidth) {
        if (useSidebar(panelWidth)) {
            return top + (tab == Tab.SAFETY ? 102 : 68);
        }
        int navHeight = navRows(panelWidth) * 26;
        int safetyHeight = tab == Tab.SAFETY ? (panelWidth < 430 ? 54 : 28) : 0;
        return top + (compact() ? 52 : 62) + navHeight + safetyHeight + 20;
    }

    private int navColumns(int panelWidth) {
        return panelWidth < 430 ? 2 : 4;
    }

    private int navRows(int panelWidth) {
        return (Tab.values().length + navColumns(panelWidth) - 1) / navColumns(panelWidth);
    }

    private int targetVersionColumns(int width) {
        if (width < 260) {
            return 2;
        }
        if (width < 430) {
            return 3;
        }
        return 4;
    }

    private int targetVersionDropdownHeight(int width) {
        int cols = targetVersionColumns(width);
        int rows = (TargetMinecraftVersion.FINAL_RELEASES.size() + cols - 1) / cols;
        return rows * 22 + 2;
    }

    private int labelWidth(int contentW) {
        return Math.min(190, Math.max(96, contentW / 3));
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
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
        WbChrome.drawBackdrop(context, width, height);
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
        drawPanel(context, left, top, panelWidth, panelHeight);
        drawSettingsNavigationChrome(context, left, top, panelWidth, virtualMouseX, virtualMouseY);
        if (useSidebar(panelWidth)) {
            GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.config.header"), contentX, top + 14, WbTheme.TEXT);
        } else {
            GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.config.header"), left + panelWidth / 2, top + 14, WbTheme.TEXT);
        }
        if (panelHeight > 390 && panelWidth > 520) {
            if (useSidebar(panelWidth)) {
                GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.config.subheader"), contentX, top + 31, WbTheme.TEXT_DIM);
            } else {
                GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.config.subheader"), left + panelWidth / 2, top + 32, WbTheme.TEXT_DIM);
            }
        }
        int cardBottom = top + panelHeight - 48;
        drawCard(context, contentX - 8, contentBaseY - 10, contentW + 16, Math.max(120, cardBottom - contentBaseY + 10), Component.translatable(tabTitle()), tabAccent(tab));
        context.enableScissor(contentX - 8, contentClipTop(), contentX + contentW + 16, contentClipBottom());
        try {
            drawContentSections(context, contentX, contentY, contentW);
            if (tab == Tab.GENERAL) drawGeneralLabels(context, formX, contentY, formW);
            if (tab == Tab.PERFORMANCE) drawPerformanceLabels(context, formX, contentY, formW);
            if (tab == Tab.HUD) drawHudLabels(context, formX, contentY, formW);
            if (tab == Tab.SAFETY) drawSafetyLabels(context, formX, contentY, formW);
            drawTargetVersionDropdown(context, formX, contentY, formW, virtualMouseX, virtualMouseY);
            drawInputChrome(context, virtualMouseX, virtualMouseY);
        } finally {
            context.disableScissor();
        }
        WbChrome.drawDivider(context, left + 14, top + panelHeight - 41, panelWidth - 28);
        if (validationError != null) {
            WbText.drawClipped(context, font, validationError, left + 18, top + panelHeight - 26, Math.max(80, panelWidth - 246), WbTheme.ERROR);
        }
        int viewportHeight = Math.max(1, contentClipBottom() - contentClipTop());
        WbChrome.drawScrollBar(context, contentX + contentW + 9, contentClipTop(), viewportHeight, scrollOffset, maxScroll, viewportHeight);
        super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
    }

    private String tabTitle() {
        return switch (tab) {
            case GENERAL -> "worldbinder.config.general_export";
            case PERFORMANCE -> "worldbinder.config.performance_presets";
            case HUD -> "worldbinder.config.hud_title";
            case SAFETY -> safetyTitle();
        };
    }

    private int tabAccent(Tab value) {
        return switch (value) {
            case GENERAL -> WbTheme.ACCENT;
            case PERFORMANCE -> WbTheme.ACCENT_RIGHT;
            case HUD -> WbTheme.INFO;
            case SAFETY -> WbTheme.WARN;
        };
    }

    private String tabLabelKey(Tab value) {
        return switch (value) {
            case GENERAL -> "worldbinder.config.tab.general";
            case PERFORMANCE -> "worldbinder.config.tab.performance";
            case HUD -> "worldbinder.config.tab.hud";
            case SAFETY -> "worldbinder.config.tab.safety";
        };
    }

    private String safetyTitle() {
        return switch (safetyPage) {
            case CORE -> "worldbinder.config.safety.page.core";
            case EXPORT -> "worldbinder.config.safety.page.export";
            case RESOURCE_PACK -> "worldbinder.config.safety.page.pack";
            case GAMERULES -> "worldbinder.config.safety.page.gamerules";
        };
    }

    private String safetyLabelKey(SafetyPage value) {
        return switch (value) {
            case CORE -> "worldbinder.config.safety.page.core";
            case EXPORT -> "worldbinder.config.safety.page.export";
            case RESOURCE_PACK -> "worldbinder.config.safety.page.pack";
            case GAMERULES -> "worldbinder.config.safety.page.gamerules";
        };
    }

    private void drawGeneralLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        int targetDropdownOffset = targetVersionDropdownOpen ? targetVersionDropdownHeight(fullW) + 10 : 0;
        int numericW = Math.max(90, Math.min(130, (fullW - 48) / 3));
        int numericGap = Math.max(18, Math.min(28, (fullW - numericW * 3) / 2));
        label(c, x, y + 34, "worldbinder.gui.archive_name");
        label(c, x, y + 86, "worldbinder.gui.target_output_version");
        clippedLabel(c, x, y + 150 + targetDropdownOffset, numericW, "worldbinder.config.radius");
        clippedLabel(c, x + numericW + numericGap + 8, y + 150 + targetDropdownOffset, numericW, "worldbinder.config.y_min");
        clippedLabel(c, x + (numericW + numericGap) * 2 + 8, y + 150 + targetDropdownOffset, numericW, "worldbinder.config.y_max");
        wrapped(c, x, y + 128 + targetDropdownOffset, w, "worldbinder.config.target_value", draft.targetVersionLabel());
    }

    private void drawPerformanceLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int cols = w < 420 ? 2 : 4;
        int formY = y + (cols == 2 ? 96 : 78);
        int fieldW = Math.max(62, Math.min(90, (w - 24) / 4));
        int fieldGap = Math.max(8, Math.min(24, (w - fieldW * 4) / 3));

        performanceLabel(c, x, formY, fieldW, "worldbinder.config.blocks_per_tick");
        performanceLabel(c, x + (fieldW + fieldGap), formY, fieldW, "worldbinder.config.commands_per_tick");
        performanceLabel(c, x + (fieldW + fieldGap) * 2, formY, fieldW, "worldbinder.config.ms_budget");
        performanceLabel(c, x + (fieldW + fieldGap) * 3, formY, fieldW, "worldbinder.config.target_fps");

        performanceLabel(c, x, formY + 52, fieldW, "worldbinder.config.new_chunks");
        performanceLabel(c, x + (fieldW + fieldGap), formY + 52, fieldW, "worldbinder.config.queue_limit");
        performanceLabel(c, x + (fieldW + fieldGap) * 2, formY + 52, fieldW, "worldbinder.config.hot_chunks");

        performanceLabel(c, x, formY + 104, fieldW, "worldbinder.config.capture_ms");
        performanceLabel(c, x + (fieldW + fieldGap), formY + 104, fieldW, "worldbinder.config.ui_ms");
        performanceLabel(c, x + (fieldW + fieldGap) * 2, formY + 104, fieldW, "worldbinder.config.archive_ms");

        if (isTextInContentArea(y + 252)) WbText.drawWrapped(c, font, draft.presetDescription(), x, y + 252, w, WbTheme.TEXT_DIM, 2);
    }

    private void performanceLabel(GuiGraphicsExtractor c, int x, int y, int width, String text) {
        clippedLabel(c, x, y, width + 18, text);
    }

    private void drawHudLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int gap = 12;
        int cols = w < 430 ? 2 : 4;
        int fieldW = Math.max(70, Math.min(112, (w - gap * (cols - 1)) / cols));
        label(c, x, y + 32, "worldbinder.config.widget_editor_section");
        wrapped(c, x + Math.min(w, Math.max(220, w / 2)) + 14, y + 48, Math.max(80, w - Math.min(w, Math.max(220, w / 2)) - 14), "worldbinder.config.widget_editor_hint");

        int radarLabelY = y + 116;
        clippedLabel(c, x, radarLabelY, fieldW, "worldbinder.config.radar_size");
        clippedLabel(c, x + fieldW + gap, radarLabelY, fieldW, "worldbinder.config.radar_cell");
        int secondRowLabelY = cols == 2 ? radarLabelY + 52 : radarLabelY;
        int secondRowX = cols == 2 ? x : x + (fieldW + gap) * 2;
        clippedLabel(c, secondRowX, secondRowLabelY, fieldW, "worldbinder.config.radar_limit");
        clippedLabel(c, secondRowX + fieldW + gap, secondRowLabelY, fieldW, "worldbinder.config.radar_update_rate");

        int mapY = y + (cols == 2 ? 244 : 196);
        label(c, x, mapY + 14, "worldbinder.config.map_layers_lod");

        int notificationY = mapY + 112;
        label(c, x, notificationY, "worldbinder.config.notifications_section");
        clippedLabel(c, x, notificationY + 18, fieldW, "worldbinder.config.notification_duration");
    }

    private void drawSafetyLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int subY = y + 28;
        if (safetyPage == SafetyPage.CORE) {
            label(c, x, subY + 30, "worldbinder.config.recovery_interval");
        } else if (safetyPage == SafetyPage.RESOURCE_PACK) {
            if (isTextInContentArea(subY + 96)) WbText.drawWrapped(c, font, Lang.string("worldbinder.config.pack_page_hint"), x, subY + 96, w, WbTheme.TEXT_DIM, 3);
        } else if (safetyPage == SafetyPage.GAMERULES) {
        }
    }

    private void label(GuiGraphicsExtractor c, int x, int y, String text) {
        clippedLabel(c, x, y, Math.max(80, panelWidth() - 80), text);
    }

    private void clippedLabel(GuiGraphicsExtractor c, int x, int y, int width, String text) {
        if (!isTextInContentArea(y)) return;
        WbText.drawClipped(c, font, Lang.string(text), x, y, width, WbTheme.TEXT);
    }

    private void wrapped(GuiGraphicsExtractor c, int x, int y, int width, String text, Object... args) {
        if (!isTextInContentArea(y)) return;
        WbText.drawWrapped(c, font, Lang.string(text, args), x, y, width, WbTheme.TEXT_DIM, 2);
    }

    private void drawPanel(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        WbChrome.drawPanel(c, x, y, w, h);
    }

    private void drawCard(GuiGraphicsExtractor c, int x, int y, int w, int h, Component title, int accent) {
        WbChrome.drawCard(c, font, x, y, w, h, title, accent, false);
    }

    private void drawContentSections(GuiGraphicsExtractor c, int x, int y, int w) {
        int innerW = Math.max(80, w - 8);
        if (tab == Tab.GENERAL) {
            int dropdownOffset = targetVersionDropdownOpen ? targetVersionDropdownHeight(Math.max(120, Math.min(w, 520))) + 10 : 0;
            drawSectionSurface(c, x + 4, y + 26, innerW, 174 + dropdownOffset, WbTheme.ACCENT);
            drawSectionSurface(c, x + 4, y + 208 + dropdownOffset, innerW, 98, WbTheme.ACCENT_DARK);
        } else if (tab == Tab.PERFORMANCE) {
            drawSectionSurface(c, x + 4, y + 18, innerW, 56, WbTheme.ACCENT_RIGHT);
            drawSectionSurface(c, x + 4, y + 82, innerW, 166, WbTheme.ACCENT_DARK);
            drawSectionSurface(c, x + 4, y + 254, innerW, 48, WbTheme.INFO);
        } else if (tab == Tab.HUD) {
            int formW = Math.max(MIN_CONTENT_WIDTH - 24, w - 24);
            int cols = formW < 430 ? 2 : 4;
            int mapY = y + (cols == 2 ? 244 : 196);
            int notificationY = mapY + 112;
            drawSectionSurface(c, x + 4, y + 26, innerW, 58, WbTheme.INFO);
            drawSectionSurface(c, x + 4, y + 94, innerW, cols == 2 ? 130 : 78, WbTheme.ACCENT_DARK);
            drawSectionSurface(c, x + 4, mapY + 6, innerW, 94, WbTheme.ACCENT);
            drawSectionSurface(c, x + 4, notificationY - 8, innerW, 74, WbTheme.INFO);
        } else if (safetyPage == SafetyPage.CORE) {
            drawSectionSurface(c, x + 4, y + 26, innerW, 142, WbTheme.WARN);
        } else if (safetyPage == SafetyPage.EXPORT) {
            drawSectionSurface(c, x + 4, y + 28, innerW, 96, WbTheme.ACCENT_RIGHT);
        } else if (safetyPage == SafetyPage.RESOURCE_PACK) {
            drawSectionSurface(c, x + 4, y + 28, innerW, 124, WbTheme.WARN);
        }
    }

    private void drawSectionSurface(GuiGraphicsExtractor c, int x, int y, int w, int h, int accent) {
        int clippedTop = Math.max(y, contentClipTop());
        int clippedBottom = Math.min(y + h, contentClipBottom());
        if (clippedBottom - clippedTop < 3) {
            return;
        }
        WbChrome.drawSectionSurface(c, x, clippedTop, w, clippedBottom - clippedTop, accent);
    }

    private void drawInputChrome(GuiGraphicsExtractor c, int mouseX, int mouseY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (!(child instanceof EditBox field) || !field.visible) {
                continue;
            }
            boolean hovered = field.isMouseOver(mouseX, mouseY);
            int accent = isInvalidNumericField(field) ? WbTheme.ERROR : tabAccent(tab);
            if (field instanceof StyledEditBox styled) {
                WbChrome.drawField(c, styled.chromeX(), styled.chromeY(), styled.chromeWidth(), styled.chromeHeight(), accent, field.isFocused(), hovered);
            } else {
                WbChrome.drawField(c, field.getX(), field.getY(), field.getWidth(), field.getHeight(), accent, field.isFocused(), hovered);
            }
        }
    }

    private void drawSettingsNavigationChrome(GuiGraphicsExtractor c, int left, int top, int panelWidth, int mouseX, int mouseY) {
        if (useSidebar(panelWidth)) {
            int sidebarX = left + 8;
            int sidebarY = top + 49;
            int sidebarW = SETTINGS_SIDEBAR_WIDTH - 8;
            int sidebarH = panelHeight() - 99;
            WbChrome.drawSectionSurface(c, sidebarX, sidebarY, sidebarW, sidebarH, tabAccent(tab));
            WbText.draw(c, font, "WORLDBINDER", sidebarX + 12, sidebarY + 12, WbTheme.TEXT);
            WbText.draw(c, font, Component.translatable("worldbinder.config.navigation"), sidebarX + 12, sidebarY + 27, WbTheme.TEXT_DIM);
            WbChrome.drawDivider(c, sidebarX + 12, sidebarY + 42, sidebarW - 24);
            WbText.drawWrapped(c, font, Component.translatable(tabDescriptionKey(tab)), sidebarX + 12, sidebarY + sidebarH - 52, sidebarW - 24, WbTheme.TEXT_DIM, 3);
            return;
        }

        int y = top + (compact() ? 42 : 52);
        int cols = navColumns(panelWidth);
        int gap = 6;
        int navW = panelWidth - 36;
        WbChrome.drawSectionSurface(c, left + 18, y - 4, navW, navRows(panelWidth) * 26 + 5, tabAccent(tab));
        if (tab != Tab.SAFETY) {
            return;
        }
        int safetyY = y + navRows(panelWidth) * 26 + 6;
        int safetyRows = panelWidth < 430 ? 2 : 1;
        WbChrome.drawSectionSurface(c, left + 18, safetyY - 4, navW, safetyRows * 24 + 5, WbTheme.WARN);
    }

    private String tabDescriptionKey(Tab value) {
        return switch (value) {
            case GENERAL -> "worldbinder.config.navigation.general";
            case PERFORMANCE -> "worldbinder.config.navigation.performance";
            case HUD -> "worldbinder.config.navigation.hud";
            case SAFETY -> "worldbinder.config.navigation.safety";
        };
    }

    private void drawTargetVersionDropdown(GuiGraphicsExtractor c, int x, int y, int w, int mouseX, int mouseY) {
        if (tab != Tab.GENERAL || !targetVersionDropdownOpen) {
            return;
        }
        int fullW = Math.max(120, Math.min(w, 520));
        int dropdownY = y + 128;
        int cols = targetVersionColumns(fullW);
        int gap = 5;
        int optionW = Math.max(58, (fullW - gap * (cols - 1)) / cols);
        int rows = (TargetMinecraftVersion.FINAL_RELEASES.size() + cols - 1) / cols;
        WbChrome.drawDropdownPanel(c, x, dropdownY - 4, fullW, 22, rows);
        for (int i = 0; i < TargetMinecraftVersion.FINAL_RELEASES.size(); i++) {
            TargetMinecraftVersion.Entry entry = TargetMinecraftVersion.FINAL_RELEASES.get(i);
            int col = i % cols;
            int row = i / cols;
            int rowX = x + col * (optionW + gap);
            int rowY = dropdownY + row * 22;
            boolean selected = entry.name().equals(TargetMinecraftVersion.normalize(draft.targetMinecraftVersion));
            boolean hovered = WbChrome.contains(rowX, rowY, optionW, 19, mouseX, mouseY);
            WbChrome.drawDropdownRow(c, rowX, rowY, optionW, 19, selected, hovered, selected ? WbTheme.ACCENT : WbTheme.INFO);
        }
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
        if (gameRulesDirty) {
            config.gameRulesOverride = buildGameRuleOverride();
            gameRulesDirty = false;
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

    private boolean isInvalidNumericField(EditBox field) {
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

    private void loadGameRules(String raw) {
        gameRuleValues.clear();
        unknownGameRuleValues.clear();
        gameRulesDirty = false;
        for (String rule : BOOLEAN_GAMERULES) {
            gameRuleValues.put(rule, defaultGameRule(rule));
        }
        randomTickSpeed = 3;
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(";")) {
            String[] split = part.split("=", 2);
            if (split.length != 2) continue;
            String key = split[0].trim();
            String value = split[1].trim();
            if ("randomTickSpeed".equals(key)) {
                Integer parsed = parseInt(value);
                randomTickSpeed = parsed == null ? randomTickSpeed : Math.max(0, Math.min(64, parsed));
            } else if (gameRuleValues.containsKey(key)) {
                gameRuleValues.put(key, Boolean.parseBoolean(value));
            } else if (key.matches("[A-Za-z0-9_.-]+") && !value.isBlank()) {
                unknownGameRuleValues.put(key, value);
            }
        }
    }

    private String buildGameRuleOverride() {
        StringBuilder builder = new StringBuilder();
        for (String rule : BOOLEAN_GAMERULES) {
            if (!builder.isEmpty()) builder.append(';');
            builder.append(rule).append('=').append(gameRuleValues.getOrDefault(rule, defaultGameRule(rule)));
        }
        if (!builder.isEmpty()) builder.append(';');
        builder.append("randomTickSpeed=").append(randomTickSpeed);
        for (Map.Entry<String, String> entry : unknownGameRuleValues.entrySet()) {
            builder.append(';').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static boolean defaultGameRule(String rule) {
        return switch (rule) {
            case "announceAdvancements", "commandBlockOutput", "doDaylightCycle", "doEntityDrops",
                    "doFireTick", "doInsomnia", "doMobLoot", "doMobSpawning", "doPatrolSpawning",
                    "doTileDrops", "doTraderSpawning", "doVinesSpread", "doWardenSpawning",
                    "doWeatherCycle", "drowningDamage", "fallDamage", "fireDamage",
                    "forgiveDeadPlayers", "logAdminCommands", "mobGriefing", "naturalRegeneration",
                    "sendCommandFeedback", "showDeathMessages", "spectatorsGenerateChunks" -> true;
            default -> false;
        };
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

    private int contentHeight(int w) {
        if (tab == Tab.GENERAL) return 308 + (targetVersionDropdownOpen ? targetVersionDropdownHeight(Math.max(120, Math.min(w, 520))) + 10 : 0);
        if (tab == Tab.PERFORMANCE) return 330;
        if (tab == Tab.HUD) return (w < 430 ? 444 : 396);
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.GAMERULES) {
            int cols = w < 420 ? 2 : 3;
            int rows = (BOOLEAN_GAMERULES.length + cols - 1) / cols;
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

    private final class StyledEditBox extends EditBox {
        private final int chromeX;
        private final int chromeY;
        private final int chromeWidth;
        private final int chromeHeight;

        private StyledEditBox(int x, int y, int width, int height) {
            super(font, x + 6, y + 6, Math.max(24, width - 12), Math.max(10, height - 10), Component.empty());
            chromeX = x;
            chromeY = y;
            chromeWidth = width;
            chromeHeight = height;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return visible
                    && mouseX >= chromeX
                    && mouseY >= chromeY
                    && mouseX < chromeX + chromeWidth
                    && mouseY < chromeY + chromeHeight;
        }

        private int chromeX() {
            return chromeX;
        }

        private int chromeY() {
            return chromeY;
        }

        private int chromeWidth() {
            return chromeWidth;
        }

        private int chromeHeight() {
            return chromeHeight;
        }
    }

    private final class GameRuleSlider extends AbstractSliderButton {
        GameRuleSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), Math.max(0.0D, Math.min(1.0D, randomTickSpeed / 64.0D)));
            updateMessage();
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            boolean highlighted = isHoveredOrFocused();
            int accent = active ? WbTheme.WARN : WbTheme.TEXT_DISABLED;
            int trackX = x + 8;
            int trackWidth = Math.max(1, width - 16);
            int trackY = y + height - 6;
            int filled = (int) Math.round(trackWidth * value);

            context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x66000000);
            context.fill(x, y, x + width, y + height, active ? 0xAA43566B : 0x55374655);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, highlighted ? WbTheme.BUTTON_HOVER : WbTheme.BUTTON);
            context.fill(trackX, trackY, trackX + trackWidth, trackY + 2, 0xAA293746);
            context.fill(trackX, trackY, trackX + filled, trackY + 2, accent);
            int knobX = Math.max(trackX, Math.min(trackX + trackWidth - 4, trackX + filled - 2));
            context.fill(knobX, trackY - 2, knobX + 4, trackY + 4, accent);
            if (isFocused() && active) {
                context.fill(x, y, x + width, y + 1, WbTheme.FOCUS_RING);
                context.fill(x, y + height - 1, x + width, y + height, WbTheme.FOCUS_RING);
            }
            GuiText.drawCenteredTextWithShadow(context, font, getMessage(), x + width / 2, y + 3, active ? WbTheme.TEXT_SOFT : WbTheme.TEXT_DISABLED);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("worldbinder.config.random_tick_speed", randomTickSpeed));
        }

        @Override
        protected void applyValue() {
            int next = (int) Math.round(value * 64.0D);
            if (next != randomTickSpeed) {
                randomTickSpeed = next;
                gameRulesDirty = true;
            }
        }
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

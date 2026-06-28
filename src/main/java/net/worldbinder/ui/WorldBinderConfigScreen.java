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

    private enum Tab { GENERAL, PERFORMANCE, HUD, SAFETY }
    private enum SafetyPage { CORE, EXPORT, RESOURCE_PACK, GAMERULES }

    private final Screen parent;
    private final Tab tab;
    private final SafetyPage safetyPage;

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
    private EditBox radarX;
    private EditBox radarY;
    private EditBox radarScale;
    private EditBox radarMaxChunks;
    private EditBox radarUpdateRate;
    private EditBox bossScale;
    private EditBox bossY;
    private EditBox recoverySeconds;
    private int randomTickSpeed;
    private final Map<String, Boolean> gameRuleValues = new LinkedHashMap<>();
    private int scrollOffset;
    private int maxScroll;
    private boolean targetVersionDropdownOpen;

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
        this(parent, Tab.GENERAL, SafetyPage.CORE);
    }

    public static WorldBinderConfigScreen performance(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.PERFORMANCE, SafetyPage.CORE);
    }

    public static WorldBinderConfigScreen general(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.GENERAL, SafetyPage.CORE);
    }

    public static WorldBinderConfigScreen hud(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.HUD, SafetyPage.CORE);
    }

    public static WorldBinderConfigScreen safety(Screen parent) {
        return new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.CORE);
    }

    private WorldBinderConfigScreen(Screen parent, Tab tab) {
        this(parent, tab, SafetyPage.CORE);
    }

    private WorldBinderConfigScreen(Screen parent, Tab tab, SafetyPage safetyPage) {
        this(parent, tab, safetyPage, 0);
    }

    private WorldBinderConfigScreen(Screen parent, Tab tab, SafetyPage safetyPage, int scrollOffset) {
        super(Component.translatable("worldbinder.config.title"));
        this.parent = parent;
        this.tab = tab;
        this.safetyPage = safetyPage;
        this.scrollOffset = Math.max(0, scrollOffset);
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
        WorldBinderConfig config = WorldBinder.config();
        loadGameRules(config.gameRulesOverride);

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);

        int contentX = left + 24;
        int contentBaseY = contentBaseY(top, panelWidth);
        int contentW = Math.max(MIN_CONTENT_WIDTH, panelWidth - 48);
        int contentBottom = top + panelHeight - 48;
        maxScroll = Math.max(0, contentHeight(contentW) - Math.max(80, contentBottom - contentBaseY));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int contentY = contentBaseY - scrollOffset;
        if (tab == Tab.GENERAL) initGeneral(config, contentX, contentY, contentW);
        if (tab == Tab.PERFORMANCE) initPerformance(config, contentX, contentY, contentW);
        if (tab == Tab.HUD) initHud(config, contentX, contentY, contentW);
        if (tab == Tab.SAFETY) initSafety(config, contentX, contentY, contentW);

        int buttonW = compact() ? 76 : 92;
        int bottomY = top + panelHeight - 32;
        addRenderableWidget(button(left + panelWidth - buttonW * 2 - 26, bottomY, buttonW, 22, "worldbinder.config.save", "worldbinder.tooltip.config.save", button -> {
            saveConfig();
            minecraft.gui.setScreen(parent);
        }));
        addRenderableWidget(button(left + panelWidth - buttonW - 16, bottomY, buttonW, 22, "worldbinder.config.cancel", "worldbinder.tooltip.config.back", button -> minecraft.gui.setScreen(parent)));
        addSettingsNavigation(left, top, panelWidth);
    }

    private void addSettingsNavigation(int left, int top, int panelWidth) {
        int y = top + (compact() ? 42 : 52);
        int cols = navColumns(panelWidth);
        int gap = 6;
        int tabW = Math.max(72, (panelWidth - 36 - gap * (cols - 1)) / cols);
        int tabX = left + 18;
        int index = 0;
        for (Tab target : Tab.values()) {
            int col = index % cols;
            int row = index / cols;
            String label = (tab == target ? "◆ " : "") + Lang.string(tabLabelKey(target));
            addRenderableWidget(WbButton.create(tabX + col * (tabW + gap), y + row * 26, tabW, 22, label,
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(tabLabelKey(target))),
                    button -> minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, target, target == Tab.SAFETY ? safetyPage : SafetyPage.CORE))));
            index++;
        }
        if (tab != Tab.SAFETY) {
            return;
        }
        int safetyY = y + navRows(panelWidth) * 26 + 6;
        int safetyCols = panelWidth < 560 ? 2 : 4;
        int safetyW = Math.max(90, (panelWidth - 36 - gap * (safetyCols - 1)) / safetyCols);
        int safetyIndex = 0;
        for (SafetyPage target : SafetyPage.values()) {
            int col = safetyIndex % safetyCols;
            int row = safetyIndex / safetyCols;
            String label = (safetyPage == target ? "◆ " : "") + Lang.string(safetyLabelKey(target));
            addRenderableWidget(WbButton.create(tabX + col * (safetyW + gap), safetyY + row * 24, safetyW, 20, label,
                    Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(safetyLabelKey(target))),
                    button -> minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, target))));
            safetyIndex++;
        }
    }

    private void initGeneral(WorldBinderConfig config, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        defaultName = field(x, y + 48, fullW, config.defaultArchiveName, 64); addContentWidget(defaultName);

        targetVersion = null;
        int targetW = Math.min(fullW, Math.max(180, w / 2));
        addContentWidget(WbButton.create(x, y + 100, targetW, 23,
                Component.translatable("worldbinder.config.target_dropdown_value", config.targetMinecraftVersion),
                Component.translatable("worldbinder.tooltip.target_version"), b -> {
                    targetVersionDropdownOpen = !targetVersionDropdownOpen;
                    rebuildConfigWidgets();
                }));
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
            String label = (entry.name().equals(TargetMinecraftVersion.normalize(config.targetMinecraftVersion)) ? "◆ " : "") + entry.name();
            addContentWidget(WbButton.create(x + col * (optionW + gap), y + row * 22, optionW, 19, label,
                    Component.translatable("worldbinder.tooltip.target_version"), b -> {
                        config.targetMinecraftVersion = entry.name();
                        targetVersionDropdownOpen = false;
                        rebuildConfigWidgets();
                    }));
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
        int gap = 12;
        int cols = w < 420 ? 2 : 3;
        int fieldW = Math.max(70, Math.min(96, (w - gap * (cols - 1)) / cols));

        bossScale = field(x, y + 50, fieldW, Integer.toString(config.bossbarScalePercent), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.bossbar_scale"); addContentWidget(bossScale);
        bossY = field(x + fieldW + gap, y + 50, fieldW, Integer.toString(config.bossbarOffsetY), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.bossbar_y"); addContentWidget(bossY);

        radarSize = field(x, y + 118, fieldW, Integer.toString(config.chunkRadarSize), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_size"); addContentWidget(radarSize);
        radarCell = field(x + fieldW + gap, y + 118, fieldW, Integer.toString(config.chunkRadarCellSize), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_cell"); addContentWidget(radarCell);
        radarScale = field(x + (fieldW + gap) * 2, y + 118, fieldW, Integer.toString(config.chunkRadarScalePercent), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_scale"); addContentWidget(radarScale);

        radarX = field(x, y + 186, fieldW, Integer.toString(config.chunkRadarOffsetX), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_x"); addContentWidget(radarX);
        radarY = field(x + fieldW + gap, y + 186, fieldW, Integer.toString(config.chunkRadarOffsetY), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_y"); addContentWidget(radarY);
        radarMaxChunks = field(x + (fieldW + gap) * 2, y + 186, fieldW, Integer.toString(config.radarMaxRenderedChunks), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_limit"); addContentWidget(radarMaxChunks);
        radarUpdateRate = field(x, y + 254, fieldW, Integer.toString(config.radarUpdateRate), NUMERIC_TEXT_MAX_LENGTH, "worldbinder.tooltip.config.radar_update_rate"); addContentWidget(radarUpdateRate);

        addButtonGrid(x, y + 306, w, 3,
                modeButton(0, 0, 100, "worldbinder.config.f10_view", () -> config.f10MapLayerMode, v -> config.f10MapLayerMode = v),
                modeButton(0, 0, 100, "worldbinder.config.radar_view", () -> config.radarLayerMode, v -> config.radarLayerMode = v),
                radarDetailButton(0, 0, 100, "worldbinder.config.radar_detail", () -> config.radarDetailMode, v -> config.radarDetailMode = v),
                toggle(0, 0, 100, 22, "worldbinder.config.bossbar_overlay", "worldbinder.tooltip.config.bossbar_overlay", () -> config.showBossbarOverlay, v -> config.showBossbarOverlay = v),
                toggle(0, 0, 100, 22, "worldbinder.config.chunk_radar", "worldbinder.tooltip.config.chunk_radar", () -> config.chunkRadarRightAligned, v -> config.chunkRadarRightAligned = v),
                toggle(0, 0, 100, 22, "worldbinder.config.markers", "worldbinder.tooltip.config.markers", () -> config.showWorldGizmos, v -> config.showWorldGizmos = v));
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
        addIfVisible(button(x, y + 6, presetW, 20, "worldbinder.config.rules.peaceful", "worldbinder.tooltip.rules.safe", b -> { WorldBinder.config().gameRulesOverride = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;keepInventory=true;randomTickSpeed=0"; WorldBinder.config().save(); minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.GAMERULES, scrollOffset)); }), y + 6, 20);
        addIfVisible(button(x + presetW + 8, y + 6, presetW, 20, "worldbinder.config.rules.vanilla", "worldbinder.tooltip.rules.vanilla", b -> { WorldBinder.config().gameRulesOverride = "doDaylightCycle=true;doWeatherCycle=true;doMobSpawning=true;keepInventory=false;randomTickSpeed=3"; WorldBinder.config().save(); minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.GAMERULES, scrollOffset)); }), y + 6, 20);
        addIfVisible(button(x + (presetW + 8) * 2, y + 6, presetW, 20, "worldbinder.config.rules.static", "worldbinder.tooltip.rules.showcase", b -> { WorldBinder.config().gameRulesOverride = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;doFireTick=false;randomTickSpeed=0"; WorldBinder.config().save(); minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.GAMERULES, scrollOffset)); }), y + 6, 20);

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

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addContentWidget(T widget) {
        widget.visible = isInContentArea(widget.getY(), widget.getHeight());
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

    private Button tabButton(int x, int y, int w, String labelKey, Tab target) {
        Component label = Component.literal(tab == target ? "◆ " : "").append(Component.translatable(labelKey));
        return WbTooltips.register(Button.builder(label, b -> minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, target, safetyPage)))
                .bounds(x, y, w, 22).build(), Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(labelKey)));
    }

    private Button safetyPageButton(int x, int y, int w, String labelKey, SafetyPage target) {
        Component label = Component.literal(safetyPage == target ? "◆ " : "").append(Component.translatable(labelKey));
        return WbTooltips.register(Button.builder(label, b -> minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, target)))
                .bounds(x, y, w, 22).build(), Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(labelKey)));
    }

    private Button preset(int x, int y, int w, String labelKey, WorldBinderConfig.PerformancePreset preset, String tooltipKey) {
        return WbButton.create(x, y, w, 22, Component.translatable(labelKey), Component.translatable(tooltipKey),
                b -> { WorldBinder.config().setPreset(preset); minecraft.gui.setScreen(new WorldBinderConfigScreen(parent, Tab.PERFORMANCE)); });
    }

    private Button resourcePackFallbackButton(int x, int y, int w, WorldBinderConfig config) {
        return WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)),
                Component.translatable("worldbinder.tooltip.config.resource_pack_fallback"), b -> {
            config.resourcePackFallbackMode = nextFallbackMode(config.resourcePackFallbackMode);
            b.setMessage(fit(Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)), b.getWidth()));
        });
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
        return WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.mode_value", Component.translatable(label), modeLabel(current)),
                Component.translatable("worldbinder.tooltip.config.map_mode"), b -> {
            WorldBinderConfig.MapLayerMode next = nextMode(getter.get());
            setter.set(next);
            b.setMessage(fit(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), modeLabel(next)), b.getWidth()));
        });
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
        return WbButton.create(x, y, w, 22, Component.translatable("worldbinder.config.mode_value", Component.translatable(label), radarDetailLabel(current)),
                Component.translatable("worldbinder.tooltip.config.radar_detail"), b -> {
            WorldBinderConfig.RadarDetailMode next = nextRadarDetail(getter.get());
            setter.set(next);
            b.setMessage(fit(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), radarDetailLabel(next)), b.getWidth()));
        });
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
        return WbButton.create(x, y, w, 22, Component.literal(rule + " " + Lang.string(enabled ? "worldbinder.common.on" : "worldbinder.common.off")),
                Component.literal(rule), b -> {
            boolean next = !gameRuleValues.getOrDefault(rule, defaultGameRule(rule));
            gameRuleValues.put(rule, next);
            b.setMessage(fit(Component.literal(rule + " " + Lang.string(next ? "worldbinder.common.on" : "worldbinder.common.off")), b.getWidth()));
        });
    }

    private void addToggleGrid(int x, int y, int w, int requestedCols, ToggleSpec... specs) {
        int cols = w < 430 ? 1 : Math.min(requestedCols, 3);
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
        int cols = w < 430 ? 1 : Math.min(requestedCols, 3);
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
        EditBox f = new EditBox(font, x, y, Math.max(36, w), 22, Component.empty());
        f.setMaxLength(maxLength);
        f.setValue(value);
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
        return WbButton.create(x, y, w, h, toggleLabel(labelKey, getter.get()), Component.translatable(tooltipKey), b -> {
            boolean n = !getter.get();
            setter.set(n);
            b.setMessage(fit(toggleLabel(labelKey, n), b.getWidth()));
        });
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

    private int contentBaseY(int top, int panelWidth) {
        int navHeight = navRows(panelWidth) * 26;
        int safetyHeight = tab == Tab.SAFETY ? (panelWidth < 560 ? 54 : 28) : 0;
        return top + (compact() ? 52 : 62) + navHeight + safetyHeight + 20;
    }

    private int navColumns(int panelWidth) {
        return panelWidth < 500 ? 2 : 4;
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
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
        WbChrome.drawBackdrop(context, width, height);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);
        int contentX = left + 24;
        int contentBaseY = contentBaseY(top, panelWidth);
        int contentW = Math.max(MIN_CONTENT_WIDTH, panelWidth - 48);
        int contentBottom = top + panelHeight - 48;
        maxScroll = Math.max(0, contentHeight(contentW) - Math.max(80, contentBottom - contentBaseY));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int contentY = contentBaseY - scrollOffset;
        drawPanel(context, left, top, panelWidth, panelHeight);
        GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.config.header"), left + panelWidth / 2, top + 14, 0xFFFFFFFF);
        if (panelHeight > 390 && panelWidth > 520) {
            GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.config.subheader"), left + panelWidth / 2, top + 32, 0xFFBDB6D9);
        }
        int cardBottom = top + panelHeight - 48;
        drawCard(context, contentX - 8, contentBaseY - 10, contentW + 16, Math.max(120, cardBottom - contentBaseY + 10), Component.translatable(tabTitle()), tabAccent(tab));
        if (tab == Tab.GENERAL) drawGeneralLabels(context, contentX, contentY, contentW);
        if (tab == Tab.PERFORMANCE) drawPerformanceLabels(context, contentX, contentY, contentW);
        if (tab == Tab.HUD) drawHudLabels(context, contentX, contentY, contentW);
        if (tab == Tab.SAFETY) drawSafetyLabels(context, contentX, contentY, contentW);
        drawSettingsNavigationChrome(context, left, top, panelWidth, virtualMouseX, virtualMouseY);
        drawTargetVersionDropdown(context, contentX, contentY, contentW, virtualMouseX, virtualMouseY);
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
        wrapped(c, x, y + 128 + targetDropdownOffset, w, "worldbinder.config.target_value", WorldBinder.config().targetVersionLabel());
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

        if (isTextInContentArea(y + 252)) WbText.drawWrapped(c, font, WorldBinder.config().presetDescription(), x, y + 252, w, WbTheme.TEXT_DIM, 2);
    }

    private void performanceLabel(GuiGraphicsExtractor c, int x, int y, int width, String text) {
        clippedLabel(c, x, y, width + 18, text);
    }

    private void drawHudLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int gap = 12;
        int cols = w < 420 ? 2 : 3;
        int fieldW = Math.max(70, Math.min(96, (w - gap * (cols - 1)) / cols));
        label(c, x, y + 34, "worldbinder.config.bossbar_scale");
        clippedLabel(c, x + fieldW + gap, y + 34, fieldW, "worldbinder.config.bossbar_y");
        label(c, x, y + 102, "worldbinder.config.radar_size");
        clippedLabel(c, x + fieldW + gap, y + 102, fieldW, "worldbinder.config.radar_cell");
        clippedLabel(c, x + (fieldW + gap) * 2, y + 102, fieldW, "worldbinder.config.radar_scale");
        label(c, x, y + 170, "worldbinder.config.radar_x");
        clippedLabel(c, x + fieldW + gap, y + 170, fieldW, "worldbinder.config.radar_y");
        clippedLabel(c, x + (fieldW + gap) * 2, y + 170, fieldW, "worldbinder.config.radar_limit");
        label(c, x, y + 238, "worldbinder.config.radar_update_rate");
        label(c, x, y + 292, "worldbinder.config.map_layers_lod");
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

    private void drawSettingsNavigationChrome(GuiGraphicsExtractor c, int left, int top, int panelWidth, int mouseX, int mouseY) {
        int y = top + (compact() ? 42 : 52);
        int cols = navColumns(panelWidth);
        int gap = 6;
        int tabW = Math.max(72, (panelWidth - 36 - gap * (cols - 1)) / cols);
        int tabX = left + 18;
        int index = 0;
        for (Tab target : Tab.values()) {
            int col = index % cols;
            int row = index / cols;
            int bx = tabX + col * (tabW + gap);
            int by = y + row * 26;
            boolean selected = target == tab;
            boolean hovered = WbChrome.contains(bx, by, tabW, 22, mouseX, mouseY);
            WbChrome.drawInset(c, bx, by, tabW, 22, selected ? tabAccent(target) : WbTheme.INFO, selected || hovered);
            if (selected) {
                c.fill(bx + 4, by + 19, bx + tabW - 4, by + 21, tabAccent(target));
            }
            index++;
        }
        if (tab != Tab.SAFETY) {
            return;
        }
        int safetyY = y + navRows(panelWidth) * 26 + 6;
        int safetyCols = panelWidth < 560 ? 2 : 4;
        int safetyW = Math.max(90, (panelWidth - 36 - gap * (safetyCols - 1)) / safetyCols);
        int safetyIndex = 0;
        for (SafetyPage target : SafetyPage.values()) {
            int col = safetyIndex % safetyCols;
            int row = safetyIndex / safetyCols;
            int bx = tabX + col * (safetyW + gap);
            int by = safetyY + row * 24;
            boolean selected = target == safetyPage;
            boolean hovered = WbChrome.contains(bx, by, safetyW, 20, mouseX, mouseY);
            WbChrome.drawInset(c, bx, by, safetyW, 20, selected ? WbTheme.ACCENT_RIGHT : WbTheme.INFO, selected || hovered);
            safetyIndex++;
        }
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
            boolean selected = entry.name().equals(TargetMinecraftVersion.normalize(WorldBinder.config().targetMinecraftVersion));
            boolean hovered = WbChrome.contains(rowX, rowY, optionW, 19, mouseX, mouseY);
            WbChrome.drawDropdownRow(c, rowX, rowY, optionW, 19, selected, hovered, selected ? WbTheme.ACCENT : WbTheme.INFO);
        }
    }

    private void saveConfig() {
        WorldBinderConfig config = WorldBinder.config();
        if (defaultName != null) config.defaultArchiveName = cleanName(defaultName.getValue(), config.defaultArchiveName);
        if (targetVersion != null) config.targetMinecraftVersion = TargetMinecraftVersion.normalize(targetVersion.getValue());
        applyInt(radiusChunks, value -> config.roamingRadiusChunks = value);
        applyInt(minY, value -> config.captureMinY = value);
        applyInt(maxY, value -> config.captureMaxY = value);
        applyCustomInt(blocksPerTick, value -> config.blocksPerTick = value, config);
        applyCustomInt(commandsPerTick, value -> config.commandsPerTick = value, config);
        applyCustomInt(budgetMs, value -> config.tickBudgetMillis = value, config);
        applyCustomInt(newChunks, value -> config.newChunksPerTick = value, config);
        applyCustomInt(queueLimit, value -> config.chunkQueueLimit = value, config);
        applyCustomInt(hotChunks, value -> config.hotChunksPerTick = value, config);
        applyInt(targetFps, value -> config.targetFps = value);
        applyInt(maxCaptureWorkMs, value -> config.maxCaptureWorkMs = value);
        applyInt(maxUiWorkMs, value -> config.maxUiWorkMs = value);
        applyInt(maxArchiveWorkMs, value -> config.maxArchiveWorkMs = value);
        applyInt(radarSize, value -> config.chunkRadarSize = value);
        applyInt(radarCell, value -> config.chunkRadarCellSize = value);
        applyInt(radarScale, value -> config.chunkRadarScalePercent = value);
        applyInt(radarX, value -> config.chunkRadarOffsetX = value);
        applyInt(radarY, value -> config.chunkRadarOffsetY = value);
        applyInt(radarMaxChunks, value -> config.radarMaxRenderedChunks = value);
        applyInt(radarUpdateRate, value -> config.radarUpdateRate = value);
        applyInt(bossScale, value -> config.bossbarScalePercent = value);
        applyInt(bossY, value -> config.bossbarOffsetY = value);
        applyInt(recoverySeconds, value -> config.recoveryAutosaveSeconds = value);
        config.gameRulesOverride = buildGameRuleOverride();
        config.save();
    }

    private static String cleanName(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static void applyCustomInt(EditBox field, IntSetter setter, WorldBinderConfig config) {
        if (applyInt(field, setter)) {
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
        for (String rule : BOOLEAN_GAMERULES) {
            gameRuleValues.put(rule, defaultGameRule(rule));
        }
        randomTickSpeed = 0;
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
        return builder.toString();
    }

    private static boolean defaultGameRule(String rule) {
        return switch (rule) {
            case "doDaylightCycle", "doEntityDrops", "doMobLoot", "doTileDrops", "drowningDamage", "fallDamage", "fireDamage", "forgiveDeadPlayers", "naturalRegeneration", "sendCommandFeedback", "showDeathMessages", "spectatorsGenerateChunks" -> true;
            default -> false;
        };
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
            rebuildConfigWidgets();
            return true;
        }
        return super.mouseScrolled(virtualMouseX, virtualMouseY, horizontalAmount, verticalAmount);
    }

    private void rebuildConfigWidgets() {
        clearWidgets();
        init();
    }

    private int contentHeight(int w) {
        if (tab == Tab.GENERAL) return 336 + (targetVersionDropdownOpen ? targetVersionDropdownHeight(Math.max(120, Math.min(w, 520))) + 10 : 0);
        if (tab == Tab.PERFORMANCE) return 330;
        if (tab == Tab.HUD) return 420;
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.GAMERULES) {
            int cols = w < 420 ? 2 : 3;
            int rows = (BOOLEAN_GAMERULES.length + cols - 1) / cols;
            return 28 + 112 + rows * 28 + 24;
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

    private final class GameRuleSlider extends AbstractSliderButton {
        GameRuleSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), Math.max(0.0D, Math.min(1.0D, randomTickSpeed / 64.0D)));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("worldbinder.config.random_tick_speed", randomTickSpeed));
        }

        @Override
        protected void applyValue() {
            randomTickSpeed = (int) Math.round(value * 64.0D);
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

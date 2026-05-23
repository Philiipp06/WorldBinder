package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
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
        WorldBinderConfig config = WorldBinder.config();
        loadGameRules(config.gameRulesOverride);

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);
        int tabY = top + (compact() ? 44 : 54);
        int tabW = Math.max(56, (panelWidth - 48) / 4);
        int tabGap = 6;
        int tabX = left + 18;

        addRenderableWidget(tabButton(tabX, tabY, tabW, "worldbinder.config.tab.general", Tab.GENERAL));
        addRenderableWidget(tabButton(tabX + (tabW + tabGap), tabY, tabW, "worldbinder.config.tab.performance", Tab.PERFORMANCE));
        addRenderableWidget(tabButton(tabX + (tabW + tabGap) * 2, tabY, tabW, "worldbinder.config.tab.hud", Tab.HUD));
        addRenderableWidget(tabButton(tabX + (tabW + tabGap) * 3, tabY, tabW, "worldbinder.config.tab.safety", Tab.SAFETY));

        int contentX = left + 24;
        int contentBaseY = top + (compact() ? 82 : 98);
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
            minecraft.setScreen(parent);
        }));
        addRenderableWidget(button(left + panelWidth - buttonW - 16, bottomY, buttonW, 22, "worldbinder.config.cancel", "worldbinder.tooltip.config.back", button -> minecraft.setScreen(parent)));
    }

    private void initGeneral(WorldBinderConfig config, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        defaultName = field(x, y + 48, fullW, config.defaultArchiveName, 64); addContentWidget(defaultName);

        int versionW = Math.max(80, Math.min(110, fullW / 3));
        int buttonW = Math.max(58, Math.min(76, (fullW - versionW - 24) / 2));
        targetVersion = field(x, y + 100, versionW, config.targetMinecraftVersion, 12); addContentWidget(targetVersion);
        addContentWidget(button(x + versionW + 10, y + 100, buttonW, 22, "worldbinder.gui.prev", "worldbinder.tooltip.prev_version", b -> { config.targetMinecraftVersion = TargetMinecraftVersion.previous(targetVersion.getValue()); targetVersion.setValue(config.targetMinecraftVersion); }));
        addContentWidget(button(x + versionW + buttonW + 18, y + 100, buttonW, 22, "worldbinder.gui.next", "worldbinder.tooltip.next_version", b -> { config.targetMinecraftVersion = TargetMinecraftVersion.next(targetVersion.getValue()); targetVersion.setValue(config.targetMinecraftVersion); }));

        int numericW = Math.max(90, Math.min(130, (fullW - 48) / 3));
        int numericGap = Math.max(18, Math.min(28, (fullW - numericW * 3) / 2));
        int numericY = y + 164;
        radiusChunks = field(x, numericY, numericW, Integer.toString(config.roamingRadiusChunks), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radiusChunks);
        minY = field(x + numericW + numericGap, numericY, numericW, Integer.toString(config.captureMinY), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(minY);
        maxY = field(x + (numericW + numericGap) * 2, numericY, numericW, Integer.toString(config.captureMaxY), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(maxY);

        int gridY = y + 218;
        addToggleGrid(x, gridY, w, 2,
                toggleSpec("worldbinder.config.capture_entities", "worldbinder.tooltip.config.entities", () -> config.captureEntities, v -> config.captureEntities = v),
                toggleSpec("worldbinder.config.capture_block_entities", "worldbinder.tooltip.config.blockentities", () -> config.captureBlockEntities, v -> config.captureBlockEntities = v),
                toggleSpec("worldbinder.config.capture_air", "worldbinder.tooltip.config.air", () -> config.captureAir, v -> config.captureAir = v),
                toggleSpec("worldbinder.config.server_resource_pack", "worldbinder.tooltip.config.server_resource_pack", () -> config.includeServerResourcePack, v -> config.includeServerResourcePack = v),
                toggleSpec("worldbinder.config.gamerules", "worldbinder.tooltip.config.gamerules", () -> config.exportGameRules, v -> config.exportGameRules = v));
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
        blocksPerTick = field(x, formY + 18, fieldW, Integer.toString(config.blocksPerTick), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(blocksPerTick);
        commandsPerTick = field(x + (fieldW + fieldGap), formY + 18, fieldW, Integer.toString(config.commandsPerTick), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(commandsPerTick);
        budgetMs = field(x + (fieldW + fieldGap) * 2, formY + 18, fieldW, Integer.toString(config.tickBudgetMillis), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(budgetMs);
        targetFps = field(x + (fieldW + fieldGap) * 3, formY + 18, fieldW, Integer.toString(config.targetFps), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(targetFps);
        newChunks = field(x, formY + 70, fieldW, Integer.toString(config.newChunksPerTick), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(newChunks);
        queueLimit = field(x + (fieldW + fieldGap), formY + 70, fieldW, Integer.toString(config.chunkQueueLimit), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(queueLimit);
        hotChunks = field(x + (fieldW + fieldGap) * 2, formY + 70, fieldW, Integer.toString(config.hotChunksPerTick), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(hotChunks);
        maxCaptureWorkMs = field(x, formY + 122, fieldW, Integer.toString(config.maxCaptureWorkMs), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(maxCaptureWorkMs);
        maxUiWorkMs = field(x + (fieldW + fieldGap), formY + 122, fieldW, Integer.toString(config.maxUiWorkMs), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(maxUiWorkMs);
        maxArchiveWorkMs = field(x + (fieldW + fieldGap) * 2, formY + 122, fieldW, Integer.toString(config.maxArchiveWorkMs), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(maxArchiveWorkMs);
    }

    private void initHud(WorldBinderConfig config, int x, int y, int w) {
        int gap = 12;
        int cols = w < 420 ? 2 : 3;
        int fieldW = Math.max(70, Math.min(96, (w - gap * (cols - 1)) / cols));

        bossScale = field(x, y + 50, fieldW, Integer.toString(config.bossbarScalePercent), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(bossScale);
        bossY = field(x + fieldW + gap, y + 50, fieldW, Integer.toString(config.bossbarOffsetY), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(bossY);

        radarSize = field(x, y + 118, fieldW, Integer.toString(config.chunkRadarSize), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarSize);
        radarCell = field(x + fieldW + gap, y + 118, fieldW, Integer.toString(config.chunkRadarCellSize), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarCell);
        radarScale = field(x + (fieldW + gap) * 2, y + 118, fieldW, Integer.toString(config.chunkRadarScalePercent), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarScale);

        radarX = field(x, y + 186, fieldW, Integer.toString(config.chunkRadarOffsetX), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarX);
        radarY = field(x + fieldW + gap, y + 186, fieldW, Integer.toString(config.chunkRadarOffsetY), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarY);
        radarMaxChunks = field(x + (fieldW + gap) * 2, y + 186, fieldW, Integer.toString(config.radarMaxRenderedChunks), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarMaxChunks);
        radarUpdateRate = field(x, y + 254, fieldW, Integer.toString(config.radarUpdateRate), NUMERIC_TEXT_MAX_LENGTH); addContentWidget(radarUpdateRate);

        addButtonGrid(x, y + 306, w, 3,
                modeButton(0, 0, 100, "worldbinder.config.f10_view", () -> config.f10MapLayerMode, v -> config.f10MapLayerMode = v),
                modeButton(0, 0, 100, "worldbinder.config.radar_view", () -> config.radarLayerMode, v -> config.radarLayerMode = v),
                radarDetailButton(0, 0, 100, "worldbinder.config.radar_detail", () -> config.radarDetailMode, v -> config.radarDetailMode = v),
                toggle(0, 0, 100, 22, "worldbinder.config.bossbar_overlay", "worldbinder.tooltip.config.bossbar_overlay", () -> config.showBossbarOverlay, v -> config.showBossbarOverlay = v),
                toggle(0, 0, 100, 22, "worldbinder.config.chunk_radar", "worldbinder.tooltip.config.chunk_radar", () -> config.chunkRadarRightAligned, v -> config.chunkRadarRightAligned = v),
                toggle(0, 0, 100, 22, "worldbinder.config.markers", "worldbinder.tooltip.config.markers", () -> config.showWorldGizmos, v -> config.showWorldGizmos = v));
    }

    private void initSafety(WorldBinderConfig config, int x, int y, int w) {
        int cols = w < 520 ? 2 : 4;
        int gap = 8;
        int buttonW = Math.max(70, (w - gap * (cols - 1)) / cols);
        int pageY = y + 28;
        addContentWidget(safetyPageButton(x, pageY, buttonW, "worldbinder.config.safety.page.core", SafetyPage.CORE));
        addContentWidget(safetyPageButton(x + (buttonW + gap), pageY, buttonW, "worldbinder.config.safety.page.export", SafetyPage.EXPORT));
        addContentWidget(safetyPageButton(x + (buttonW + gap) * (cols == 2 ? 0 : 2), pageY + (cols == 2 ? 30 : 0), buttonW, "worldbinder.config.safety.page.pack", SafetyPage.RESOURCE_PACK));
        addContentWidget(safetyPageButton(x + (buttonW + gap) * (cols == 2 ? 1 : 3), pageY + (cols == 2 ? 30 : 0), buttonW, "worldbinder.config.safety.page.gamerules", SafetyPage.GAMERULES));

        int contentY = y + (cols == 2 ? 94 : 68);
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
        addIfVisible(button(x, y + 6, presetW, 20, "worldbinder.config.rules.peaceful", "worldbinder.tooltip.rules.safe", b -> { WorldBinder.config().gameRulesOverride = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;keepInventory=true;randomTickSpeed=0"; WorldBinder.config().save(); minecraft.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.GAMERULES, scrollOffset)); }), y + 6, 20);
        addIfVisible(button(x + presetW + 8, y + 6, presetW, 20, "worldbinder.config.rules.vanilla", "worldbinder.tooltip.rules.vanilla", b -> { WorldBinder.config().gameRulesOverride = "doDaylightCycle=true;doWeatherCycle=true;doMobSpawning=true;keepInventory=false;randomTickSpeed=3"; WorldBinder.config().save(); minecraft.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.GAMERULES, scrollOffset)); }), y + 6, 20);
        addIfVisible(button(x + (presetW + 8) * 2, y + 6, presetW, 20, "worldbinder.config.rules.static", "worldbinder.tooltip.rules.showcase", b -> { WorldBinder.config().gameRulesOverride = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;doFireTick=false;randomTickSpeed=0"; WorldBinder.config().save(); minecraft.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, SafetyPage.GAMERULES, scrollOffset)); }), y + 6, 20);

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
        int panelHeight = panelHeight();
        int top = WbLayout.top(height, panelHeight);
        return top + (compact() ? 82 : 98) + 24;
    }

    private int contentClipBottom() {
        int panelHeight = panelHeight();
        int top = WbLayout.top(height, panelHeight);
        return top + panelHeight - 50;
    }

    private Button tabButton(int x, int y, int w, String labelKey, Tab target) {
        Component label = Component.literal(tab == target ? "◆ " : "").append(Component.translatable(labelKey));
        return Button.builder(label, b -> minecraft.setScreen(new WorldBinderConfigScreen(parent, target, safetyPage)))
                .bounds(x, y, w, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(labelKey)))).build();
    }

    private Button safetyPageButton(int x, int y, int w, String labelKey, SafetyPage target) {
        Component label = Component.literal(safetyPage == target ? "◆ " : "").append(Component.translatable(labelKey));
        return Button.builder(label, b -> minecraft.setScreen(new WorldBinderConfigScreen(parent, Tab.SAFETY, target)))
                .bounds(x, y, w, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.config.tab", Component.translatable(labelKey)))).build();
    }

    private Button preset(int x, int y, int w, String labelKey, WorldBinderConfig.PerformancePreset preset, String tooltipKey) {
        return Button.builder(Component.translatable(labelKey), b -> { WorldBinder.config().setPreset(preset); minecraft.setScreen(new WorldBinderConfigScreen(parent, Tab.PERFORMANCE)); })
                .bounds(x, y, w, 22).tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
    }

    private Button resourcePackFallbackButton(int x, int y, int w, WorldBinderConfig config) {
        return Button.builder(Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)), b -> {
            config.resourcePackFallbackMode = nextFallbackMode(config.resourcePackFallbackMode);
            b.setMessage(Component.translatable("worldbinder.config.resource_pack_fallback.value", fallbackLabel(config.resourcePackFallbackMode)));
        }).bounds(x, y, w, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.config.resource_pack_fallback"))).build();
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
        return Button.builder(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), modeLabel(current)), b -> {
            WorldBinderConfig.MapLayerMode next = nextMode(getter.get());
            setter.set(next);
            b.setMessage(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), modeLabel(next)));
        }).bounds(x, y, w, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.config.map_mode"))).build();
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
        return Button.builder(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), radarDetailLabel(current)), b -> {
            WorldBinderConfig.RadarDetailMode next = nextRadarDetail(getter.get());
            setter.set(next);
            b.setMessage(Component.translatable("worldbinder.config.mode_value", Component.translatable(label), radarDetailLabel(next)));
        }).bounds(x, y, w, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.config.radar_detail"))).build();
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
        return Button.builder(Component.literal(rule + " " + Lang.string(enabled ? "worldbinder.common.on" : "worldbinder.common.off")), b -> {
            boolean next = !gameRuleValues.getOrDefault(rule, defaultGameRule(rule));
            gameRuleValues.put(rule, next);
            b.setMessage(Component.literal(rule + " " + Lang.string(next ? "worldbinder.common.on" : "worldbinder.common.off")));
        }).bounds(x, y, w, 22).tooltip(Tooltip.create(Component.literal(rule))).build();
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

    private Button button(int x, int y, int w, int h, String key, String tooltipKey, Button.OnPress action) {
        return Button.builder(Component.translatable(key), action).bounds(x, y, w, h).tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
    }

    private Button toggle(int x, int y, int w, int h, String labelKey, String tooltipKey, BoolGetter getter, BoolSetter setter) {
        return Button.builder(toggleLabel(labelKey, getter.get()), b -> {
            boolean n = !getter.get();
            setter.set(n);
            b.setMessage(toggleLabel(labelKey, n));
        }).bounds(x, y, w, h).tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
    }

    private Component toggleLabel(String labelKey, boolean enabled) {
        return Component.translatable("worldbinder.config.toggle_value", Component.translatable(labelKey), Component.translatable(enabled ? "worldbinder.common.on" : "worldbinder.common.off"));
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

    private int labelWidth(int contentW) {
        return Math.min(190, Math.max(96, contentW / 3));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xEE05050C);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);
        int contentX = left + 24;
        int contentBaseY = top + (compact() ? 82 : 98);
        int contentW = Math.max(MIN_CONTENT_WIDTH, panelWidth - 48);
        int contentBottom = top + panelHeight - 48;
        maxScroll = Math.max(0, contentHeight(contentW) - Math.max(80, contentBottom - contentBaseY));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int contentY = contentBaseY - scrollOffset;
        drawPanel(context, left, top, panelWidth, panelHeight);
        GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.config.header"), left + panelWidth / 2, top + 14, 0xFFFFFFFF);
        if (!compact()) {
            GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.config.subheader"), left + panelWidth / 2, top + 32, 0xFFBDB6D9);
        }
        int cardBottom = top + panelHeight - 48;
        drawCard(context, contentX - 8, contentBaseY - 10, contentW + 16, Math.max(120, cardBottom - contentBaseY + 10), Component.translatable(tabTitle()));
        if (tab == Tab.GENERAL) drawGeneralLabels(context, contentX, contentY, contentW);
        if (tab == Tab.PERFORMANCE) drawPerformanceLabels(context, contentX, contentY, contentW);
        if (tab == Tab.HUD) drawHudLabels(context, contentX, contentY, contentW);
        if (tab == Tab.SAFETY) drawSafetyLabels(context, contentX, contentY, contentW);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private String tabTitle() {
        return switch (tab) {
            case GENERAL -> "worldbinder.config.general_export";
            case PERFORMANCE -> "worldbinder.config.performance_presets";
            case HUD -> "worldbinder.config.hud_title";
            case SAFETY -> safetyTitle();
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

    private void drawGeneralLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        int numericW = Math.max(90, Math.min(130, (fullW - 48) / 3));
        int numericGap = Math.max(18, Math.min(28, (fullW - numericW * 3) / 2));
        label(c, x, y + 34, "worldbinder.gui.archive_name");
        label(c, x, y + 86, "worldbinder.gui.target_output_version");
        clippedLabel(c, x, y + 150, numericW + 8, "worldbinder.config.radius");
        clippedLabel(c, x + numericW + numericGap, y + 150, numericW, "worldbinder.config.y_min");
        clippedLabel(c, x + (numericW + numericGap) * 2, y + 150, numericW, "worldbinder.config.y_max");
        wrapped(c, x, y + 312, w, "worldbinder.config.target_value", WorldBinder.config().targetVersionLabel());
    }

    private void drawPerformanceLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int formY = y + (w < 420 ? 96 : 78);
        label(c, x, formY + 4, "worldbinder.config.blocks_per_tick");
        label(c, x, formY + 56, "worldbinder.config.new_chunks");
        label(c, x, formY + 108, "worldbinder.config.capture_ms");
        if (isTextInContentArea(y + 252)) WbText.drawWrapped(c, font, WorldBinder.config().presetDescription(), x, y + 252, w, WbTheme.TEXT_DIM, 2);
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
        int subY = y + (w < 520 ? 94 : 68);
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
        c.fill(x, y, x + w, y + h, 0xF00A0A15);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xDD121225);
        c.fill(x, y, x + w, y + 3, 0xFFFF55FF);
        c.fill(x, y + h - 3, x + w, y + h, 0xFF5E03FC);
        c.fill(x, y, x + 3, y + h, 0xFF5E03FC);
        c.fill(x + w - 3, y, x + w, y + h, 0xFFF803FC);
    }

    private void drawCard(GuiGraphicsExtractor c, int x, int y, int w, int h, Component title) {
        c.fill(x, y, x + w, y + h, 0xAA090914);
        c.fill(x, y, x + w, y + 1, 0xFFFF55FF);
        WbText.drawClipped(c, font, title.getString(), x + 12, y + 10, w - 24, WbTheme.ACCENT);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int before = scrollOffset;
        int step = compact() ? 18 : 28;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (verticalAmount < 0 ? step : -step)));
        if (before != scrollOffset) {
            rebuildConfigWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void rebuildConfigWidgets() {
        clearWidgets();
        init();
    }

    private int contentHeight(int w) {
        if (tab == Tab.GENERAL) return 336;
        if (tab == Tab.PERFORMANCE) return 330;
        if (tab == Tab.HUD) return 420;
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.GAMERULES) {
            int cols = w < 420 ? 2 : 3;
            int rows = (BOOLEAN_GAMERULES.length + cols - 1) / cols;
            return (w < 520 ? 94 : 68) + 112 + rows * 28 + 24;
        }
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.CORE) return 230;
        if (tab == Tab.SAFETY && safetyPage == SafetyPage.RESOURCE_PACK) return 210;
        if (tab == Tab.SAFETY) return 180;
        return 320;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
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

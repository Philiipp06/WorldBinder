package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.config.GameRuleSettings;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbBrand;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;

abstract class WorldBinderConfigScreenView extends Screen {
    protected static final int NUMERIC_TEXT_MAX_LENGTH = 16;
    protected static final int MIN_CONTENT_WIDTH = 180;
    protected static final int SETTINGS_SIDEBAR_WIDTH = 178;

    protected enum Tab { GENERAL, PERFORMANCE, HUD, SAFETY, MOVEMENT, DISPLAY }
    protected enum SafetyPage { CORE, EXPORT, RESOURCE_PACK, GAMERULES }

    protected final Screen parent;
    protected final Tab tab;
    protected final SafetyPage safetyPage;
    protected final WorldBinderConfig draft;
    protected EditBox defaultName;
    protected EditBox radiusChunks;
    protected EditBox minY;
    protected EditBox maxY;
    protected EditBox blocksPerTick;
    protected EditBox commandsPerTick;
    protected EditBox budgetMs;
    protected EditBox newChunks;
    protected EditBox queueLimit;
    protected EditBox hotChunks;
    protected EditBox targetFps;
    protected EditBox maxUiWorkMs;
    protected EditBox maxCaptureWorkMs;
    protected EditBox maxArchiveWorkMs;
    protected EditBox radarSize;
    protected EditBox radarCell;
    protected EditBox radarMaxChunks;
    protected EditBox radarUpdateRate;
    protected EditBox notificationDuration;
    protected EditBox recoverySeconds;
    protected final GameRuleSettings gameRules = new GameRuleSettings();
    protected int scrollOffset;
    protected int maxScroll;
    protected int movementGroup;
    protected int gameRuleCategory;
    protected Component validationError;

    protected WorldBinderConfigScreenView(Screen parent, Tab tab, SafetyPage safetyPage,
                                          int scrollOffset, WorldBinderConfig draft) {
        super(Component.translatable("worldbinder.config.title"));
        this.parent = parent;
        this.tab = tab;
        this.safetyPage = safetyPage;
        this.scrollOffset = Math.max(0, scrollOffset);
        this.draft = draft;
    }

    protected abstract boolean isInvalidNumericField(EditBox field);

    protected abstract int contentHeight(int width);
    protected boolean isInContentArea(int y, int h) {
        return y >= contentClipTop() && y + h <= contentClipBottom();
    }

    protected boolean isTextInContentArea(int y) {
        return y >= contentClipTop() && y + font.lineHeight <= contentClipBottom();
    }

    protected int contentClipTop() {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int top = WbLayout.top(height, panelHeight);
        return contentBaseY(top, panelWidth) + 18;
    }

    protected int contentClipBottom() {
        int panelHeight = panelHeight();
        int top = WbLayout.top(height, panelHeight);
        return top + panelHeight - 50;
    }

    protected int panelWidth() {
        return WbLayout.panelWidth(width);
    }

    protected int panelHeight() {
        return WbLayout.panelHeight(height);
    }

    protected boolean compact() {
        return WbLayout.compact(panelWidth(), panelHeight());
    }

    protected boolean useSidebar(int panelWidth) {
        return panelWidth >= 430;
    }

    protected int settingsSidebarWidth(int panelWidth) {
        return panelWidth < 700 ? 130 : SETTINGS_SIDEBAR_WIDTH;
    }

    protected int contentAreaX(int left, int panelWidth) {
        return useSidebar(panelWidth) ? left + settingsSidebarWidth(panelWidth) + (panelWidth < 700 ? 14 : 20) : left + 24;
    }

    protected int contentAreaWidth(int panelWidth) {
        int reserved = useSidebar(panelWidth) ? settingsSidebarWidth(panelWidth) + (panelWidth < 700 ? 32 : 44) : 48;
        return Math.max(MIN_CONTENT_WIDTH, panelWidth - reserved);
    }

    protected int contentBaseY(int top, int panelWidth) {
        if (useSidebar(panelWidth)) {
            return top + (tab == Tab.SAFETY || tab == Tab.MOVEMENT ? (panelWidth < 700 ? 122 : 102) : 68);
        }
        int navHeight = navRows(panelWidth) * 26;
        int safetyHeight = tab == Tab.SAFETY || tab == Tab.MOVEMENT ? (panelWidth < 430 ? 54 : 28) : 0;
        return top + (compact() ? 52 : 62) + navHeight + safetyHeight + 20;
    }

    protected int navColumns(int panelWidth) {
        return panelWidth < 430 ? 2 : 4;
    }

    protected int navRows(int panelWidth) {
        return (Tab.values().length + navColumns(panelWidth) - 1) / navColumns(panelWidth);
    }

    protected int labelWidth(int contentW) {
        return Math.min(190, Math.max(96, contentW / 3));
    }

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
                WbText.drawClipped(context, font, Component.translatable(tabDescriptionKey(tab)), contentX,
                        top + 31, contentW, WbTheme.mutedText());
            } else {
                GuiText.drawCenteredTextWithShadow(context, font, Component.translatable(tabDescriptionKey(tab)),
                        left + panelWidth / 2, top + 32, WbTheme.mutedText());
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
            if (tab == Tab.DISPLAY) drawDisplayLabels(context, formX, contentY, formW);
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

    protected String tabTitle() {
        return switch (tab) {
            case GENERAL -> "worldbinder.config.general_export";
            case PERFORMANCE -> "worldbinder.config.performance_presets";
            case HUD -> "worldbinder.config.hud_title";
            case SAFETY -> safetyTitle();
            case MOVEMENT -> "worldbinder.config.tab.movement";
            case DISPLAY -> "worldbinder.config.tab.display";
        };
    }

    protected int tabAccent(Tab value) {
        return switch (value) {
            case GENERAL -> WbTheme.ACCENT;
            case PERFORMANCE -> WbTheme.ACCENT_RIGHT;
            case HUD -> WbTheme.INFO;
            case SAFETY -> WbTheme.WARN;
            case MOVEMENT -> WbTheme.INFO;
            case DISPLAY -> WbTheme.ACCENT_DARK;
        };
    }

    protected String tabLabelKey(Tab value) {
        return switch (value) {
            case GENERAL -> "worldbinder.config.tab.general";
            case PERFORMANCE -> "worldbinder.config.tab.performance";
            case HUD -> "worldbinder.config.tab.hud";
            case SAFETY -> "worldbinder.config.tab.safety";
            case MOVEMENT -> "worldbinder.config.tab.movement";
            case DISPLAY -> "worldbinder.config.tab.display";
        };
    }

    protected String safetyTitle() {
        return switch (safetyPage) {
            case CORE -> "worldbinder.config.safety.page.core";
            case EXPORT -> "worldbinder.config.safety.page.export";
            case RESOURCE_PACK -> "worldbinder.config.safety.page.pack";
            case GAMERULES -> "worldbinder.config.safety.page.gamerules";
        };
    }

    protected String safetyLabelKey(SafetyPage value) {
        return switch (value) {
            case CORE -> "worldbinder.config.safety.page.core";
            case EXPORT -> "worldbinder.config.safety.page.export";
            case RESOURCE_PACK -> "worldbinder.config.safety.page.pack";
            case GAMERULES -> "worldbinder.config.safety.page.gamerules";
        };
    }

    protected void drawGeneralLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int fullW = Math.max(120, Math.min(w, 520));
        int numericGap = 10;
        int numericW = (fullW - numericGap * 2) / 3;
        label(c, x, y + 34, "worldbinder.gui.archive_name");
        label(c, x, y + 86, "worldbinder.gui.target_output_version");
        clippedLabel(c, x, y + 150, numericW, "worldbinder.config.radius");
        clippedLabel(c, x + numericW + numericGap, y + 150, numericW, "worldbinder.config.y_min");
        clippedLabel(c, x + (numericW + numericGap) * 2, y + 150, numericW, "worldbinder.config.y_max");
        wrapped(c, x, y + 128, w, "worldbinder.config.target_value", draft.targetVersionLabel());
    }

    protected void drawPerformanceLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int cols = w < 420 ? 2 : 4;
        int formY = y + (cols == 2 ? 96 : 78);
        int gap = 10;
        int fieldW = (w - gap * (cols - 1)) / cols;
        String[] labels = {
                "worldbinder.config.blocks_per_tick", "worldbinder.config.commands_per_tick",
                "worldbinder.config.ms_budget", "worldbinder.config.target_fps",
                "worldbinder.config.new_chunks", "worldbinder.config.queue_limit",
                "worldbinder.config.hot_chunks", "worldbinder.config.capture_ms",
                "worldbinder.config.ui_ms", "worldbinder.config.archive_ms"
        };
        for (int i = 0; i < labels.length; i++) {
            performanceLabel(c, x + (i % cols) * (fieldW + gap), formY + (i / cols) * 52, fieldW, labels[i]);
        }
        int hintY = formY + ((labels.length + cols - 1) / cols) * 52 + 2;
        if (isTextInContentArea(hintY)) WbText.drawWrapped(c, font, draft.presetDescription(), x, hintY, w, WbTheme.TEXT_DIM, 2);
    }

    protected void performanceLabel(GuiGraphicsExtractor c, int x, int y, int width, String text) {
        clippedLabel(c, x, y, width, text);
    }

    protected void drawHudLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int gap = 12;
        int cols = w < 430 ? 2 : 4;
        int fieldW = Math.max(70, Math.min(112, (w - gap * (cols - 1)) / cols));
        label(c, x, y + 32, "worldbinder.config.widget_editor_section");
        if (w >= 600) {
            wrapped(c, x + Math.min(w, Math.max(220, w / 2)) + 14, y + 48,
                    w - Math.min(w, Math.max(220, w / 2)) - 14, "worldbinder.config.widget_editor_hint");
        }

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

    protected void drawSafetyLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int subY = y + 28;
        if (safetyPage == SafetyPage.CORE) {
            label(c, x, subY + 30, "worldbinder.config.recovery_interval");
        } else if (safetyPage == SafetyPage.RESOURCE_PACK) {
            if (isTextInContentArea(subY + 128)) WbText.drawWrapped(c, font, Lang.string("worldbinder.config.pack_page_hint"), x, subY + 128, w, WbTheme.TEXT_DIM, 3);
        } else if (safetyPage == SafetyPage.GAMERULES) {
        }
    }

    protected void label(GuiGraphicsExtractor c, int x, int y, String text) {
        clippedLabel(c, x, y, Math.max(80, panelWidth() - 80), text);
    }

    protected void clippedLabel(GuiGraphicsExtractor c, int x, int y, int width, String text) {
        if (!isTextInContentArea(y)) return;
        WbText.drawClipped(c, font, Lang.string(text), x, y, width, WbTheme.TEXT);
    }

    protected void wrapped(GuiGraphicsExtractor c, int x, int y, int width, String text, Object... args) {
        if (!isTextInContentArea(y)) return;
        WbText.drawWrapped(c, font, Lang.string(text, args), x, y, width, WbTheme.TEXT_DIM, 2);
    }

    protected void drawPanel(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        WbChrome.drawPanel(c, x, y, w, h);
    }

    protected void drawCard(GuiGraphicsExtractor c, int x, int y, int w, int h, Component title, int accent) {
        c.fill(x, y, x + 2, y + h, accent);
        c.fill(x + 12, y + 30, x + w - 10, y + 31, WbTheme.DIVIDER);
        WbText.drawClipped(c, font, title, x + 14, y + 10, w - 28, WbTheme.TEXT);
    }

    protected void drawContentSections(GuiGraphicsExtractor c, int x, int y, int w) {
        int innerW = Math.max(80, w - 8);
        if (tab == Tab.GENERAL) {
            drawSectionSurface(c, x + 4, y + 26, innerW, 174, WbTheme.ACCENT);
            drawSectionSurface(c, x + 4, y + 208, innerW, 98, WbTheme.ACCENT_DARK);
        } else if (tab == Tab.PERFORMANCE) {
            int cols = w - 24 < 420 ? 2 : 4;
            int formY = cols == 2 ? 96 : 78;
            int rows = (10 + cols - 1) / cols;
            drawSectionSurface(c, x + 4, y + 18, innerW, cols == 2 ? 74 : 56, WbTheme.ACCENT_RIGHT);
            drawSectionSurface(c, x + 4, y + formY - 5, innerW, rows * 52 + 8, WbTheme.ACCENT_DARK);
        } else if (tab == Tab.HUD) {
            int formW = Math.max(MIN_CONTENT_WIDTH - 24, w - 24);
            int cols = formW < 430 ? 2 : 4;
            int mapY = y + (cols == 2 ? 244 : 196);
            int notificationY = mapY + 112;
            drawSectionSurface(c, x + 4, y + 26, innerW, 58, WbTheme.INFO);
            drawSectionSurface(c, x + 4, y + 94, innerW, cols == 2 ? 130 : 78, WbTheme.ACCENT_DARK);
            drawSectionSurface(c, x + 4, mapY + 6, innerW, 94, WbTheme.ACCENT);
            drawSectionSurface(c, x + 4, notificationY - 8, innerW, 130, WbTheme.INFO);
        } else if (tab == Tab.MOVEMENT) {
            drawMovementSections(c, x, y, w);
        } else if (tab == Tab.DISPLAY) {
            drawSectionSurface(c, x + 4, y + 18, innerW, 158, WbTheme.ACCENT_DARK);
        } else if (safetyPage == SafetyPage.GAMERULES) {
            drawSectionSurface(c, x + 4, y + 22, innerW, 42, WbTheme.WARN);
            WbText.drawClipped(c, font, Lang.string("worldbinder.gamerules.target", draft.targetMinecraftVersion),
                    x + 16, y + 32, w - 32, WbTheme.TEXT_MUTED);
        } else if (safetyPage == SafetyPage.CORE) {
            drawSectionSurface(c, x + 4, y + 26, innerW, 142, WbTheme.WARN);
        } else if (safetyPage == SafetyPage.EXPORT) {
            drawSectionSurface(c, x + 4, y + 28, innerW, 96, WbTheme.ACCENT_RIGHT);
        } else if (safetyPage == SafetyPage.RESOURCE_PACK) {
            drawSectionSurface(c, x + 4, y + 28, innerW, 156, WbTheme.WARN);
        }
    }

    protected void drawDisplayLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        clippedLabel(c, x, y + 28, w, "worldbinder.config.display.title");
        if (isTextInContentArea(y + 142)) {
            WbText.drawWrapped(c, font, Lang.string("worldbinder.config.display.apply"), x, y + 142,
                    w, WbTheme.mutedText(), 2);
        }
    }

    private void drawMovementSections(GuiGraphicsExtractor c, int x, int y, int w) {
        int rowY = y + 22;
        MovementSettingsLayout.Group group = MovementSettingsLayout.GROUPS.get(movementGroup);
            if (isTextInContentArea(rowY)) {
                String heading = Lang.string(group.titleKey());
                WbText.drawClipped(c, font, heading, x + 16, rowY, w - 32, WbTheme.TEXT_MUTED);
                int dividerX = x + 26 + font.width(heading);
                if (dividerX < x + w - 12) WbChrome.drawDivider(c, dividerX, rowY + 8, x + w - 12 - dividerX);
            }
            rowY += MovementSettingsLayout.HEADING_HEIGHT;
            for (net.worldbinder.movement.MovementTool tool : group.tools()) {
                drawSectionSurface(c, x + 4, rowY - 4, w - 8,
                        MovementSettingsLayout.ROW_HEIGHT - 8, WbTheme.INFO);
                if (isTextInContentArea(rowY + 27)) {
                    WbText.drawClipped(c, font, Lang.string(tool.key() + ".tooltip"), x + 12,
                            rowY + 27, w - 24, WbTheme.TEXT_DIM);
                }
                rowY += MovementSettingsLayout.ROW_HEIGHT;
            }
    }

    protected void drawSectionSurface(GuiGraphicsExtractor c, int x, int y, int w, int h, int accent) {
        int clippedTop = Math.max(y, contentClipTop());
        int clippedBottom = Math.min(y + h, contentClipBottom());
        if (clippedBottom - clippedTop < 3) {
            return;
        }
        WbChrome.drawSectionSurface(c, x, clippedTop, w, clippedBottom - clippedTop, accent);
    }

    protected void drawInputChrome(GuiGraphicsExtractor c, int mouseX, int mouseY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (!(child instanceof EditBox field) || !field.visible) {
                continue;
            }
            boolean hovered = field.isMouseOver(mouseX, mouseY);
            int accent = isInvalidNumericField(field) ? WbTheme.ERROR : tabAccent(tab);
            if (field instanceof WbEditBox styled) {
                WbChrome.drawField(c, styled.chromeX(), styled.chromeY(), styled.chromeWidth(), styled.chromeHeight(), accent, field.isFocused(), hovered);
            } else {
                WbChrome.drawField(c, field.getX(), field.getY(), field.getWidth(), field.getHeight(), accent, field.isFocused(), hovered);
            }
        }
    }

    protected void drawSettingsNavigationChrome(GuiGraphicsExtractor c, int left, int top, int panelWidth, int mouseX, int mouseY) {
        if (useSidebar(panelWidth)) {
            int sidebarX = left + 8;
            int sidebarY = top + 49;
            int sidebarW = settingsSidebarWidth(panelWidth) - 8;
            int sidebarH = panelHeight() - 99;
            c.fill(sidebarX + sidebarW - 1, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, WbTheme.DIVIDER);
            WbBrand.draw(c, font, sidebarX + 12, sidebarY + 1, sidebarW - 24);
            WbChrome.drawDivider(c, sidebarX + 12, sidebarY + 39, sidebarW - 24);
            if (panelHeight() >= 390) {
                WbText.drawWrapped(c, font, Component.translatable(tabDescriptionKey(tab)),
                        sidebarX + 12, sidebarY + sidebarH - 52, sidebarW - 24, WbTheme.TEXT_DIM, 3);
            }
            return;
        }

        int y = top + (compact() ? 42 : 52);
        int cols = navColumns(panelWidth);
        int navW = panelWidth - 36;
        WbChrome.drawDivider(c, left + 18, y - 5, navW);
        if (tab != Tab.SAFETY && tab != Tab.MOVEMENT) {
            return;
        }
        int safetyY = y + navRows(panelWidth) * 26 + 6;
        int safetyRows = panelWidth < 430 ? 2 : 1;
        WbChrome.drawDivider(c, left + 18, safetyY - 4, navW);
    }

    protected String tabDescriptionKey(Tab value) {
        return switch (value) {
            case GENERAL -> "worldbinder.config.navigation.general";
            case PERFORMANCE -> "worldbinder.config.navigation.performance";
            case HUD -> "worldbinder.config.navigation.hud";
            case SAFETY -> "worldbinder.config.navigation.safety";
            case MOVEMENT -> "worldbinder.config.navigation.movement";
            case DISPLAY -> "worldbinder.config.navigation.display";
        };
    }

}

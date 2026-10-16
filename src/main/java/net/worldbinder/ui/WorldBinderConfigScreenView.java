package net.worldbinder.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.GameRuleSettings;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.input.MovementKeys;
import net.worldbinder.movement.MovementTool;
import net.worldbinder.ui.component.WbBrand;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.ui.component.WbTransition;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.List;

abstract class WorldBinderConfigScreenView extends Screen {
    protected static final int NUMERIC_TEXT_MAX_LENGTH = 16;
    protected static final int MIN_CONTENT_WIDTH = 180;
    protected static final int SETTINGS_SIDEBAR_WIDTH = 178;
    protected static final int MOVEMENT_OPTIONS_GROUP = MovementTool.Category.values().length;

    protected enum Tab { GENERAL, PERFORMANCE, HUD, SAFETY, MOVEMENT, DISPLAY }
    protected enum SafetyPage { CORE, EXPORT, RESOURCE_PACK, GAMERULES }

    /** Decorations recorded while the widgets are laid out, so drawing and widgets share one layout. */
    protected enum DecoKind { SECTION, LABEL, WRAPPED, TOOL_CARD }

    protected record Deco(DecoKind kind, int x, int y, int width, int height, int color, String text, String subtitle,
                          WbIcon icon, MovementTool tool) {
    }

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
    protected final List<Deco> decorations = new ArrayList<>();
    protected int scrollOffset;
    protected int maxScroll;
    protected int movementGroup;
    protected int gameRuleCategory;
    protected Component validationError;
    protected Component statusMessage;
    protected long statusMessageMillis;
    protected boolean dirty;
    protected long pageChangedMillis;

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

    protected abstract boolean computeDirty();

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

    protected boolean hasSubNavigation() {
        return tab == Tab.SAFETY || tab == Tab.MOVEMENT;
    }

    protected int contentBaseY(int top, int panelWidth) {
        if (useSidebar(panelWidth)) {
            return top + (hasSubNavigation() ? (panelWidth < 700 ? 122 : 102) : 68);
        }
        int navHeight = navRows(panelWidth) * 26;
        int safetyHeight = hasSubNavigation() ? (panelWidth < 430 ? 54 : 28) : 0;
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        boolean transition = WbTransition.begin(context, this, realWidth, realHeight);
        WbTheme.apply(draft);
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
            WbChrome.drawPanel(context, left, top, panelWidth, panelHeight);
            drawSettingsNavigationChrome(context, left, top, panelWidth, virtualMouseX, virtualMouseY);
            drawHeader(context, left, top, panelWidth, panelHeight, contentX, contentW);
            int cardBottom = top + panelHeight - 48;
            drawContentFrame(context, contentX - 8, contentBaseY - 10, contentW + 16, Math.max(120, cardBottom - contentBaseY + 10));
            context.enableScissor(contentX - 8, contentClipTop(), contentX + contentW + 16, contentClipBottom());
            try {
                drawContentSections(context, contentX, contentY, contentW);
                drawDecorations(context, virtualMouseX, virtualMouseY);
                if (tab == Tab.GENERAL) drawGeneralLabels(context, formX, contentY, formW);
                if (tab == Tab.PERFORMANCE) drawPerformanceLabels(context, formX, contentY, formW);
                if (tab == Tab.HUD) drawHudLabels(context, formX, contentY, formW);
                if (tab == Tab.SAFETY) drawSafetyLabels(context, formX, contentY, formW);
                drawInputChrome(context, virtualMouseX, virtualMouseY);
            } finally {
                context.disableScissor();
            }
            int fadeTop = contentClipTop();
            context.fillGradient(contentX - 6, fadeTop, contentX + contentW + 14, fadeTop + 6, WbTheme.withAlpha(WbTheme.PANEL_INNER, 0xB0), 0x00000000);
            context.fillGradient(contentX - 6, contentClipBottom() - 6, contentX + contentW + 14, contentClipBottom(), 0x00000000, WbTheme.withAlpha(WbTheme.PANEL_INNER, 0xB0));
            drawFooter(context, left, top, panelWidth, panelHeight);
            int viewportHeight = Math.max(1, contentClipBottom() - contentClipTop());
            WbChrome.drawScrollBar(context, contentX + contentW + 9, contentClipTop(), viewportHeight, scrollOffset, maxScroll, viewportHeight);
            super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
            WbTransition.contentFade(context, contentX - 8, contentClipTop(), contentW + 24, contentClipBottom() - contentClipTop(), pageChangedMillis);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
        WbTransition.end(context, transition);
        WbTransition.veil(context, this, realWidth, realHeight);
    }

    private long nextDirtyCheckMillis;

    private void drawHeader(GuiGraphicsExtractor context, int left, int top, int panelWidth, int panelHeight, int contentX, int contentW) {
        Component header = Component.translatable("worldbinder.config.header").withStyle(ChatFormatting.BOLD);
        if (useSidebar(panelWidth)) {
            WbIcon icon = tabIcon(tab);
            int textX = contentX;
            if (WorldBinder.config().uiShowIcons) {
                WbChrome.rounded(context, contentX, top + 12, 22, 22, WbTheme.withAlpha(tabAccent(tab), 0x2C));
                icon.draw(context, contentX + 4, top + 16, 2, tabAccent(tab));
                textX += 30;
            }
            String crumb = Lang.string("worldbinder.config.header");
            int crumbW = font.width(crumb);
            boolean showCrumb = contentW - (textX - contentX) > crumbW + 160;
            int textLimit = contentW - (textX - contentX) - (showCrumb ? crumbW + 24 : 0);
            context.text(font, Component.literal(WbText.ellipsize(font, Lang.string(tabLabelKey(tab)), textLimit)).withStyle(ChatFormatting.BOLD),
                    textX, top + 13, WbTheme.TEXT, true);
            if (panelHeight > 300) {
                WbText.drawClipped(context, font, Component.translatable(tabDescriptionKey(tab)), textX,
                        top + 26, textLimit, WbTheme.mutedText());
            }
            if (showCrumb) {
                int crumbX = contentX + contentW - crumbW;
                WbIcon.SETTINGS.draw(context, crumbX - 11, top + 14, 1, WbTheme.TEXT_DIM);
                context.text(font, crumb, crumbX, top + 14, WbTheme.TEXT_DIM, false);
            }
        } else {
            GuiText.drawCenteredTextWithShadow(context, font, header, left + panelWidth / 2, top + 14, WbTheme.TEXT);
        }
    }

    private void drawContentFrame(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        WbChrome.rounded(c, x, y, w, h, 0x26000000);
        WbChrome.roundedOutline(c, x, y, w, h, WbTheme.withAlpha(WbTheme.PANEL_BORDER, 0x40));
        WbChrome.hGradient(c, x + 3, y, Math.min(w - 6, 120), 1, tabAccent(tab), WbTheme.withAlpha(tabAccent(tab), 0x00));
        String title = Lang.string(tabTitle());
        c.text(font, Component.literal(WbText.ellipsize(font, title, w - 28)).withStyle(ChatFormatting.BOLD), x + 14, y + 8, WbMotion.mix(tabAccent(tab), WbTheme.TEXT, 0.55F), true);
    }

    private void drawFooter(GuiGraphicsExtractor context, int left, int top, int panelWidth, int panelHeight) {
        WbChrome.drawDivider(context, left + 14, top + panelHeight - 41, panelWidth - 28);
        long now = System.currentTimeMillis();
        if (now >= nextDirtyCheckMillis) {
            dirty = computeDirty();
            nextDirtyCheckMillis = now + 400L;
        }
        int footerY = top + panelHeight - 30;
        int x = left + 18;
        int limit = footerButtonsLeft(left, panelWidth) - 8;
        int maxWidth = Math.max(0, limit - x);
        if (maxWidth < 20) {
            return;
        }
        if (validationError != null) {
            WbIcon.WARNING.draw(context, x, footerY + 6, 1, WbTheme.ERROR);
            WbText.drawClipped(context, font, validationError, x + 12, footerY + 6, maxWidth - 12, WbTheme.ERROR);
            return;
        }
        if (statusMessage != null && now - statusMessageMillis < 4000L) {
            footerPill(context, x, footerY + 3, maxWidth, statusMessage.getString(), WbTheme.WARN, WbIcon.INFO);
            return;
        }
        int pillW;
        if (dirty) {
            float pulse = WbMotion.pulse(1600L);
            pillW = footerPill(context, x, footerY + 3, maxWidth, Lang.string("worldbinder.config.unsaved"),
                    WbMotion.mix(WbTheme.WARN, 0xFFFFFFFF, pulse * 0.2F), WbIcon.SAVE);
        } else {
            pillW = footerPill(context, x, footerY + 3, maxWidth, Lang.string("worldbinder.config.saved_state"), WbTheme.OK, WbIcon.CHECK);
        }
        int hintX = x + pillW + 10;
        int hintW = 30 + font.width("Ctrl") + font.width("S") + font.width(Lang.string("worldbinder.config.shortcut_save"));
        if (hintX + hintW < limit) {
            int kx = hintX;
            kx += WbChrome.drawKeycap(context, font, kx, footerY + 4, "Ctrl") + 2;
            kx += WbChrome.drawKeycap(context, font, kx, footerY + 4, "S") + 4;
            context.text(font, Lang.string("worldbinder.config.shortcut_save"), kx, footerY + 6, WbTheme.TEXT_DIM, false);
        }
    }

    /** Draws a footer pill that never overlaps the footer buttons; falls back to an icon badge. */
    private int footerPill(GuiGraphicsExtractor context, int x, int y, int maxWidth, String text, int accent, WbIcon icon) {
        int full = WbChrome.pillWidth(font, text, true);
        if (full <= maxWidth) {
            return WbChrome.drawPill(context, font, x, y, text, accent, icon);
        }
        if (maxWidth >= 60) {
            String shortened = WbText.ellipsize(font, text, maxWidth - WbIcon.SIZE - 16);
            return WbChrome.drawPill(context, font, x, y, shortened, accent, icon);
        }
        WbChrome.rounded(context, x, y, 18, 14, WbTheme.withAlpha(accent, 0x2C));
        WbChrome.roundedOutline(context, x, y, 18, 14, WbTheme.withAlpha(accent, 0x88));
        icon.draw(context, x + 6, y + 4, 1, accent);
        return 18;
    }

    protected int footerButtonWidth() {
        return compact() ? 80 : 96;
    }

    protected int footerResetWidth() {
        return compact() ? 92 : 118;
    }

    /** Left edge of the right-aligned footer button group (Reset, Save, Cancel). */
    protected int footerButtonsLeft(int left, int panelWidth) {
        return left + panelWidth - footerButtonWidth() * 2 - footerResetWidth() - 36;
    }

    protected String tabTitle() {
        return switch (tab) {
            case GENERAL -> "worldbinder.config.general_export";
            case PERFORMANCE -> "worldbinder.config.performance_presets";
            case HUD -> "worldbinder.config.hud_title";
            case SAFETY -> safetyTitle();
            case MOVEMENT -> movementGroup >= MOVEMENT_OPTIONS_GROUP ? "worldbinder.config.movement.group.options"
                    : MovementTool.Category.values()[movementGroup].titleKey;
            case DISPLAY -> "worldbinder.config.display.title";
        };
    }

    protected int tabAccent(Tab value) {
        return switch (value) {
            case GENERAL -> WbTheme.ACCENT;
            case PERFORMANCE -> WbTheme.ACCENT_RIGHT;
            case HUD -> WbTheme.INFO;
            case SAFETY -> WbTheme.WARN;
            case MOVEMENT -> WbTheme.OK;
            case DISPLAY -> WbTheme.ACCENT_DARK;
        };
    }

    protected WbIcon tabIcon(Tab value) {
        return switch (value) {
            case GENERAL -> WbIcon.GLOBE;
            case PERFORMANCE -> WbIcon.PERFORMANCE;
            case HUD -> WbIcon.HUD;
            case SAFETY -> WbIcon.SHIELD;
            case MOVEMENT -> WbIcon.MOVEMENT;
            case DISPLAY -> WbIcon.PALETTE;
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
        return safetyLabelKey(safetyPage);
    }

    protected String safetyLabelKey(SafetyPage value) {
        return switch (value) {
            case CORE -> "worldbinder.config.safety.page.core";
            case EXPORT -> "worldbinder.config.safety.page.export";
            case RESOURCE_PACK -> "worldbinder.config.safety.page.pack";
            case GAMERULES -> "worldbinder.config.safety.page.gamerules";
        };
    }

    protected WbIcon safetyIcon(SafetyPage value) {
        return switch (value) {
            case CORE -> WbIcon.SHIELD;
            case EXPORT -> WbIcon.DOWNLOAD;
            case RESOURCE_PACK -> WbIcon.PALETTE;
            case GAMERULES -> WbIcon.SETTINGS;
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
            clippedLabel(c, x + (i % cols) * (fieldW + gap), formY + (i / cols) * 52, fieldW, labels[i]);
        }
        int hintY = formY + ((labels.length + cols - 1) / cols) * 52 + 2;
        if (isTextInContentArea(hintY)) WbText.drawWrapped(c, font, draft.presetDescription(), x, hintY, w, WbTheme.TEXT_DIM, 2);
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

        int notificationY = mapY + hudMapSectionHeight(w);
        label(c, x, notificationY, "worldbinder.config.notifications_section");
        clippedLabel(c, x, notificationY + 18, fieldW, "worldbinder.config.notification_duration");
    }

    /** Height of the "Live map & radar" block in the HUD tab (layer buttons plus the new live map toggles). */
    protected int hudMapSectionHeight(int w) {
        int cols = w < 300 ? 1 : w < 430 ? 2 : 3;
        int buttons = 4;
        int toggles = 6;
        int rows = (buttons + cols - 1) / cols + (toggles + cols - 1) / cols;
        return 48 + rows * 30 + 6;
    }

    protected void drawSafetyLabels(GuiGraphicsExtractor c, int x, int y, int w) {
        int subY = y + 28;
        if (safetyPage == SafetyPage.CORE) {
            label(c, x, subY + 30, "worldbinder.config.recovery_interval");
        } else if (safetyPage == SafetyPage.RESOURCE_PACK) {
            if (isTextInContentArea(subY + 128)) WbText.drawWrapped(c, font, Lang.string("worldbinder.config.pack_page_hint"), x, subY + 128, w, WbTheme.TEXT_DIM, 3);
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
            int notificationY = mapY + hudMapSectionHeight(formW);
            drawSectionSurface(c, x + 4, y + 26, innerW, 58, WbTheme.INFO);
            drawSectionSurface(c, x + 4, y + 94, innerW, cols == 2 ? 130 : 78, WbTheme.ACCENT_DARK);
            drawSectionSurface(c, x + 4, mapY + 6, innerW, hudMapSectionHeight(formW) - 18, WbTheme.ACCENT);
            drawSectionSurface(c, x + 4, notificationY - 8, innerW, 130, WbTheme.INFO);
        } else if (tab == Tab.SAFETY && safetyPage == SafetyPage.GAMERULES) {
            drawSectionSurface(c, x + 4, y + 22, innerW, 42, WbTheme.WARN);
            if (isTextInContentArea(y + 32)) {
                WbText.drawClipped(c, font, Lang.string("worldbinder.gamerules.target", draft.targetMinecraftVersion),
                        x + 16, y + 32, w - 32, WbTheme.TEXT_MUTED);
            }
        } else if (tab == Tab.SAFETY && safetyPage == SafetyPage.CORE) {
            drawSectionSurface(c, x + 4, y + 26, innerW, 142, WbTheme.WARN);
        } else if (tab == Tab.SAFETY && safetyPage == SafetyPage.EXPORT) {
            drawSectionSurface(c, x + 4, y + 28, innerW, 96, WbTheme.ACCENT_RIGHT);
        } else if (tab == Tab.SAFETY && safetyPage == SafetyPage.RESOURCE_PACK) {
            drawSectionSurface(c, x + 4, y + 28, innerW, 156, WbTheme.WARN);
        }
    }

    protected void drawDecorations(GuiGraphicsExtractor c, int mouseX, int mouseY) {
        for (Deco deco : decorations) {
            switch (deco.kind()) {
                case SECTION -> drawSectionDeco(c, deco);
                case LABEL -> {
                    if (isTextInContentArea(deco.y())) {
                        WbText.drawClipped(c, font, deco.text(), deco.x(), deco.y(), deco.width(), deco.color());
                    }
                }
                case WRAPPED -> {
                    if (isTextInContentArea(deco.y())) {
                        WbText.drawWrapped(c, font, deco.text(), deco.x(), deco.y(), deco.width(), deco.color(), Math.max(1, deco.height()));
                    }
                }
                case TOOL_CARD -> drawToolCard(c, deco, mouseX, mouseY);
            }
        }
    }

    private void drawSectionDeco(GuiGraphicsExtractor c, Deco deco) {
        int clippedTop = Math.max(deco.y(), contentClipTop() - 40);
        if (deco.y() + deco.height() < contentClipTop() || deco.y() > contentClipBottom()) {
            return;
        }
        WbChrome.drawSectionSurface(c, deco.x(), deco.y(), deco.width(), deco.height(), deco.color());
        int textX = deco.x() + 12;
        if (deco.icon() != null && WorldBinder.config().uiShowIcons) {
            deco.icon().draw(c, textX, deco.y() + 9, 1, deco.color());
            textX += WbIcon.SIZE + 6;
        }
        if (deco.text() != null && deco.y() + 8 >= clippedTop) {
            c.text(font, Component.literal(WbText.ellipsize(font, deco.text(), deco.width() - 24)).withStyle(ChatFormatting.BOLD),
                    textX, deco.y() + 8, WbTheme.TEXT, true);
            if (deco.subtitle() != null && !deco.subtitle().isBlank()) {
                int subX = textX + font.width(Component.literal(deco.text()).withStyle(ChatFormatting.BOLD)) + 12;
                if (subX < deco.x() + deco.width() - 40) {
                    WbText.drawClipped(c, font, deco.subtitle(), subX, deco.y() + 8, deco.x() + deco.width() - subX - 10, WbTheme.TEXT_DIM);
                }
            }
        }
    }

    private void drawToolCard(GuiGraphicsExtractor c, Deco deco, int mouseX, int mouseY) {
        MovementTool tool = deco.tool();
        if (tool == null || deco.y() + deco.height() < contentClipTop() || deco.y() > contentClipBottom()) {
            return;
        }
        boolean enabled = draft.movement.enabled(tool);
        boolean hovered = WbChrome.contains(deco.x(), deco.y(), deco.width(), deco.height(), mouseX, mouseY)
                && mouseY >= contentClipTop() && mouseY <= contentClipBottom();
        int accent = categoryAccent(tool.category);
        WbChrome.drawCardSurface(c, deco.x(), deco.y(), deco.width(), deco.height(), enabled ? accent : WbTheme.PANEL_BORDER, hovered, enabled);
        int tileX = deco.x() + 10;
        int tileY = deco.y() + 9;
        WbChrome.rounded(c, tileX, tileY, 20, 20, enabled ? WbTheme.withAlpha(accent, 0x40) : 0x40000000);
        WbIcon.movement(tool).draw(c, tileX + 3, tileY + 3, 2, enabled ? accent : WbTheme.TEXT_DIM);
        int textX = deco.x() + 38;
        int textRight = deco.x() + deco.width() - 40;
        String name = Lang.string(tool.key());
        Component nameText = Component.literal(WbText.ellipsize(font, name, textRight - textX)).withStyle(ChatFormatting.BOLD);
        c.text(font, nameText, textX, deco.y() + 10, enabled ? WbTheme.TEXT : WbTheme.TEXT_SOFT, true);
        int afterName = textX + font.width(nameText) + 6;
        if (tool.isNew() && afterName + 26 < textRight) {
            afterName += drawNewBadge(c, afterName, deco.y() + 9) + 5;
        }
        String bound = MovementKeys.boundKey(tool);
        if (bound != null && afterName + font.width(bound) + 10 < textRight) {
            WbChrome.drawKeycap(c, font, afterName, deco.y() + 8, bound);
        }
        int descLines = tool.adjustable() ? 1 : 2;
        WbText.drawWrapped(c, font, Lang.string(tool.tooltipKey()), textX, deco.y() + 23, textRight - textX,
                WbTheme.mutedText(), descLines);
    }

    /** Compact glowing "NEW" badge: gradient pill with a sparkle and small caps text. Returns its width. */
    private int drawNewBadge(GuiGraphicsExtractor c, int x, int y) {
        String text = Lang.string("worldbinder.common.new");
        float scale = 0.75F;
        int textW = Math.round(font.width(text) * scale);
        int width = textW + 15;
        int height = 9;
        float pulse = WbMotion.pulse(2400L);
        WbChrome.rounded(c, x - 1, y - 1, width + 2, height + 2, WbTheme.withAlpha(WbTheme.ACCENT_RIGHT, 0x20 + Math.round(0x30 * pulse)));
        c.fillGradient(x + 1, y, x + width - 1, y + height, 0xFFFFD08A, 0xFFF29A3D);
        c.fill(x, y + 1, x + 1, y + height - 1, 0xFFF4AE55);
        c.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFFF4AE55);
        c.fill(x + 3, y + 4, x + 6, y + 5, 0xFF3A1E05);
        c.fill(x + 4, y + 3, x + 5, y + 6, 0xFF3A1E05);
        c.pose().pushMatrix();
        c.pose().translate(x + 9, y + 1.5F);
        c.pose().scale(scale, scale);
        c.text(font, text, 0, 0, 0xFF3A1E05, false);
        c.pose().popMatrix();
        return width;
    }

    protected int categoryAccent(MovementTool.Category category) {
        return switch (category) {
            case GROUND -> WbTheme.OK;
            case VERTICAL -> WbTheme.ACCENT;
            case WATER -> WbTheme.INFO;
            case AIR -> WbTheme.ACCENT_DARK;
        };
    }

    protected WbIcon categoryIcon(int group) {
        if (group >= MOVEMENT_OPTIONS_GROUP) {
            return WbIcon.SETTINGS;
        }
        return switch (MovementTool.Category.values()[group]) {
            case GROUND -> WbIcon.SPEED;
            case VERTICAL -> WbIcon.FLY;
            case WATER -> WbIcon.JESUS;
            case AIR -> WbIcon.AIR_CONTROL;
        };
    }

    protected void drawSectionSurface(GuiGraphicsExtractor c, int x, int y, int w, int h, int accent) {
        int clippedTop = Math.max(y, contentClipTop());
        int clippedBottom = Math.min(y + h, contentClipBottom());
        if (clippedBottom - clippedTop < 3) {
            return;
        }
        WbChrome.drawSectionSurface(c, x, y, w, h, accent);
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
            int sidebarY = top + 10;
            int sidebarW = settingsSidebarWidth(panelWidth) - 8;
            int sidebarH = panelHeight() - 60;
            c.fillGradient(left + 1, top + 3, left + settingsSidebarWidth(panelWidth), top + panelHeight() - 44, 0x22000000, 0x3A000000);
            c.fill(sidebarX + sidebarW - 1, sidebarY + 4, sidebarX + sidebarW, sidebarY + sidebarH, WbTheme.divider());
            WbBrand.draw(c, font, sidebarX + 8, sidebarY + 6, sidebarW - 18);
            WbChrome.drawDivider(c, sidebarX + 8, sidebarY + 42, sidebarW - 20);
            if (panelHeight() >= 390) {
                int boxY = sidebarY + sidebarH - 58;
                WbChrome.rounded(c, sidebarX + 6, boxY, sidebarW - 18, 50, 0x2A000000);
                WbIcon.INFO.draw(c, sidebarX + 12, boxY + 7, 1, WbTheme.withAlpha(tabAccent(tab), 0xCC));
                WbText.drawWrapped(c, font, Component.translatable(tabDescriptionKey(tab)),
                        sidebarX + 23, boxY + 6, sidebarW - 36, WbTheme.TEXT_DIM, 3);
            }
            return;
        }

        int y = top + (compact() ? 42 : 52);
        int navW = panelWidth - 36;
        WbChrome.drawDivider(c, left + 18, y - 5, navW);
        if (!hasSubNavigation()) {
            return;
        }
        int safetyY = y + navRows(panelWidth) * 26 + 6;
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

    protected void section(int x, int y, int w, int h, int accent, WbIcon icon, String titleKey, String subtitleKey) {
        decorations.add(new Deco(DecoKind.SECTION, x, y, w, h, accent, Lang.string(titleKey),
                subtitleKey == null ? null : Lang.string(subtitleKey), icon, null));
    }

    protected void textLine(int x, int y, int w, String text, int color) {
        decorations.add(new Deco(DecoKind.LABEL, x, y, w, 1, color, text, null, null, null));
    }

    protected void textBlock(int x, int y, int w, int lines, String text, int color) {
        decorations.add(new Deco(DecoKind.WRAPPED, x, y, w, lines, color, text, null, null, null));
    }

    protected void toolCard(int x, int y, int w, int h, MovementTool tool) {
        decorations.add(new Deco(DecoKind.TOOL_CARD, x, y, w, h, 0, null, null, null, tool));
    }
}

package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.placement.ScenePlacementService;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.status.WorldBinderActivityLog;
import net.worldbinder.ui.component.WbCard;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbProgressBar;
import net.worldbinder.ui.component.WbSectionHeader;
import net.worldbinder.ui.component.WbSelectableList;
import net.worldbinder.ui.component.WbSidebar;
import net.worldbinder.ui.component.WbStatusChip;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

abstract class WorldBinderScreenView extends Screen {
    protected enum Section {
        OVERVIEW("worldbinder.section.overview", "worldbinder.section.overview.short", "worldbinder.section.overview.tooltip"),
        CAPTURE("worldbinder.section.capture", "worldbinder.section.capture.short", "worldbinder.section.capture.tooltip"),
        MAP("worldbinder.section.map", "worldbinder.section.map.short", "worldbinder.section.map.tooltip"),
        ARCHIVES("worldbinder.section.archives", "worldbinder.section.archives.short", "worldbinder.section.archives.tooltip"),
        RECOVERY("worldbinder.section.recovery", "worldbinder.section.recovery.short", "worldbinder.section.recovery.tooltip"),
        SETTINGS("worldbinder.section.settings", "worldbinder.section.settings.short", "worldbinder.section.settings.tooltip"),
        TOOLS("worldbinder.section.tools", "worldbinder.section.tools.short", "worldbinder.section.tools.tooltip"),
        ABOUT("worldbinder.section.about", "worldbinder.section.about.short", "worldbinder.section.about.tooltip");

        final String titleKey;
        final String shortTitleKey;
        final String subtitleKey;

        Section(String titleKey, String shortTitleKey, String subtitleKey) {
            this.titleKey = titleKey;
            this.shortTitleKey = shortTitleKey;
            this.subtitleKey = subtitleKey;
        }

        String title() {
            return Lang.string(titleKey);
        }

        String shortTitle() {
            return Lang.string(shortTitleKey);
        }

        Component subtitle() {
            return Component.translatable(subtitleKey);
        }
    }

    protected final SelectionManager selections;
    protected final SceneCaptureService capture;
    protected final ScenePlacementService placement;
    protected final SceneLibrary library;
    protected final Screen parent;
    protected EditBox archiveName;
    protected String pendingArchiveName;
    protected Section section = Section.OVERVIEW;
    protected Component customTooltip;
    protected int selectedArchiveIndex;
    protected Button placeSelectedButton;
    protected Button openSelectedButton;
    protected Button finalizeRecoveryButton;
    protected Button continueRecoveryButton;
    protected Button saveAsArchiveButton;
    protected Button exportPreviewButton;
    protected Button validateSelectedButton;
    protected Button deleteSelectedButton;
    protected int archiveListOffset;
    protected Path pendingDeletePath;
    protected long pendingDeleteMillis;
    protected long nextActivityRefreshMillis;
    protected int contentScroll;
    protected boolean sectionDropdownOpen;
    protected boolean presetDropdownOpen;
    protected boolean targetVersionDropdownOpen;
    protected List<String> cachedActivityEntries = List.of();

    protected WorldBinderScreenView(SelectionManager selections, SceneCaptureService capture,
                                    ScenePlacementService placement, SceneLibrary library, Screen parent) {
        super(Component.translatable("worldbinder.gui.title"));
        this.selections = selections;
        this.capture = capture;
        this.placement = placement;
        this.library = library;
        this.parent = parent;
    }

    protected abstract boolean worldAvailable();

    protected abstract String currentArchiveName();

    protected abstract Path selectedArchive();

    protected abstract List<Path> archiveRows(boolean recoveryOnly);

    protected abstract Path selectedArchiveForRows(List<Path> rows);

    protected abstract int visibleArchiveRows();

    protected abstract void clampArchiveWindow(int size);

    protected abstract int captureTargetDropdownWidth(int contentW);

    protected abstract int captureTargetVersionColumns(int width);

    protected abstract int captureTargetDropdownOffset(int contentW);

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    protected int contentViewportTop() {
        int panelWidth = panelWidth();
        return WbLayout.top(height, panelHeight()) + (WbLayout.sidebarWidth(panelWidth) <= 0 ? 50 : 68);
    }

    protected int contentViewportBottom() {
        return WbLayout.top(height, panelHeight()) + panelHeight() - 42;
    }

    protected boolean isInsideContentViewport(int y, int h) {
        return y >= contentViewportTop() && y + h <= contentViewportBottom();
    }

    protected boolean isPointInsideContentViewport(int x, int y) {
        int left = WbLayout.left(width, panelWidth());
        int contentLeft = contentX(left);
        int contentRight = contentLeft + contentWidth(panelWidth());
        return x >= contentLeft && x < contentRight
                && y >= contentViewportTop() && y < contentViewportBottom();
    }

    protected void drawSectionContent(GuiGraphicsExtractor context, int left, int top, int panelWidth, int mouseX, int mouseY) {
        int x = contentX(left);
        int w = contentWidth(panelWidth);
        if (section == Section.OVERVIEW) {
            drawOverview(context, x, top, w, mouseX, mouseY);
        } else if (section == Section.CAPTURE) {
            drawCapture(context, x, top, w, mouseX, mouseY);
        } else if (section == Section.MAP) {
            drawMapSection(context, x, top, w, mouseX, mouseY);
        } else if (section == Section.ARCHIVES) {
            drawArchiveSection(context, x, top, w, mouseX, mouseY, false);
        } else if (section == Section.RECOVERY) {
            drawRecoverySection(context, x, top, w, mouseX, mouseY);
        } else if (section == Section.SETTINGS) {
            drawSettingsSection(context, x, top, w, mouseX, mouseY);
        } else if (section == Section.TOOLS) {
            drawToolsSection(context, x, top, w, mouseX, mouseY);
        } else {
            drawAboutSection(context, x, top, w, mouseX, mouseY);
        }
    }

    protected int panelWidth() {
        return WbLayout.panelWidth(width);
    }

    protected int panelHeight() {
        return WbLayout.panelHeight(height);
    }

    protected int contentX(int left) {
        return WbLayout.contentX(left, panelWidth());
    }

    protected int contentWidth(int panelWidth) {
        return WbLayout.contentWidth(panelWidth);
    }

protected void drawOverview(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean compact = WbLayout.compact(panelWidth(), panelHeight());
        boolean tiny = WbLayout.tiny(panelWidth(), panelHeight());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int heroY = top + 70;
        int actionRows = 2;
        int actionsTop = bottom - (tiny ? 52 : 60);
        int heroH = tiny
                ? 88
                : Math.max(132, Math.min(compact ? 174 : 208, actionsTop - heroY - 96));
        drawCard(context, x, heroY, width, heroH, Lang.text("worldbinder.overview.title"), Lang.text("worldbinder.overview.subtitle"), mouseX, mouseY);

        String status = !worldAvailable()
                ? Lang.string("worldbinder.common.no_world")
                : capture.isSaving() ? Lang.string("worldbinder.common.saving") : capture.isPaused() ? Lang.string("worldbinder.common.paused") : capture.isCapturing() ? Lang.string("worldbinder.common.capturing") : Lang.string("worldbinder.common.idle");
        int statusColor = !worldAvailable()
                ? WbTheme.WARN
                : capture.isSaving() || capture.isPaused() ? WbTheme.WARN : capture.isCapturing() ? WbTheme.OK : WbTheme.TEXT_MUTED;
        WbText.drawClipped(context, font, "◆ " + status, x + 20, heroY + 32, width - 40, statusColor);
        int recoveries = library.recoveryCount();
        if (recoveries > 0 && width > 420) {
            WbText.drawClipped(context, font, Lang.string("worldbinder.recovery.sessions") + ": " + recoveries, x + Math.max(20, width / 2), heroY + 32, width / 2 - 24, WbTheme.WARN);
        }

        int twoCol = width > 560 ? width / 2 : width;
        if (capture.isCapturing() && heroH > 178) {
            WbText.drawClipped(context, font, capture.captureRouteHint(), x + 20, heroY + 52, width - 40, WbTheme.INFO);
        }
        drawLine(context, x + 20, heroY + (capture.isCapturing() && heroH > 178 ? 72 : 56), Lang.string("worldbinder.common.archive"), capture.isCapturing() ? capture.activeArchiveDisplayName() : currentArchiveName(), WbTheme.TEXT_SOFT);
        int infoBaseY = capture.isCapturing() && heroH > 178 ? heroY + 92 : heroY + 76;
        drawLine(context, x + 20, infoBaseY, Lang.string("worldbinder.common.preset"), presetLine(), WbTheme.TEXT_MUTED);
        if (width > 560) {
            drawLine(context, x + width / 2, heroY + (capture.isCapturing() && heroH > 178 ? 72 : 56), Lang.string("worldbinder.common.mode"), capture.modeName(), WbTheme.TEXT_SOFT);
            drawLine(context, x + width / 2, infoBaseY, Lang.string("worldbinder.common.last"), lastSessionLine(), WbTheme.TEXT_MUTED);
        }

        int chipTop = heroY + (capture.isCapturing() && heroH > 178 ? 120 : 104);
        int chipCols = width < 520 ? 2 : 3;
        int chipGap = 8;
        int chipW = Math.max(64, (width - 40 - chipGap * (chipCols - 1)) / chipCols);
        String[][] chips = {
                {Lang.string("worldbinder.common.saved"), Integer.toString(capture.scannedChunks())},
                {Lang.string("worldbinder.common.queued"), Integer.toString(capture.queuedChunks())},
                {Lang.string("worldbinder.common.entities"), Integer.toString(capture.capturedEntities())},
                {Lang.string("worldbinder.common.eta"), capture.estimatedFinishText()},
                {Lang.string("worldbinder.common.throttle"), capture.adaptiveThrottlePercent() + "%"},
                {Lang.string("worldbinder.common.last"), lastSessionLine()}
        };
        int[] colors = {WbTheme.OK, capture.queuedChunks() > 300 ? WbTheme.ERROR : WbTheme.WARN, WbTheme.ACCENT, WbTheme.INFO, capture.adaptiveThrottlePercent() < 70 ? WbTheme.ERROR : WbTheme.OK, WbTheme.TEXT_MUTED};
        int availableChipRows = Math.max(1, (heroY + heroH - chipTop - 8) / 44);
        int maxChips = Math.min(chips.length, chipCols * availableChipRows);
        for (int i = 0; i < maxChips; i++) {
            int row = i / chipCols;
            int col = i % chipCols;
            int cy = chipTop + row * 44;
            if (cy + 38 <= heroY + heroH - 8) {
                drawChip(context, x + 20 + col * (chipW + chipGap), cy, chipW, chips[i][0], chips[i][1], colors[i]);
            }
        }

        int cardsY = heroY + heroH + 12;
        int cardsH = Math.max(42, actionsTop - cardsY - 16);
        if (cardsH > 42) {
            int gap = 16;
            int cardW = width > 620 ? (width - gap) / 2 : width;
            drawCard(context, x, cardsY, cardW, cardsH, Lang.text("worldbinder.overview.activity"), Lang.text("worldbinder.overview.activity.tooltip"), mouseX, mouseY);
            drawActivity(context, x + 20, cardsY + 34, Math.max(60, cardW - 40), Math.max(1, (cardsH - 42) / 16));
            if (width > 620) {
                drawCard(context, x + cardW + gap, cardsY, cardW, cardsH, Lang.text("worldbinder.overview.hints"), Lang.text("worldbinder.overview.hints.tooltip"), mouseX, mouseY);
                drawHints(context, x + cardW + gap + 20, cardsY + 34, Math.max(60, cardW - 40), Math.max(1, (cardsH - 42) / 16));
            }
        }

        if (!tiny) {
            WbText.drawClipped(context, font, Lang.string("worldbinder.overview.quick_actions"), x + 20, actionsTop - 18, width - 40, WbTheme.TEXT_DIM);
        }
    }

protected void drawCapture(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean narrow = width < 620;
        int card1Y = top + 88;
        int targetOffset = captureTargetDropdownOffset(width);
        int card1H = (narrow ? 232 : 216) + targetOffset;
        int archiveFieldY = card1Y + 54;
        int targetFieldY = card1Y + 116;
        int actionY = card1Y + 154 + targetOffset;
        int archiveW = Math.max(120, width - 36);
        int targetW = captureTargetDropdownWidth(width);
        drawCard(context, x, card1Y, width, card1H, Lang.text("worldbinder.section.capture"), Lang.text("worldbinder.capture.card.tooltip"), mouseX, mouseY);
        WbChrome.drawInset(context, x + 18, archiveFieldY - 2, archiveW, 26, WbTheme.INFO, false);
        WbChrome.drawInset(context, x + 18, targetFieldY - 2, targetW, 26, WbTheme.ACCENT_RIGHT, targetVersionDropdownOpen || WbChrome.contains(x + 18, targetFieldY, targetW, 22, mouseX, mouseY));
        WbText.drawClipped(context, font, Lang.string("worldbinder.gui.archive_name"), x + 20, archiveFieldY - 16, width - 40, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, Lang.string("worldbinder.gui.target_output_version"), x + 20, targetFieldY - 16, width - 40, WbTheme.TEXT_MUTED);
        drawCaptureTargetVersionDropdown(context, x + 18, targetFieldY + 28, targetW, mouseX, mouseY);
        WbText.drawClipped(context, font, targetSummaryLine(), x + 20, actionY + 30, width - 40, WbTheme.INFO);

        int presetY = card1Y + (narrow ? 244 : 228) + targetOffset;
        int presetH = width < 560 ? 190 : 164;
        drawCard(context, x, presetY, width, presetH, Lang.text("worldbinder.capture.performance"), Lang.text("worldbinder.capture.performance.tooltip"), mouseX, mouseY);
        WbText.drawClipped(context, font, Lang.string("worldbinder.common.preset") + ": " + presetLine(), x + 20, presetY + 34, width - 40, WbTheme.TEXT_SOFT);
        WbText.drawClipped(context, font, WorldBinder.config().presetDescription(), x + 20, presetY + 52, width - 40, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, Lang.string("worldbinder.capture.budget_line", WorldBinder.config().effectiveBlocksPerTick(), WorldBinder.config().effectiveTickBudgetMillis(), WorldBinder.config().effectiveRoamingRadiusChunks()), x + 20, presetY + 70, width - 40, WbTheme.TEXT_DIM);
        if (presetDropdownOpen) {
            int menuW = Math.min(230, Math.max(146, width - 36));
            int menuY = presetY + 112;
            WbChrome.drawDropdownPanel(context, x + 18, menuY, menuW, 20, 4, WbTheme.WARN);
            net.worldbinder.config.WorldBinderConfig.PerformancePreset active = WorldBinder.config().performancePreset;
            net.worldbinder.config.WorldBinderConfig.PerformancePreset[] rows = {
                    net.worldbinder.config.WorldBinderConfig.PerformancePreset.SAFE,
                    net.worldbinder.config.WorldBinderConfig.PerformancePreset.BALANCED,
                    net.worldbinder.config.WorldBinderConfig.PerformancePreset.FAST,
                    net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME
            };
            for (int i = 0; i < rows.length; i++) {
                int rowY = menuY + 3 + i * 20;
                boolean selected = active == rows[i];
                boolean hovered = WbChrome.contains(x + 18, rowY, menuW, 20, mouseX, mouseY);
                WbChrome.drawDropdownRow(context, x + 18, rowY, menuW, 20, selected, hovered, selected ? WbTheme.ACCENT : WbTheme.INFO);
            }
        }
    }

    protected void drawCaptureTargetVersionDropdown(GuiGraphicsExtractor context, int x, int y, int width, int mouseX, int mouseY) {
        if (!targetVersionDropdownOpen) {
            return;
        }
        int cols = captureTargetVersionColumns(width);
        int gap = 5;
        int optionW = Math.max(34, (width - gap * (cols - 1)) / cols);
        int rows = (TargetMinecraftVersion.FINAL_RELEASES.size() + cols - 1) / cols;
        WbChrome.drawDropdownPanel(context, x, y - 4, width, 22, rows, WbTheme.ACCENT_RIGHT);
        String current = TargetMinecraftVersion.normalize(WorldBinder.config().targetMinecraftVersion);
        for (int i = 0; i < TargetMinecraftVersion.FINAL_RELEASES.size(); i++) {
            TargetMinecraftVersion.Entry entry = TargetMinecraftVersion.FINAL_RELEASES.get(i);
            int col = i % cols;
            int row = i / cols;
            int rowX = x + col * (optionW + gap);
            int rowY = y + row * 22;
            boolean selected = entry.name().equals(current);
            boolean hovered = WbChrome.contains(rowX, rowY, optionW, 19, mouseX, mouseY);
            WbChrome.drawDropdownRow(context, rowX, rowY, optionW, 19, selected, hovered, selected ? WbTheme.ACCENT : WbTheme.INFO);
        }
    }

protected void drawMapSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean tiny = WbLayout.tiny(panelWidth(), panelHeight());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int cardH = Math.max(128, bottom - top - 146);
        drawCard(context, x, top + 70, width, cardH, Lang.text("worldbinder.section.map"), Lang.text("worldbinder.section.map.tooltip"), mouseX, mouseY);
        if (!tiny && width > 360) {
            drawMiniChunkMap(context, x + 24, top + 112, 9, Math.min(12, Math.max(7, (width - 220) / 24)));
        }
        int textX = tiny || width <= 360 ? x + 20 : x + 170;
        int textW = tiny || width <= 360 ? width - 40 : width - 190;
        WbText.drawWrapped(context, font, Lang.string("worldbinder.map.screen_hint"), textX, top + 112, textW, WbTheme.TEXT_SOFT, 3);
        WbText.drawClipped(context, font, Lang.string(
                "worldbinder.map.mode_line",
                mapLayerLabel(WorldBinder.config().f10MapLayerMode),
                radarDetailLabel(WorldBinder.config().radarDetailMode)
        ), textX, top + 166, textW, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, Lang.string("worldbinder.map.saved_line", capture.scannedChunks(), capture.queuedChunks(), capture.partialChunksView().size()), textX, top + 190, textW, WbTheme.TEXT_DIM);
        WbText.drawWrapped(context, font, capture.captureRouteHint(), textX, top + 214, textW, WbTheme.INFO, 2);
    }

protected void drawArchiveSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY, boolean recoveryOnly) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int actionRows = width < 420 ? 3 : width < 720 ? 3 : 2;
        int actionsSpace = actionRows * 27 + 18;
        int cardH = Math.max(108, bottom - (top + 70) - actionsSpace);
        drawCard(context, x, top + 70, width, cardH, recoveryOnly ? Lang.text("worldbinder.recovery.sessions") : Lang.text("worldbinder.section.archives"), recoveryOnly ? Lang.text("worldbinder.recovery.sessions.tooltip") : Lang.text("worldbinder.archives.tooltip"), mouseX, mouseY);
        drawArchiveList(context, x, top, width, recoveryOnly, false);
    }

protected void drawRecoverySection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawArchiveSection(context, x, top, width, mouseX, mouseY, true);
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int recoveries = library.recoveryCount();
        int color = recoveries > 0 ? WbTheme.WARN : WbTheme.OK;
        WbText.drawWrapped(context, font, recoveries > 0 ? Lang.string("worldbinder.recovery.available_hint") : Lang.string("worldbinder.recovery.empty"), x + 20, Math.max(top + 156, bottom - 110), width - 40, color, 2);
    }

protected void drawValidationSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawCard(context, x, top + 76, width, 226, Lang.text("worldbinder.gui.validate"), Lang.text("worldbinder.tooltip.validate_selected"), mouseX, mouseY);
        drawArchiveList(context, x, top, width, false, true);
        Path selected = selectedArchive();
        String line = selected == null ? Lang.string("worldbinder.chat.no_archive_selected") : library.validationLine(selected);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.validation.selected_result", line), x + 20, top + 282, WbTheme.TEXT_MUTED);
    }

protected void drawSettingsSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        int bottom = WbLayout.contentBottom(top + contentScroll, panelHeight());
        boolean compact = WbLayout.compact(panelWidth(), panelHeight());
        int cols = width < 560 ? 1 : 2;
        int gap = 14;
        int cardW = cols == 1 ? width : (width - gap) / 2;
        String[][] cards = {
                {"worldbinder.settings.card.general", "worldbinder.settings.card.general.desc"},
                {"worldbinder.settings.card.capture", "worldbinder.settings.card.capture.desc"},
                {"worldbinder.settings.card.performance", "worldbinder.settings.card.performance.desc"},
                {"worldbinder.settings.card.map", "worldbinder.settings.card.map.desc"},
                {"worldbinder.settings.card.recovery", "worldbinder.settings.card.recovery.desc"},
                {"worldbinder.settings.card.export", "worldbinder.settings.card.export.desc"},
                {"worldbinder.settings.card.safety", "worldbinder.settings.card.safety.desc"},
                {"worldbinder.settings.card.advanced", "worldbinder.settings.card.advanced.desc"}
        };
        int startY = top + 66;
        int rowsNeeded = (int) Math.ceil(cards.length / (double) cols);
        int cardH = compact ? 56 : 68;
        for (int i = 0; i < cards.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = startY + row * (cardH + 12);
            if (cy + cardH < top + 58 || cy > bottom - 48) {
                continue;
            }
            int accent = switch (i) {
                case 1 -> WbTheme.OK;
                case 2 -> WbTheme.WARN;
                case 3 -> WbTheme.INFO;
                case 4 -> WbTheme.ACCENT_RIGHT;
                case 5 -> WbTheme.ACCENT_DARK;
                case 6 -> WbTheme.ERROR;
                default -> WbTheme.ACCENT;
            };
            String cardTitle = Lang.string(cards[i][0]);
            String cardDesc = Lang.string(cards[i][1]);
            boolean hovered = isPointInsideContentViewport(mouseX, mouseY)
                    && WbChrome.contains(cx, cy, cardW, cardH, mouseX, mouseY);
            WbChrome.drawCard(context, font, cx, cy, cardW, cardH, Component.literal(cardTitle), accent, hovered);
            WbText.drawWrapped(context, font, cardDesc, cx + 14, cy + 32, cardW - 28, i == 7 ? WbTheme.WARN : WbTheme.TEXT_MUTED, cardH < 60 ? 1 : 2);
            if (hovered) {
                customTooltip = Component.literal(cardTitle + "\n" + cardDesc);
                context.fill(cx + cardW - 3, cy + 4, cx + cardW - 1, cy + cardH - 4, accent);
            }
        }
        int infoY = startY + rowsNeeded * (cardH + 12) + 8;
        if (infoY < bottom - 44) {
            WbText.drawClipped(context, font, Lang.string("worldbinder.settings.current_line", presetLine(), WorldBinder.config().adaptiveThrottle ? Lang.string("worldbinder.common.enabled") : Lang.string("worldbinder.common.disabled"), WorldBinder.config().targetFps), x + 20, infoY, width - 40, WbTheme.TEXT_MUTED);
        }
    }

    protected void drawToolsSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawCard(context, x, top + 76, width, 210, Lang.text("worldbinder.section.tools"), Lang.text("worldbinder.section.tools.tooltip"), mouseX, mouseY);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.tools.description"), x + 20, top + 116, WbTheme.TEXT_SOFT);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.tools.save_folder", net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath()), x + 20, top + 146, WbTheme.TEXT_DIM);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.tools.current_operation", OperationStatus.visible() ? OperationStatus.detail() : Lang.string("worldbinder.common.none")), x + 20, top + 172, WbTheme.TEXT_MUTED);
    }

protected void drawAboutSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int cardY = top + 70;
        int cardH = Math.max(150, bottom - cardY - 10);
        drawCard(context, x, cardY, width, cardH, Lang.text("worldbinder.about.title"), Lang.text("worldbinder.about.tooltip"), mouseX, mouseY);
        int y = cardY + 36;
        int textW = width - 40;
        WbText.drawClipped(context, font, Lang.string("worldbinder.about.version"), x + 20, y, textW, WbTheme.ACCENT);
        y += 22;
        y += WbText.drawWrapped(context, font, Lang.string("worldbinder.about.author"), x + 20, y, textW, WbTheme.TEXT_SOFT, 2);
        y += 6;
        y += WbText.drawWrapped(context, font, Lang.string("worldbinder.about.keys"), x + 20, y, textW, WbTheme.TEXT_MUTED, 2);
        y += 6;
        y += WbText.drawWrapped(context, font, Lang.string("worldbinder.about.feedback"), x + 20, y, textW, WbTheme.INFO, 2);
        y += 6;
        y += WbText.drawWrapped(context, font, Lang.string("worldbinder.about.data", net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath()), x + 20, y, textW, WbTheme.TEXT_MUTED, 2);
        y += 12;
        if (y < bottom - 88) {
            WbText.draw(context, font, Lang.string("worldbinder.about.legal_title"), x + 20, y, WbTheme.WARN);
            y += 20;
            y += WbText.drawWrapped(context, font, Lang.string("worldbinder.about.legal_1"), x + 20, y, textW, WbTheme.TEXT_SOFT, 3);
            y += 4;
            y += WbText.drawWrapped(context, font, Lang.string("worldbinder.about.legal_2"), x + 20, y, textW, WbTheme.TEXT_SOFT, 3);
            y += 4;
            WbText.drawWrapped(context, font, Lang.string("worldbinder.about.legal_3"), x + 20, y, textW, WbTheme.ERROR, 2);
        }
    }

protected void drawArchiveList(GuiGraphicsExtractor context, int x, int top, int width, boolean recoveryOnly, boolean validationMode) {
        List<Path> scenes = archiveRows(recoveryOnly);
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int listTop = top + 146;
        int listBottom = bottom - (width < 420 ? 112 : width < 720 ? 104 : 74);
        if (scenes.isEmpty()) {
            WbText.drawWrapped(context, font, recoveryOnly ? Lang.string("worldbinder.archive.no_recoveries") : Lang.string("worldbinder.archive.no_archives"), x + 20, listTop, width - 40, WbTheme.TEXT_MUTED, 2);
            WbText.drawWrapped(context, font, Lang.string("worldbinder.archive.folder", net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath()), x + 20, listTop + 34, width - 40, WbTheme.TEXT_DIM, 3);
            return;
        }
        clampArchiveWindow(scenes.size());
        Path selectedPath = selectedArchiveForRows(scenes);
        boolean selectedRecovery = library.isRecovery(selectedPath);
        String selectedName = selectedPath == null ? Lang.string("worldbinder.common.none") : selectedPath.getFileName().toString();
        String recoveryState = selectedRecovery ? Lang.string("worldbinder.archive.state_suffix", library.recoveryState(selectedPath)) : "";
        WbText.drawClipped(context, font, Lang.string("worldbinder.archive.selected_line", shorten(selectedName, 54), library.recoveryCount()) + recoveryState, x + 20, top + 104, width - 40, selectedRecovery ? WbTheme.WARN : WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, Lang.string("worldbinder.archive.validation_line", selectedPath == null ? Lang.string("worldbinder.selection.no_complete") : library.validationLine(selectedPath)), x + 20, top + 120, width - 40, WbTheme.TEXT_DIM);
        if (selectedPath != null && selectedPath.equals(pendingDeletePath) && System.currentTimeMillis() - pendingDeleteMillis < 5000L) {
            WbText.drawClipped(context, font, Lang.string("worldbinder.archive.delete_armed"), x + 20, listBottom + 6, width - 40, WbTheme.ERROR);
        }
        int rowH = 20;
        int count = Math.min(Math.max(1, (listBottom - listTop) / rowH), scenes.size() - archiveListOffset);
        for (int i = 0; i < count; i++) {
            Path path = scenes.get(archiveListOffset + i);
            int rowY = listTop + i * rowH;
            boolean selected = path.equals(selectedPath);
            boolean recovery = library.isRecovery(path);
            WbSelectableList.drawRow(context, x + 18, rowY - 4, width - 36, 17, selected, i % 2 == 0);
            String name = (selected ? "◆ " : "• ") + (recovery ? Lang.string("worldbinder.archive.recovery_tag") : Lang.string("worldbinder.archive.archive_tag")) + path.getFileName();
            int metaW = width > 460 ? 220 : 0;
            WbText.drawClipped(context, font, name, x + 28, rowY, width - 56 - metaW, recovery ? WbTheme.WARN : WbTheme.TEXT_SOFT);
            if (metaW > 0) {
                WbText.drawClipped(context, font, library.validationLine(path), x + width - metaW - 18, rowY, metaW, WbTheme.TEXT_DIM);
            }
        }
        if (scenes.size() > count) {
            int from = archiveListOffset + 1;
            int to = archiveListOffset + count;
            WbText.drawClipped(context, font, Lang.string("worldbinder.archive.showing", from, to, scenes.size()), x + 28, Math.min(listBottom - 2, listTop + count * rowH + 2), width - 56, WbTheme.TEXT_DIM);
        }
    }

    protected void drawShell(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        WbSidebar.drawShell(context, x, y, width, height);
        int cx = contentX(x);
        int cw = contentWidth(width);
        int top = y + 18;
        int bottom = y + height - 42;
        context.fill(cx - 10, top, cx + cw + 10, bottom, 0x33141D28);
        context.fill(cx - 10, top, cx + cw + 10, top + 1, 0x55415364);
        context.fill(cx - 10, top, cx - 7, bottom, sectionAccent(section));
        context.fill(cx + cw + 7, top, cx + cw + 10, bottom, 0x33202A3A);
    }

protected void drawSidebar(GuiGraphicsExtractor context, int left, int top) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int sidebarWidth = WbLayout.sidebarWidth(panelWidth);
        if (sidebarWidth <= 0) {
            return;
        }
        int buttonX = left + 12;
        int buttonW = Math.max(50, sidebarWidth - 24);
        int buttonH = WbLayout.sidebarButtonHeight(panelHeight);
        int y = top + (panelHeight < 390 ? 54 : 76);
        int step = WbLayout.sidebarStep(panelHeight);
        WbSidebar.drawHeader(context, font, left, top, panelWidth);
        for (Section entry : Section.values()) {
            WbSidebar.drawEntry(context, buttonX, y, buttonW, buttonH, section == entry);
            y += step;
        }
        int statusY = top + panelHeight - (panelHeight < 390 ? 48 : 74);
        if (statusY > y + 8) {
            WbText.drawClipped(context, font, Lang.string("worldbinder.common.status"), left + 14, statusY, sidebarWidth - 22, WbTheme.TEXT_DIM);
            WbText.drawClipped(context, font, capture.isCapturing() ? "§a" + Lang.string("worldbinder.common.capturing") : capture.isSaving() ? "§e" + Lang.string("worldbinder.common.saving") : "§7" + Lang.string("worldbinder.common.idle"), left + 14, statusY + 16, sidebarWidth - 22, WbTheme.TEXT_SOFT);
            if (sidebarWidth > 96 && panelHeight > 390) {
                WbText.drawClipped(context, font, Lang.string("worldbinder.common.queue_value", capture.queuedChunks()), left + 14, statusY + 32, sidebarWidth - 22, capture.highQueuePressure() ? WbTheme.ERROR : WbTheme.TEXT_MUTED);
            }
        }
    }

protected void drawSectionHeader(GuiGraphicsExtractor context, int x, int y, int width) {
        int reserved = panelWidth() < 620 && width > 320 ? 190 : 0;
        WbSectionHeader.draw(context, font, x, y, Math.max(80, width - reserved), section.title(), WbText.ellipsize(font, section.subtitle().getString(), Math.max(40, width - reserved - 8)), sectionAccent(section));
    }

    protected void drawSectionDropdown(GuiGraphicsExtractor context, int left, int top, int panelWidth, int mouseX, int mouseY) {
        if (!sectionDropdownOpen || panelWidth >= 620) {
            return;
        }
        int contentX = contentX(left);
        int contentW = contentWidth(panelWidth);
        int dropdownW = Math.min(178, Math.max(132, contentW / 3));
        int x = contentX + contentW - dropdownW;
        int y = top + 48;
        WbChrome.drawDropdownPanel(context, x, y, dropdownW, 20, Section.values().length);
        int rowY = y + 3;
        for (Section target : Section.values()) {
            boolean selected = target == section;
            boolean hovered = WbChrome.contains(x, rowY, dropdownW, 20, mouseX, mouseY);
            WbChrome.drawDropdownRow(context, x, rowY, dropdownW, 20, selected, hovered, selected ? WbTheme.ACCENT : WbTheme.INFO);
            rowY += 20;
        }
    }

    protected void drawCard(GuiGraphicsExtractor context, int x, int y, int width, int height, Component title, Component tooltip, int mouseX, int mouseY) {
        if (WbCard.draw(context, font, x, y, width, height, title, sectionAccent(section), mouseX, mouseY)) {
            customTooltip = tooltip;
        }
    }

    protected int sectionAccent(Section value) {
        return switch (value) {
            case OVERVIEW -> WbTheme.ACCENT;
            case CAPTURE -> WbTheme.ACCENT_RIGHT;
            case MAP -> WbTheme.INFO;
            case ARCHIVES -> WbTheme.OK;
            case RECOVERY -> WbTheme.WARN;
            case SETTINGS -> WbTheme.ACCENT_DARK;
            case TOOLS -> WbTheme.TEXT_MUTED;
            case ABOUT -> WbTheme.ERROR;
        };
    }

    protected void drawChip(GuiGraphicsExtractor context, int x, int y, int width, String label, String value, int accent) {
        WbStatusChip.draw(context, font, x, y, width, label, value, accent);
    }

    protected void drawActivity(GuiGraphicsExtractor context, int x, int y) {
        drawActivity(context, x, y, 240, 4);
    }

    protected void drawActivity(GuiGraphicsExtractor context, int x, int y, int width, int maxLines) {
        long now = System.currentTimeMillis();
        if (now >= nextActivityRefreshMillis) {
            cachedActivityEntries = WorldBinderActivityLog.snapshot();
            nextActivityRefreshMillis = now + 500L;
        }
        List<String> entries = cachedActivityEntries;
        if (entries.isEmpty()) {
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.activity.empty"), x, y, WbTheme.TEXT_DIM);
            return;
        }
        for (int i = 0; i < Math.min(maxLines, entries.size()); i++) {
            WbText.drawClipped(context, font, entries.get(i), x, y + i * 16, width, WbTheme.TEXT_MUTED);
        }
    }

    protected void drawHints(GuiGraphicsExtractor context, int x, int y) {
        drawHints(context, x, y, 240, 4);
    }

    protected void drawHints(GuiGraphicsExtractor context, int x, int y, int width, int maxLines) {
        List<String> hints = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (capture.largeSessionDetected()) {
            hints.add(Lang.string("worldbinder.hint.large_session"));
            colors.add(WbTheme.WARN);
        }
        if (capture.isRecoverySaveRunning()) {
            hints.add(Lang.string("worldbinder.hint.recovery_writing"));
            colors.add(WbTheme.INFO);
        }
        if (WorldBinder.config().f10MapLayerMode != net.worldbinder.config.WorldBinderConfig.MapLayerMode.BOTH) {
            hints.add(Lang.string("worldbinder.hint.map_quality"));
            colors.add(WbTheme.WARN);
        }
        if (capture.highQueuePressure()) {
            hints.add(Lang.string("worldbinder.hint.high_queue"));
            colors.add(WbTheme.ERROR);
        }
        if (capture.multiplayerSafetyActive()) {
            hints.add(Lang.string("worldbinder.hint.server_safety"));
            colors.add(WbTheme.INFO);
        }
        if (hints.isEmpty()) {
            hints.add(Lang.string("worldbinder.common.done"));
            colors.add(WbTheme.OK);
            hints.add(Lang.string("worldbinder.hint.f10_map"));
            colors.add(WbTheme.TEXT_MUTED);
        }
        for (int i = 0; i < Math.min(maxLines, hints.size()); i++) {
            WbText.drawClipped(context, font, "• " + hints.get(i), x, y + i * 16, width, colors.get(i));
        }
    }

    protected void drawMiniChunkMap(GuiGraphicsExtractor context, int x, int y, int size, int cell) {
        Set<Long> doneChunks = capture.downloadedChunksView();
        Set<Long> partialChunks = capture.partialChunksView();
        Set<Long> queuedChunks = capture.queuedChunksView();
        int center = size / 2;
        int playerChunkX = minecraft.player != null ? minecraft.player.blockPosition().getX() >> 4 : 0;
        int playerChunkZ = minecraft.player != null ? minecraft.player.blockPosition().getZ() >> 4 : 0;
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("§a" + Lang.string("worldbinder.common.saved") + " §e" + Lang.string("worldbinder.common.scanning") + " §6" + Lang.string("worldbinder.common.queued") + " §d" + Lang.string("worldbinder.map.player")), x, y, WbTheme.TEXT_MUTED);
        int gridY = y + 18;
        for (int dz = -center; dz <= center; dz++) {
            for (int dx = -center; dx <= center; dx++) {
                long key = ChunkPos.pack(playerChunkX + dx, playerChunkZ + dz);
                boolean done = doneChunks.contains(key);
                boolean partial = partialChunks.contains(key);
                boolean queued = queuedChunks.contains(key);
                int color = done ? 0xDD55FFAA : (partial ? 0xDDD9A441 : (queued ? 0xAAFFD166 : 0x55333344));
                if (dx == 0 && dz == 0) {
                    color = 0xFFFF55FF;
                }
                int px = x + (dx + center) * cell;
                int py = gridY + (dz + center) * cell;
                context.fill(px, py, px + cell - 1, py + cell - 1, color);
            }
        }
    }

protected void drawLine(GuiGraphicsExtractor context, int x, int y, String label, String value, int color) {
        int max = Math.max(60, contentX(WbLayout.left(width, panelWidth())) + contentWidth(panelWidth()) - x - 16);
        WbText.drawClipped(context, font, "§7" + label + ": §f" + value, x, y, max, color);
    }

    protected void drawProgress(GuiGraphicsExtractor context, int x, int y, int width, double progress, String detail) {
        WbProgressBar.draw(context, font, x, y, width, progress, detail);
    }

    protected String targetSummaryLine() {
        String version = WorldBinder.config().targetMinecraftVersion;
        String normalized = TargetMinecraftVersion.normalize(version);
        return Lang.string(
                "worldbinder.config.target_value",
                normalized + " • " + Lang.string(TargetMinecraftVersion.profileTranslationKey(normalized))
        );
    }

    protected static String mapLayerLabel(WorldBinderConfig.MapLayerMode mode) {
        return switch (mode == null ? WorldBinderConfig.MapLayerMode.BOTH : mode) {
            case BOTH -> Lang.string("worldbinder.config.map_mode.both");
            case CHUNKS_ONLY -> Lang.string("worldbinder.config.map_mode.chunks");
            case MAP_ONLY -> Lang.string("worldbinder.config.map_mode.map");
        };
    }

    protected static String radarDetailLabel(WorldBinderConfig.RadarDetailMode mode) {
        return switch (mode == null ? WorldBinderConfig.RadarDetailMode.AUTO : mode) {
            case AUTO -> Lang.string("worldbinder.config.radar.auto");
            case LOW -> Lang.string("worldbinder.config.radar.low");
            case MEDIUM -> Lang.string("worldbinder.config.radar.medium");
            case HIGH -> Lang.string("worldbinder.config.radar.high");
        };
    }

    protected String presetLine() {
        String preset = WorldBinder.config().performancePreset == null ? "CUSTOM" : WorldBinder.config().performancePreset.name();
        return preset + " • " + WorldBinder.config().effectiveBlocksPerTick() + " " + Lang.string("worldbinder.config.blocks_per_tick");
    }

    protected String lastSessionLine() {
        List<Path> scenes = library.scenes();
        return scenes.isEmpty() ? Lang.string("worldbinder.archive.none_yet") : shorten(scenes.get(0).getFileName().toString(), 32);
    }

    protected static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

}

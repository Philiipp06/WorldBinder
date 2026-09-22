package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.placement.ScenePlacementService;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.util.Chat;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WorldBinderScreen extends WorldBinderScreenView {

    public WorldBinderScreen(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library) {
        this(selections, capture, placement, library, null);
    }

    public WorldBinderScreen(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library, Screen parent) {
        super(selections, capture, placement, library, parent);
    }

    public void openAbout() {
        section = Section.ABOUT;
        contentScroll = 0;
        sectionDropdownOpen = false;
        presetDropdownOpen = false;
        targetVersionDropdownOpen = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
        if (archiveName != null) {
            pendingArchiveName = archiveName.getValue();
        }
        clearWidgets();
        archiveName = null;
        if (section != Section.CAPTURE) {
            presetDropdownOpen = false;
            targetVersionDropdownOpen = false;
        }
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);
        int maxScroll = maxContentScroll();
        if (contentScroll > maxScroll) {
            contentScroll = maxScroll;
        }
        int contentX = contentX(left);
        int contentW = contentWidth(panelWidth);
        int contentTop = top - contentScroll;

        addSidebar(left, top);
        if (section == Section.CAPTURE) {
            boolean narrowCapture = contentW < 620;
            int archiveW = Math.max(120, contentW - 36);
            int captureCardY = contentTop + 88;
            int archiveY = captureCardY + 54;
            archiveName = new EditBox(font, contentX + 23, archiveY + 5, Math.max(110, archiveW - 10), 12, Lang.text("worldbinder.gui.archive_name"));
            archiveName.setBordered(false);
            archiveName.setMaxLength(64);
            WbTooltips.register(archiveName, Component.translatable("worldbinder.tooltip.archive_name"));
            archiveName.setValue(pendingArchiveName == null ? WorldBinder.config().defaultArchiveName : pendingArchiveName);
            clipContentWidget(archiveName);
            addRenderableWidget(archiveName);

            int targetY = captureCardY + 116;
            int targetX = contentX + 18;
            int targetW = captureTargetDropdownWidth(contentW);
            Button targetToggle = WbButton.dropdown(button(targetX, targetY, targetW, 22,
                    Lang.string("worldbinder.config.target_dropdown_value", WorldBinder.config().targetMinecraftVersion),
                    Component.translatable("worldbinder.tooltip.target_version"), b -> {
                        targetVersionDropdownOpen = !targetVersionDropdownOpen;
                        presetDropdownOpen = false;
                        sectionDropdownOpen = false;
                        rebuildWidgets();
                    }), WbTheme.ACCENT_RIGHT, targetVersionDropdownOpen);
            clipContentWidget(targetToggle);
            addRenderableWidget(targetToggle);
            if (targetVersionDropdownOpen) {
                addCaptureTargetVersionOptions(targetX, targetY + 28, targetW);
            }
        }

        if (section == Section.OVERVIEW) {
            overviewWidgets(contentX, contentTop, contentW);
        } else if (section == Section.CAPTURE) {
            captureWidgets(contentX, contentTop, contentW);
        } else if (section == Section.MAP) {
            mapWidgets(contentX, contentTop, contentW);
        } else if (section == Section.ARCHIVES) {
            archiveWidgets(contentX, top, false);
        } else if (section == Section.RECOVERY) {
            archiveWidgets(contentX, top, true);
        } else if (section == Section.SETTINGS) {
            settingsWidgets(contentX, contentTop);
        } else if (section == Section.TOOLS) {
            toolsWidgets(contentX, contentTop, contentW);
        }

        addRenderableWidget(WbButton.quiet(WbButton.create(left + panelWidth - 116, top + panelHeight - 32, 96, 22,
                Lang.string("worldbinder.gui.close"), Component.translatable("worldbinder.tooltip.close"), b -> onClose())));
        addSectionDropdown(left, top, panelWidth);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }
    private void addSidebar(int left, int top) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int sidebarWidth = WbLayout.sidebarWidth(panelWidth);
        if (sidebarWidth <= 0) {
            return;
        }
        int buttonX = left + 12;
        int buttonW = Math.max(68, sidebarWidth - 24);
        int buttonH = WbLayout.sidebarButtonHeight(panelHeight);
        int y = top + (panelHeight < 390 ? 58 : 76);
        int step = WbLayout.sidebarStep(panelHeight);
        boolean compact = panelWidth < 430;
        for (Section target : Section.values()) {
            String label = compact || (panelWidth < 520 && target == Section.ARCHIVES)
                    ? compactSectionLabel(target)
                    : target.title();
            Button widget = WbButton.tab(WbButton.create(buttonX, y, buttonW, buttonH, label, target.subtitle(), button -> {
                section = target;
                contentScroll = 0;
                sectionDropdownOpen = false;
                presetDropdownOpen = false;
                targetVersionDropdownOpen = false;
                pendingDeletePath = null;
                pendingDeleteMillis = 0L;
                rebuildWidgets();
            }), sectionAccent(target), section == target);
            addRenderableWidget(widget);
            y += step;
        }
    }

    private void addSectionDropdown(int left, int top, int panelWidth) {
        if (panelWidth >= 620) {
            sectionDropdownOpen = false;
            return;
        }
        int contentX = contentX(left);
        int contentW = contentWidth(panelWidth);
        int dropdownW = Math.min(178, Math.max(132, contentW / 3));
        int x = contentX + contentW - dropdownW;
        int y = top + 22;
        addRenderableWidget(WbButton.dropdown(WbButton.create(x, y, dropdownW, 22, Lang.string("worldbinder.gui.menu_value", section.title()),
                Component.translatable("worldbinder.tooltip.section_dropdown"), button -> {
                    sectionDropdownOpen = !sectionDropdownOpen;
                    presetDropdownOpen = false;
                    targetVersionDropdownOpen = false;
                    rebuildWidgets();
                }), sectionAccent(section), sectionDropdownOpen));
        if (!sectionDropdownOpen) {
            return;
        }
        int optionY = y + 29;
        for (Section target : Section.values()) {
            Button option = WbButton.create(x, optionY, dropdownW, 20, target.title(),
                    target.subtitle(), button -> {
                        section = target;
                        contentScroll = 0;
                        sectionDropdownOpen = false;
                        presetDropdownOpen = false;
                        targetVersionDropdownOpen = false;
                        pendingDeletePath = null;
                        pendingDeleteMillis = 0L;
                        rebuildWidgets();
                    });
            addRenderableWidget(WbButton.tab(option, sectionAccent(target), section == target));
            optionY += 20;
        }
    }

    private String compactSectionLabel(Section target) {
        return target.shortTitle();
    }

    private void addCaptureTargetVersionOptions(int x, int y, int width) {
        int cols = captureTargetVersionColumns(width);
        int gap = 5;
        int optionW = Math.max(34, (width - gap * (cols - 1)) / cols);
        String current = TargetMinecraftVersion.normalize(WorldBinder.config().targetMinecraftVersion);
        for (int i = 0; i < TargetMinecraftVersion.FINAL_RELEASES.size(); i++) {
            TargetMinecraftVersion.Entry entry = TargetMinecraftVersion.FINAL_RELEASES.get(i);
            boolean selected = entry.name().equals(current);
            int col = i % cols;
            int row = i / cols;
            Button option = WbButton.tab(button(x + col * (optionW + gap), y + row * 22, optionW, 19,
                    entry.name(),
                    Component.translatable("worldbinder.tooltip.target_version"), b -> {
                        WorldBinder.config().targetMinecraftVersion = entry.name();
                        WorldBinder.config().save();
                        targetVersionDropdownOpen = false;
                        rebuildWidgets();
                    }), WbTheme.ACCENT_RIGHT, selected);
            clipContentWidget(option);
            addRenderableWidget(option);
        }
    }

    @Override
    protected int captureTargetDropdownWidth(int contentW) {
        return Math.max(44, Math.min(contentW - 36, 520));
    }

    @Override
    protected int captureTargetVersionColumns(int width) {
        if (width < 150) {
            return 1;
        }
        if (width < 270) {
            return 2;
        }
        if (width < 400) {
            return 3;
        }
        if (width < 520) {
            return 4;
        }
        return 5;
    }

    private int captureTargetDropdownHeight(int contentW) {
        int cols = captureTargetVersionColumns(captureTargetDropdownWidth(contentW));
        int rows = (TargetMinecraftVersion.FINAL_RELEASES.size() + cols - 1) / cols;
        return rows * 22 + 6;
    }

    @Override
    protected int captureTargetDropdownOffset(int contentW) {
        return targetVersionDropdownOpen ? captureTargetDropdownHeight(contentW) + 10 : 0;
    }

    private void overviewWidgets(int x, int top, int width) {
        boolean tiny = WbLayout.tiny(panelWidth(), panelHeight());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int gap = tiny ? 5 : 8;
        int rowH = tiny ? 20 : 23;
        int cols = width < 430 ? 2 : 3;
        int buttonW = Math.max(54, (width - 36 - gap * (cols - 1)) / cols);
        int rows = library.recoveryCount() > 0 && cols == 3 ? 2 : 2;
        int y = bottom - rows * rowH - (rows - 1) * 6 - 4;

        addRenderableWidget(worldButton(x + 18, y, buttonW, rowH,
                capture.isRoamingCapture() ? Lang.string("worldbinder.gui.finish_export") : Lang.string("worldbinder.gui.start_capture"),
                Component.translatable("worldbinder.tooltip.start_download"),
                b -> startRoamingCaptureWithLegalReminder()));
        addRenderableWidget(worldButton(x + 18 + (buttonW + gap), y, buttonW, rowH,
                capture.isCapturing() && capture.isPaused() ? Lang.string("worldbinder.gui.resume_capture") : Lang.string("worldbinder.gui.pause_capture"),
                Component.translatable("worldbinder.tooltip.pause_capture"),
                b -> { capture.togglePause(); rebuildWidgets(); }));

        if (cols == 3) {
            addRenderableWidget(worldButton(x + 18 + (buttonW + gap) * 2, y, buttonW, rowH,
                    Lang.string("worldbinder.gui.open_f10_map"), Component.translatable("worldbinder.tooltip.open_map"),
                    b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
            y += rowH + 6;
            addRenderableWidget(worldButton(x + 18, y, buttonW, rowH,
                    Lang.string("worldbinder.gui.place_latest"), Component.translatable("worldbinder.tooltip.place_latest"),
                    b -> placement.placeLatestAtPlayer()));
            addRenderableWidget(button(x + 18 + (buttonW + gap), y, buttonW, rowH,
                    Lang.string("worldbinder.gui.open_archives"), Component.translatable("worldbinder.archives.tooltip"),
                    b -> { section = Section.ARCHIVES; rebuildWidgets(); }));
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 2, y, buttonW, rowH,
                    library.recoveryCount() > 0 ? Lang.string("worldbinder.section.recovery") : Lang.string("worldbinder.section.tools"),
                    library.recoveryCount() > 0 ? Component.translatable("worldbinder.recovery.sessions.tooltip") : Component.translatable("worldbinder.section.tools.tooltip"),
                    b -> { section = library.recoveryCount() > 0 ? Section.RECOVERY : Section.TOOLS; rebuildWidgets(); }));
        } else {
            addRenderableWidget(worldButton(x + 18, y + rowH + 6, buttonW, rowH,
                    Lang.string("worldbinder.gui.f10_map"), Component.translatable("worldbinder.tooltip.open_map"),
                    b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
            addRenderableWidget(button(x + 18 + (buttonW + gap), y + rowH + 6, buttonW, rowH,
                    Lang.string("worldbinder.section.archives.short"), Component.translatable("worldbinder.archives.tooltip"),
                    b -> { section = Section.ARCHIVES; rebuildWidgets(); }));
        }
    }
private void captureWidgets(int x, int top, int width) {
        boolean narrow = width < 620;
        int captureCardY = top + 88;
        int targetOffset = captureTargetDropdownOffset(width);
        int actionY = captureCardY + 154 + targetOffset;
        int actionW = Math.max(92, Math.min(210, (width - 48) / 2));
        Button start = worldButton(x + 18, actionY, actionW, 23,
                capture.isRoamingCapture() ? Lang.string("worldbinder.gui.finish") : Lang.string("worldbinder.gui.start"),
                Component.translatable("worldbinder.tooltip.start_download"),
                b -> startRoamingCaptureWithLegalReminder());
        Button pause = worldButton(x + 28 + actionW, actionY, actionW, 23,
                capture.isPaused() ? Lang.string("worldbinder.gui.resume") : Lang.string("worldbinder.gui.pause"),
                Component.translatable("worldbinder.tooltip.pause_capture"),
                b -> { capture.togglePause(); rebuildWidgets(); });
        clipContentWidget(start);
        clipContentWidget(pause);
        addRenderableWidget(start);
        addRenderableWidget(pause);

        int presetY = captureCardY + (narrow ? 244 : 228) + targetOffset;
        int gap = 8;
        int presetButtonY = presetY + 86;
        String[] labelKeys = {
                "worldbinder.gui.preset_safe",
                "worldbinder.gui.preset_balanced",
                "worldbinder.gui.preset_fast",
                "worldbinder.gui.preset_extreme"
        };
        String[] tooltipKeys = {
                "worldbinder.tooltip.preset_safe",
                "worldbinder.tooltip.preset_balanced",
                "worldbinder.tooltip.preset_fast",
                "worldbinder.tooltip.preset_extreme"
        };
        net.worldbinder.config.WorldBinderConfig.PerformancePreset[] presets = {
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.SAFE,
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.BALANCED,
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.FAST,
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME
        };
        int presetMenuW = Math.min(230, Math.max(146, width - 36));
        Button presetToggle = WbButton.dropdown(button(x + 18, presetButtonY, presetMenuW, 22,
                Lang.string("worldbinder.capture.preset_dropdown_value", capturePresetLabel()), Component.translatable("worldbinder.tooltip.presets_card"), b -> {
                    presetDropdownOpen = !presetDropdownOpen;
                    sectionDropdownOpen = false;
                    targetVersionDropdownOpen = false;
                    rebuildWidgets();
                }), WbTheme.WARN, presetDropdownOpen);
        clipContentWidget(presetToggle);
        addRenderableWidget(presetToggle);
        if (presetDropdownOpen) {
            for (int i = 0; i < labelKeys.length; i++) {
                final int idx = i;
                boolean selected = WorldBinder.config().performancePreset == presets[i];
                Button preset = WbButton.tab(button(x + 18, presetButtonY + 29 + i * 20, presetMenuW, 20,
                        Lang.string(labelKeys[i]),
                        Component.translatable(tooltipKeys[i]),
                        b -> {
                            WorldBinder.config().setPreset(presets[idx]);
                            presetDropdownOpen = false;
                            rebuildWidgets();
                        }), WbTheme.WARN, selected);
                clipContentWidget(preset);
                addRenderableWidget(preset);
            }
        }
        int actionRowY = presetButtonY + (presetDropdownOpen ? 120 : 38);
        int actionCols = width < 560 ? 1 : 3;
        int capW = actionCols == 1 ? Math.min(width - 36, 240) : Math.max(96, Math.min(210, (width - 62) / 3));
        Button pos = worldButton(x + 18, actionRowY, capW, 22, Lang.string("worldbinder.gui.capture_position"), Component.translatable("worldbinder.tooltip.capture_position"),
                b -> captureWorldArchiveWithLegalReminder());
        Button scene = worldButton(actionCols == 1 ? x + 18 : x + 28 + capW, actionCols == 1 ? actionRowY + 28 : actionRowY, capW, 22, Lang.string("worldbinder.gui.capture_scene"), Component.translatable("worldbinder.tooltip.capture_scene"),
                b -> captureSceneWithLegalReminder());
        Button settings = button(actionCols == 1 ? x + 18 : x + 38 + capW * 2, actionCols == 1 ? actionRowY + 56 : actionRowY, capW, 22,
                Lang.string("worldbinder.gui.custom_settings"), Component.translatable("worldbinder.tooltip.custom_settings"),
                b -> minecraft.gui.setScreen(WorldBinderConfigScreen.performance(this)));
        clipContentWidget(pos);
        clipContentWidget(scene);
        clipContentWidget(settings);
        addRenderableWidget(pos);
        addRenderableWidget(scene);
        addRenderableWidget(settings);
    }

private void mapWidgets(int x, int top, int width) {
        int y = Math.min(top + 328, top + panelHeight() - 70);
        int buttonW = Math.max(104, Math.min(150, (width - 44) / 2));
        addRenderableWidget(worldButton(x + 18, y, buttonW, 25, Lang.string("worldbinder.gui.open_f10_map"), Component.translatable("worldbinder.tooltip.open_map"),
                b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
        addRenderableWidget(worldButton(x + 30 + buttonW, y, buttonW, 25, Lang.string("worldbinder.gui.profiler"), Component.translatable("worldbinder.tooltip.profiler"),
                b -> minecraft.gui.setScreen(new WorldBinderProfilerScreen(this))));
    }
private void archiveWidgets(int x, int top, boolean recoveryOnly) {
        normalizeArchiveSelection(recoveryOnly);
        int width = contentWidth(panelWidth());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int gap = 6;
        int cols = width < 420 ? 2 : width < 720 ? 3 : 5;
        int rows = cols == 5 ? 2 : 3;
        int rowH = 21;
        int y = bottom - rows * rowH - (rows - 1) * 6 - 4;
        int buttonW = Math.max(58, (width - 36 - gap * (cols - 1)) / cols);

        Button[] buttons = new Button[10];
        String[] labelKeys = {
                "worldbinder.gui.place",
                "worldbinder.gui.folder",
                "worldbinder.gui.continue",
                "worldbinder.gui.finalize",
                "worldbinder.gui.preview",
                "worldbinder.gui.duplicate",
                "worldbinder.gui.validate",
                "worldbinder.gui.refresh",
                "worldbinder.gui.delete"
        };
        Button.OnPress[] actions = new Button.OnPress[] {
                b -> placeSelectedArchive(),
                b -> openSelectedArchive(),
                b -> continueSelectedRecovery(),
                b -> finalizeSelectedRecovery(),
                b -> exportSelectedPreview(),
                b -> saveSelectedAsArchive(),
                b -> validateSelectedArchive(),
                b -> { library.refresh(); selectedArchiveIndex = Math.min(selectedArchiveIndex, Math.max(0, library.scenes().size() - 1)); rebuildWidgets(); },
                b -> deleteSelectedArchive()
        };
        Component[] tooltips = new Component[] {
                Component.translatable("worldbinder.tooltip.place_latest"),
                Component.translatable("worldbinder.tooltip.open_saves_folder"),
                Component.translatable("worldbinder.tooltip.continue_recovery"),
                Component.translatable("worldbinder.tooltip.finalize_recovery"),
                Component.translatable("worldbinder.tooltip.export_preview"),
                Component.translatable("worldbinder.tooltip.save_as_archive"),
                Component.translatable("worldbinder.tooltip.validate_selected"),
                Component.translatable("worldbinder.tooltip.refresh"),
                Component.translatable("worldbinder.tooltip.delete_archive")
        };
        for (int i = 0; i < labelKeys.length; i++) {
            int row = i / cols;
            int col = i % cols;
            boolean requiresWorld = i == 0 || i == 2;
            Component tooltip = requiresWorld && !worldAvailable()
                    ? Component.translatable("worldbinder.tooltip.world_required")
                    : tooltips[i];
            Button created = addRenderableWidget(button(x + 18 + col * (buttonW + gap), y + row * (rowH + 6), buttonW, rowH, Lang.string(labelKeys[i]), tooltip, actions[i]));
            buttons[i] = created;
        }
        placeSelectedButton = buttons[0];
        openSelectedButton = buttons[1];
        continueRecoveryButton = buttons[2];
        finalizeRecoveryButton = buttons[3];
        exportPreviewButton = buttons[4];
        saveAsArchiveButton = buttons[5];
        validateSelectedButton = buttons[6];
        deleteSelectedButton = buttons[8];
        updateArchiveButtonState();
    }

    private void validationWidgets(int x, int top) {
        exportPreviewButton = addRenderableWidget(button(x + 18, top + 316, 130, 23, Lang.string("worldbinder.gui.export_preview"), Component.translatable("worldbinder.tooltip.export_preview"), b -> exportSelectedPreview()));
        validateSelectedButton = addRenderableWidget(button(x + 160, top + 316, 130, 23, Lang.string("worldbinder.gui.validate_selected"), Component.translatable("worldbinder.tooltip.validate_selected"), b -> validateSelectedArchive()));
        openSelectedButton = addRenderableWidget(button(x + 302, top + 316, 130, 23, Lang.string("worldbinder.gui.open_folder"), Component.translatable("worldbinder.tooltip.open_saves_folder"), b -> openSelectedArchive()));
        addRenderableWidget(button(x + 444, top + 316, 90, 23, Lang.string("worldbinder.gui.refresh"), Component.translatable("worldbinder.tooltip.refresh"), b -> { library.refresh(); rebuildWidgets(); }));
        updateArchiveButtonState();
    }
private void settingsWidgets(int x, int top) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int w = Math.min(170, Math.max(110, contentWidth(panelWidth()) - 36));
        addRenderableWidget(button(x + 18, bottom - 28, w, 23, Lang.string("worldbinder.gui.open_full_settings"), Component.translatable("worldbinder.tooltip.settings"),
                b -> minecraft.gui.setScreen(new WorldBinderConfigScreen(this))));
    }
private void toolsWidgets(int x, int top, int width) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int gap = 6;
        int cols = width < 460 ? 2 : 4;
        int buttonW = Math.max(70, (width - 36 - gap * (cols - 1)) / cols);
        int y = bottom - (cols == 2 ? 52 : 26);
        addRenderableWidget(worldButton(x + 18, y, buttonW, 23, Lang.string("worldbinder.section.map"), Component.translatable("worldbinder.tooltip.open_map"), b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
        addRenderableWidget(worldButton(x + 18 + (buttonW + gap), y, buttonW, 23, Lang.string("worldbinder.gui.profiler"), Component.translatable("worldbinder.tooltip.profiler"), b -> minecraft.gui.setScreen(new WorldBinderProfilerScreen(this))));
        if (cols == 4) {
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 2, y, buttonW, 23, Lang.string("worldbinder.gui.open_saves"), Component.translatable("worldbinder.tooltip.open_saves_folder"),
                    b -> net.worldbinder.util.PathOpener.open(net.worldbinder.io.WorldBinderPaths.WORLDS)));
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 3, y, buttonW, 23, Lang.string("worldbinder.gui.cancel"), Component.translatable("worldbinder.tooltip.cancel_capture"),
                    b -> { capture.cancelActiveCapture(); rebuildWidgets(); }));
        } else {
            addRenderableWidget(button(x + 18, y + 29, buttonW, 23, Lang.string("worldbinder.gui.open_saves"), Component.translatable("worldbinder.tooltip.open_saves_folder"),
                    b -> net.worldbinder.util.PathOpener.open(net.worldbinder.io.WorldBinderPaths.WORLDS)));
            addRenderableWidget(button(x + 18 + (buttonW + gap), y + 29, buttonW, 23, Lang.string("worldbinder.gui.cancel"), Component.translatable("worldbinder.tooltip.cancel_capture"),
                    b -> { capture.cancelActiveCapture(); rebuildWidgets(); }));
        }
    }

    private String capturePresetLabel() {
        return switch (WorldBinder.config().performancePreset == null ? net.worldbinder.config.WorldBinderConfig.PerformancePreset.CUSTOM : WorldBinder.config().performancePreset) {
            case SAFE -> Lang.string("worldbinder.gui.preset_safe");
            case BALANCED -> Lang.string("worldbinder.gui.preset_balanced");
            case FAST -> Lang.string("worldbinder.gui.preset_fast");
            case EXTREME -> Lang.string("worldbinder.gui.preset_extreme");
            case CUSTOM -> Lang.string("worldbinder.gui.custom_mode");
        };
    }

    private void startRoamingCaptureWithLegalReminder() {
        if (capture.isRoamingCapture()) {
            capture.finishActiveCapture();
            return;
        }
        applyCaptureInputs();
        runWithCapturePrompt(name -> {
            capture.toggleRoamingCapture(name);
            if (archiveName != null) archiveName.setValue(name);
            rebuildWidgets();
        }, Lang.string("worldbinder.gui.start_saving"));
    }

    private void captureWorldArchiveWithLegalReminder() {
        applyCaptureInputs();
        runWithCapturePrompt(name -> {
            capture.captureWorldArchive(name);
            if (archiveName != null) archiveName.setValue(name);
            rebuildWidgets();
        }, Lang.string("worldbinder.gui.start_saving"));
    }

    private void captureSceneWithLegalReminder() {
        applyCaptureInputs();
        runWithCapturePrompt(name -> {
            capture.captureScene(name);
            if (archiveName != null) archiveName.setValue(name);
            rebuildWidgets();
        }, Lang.string("worldbinder.gui.start_saving"));
    }

    private void runWithCapturePrompt(java.util.function.Consumer<String> action, String label) {
        minecraft.gui.setScreen(new WorldBinderLegalStartScreen(this, action, label, currentArchiveName()));
    }

    private Button button(int x, int y, int width, int height, String label, Component tooltip, Button.OnPress action) {
        return clipContentWidget(WbButton.create(x, y, width, height, label, tooltip, action));
    }

    private Button worldButton(int x, int y, int width, int height, String label, Component tooltip, Button.OnPress action) {
        boolean available = worldAvailable();
        Button button = button(
                x,
                y,
                width,
                height,
                label,
                available ? tooltip : Component.translatable("worldbinder.tooltip.world_required"),
                action
        );
        button.active = button.active && available;
        return button;
    }

    @Override
    protected boolean worldAvailable() {
        return minecraft != null && minecraft.player != null && minecraft.level != null;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.gui.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private void updateArchiveButtonState() {
        Path selected = selectedArchive();
        boolean hasSelection = selected != null;
        boolean recovery = library.isRecovery(selected);
        if (placeSelectedButton != null) placeSelectedButton.active = hasSelection && !recovery && worldAvailable();
        if (openSelectedButton != null) openSelectedButton.active = hasSelection;
        if (continueRecoveryButton != null) continueRecoveryButton.active = hasSelection && library.canFinalizeRecovery(selected) && !capture.isCapturing() && worldAvailable();
        if (finalizeRecoveryButton != null) finalizeRecoveryButton.active = hasSelection && library.canFinalizeRecovery(selected);
        if (saveAsArchiveButton != null) saveAsArchiveButton.active = hasSelection;
        if (exportPreviewButton != null) exportPreviewButton.active = hasSelection;
        if (validateSelectedButton != null) validateSelectedButton.active = hasSelection;
        if (deleteSelectedButton != null) {
            deleteSelectedButton.active = hasSelection;
            boolean armed = hasSelection && selected.equals(pendingDeletePath) && System.currentTimeMillis() - pendingDeleteMillis < 5000L;
            deleteSelectedButton.setMessage(Component.literal(armed ? Lang.string("worldbinder.gui.confirm_delete") : Lang.string("worldbinder.gui.delete")));
        }
    }
    @Override
    protected Path selectedArchive() {
        List<Path> scenes = library.scenes();
        if (scenes.isEmpty()) {
            return null;
        }
        selectedArchiveIndex = Math.max(0, Math.min(selectedArchiveIndex, scenes.size() - 1));
        return scenes.get(selectedArchiveIndex);
    }

    @Override
    protected Path selectedArchiveForRows(List<Path> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Path selected = selectedArchive();
        if (selected != null && rows.contains(selected)) {
            return selected;
        }
        Path fallback = rows.get(Math.min(Math.max(0, selectedArchiveIndex), rows.size() - 1));
        int index = library.scenes().indexOf(fallback);
        if (index >= 0) {
            selectedArchiveIndex = index;
        }
        return fallback;
    }

    private void normalizeArchiveSelection(boolean recoveryOnly) {
        selectedArchiveForRows(archiveRows(recoveryOnly));
    }

    private void placeSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_saved_archive"));
            return;
        }
        placement.placeAtPlayer(selected);
    }

    private void openSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_archive_selected"));
            return;
        }
        net.worldbinder.util.PathOpener.open(java.nio.file.Files.isDirectory(selected) ? selected : selected.getParent());
    }

    private void finalizeSelectedRecovery() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_recovery_selected"));
            return;
        }
        if (!library.canFinalizeRecovery(selected)) {
            Chat.warn(Lang.string("worldbinder.chat.recovery_not_completed"));
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin(Lang.string("worldbinder.status.recovery_title"), Lang.string("worldbinder.operation.finalizing_recovery"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.finalizeRecovery(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((finalized, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish(Lang.string("worldbinder.status.recovery_finalized"));
                Chat.info(Lang.string("worldbinder.chat.recovery_finalized", finalized.getFileName()));
                selectedArchiveIndex = 0;
                rebuildWidgets();
            } else {
                OperationStatus.finish(Lang.string("worldbinder.status.recovery_finalize_failed"));
                Chat.error(Lang.string("worldbinder.chat.recovery_finalize_failed"));
                WorldBinder.LOGGER.warn("Failed to finalize recovery", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void continueSelectedRecovery() {
        Path selected = selectedArchive();
        if (selected == null || !library.isRecovery(selected)) {
            Chat.warn(Lang.string("worldbinder.chat.no_recovery_selected"));
            return;
        }
        capture.continueRecovery(selected);
        rebuildWidgets();
    }

    private void saveSelectedAsArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_archive_selected"));
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin(Lang.string("worldbinder.status.storage_title"), Lang.string("worldbinder.operation.saving_archive_copy"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.saveAsArchive(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((saved, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish(Lang.string("worldbinder.status.archive_copy_saved"));
                Chat.info(Lang.string("worldbinder.chat.archive_copy_saved", saved.getFileName()));
                selectedArchiveIndex = 0;
                rebuildWidgets();
            } else {
                OperationStatus.finish(Lang.string("worldbinder.status.archive_copy_failed"));
                Chat.error(Lang.string("worldbinder.chat.archive_copy_failed"));
                WorldBinder.LOGGER.warn("Failed to save archive copy", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void validateSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_archive_selected"));
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin(Lang.string("worldbinder.status.title"), Lang.string("worldbinder.operation.validating_archive"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.validateArchive(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((report, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish(Lang.string("worldbinder.status.validation_complete"));
                Chat.info(Lang.string("worldbinder.chat.validation_complete", net.worldbinder.validation.ExportValidator.shortLine(report)));
                rebuildWidgets();
            } else {
                OperationStatus.finish(Lang.string("worldbinder.status.validation_failed"));
                Chat.error(Lang.string("worldbinder.chat.validation_failed"));
                WorldBinder.LOGGER.warn("Failed to validate archive", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void deleteSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_archive_selected"));
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (!selected.equals(pendingDeletePath) || now - pendingDeleteMillis >= 5000L) {
                pendingDeletePath = selected;
                pendingDeleteMillis = now;
                Chat.warn(Lang.string("worldbinder.chat.delete_confirm", selected.getFileName()));
                updateArchiveButtonState();
                return;
            }
            setArchiveActionsActive(false);
            OperationStatus.begin(Lang.string("worldbinder.status.storage_title"), Lang.string("worldbinder.operation.deleting_archive"));
            CompletableFuture.runAsync(() -> {
                try {
                    library.deleteArchive(selected);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).whenComplete((ignored, throwable) -> runOnClient(() -> {
                pendingDeletePath = null;
                pendingDeleteMillis = 0L;
                if (throwable == null) {
                    OperationStatus.finish(Lang.string("worldbinder.status.archive_deleted"));
                    Chat.info(Lang.string("worldbinder.chat.archive_deleted", selected.getFileName()));
                    selectedArchiveIndex = 0;
                    archiveListOffset = 0;
                    rebuildWidgets();
                } else {
                    OperationStatus.finish(Lang.string("worldbinder.status.archive_delete_failed"));
                    Chat.error(Lang.string("worldbinder.chat.delete_failed"));
                    WorldBinder.LOGGER.warn("Failed to delete archive", throwable);
                    setArchiveActionsActive(true);
                }
            }));
        } catch (Exception exception) {
            Chat.error(Lang.string("worldbinder.chat.delete_schedule_failed"));
            WorldBinder.LOGGER.warn("Failed to schedule archive delete", exception);
        }
    }

    private void exportSelectedPreview() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn(Lang.string("worldbinder.chat.no_archive_preview"));
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin(Lang.string("worldbinder.status.title"), Lang.string("worldbinder.operation.generating_preview"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.exportPreviewThumbnail(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((preview, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish(Lang.string("worldbinder.status.preview_exported"));
                Chat.info(Lang.string("worldbinder.chat.preview_exported", preview.toAbsolutePath()));
                net.worldbinder.util.PathOpener.open(preview.getParent());
                rebuildWidgets();
            } else {
                OperationStatus.finish(Lang.string("worldbinder.status.preview_failed"));
                Chat.error(Lang.string("worldbinder.chat.preview_failed"));
                WorldBinder.LOGGER.warn("Failed to export archive preview", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void setArchiveActionsActive(boolean active) {
        if (placeSelectedButton != null) placeSelectedButton.active = active;
        if (openSelectedButton != null) openSelectedButton.active = active;
        if (continueRecoveryButton != null) continueRecoveryButton.active = active;
        if (finalizeRecoveryButton != null) finalizeRecoveryButton.active = active;
        if (saveAsArchiveButton != null) saveAsArchiveButton.active = active;
        if (exportPreviewButton != null) exportPreviewButton.active = active;
        if (validateSelectedButton != null) validateSelectedButton.active = active;
        if (deleteSelectedButton != null) deleteSelectedButton.active = active;
        if (active) {
            updateArchiveButtonState();
        }
    }

    private void runOnClient(Runnable runnable) {
        minecraft.execute(runnable);
    }

    private void applyCaptureInputs() {
        String value = archiveName == null ? pendingArchiveName : archiveName.getValue();
        if (value != null && !value.isBlank()) {
            pendingArchiveName = value.replaceAll("[^a-zA-Z0-9_.-]", "_");
            WorldBinder.config().defaultArchiveName = pendingArchiveName;
        }
        WorldBinder.config().save();
    }

    @Override
    protected String currentArchiveName() {
        String value = archiveName == null ? pendingArchiveName : archiveName.getValue();
        if (value != null && !value.isBlank()) {
            return value;
        }
        return WorldBinder.config().defaultArchiveName;
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
        if (section == Section.SETTINGS) {
            int panelWidth = panelWidth();
            int left = WbLayout.left(width, panelWidth);
            int top = WbLayout.top(height, panelHeight()) - contentScroll;
            int x = (int) virtualEvent.x();
            int y = (int) virtualEvent.y();
            int cardIndex = settingsCardIndex(x, y, contentX(left), top, contentWidth(panelWidth));
            if (cardIndex >= 0) {
                minecraft.gui.setScreen(settingsScreenForCard(cardIndex));
                return true;
            }
        }
        if (section == Section.ARCHIVES || section == Section.RECOVERY) {
            int panelWidth = panelWidth();
            int panelHeight = panelHeight();
            int left = WbLayout.left(width, panelWidth);
            int top = WbLayout.top(height, panelHeight);
            int contentX = contentX(left);
            int x = (int) virtualEvent.x();
            int y = (int) virtualEvent.y();
            List<Path> scenes = archiveRows();
            int count = Math.min(visibleArchiveRows(), scenes.size() - archiveListOffset);
            for (int i = 0; i < count; i++) {
                int rowY = top + 146 + i * 20;
                if (x >= contentX + 18 && x <= contentX + contentWidth(panelWidth) - 18 && y >= rowY - 4 && y <= rowY + 13) {
                    Path selectedPath = scenes.get(archiveListOffset + i);
                    selectedArchiveIndex = Math.max(0, library.scenes().indexOf(selectedPath));
                    pendingDeletePath = null;
                    pendingDeleteMillis = 0L;
                    updateArchiveButtonState();
                    return true;
                }
            }
        }
        return super.mouseClicked(virtualEvent, doubleClick);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        return super.mouseDragged(WbLayout.virtualMouseEvent(event, width, height), uiScale.toVirtualDelta(offsetX), uiScale.toVirtualDelta(offsetY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        WbLayout.UiScale uiScale = WbLayout.uiScale(width, height);
        double virtualMouseX = uiScale.toVirtualX(mouseX);
        double virtualMouseY = uiScale.toVirtualY(mouseY);
        int realWidth = width;
        int realHeight = height;
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
        if (section == Section.ARCHIVES || section == Section.RECOVERY) {
            List<Path> scenes = archiveRows();
            int maxOffset = Math.max(0, scenes.size() - visibleArchiveRows());
            if (maxOffset > 0) {
                archiveListOffset = Math.max(0, Math.min(maxOffset, archiveListOffset + (verticalAmount < 0 ? 1 : -1)));
                updateArchiveButtonState();
                return true;
            }
        }
        int maxScroll = maxContentScroll();
        if (maxScroll > 0) {
            int next = Math.max(0, Math.min(maxScroll, contentScroll + (verticalAmount < 0 ? 24 : -24)));
            if (next != contentScroll) {
                contentScroll = next;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(virtualMouseX, virtualMouseY, horizontalAmount, verticalAmount);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    private int maxContentScroll() {
        int panelTop = WbLayout.top(height, panelHeight());
        int width = contentWidth(panelWidth());
        int pageBottom = switch (section) {
            case CAPTURE -> capturePageContentBottom(panelTop, width);
            case SETTINGS -> settingsPageContentBottom(panelTop, width);
            default -> contentViewportBottom();
        };
        return Math.max(0, pageBottom - contentViewportBottom());
    }

    private int capturePageContentBottom(int top, int width) {
        boolean narrow = width < 620;
        int targetOffset = captureTargetDropdownOffset(width);
        int cardY = top + 88;
        int pageBottom = 0;

        int targetY = cardY + 116;
        if (targetVersionDropdownOpen) {
            int columns = captureTargetVersionColumns(captureTargetDropdownWidth(width));
            int rows = (TargetMinecraftVersion.FINAL_RELEASES.size() + columns - 1) / columns;
            pageBottom = Math.max(pageBottom, targetY + 28 + Math.max(0, rows - 1) * 22 + 19);
        }

        int presetY = cardY + (narrow ? 244 : 228) + targetOffset;
        int presetButtonY = presetY + 86;
        if (presetDropdownOpen) {
            pageBottom = Math.max(pageBottom, presetButtonY + 29 + 3 * 20 + 20);
        }

        int actionRowY = presetButtonY + (presetDropdownOpen ? 120 : 38);
        int actionBottom = actionRowY + (width < 560 ? 56 : 0) + 22;
        return Math.max(pageBottom, actionBottom);
    }

    private int settingsPageContentBottom(int top, int width) {
        int columns = width < 560 ? 1 : 2;
        int cardHeight = WbLayout.compact(panelWidth(), panelHeight()) ? 56 : 68;
        int rows = (8 + columns - 1) / columns;
        int startY = top + 66;
        int cardsBottom = startY + Math.max(0, rows - 1) * (cardHeight + 12) + cardHeight;
        int infoY = startY + rows * (cardHeight + 12) + 8;
        return infoY < contentViewportBottom() - 44
                ? Math.max(cardsBottom, infoY + font.lineHeight)
                : cardsBottom;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T clipContentWidget(T widget) {
        int left = WbLayout.left(width, panelWidth());
        int contentLeft = contentX(left);
        int contentRight = contentLeft + contentWidth(panelWidth());
        boolean contentWidget = widget.getX() >= contentLeft - 2 && widget.getX() <= contentRight + 2;
        if (contentWidget) {
            boolean visible = widget.getY() >= contentViewportTop() && widget.getY() + widget.getHeight() <= contentViewportBottom();
            widget.visible = visible;
            widget.active = widget.active && visible;
        }
        return widget;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        customTooltip = null;
        context.fill(0, 0, realWidth, realHeight, WbTheme.BACKDROP);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
        context.fill(0, 0, width, height, WbTheme.BACKDROP);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);

        drawShell(context, left, top, panelWidth, panelHeight);
        drawSidebar(context, left, top);
        drawSectionHeader(context, contentX(left), top + 26, contentWidth(panelWidth));
        context.enableScissor(contentX(left), contentViewportTop(), contentX(left) + contentWidth(panelWidth), contentViewportBottom());
        drawSectionContent(context, left, top - contentScroll, panelWidth, virtualMouseX, virtualMouseY);
        context.disableScissor();
        drawSectionDropdown(context, left, top, panelWidth, virtualMouseX, virtualMouseY);
        super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        if (customTooltip != null) {
            context.setTooltipForNextFrame(font, customTooltip, mouseX, mouseY);
        } else {
            WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
        }
    }

    private List<Path> archiveRows() {
        return archiveRows(section == Section.RECOVERY);
    }

    @Override
    protected List<Path> archiveRows(boolean recoveryOnly) {
        if (!recoveryOnly) {
            return library.scenes();
        }
        List<Path> result = new ArrayList<>();
        for (Path path : library.scenes()) {
            if (library.isRecovery(path)) {
                result.add(path);
            }
        }
        return result;
    }
    @Override
    protected int visibleArchiveRows() {
        int bottom = WbLayout.contentBottom(WbLayout.top(height, panelHeight()), panelHeight());
        int top = WbLayout.top(height, panelHeight());
        int listTop = top + 146;
        int listBottom = bottom - (contentWidth(panelWidth()) < 520 ? 112 : 84);
        return Math.max(1, (listBottom - listTop) / 20);
    }

    @Override
    protected void clampArchiveWindow(int size) {
        if (size <= 0) {
            archiveListOffset = 0;
            selectedArchiveIndex = 0;
            return;
        }
        int rows = visibleArchiveRows();
        int maxOffset = Math.max(0, size - rows);
        archiveListOffset = Math.max(0, Math.min(archiveListOffset, maxOffset));
    }
    private int settingsCardIndex(int mouseX, int mouseY, int x, int top, int width) {
        if (!isPointInsideContentViewport(mouseX, mouseY)) {
            return -1;
        }
        int cols = width < 560 ? 1 : 2;
        int gap = 14;
        int cardW = cols == 1 ? width : (width - gap) / 2;
        int cardH = WbLayout.compact(panelWidth(), panelHeight()) ? 56 : 68;
        int startY = top + 66;
        for (int i = 0; i < 8; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = startY + row * (cardH + 12);
            if (WbChrome.contains(cx, cy, cardW, cardH, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private Screen settingsScreenForCard(int index) {
        return switch (index) {
            case 2, 7 -> WorldBinderConfigScreen.performance(this);
            case 3 -> WorldBinderConfigScreen.hud(this);
            case 5 -> WorldBinderConfigScreen.safetyExport(this);
            case 4, 6 -> WorldBinderConfigScreen.safety(this);
            default -> WorldBinderConfigScreen.general(this);
        };
    }

}

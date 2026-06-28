package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.placement.ScenePlacementService;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.status.WorldBinderActivityLog;
import net.worldbinder.util.Chat;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbCard;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbProgressBar;
import net.worldbinder.ui.component.WbSectionHeader;
import net.worldbinder.ui.component.WbSelectableList;
import net.worldbinder.ui.component.WbSidebar;
import net.worldbinder.ui.component.WbStatusChip;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbWarningBox;
import net.worldbinder.ui.component.WbTooltips;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class WorldBinderScreen extends Screen {
    private enum Section {
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

    private final SelectionManager selections;
    private final SceneCaptureService capture;
    private final ScenePlacementService placement;
    private final SceneLibrary library;
    private EditBox archiveName;
    private Section section = Section.OVERVIEW;
    private Component customTooltip;
    private int selectedArchiveIndex = 0;
    private Button placeSelectedButton;
    private Button openSelectedButton;
    private Button finalizeRecoveryButton;
    private Button continueRecoveryButton;
    private Button saveAsArchiveButton;
    private Button exportPreviewButton;
    private Button validateSelectedButton;
    private Button deleteSelectedButton;
    private int archiveListOffset;
    private Path pendingDeletePath;
    private long pendingDeleteMillis;
    private long nextActivityRefreshMillis;
    private int contentScroll;
    private boolean sectionDropdownOpen;
    private boolean presetDropdownOpen;
    private boolean targetVersionDropdownOpen;
    private List<String> cachedActivityEntries = List.of();

    public WorldBinderScreen(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library) {
        super(Component.translatable("worldbinder.gui.title"));
        this.selections = selections;
        this.capture = capture;
        this.placement = placement;
        this.library = library;
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
        int realWidth = width;
        int realHeight = height;
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
            rebuildWidgets();
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    protected void rebuildWidgets() {
        int realWidth = width;
        int realHeight = height;
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
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
            archiveName = new EditBox(font, contentX + 18, archiveY, archiveW, 22, Lang.text("worldbinder.gui.archive_name"));
            archiveName.setMaxLength(64);
            WbTooltips.register(archiveName, Component.translatable("worldbinder.tooltip.archive_name"));
            archiveName.setValue(WorldBinder.config().defaultArchiveName);
            clipContentWidget(archiveName);
            addRenderableWidget(archiveName);

            int targetY = captureCardY + 116;
            int targetX = contentX + 18;
            int targetW = captureTargetDropdownWidth(contentW);
            Button targetToggle = button(targetX, targetY, targetW, 22,
                    Lang.string("worldbinder.config.target_dropdown_value", WorldBinder.config().targetMinecraftVersion),
                    Component.translatable("worldbinder.tooltip.target_version"), b -> {
                        targetVersionDropdownOpen = !targetVersionDropdownOpen;
                        presetDropdownOpen = false;
                        sectionDropdownOpen = false;
                        rebuildWidgets();
                    });
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

        addRenderableWidget(button(left + panelWidth - 116, top + panelHeight - 32, 96, 22,
                Lang.string("worldbinder.gui.close"), Component.translatable("worldbinder.tooltip.close"), b -> onClose()));
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
        boolean compact = panelWidth < 520;
        for (Section target : Section.values()) {
            String label = compact ? compactSectionLabel(target) : target.title();
            Button widget = WbTooltips.register(Button.builder(Component.literal((section == target ? "◆ " : "") + label), button -> {
                section = target;
                contentScroll = 0;
                sectionDropdownOpen = false;
                presetDropdownOpen = false;
                targetVersionDropdownOpen = false;
                pendingDeletePath = null;
                pendingDeleteMillis = 0L;
                rebuildWidgets();
            }).bounds(buttonX, y, buttonW, buttonH).build(), target.subtitle());
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
        addRenderableWidget(WbButton.create(x, y, dropdownW, 22, Lang.string("worldbinder.gui.menu_value", section.title()),
                Component.translatable("worldbinder.tooltip.section_dropdown"), button -> {
                    sectionDropdownOpen = !sectionDropdownOpen;
                    presetDropdownOpen = false;
                    targetVersionDropdownOpen = false;
                    rebuildWidgets();
                }));
        if (!sectionDropdownOpen) {
            return;
        }
        int optionY = y + 26;
        for (Section target : Section.values()) {
            addRenderableWidget(WbButton.create(x, optionY, dropdownW, 20, (section == target ? "> " : "") + target.title(),
                    target.subtitle(), button -> {
                        section = target;
                        contentScroll = 0;
                        sectionDropdownOpen = false;
                        presetDropdownOpen = false;
                        targetVersionDropdownOpen = false;
                        pendingDeletePath = null;
                        pendingDeleteMillis = 0L;
                        rebuildWidgets();
                    }));
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
            int col = i % cols;
            int row = i / cols;
            Button option = button(x + col * (optionW + gap), y + row * 22, optionW, 19,
                    (entry.name().equals(current) ? "◆ " : "") + entry.name(),
                    Component.translatable("worldbinder.tooltip.target_version"), b -> {
                        WorldBinder.config().targetMinecraftVersion = entry.name();
                        WorldBinder.config().save();
                        targetVersionDropdownOpen = false;
                        rebuildWidgets();
                    });
            clipContentWidget(option);
            addRenderableWidget(option);
        }
    }

    private int captureTargetDropdownWidth(int contentW) {
        return Math.max(44, Math.min(contentW - 36, 520));
    }

    private int captureTargetVersionColumns(int width) {
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

    private int captureTargetDropdownOffset(int contentW) {
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

        addRenderableWidget(button(x + 18, y, buttonW, rowH,
                capture.isRoamingCapture() ? Lang.string("worldbinder.gui.finish_export") : Lang.string("worldbinder.gui.start_capture"),
                Component.translatable("worldbinder.tooltip.start_download"),
                b -> startRoamingCaptureWithLegalReminder()));
        addRenderableWidget(button(x + 18 + (buttonW + gap), y, buttonW, rowH,
                capture.isCapturing() && capture.isPaused() ? Lang.string("worldbinder.gui.resume_capture") : Lang.string("worldbinder.gui.pause_capture"),
                Component.translatable("worldbinder.tooltip.pause_capture"),
                b -> { capture.togglePause(); rebuildWidgets(); }));

        if (cols == 3) {
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 2, y, buttonW, rowH,
                    Lang.string("worldbinder.gui.open_f10_map"), Component.translatable("worldbinder.tooltip.open_map"),
                    b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
            y += rowH + 6;
            addRenderableWidget(button(x + 18, y, buttonW, rowH,
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
            addRenderableWidget(button(x + 18, y + rowH + 6, buttonW, rowH,
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
        Button start = button(x + 18, actionY, actionW, 23,
                capture.isRoamingCapture() ? Lang.string("worldbinder.gui.finish") : Lang.string("worldbinder.gui.start"),
                Component.translatable("worldbinder.tooltip.start_download"),
                b -> startRoamingCaptureWithLegalReminder());
        Button pause = button(x + 28 + actionW, actionY, actionW, 23,
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
        Button presetToggle = button(x + 18, presetButtonY, presetMenuW, 22,
                Lang.string("worldbinder.capture.preset_dropdown_value", capturePresetLabel()), Component.translatable("worldbinder.tooltip.presets_card"), b -> {
                    presetDropdownOpen = !presetDropdownOpen;
                    sectionDropdownOpen = false;
                    targetVersionDropdownOpen = false;
                    rebuildWidgets();
                });
        clipContentWidget(presetToggle);
        addRenderableWidget(presetToggle);
        if (presetDropdownOpen) {
            for (int i = 0; i < labelKeys.length; i++) {
                final int idx = i;
                Button preset = button(x + 18, presetButtonY + 26 + i * 21, presetMenuW, 20,
                        (WorldBinder.config().performancePreset == presets[i] ? "> " : "") + Lang.string(labelKeys[i]),
                        Component.translatable(tooltipKeys[i]),
                        b -> {
                            WorldBinder.config().setPreset(presets[idx]);
                            presetDropdownOpen = false;
                            rebuildWidgets();
                        });
                clipContentWidget(preset);
                addRenderableWidget(preset);
            }
        }
        int actionRowY = presetButtonY + (presetDropdownOpen ? 120 : 38);
        int actionCols = width < 560 ? 1 : 3;
        int capW = actionCols == 1 ? Math.min(width - 36, 240) : Math.max(96, Math.min(210, (width - 62) / 3));
        Button pos = button(x + 18, actionRowY, capW, 22, Lang.string("worldbinder.gui.capture_position"), Component.translatable("worldbinder.tooltip.capture_position"),
                b -> captureWorldArchiveWithLegalReminder());
        Button scene = button(actionCols == 1 ? x + 18 : x + 28 + capW, actionCols == 1 ? actionRowY + 28 : actionRowY, capW, 22, Lang.string("worldbinder.gui.capture_scene"), Component.translatable("worldbinder.tooltip.capture_scene"),
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
        addRenderableWidget(button(x + 18, y, buttonW, 25, Lang.string("worldbinder.gui.open_f10_map"), Component.translatable("worldbinder.tooltip.open_map"),
                b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
        addRenderableWidget(button(x + 30 + buttonW, y, buttonW, 25, Lang.string("worldbinder.gui.profiler"), Component.translatable("worldbinder.tooltip.profiler"),
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
            Button created = addRenderableWidget(button(x + 18 + col * (buttonW + gap), y + row * (rowH + 6), buttonW, rowH, Lang.string(labelKeys[i]), tooltips[i], actions[i]));
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
        addRenderableWidget(button(x + 18, y, buttonW, 23, Lang.string("worldbinder.section.map"), Component.translatable("worldbinder.tooltip.open_map"), b -> minecraft.gui.setScreen(new WorldBinderMapScreen(this))));
        addRenderableWidget(button(x + 18 + (buttonW + gap), y, buttonW, 23, Lang.string("worldbinder.gui.profiler"), Component.translatable("worldbinder.tooltip.profiler"), b -> minecraft.gui.setScreen(new WorldBinderProfilerScreen(this))));
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

    private void updateArchiveButtonState() {
        Path selected = selectedArchive();
        boolean hasSelection = selected != null;
        boolean recovery = library.isRecovery(selected);
        if (placeSelectedButton != null) placeSelectedButton.active = hasSelection && !recovery;
        if (openSelectedButton != null) openSelectedButton.active = hasSelection;
        if (continueRecoveryButton != null) continueRecoveryButton.active = hasSelection && library.canFinalizeRecovery(selected) && !capture.isCapturing();
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
    private Path selectedArchive() {
        List<Path> scenes = library.scenes();
        if (scenes.isEmpty()) {
            return null;
        }
        selectedArchiveIndex = Math.max(0, Math.min(selectedArchiveIndex, scenes.size() - 1));
        return scenes.get(selectedArchiveIndex);
    }

    private Path selectedArchiveForRows(List<Path> rows) {
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
        if (archiveName != null && !archiveName.getValue().isBlank()) {
            WorldBinder.config().defaultArchiveName = archiveName.getValue().replaceAll("[^a-zA-Z0-9_.-]", "_");
        }
        WorldBinder.config().save();
    }

    private String currentArchiveName() {
        if (archiveName == null || archiveName.getValue().isBlank()) {
            return WorldBinder.config().defaultArchiveName;
        }
        return archiveName.getValue();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = WbLayout.virtualMouseEvent(event, width, height);
        int realWidth = width;
        int realHeight = height;
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
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
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
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
        int view = Math.max(1, contentViewportBottom() - contentViewportTop());
        int content = switch (section) {
            case OVERVIEW -> 468;
            case CAPTURE -> (WbLayout.tiny(panelWidth(), panelHeight()) ? 570 : 500) + captureTargetDropdownOffset(contentWidth(panelWidth()));
            case MAP -> 360;
            case SETTINGS -> 520;
            case TOOLS -> 340;
            case ABOUT -> 560;
            default -> view;
        };
        return content > view + 12 ? content - view : 0;
    }

    private int contentViewportTop() {
        int panelWidth = panelWidth();
        return WbLayout.top(height, panelHeight()) + (WbLayout.sidebarWidth(panelWidth) <= 0 ? 50 : 68);
    }

    private int contentViewportBottom() {
        return WbLayout.top(height, panelHeight()) + panelHeight() - 42;
    }

    private boolean isInsideContentViewport(int y, int h) {
        return y >= contentViewportTop() && y + h <= contentViewportBottom();
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
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
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

    private void drawSectionContent(GuiGraphicsExtractor context, int left, int top, int panelWidth, int mouseX, int mouseY) {
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
    private int panelWidth() {
        return WbLayout.panelWidth(width);
    }
    private int panelHeight() {
        return WbLayout.panelHeight(height);
    }
    private int contentX(int left) {
        return WbLayout.contentX(left, panelWidth());
    }
    private int contentWidth(int panelWidth) {
        return WbLayout.contentWidth(panelWidth);
    }
private void drawOverview(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean compact = WbLayout.compact(panelWidth(), panelHeight());
        boolean tiny = WbLayout.tiny(panelWidth(), panelHeight());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int heroY = top + 70;
        int actionRows = 2;
        int actionsTop = bottom - (tiny ? 52 : 60);
        int heroH = Math.max(132, Math.min(compact ? 174 : 208, actionsTop - heroY - (tiny ? 72 : 96)));
        drawCard(context, x, heroY, width, heroH, Lang.text("worldbinder.overview.title"), Lang.text("worldbinder.overview.subtitle"), mouseX, mouseY);

        String status = capture.isSaving() ? Lang.string("worldbinder.common.saving") : capture.isPaused() ? Lang.string("worldbinder.common.paused") : capture.isCapturing() ? Lang.string("worldbinder.common.capturing") : Lang.string("worldbinder.common.idle");
        int statusColor = capture.isSaving() || capture.isPaused() ? WbTheme.WARN : capture.isCapturing() ? WbTheme.OK : WbTheme.TEXT_MUTED;
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

        WbText.drawClipped(context, font, Lang.string("worldbinder.overview.quick_actions"), x + 20, actionsTop - 18, width - 40, WbTheme.TEXT_DIM);
    }

private void drawCapture(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
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
        WbText.drawClipped(context, font, "Preset: " + presetLine(), x + 20, presetY + 34, width - 40, WbTheme.TEXT_SOFT);
        WbText.drawClipped(context, font, WorldBinder.config().presetDescription(), x + 20, presetY + 52, width - 40, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, Lang.string("worldbinder.capture.budget_line", WorldBinder.config().effectiveBlocksPerTick(), WorldBinder.config().effectiveTickBudgetMillis(), WorldBinder.config().effectiveRoamingRadiusChunks()), x + 20, presetY + 70, width - 40, WbTheme.TEXT_DIM);
        if (presetDropdownOpen) {
            int menuW = Math.min(230, Math.max(146, width - 36));
            int menuY = presetY + 111;
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

    private void drawCaptureTargetVersionDropdown(GuiGraphicsExtractor context, int x, int y, int width, int mouseX, int mouseY) {
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

private void drawMapSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean tiny = WbLayout.tiny(panelWidth(), panelHeight());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int cardH = Math.max(128, bottom - top - 146);
        drawCard(context, x, top + 70, width, cardH, Lang.text("worldbinder.section.map"), Lang.text("worldbinder.section.map.tooltip"), mouseX, mouseY);
        if (!tiny && width > 360) {
            drawMiniChunkMap(context, x + 24, top + 112, 9, Math.min(12, Math.max(7, (width - 220) / 24)));
        }
        int textX = tiny || width <= 360 ? x + 20 : x + 170;
        int textW = tiny || width <= 360 ? width - 40 : width - 190;
        WbText.drawWrapped(context, font, "F10 is the detailed chunk map with filters, inspector, status colors and LOD rendering.", textX, top + 112, textW, WbTheme.TEXT_SOFT, 3);
        WbText.drawClipped(context, font, "Layer: " + WorldBinder.config().f10MapLayerMode + " • Radar: " + WorldBinder.config().radarDetailMode, textX, top + 166, textW, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, Lang.string("worldbinder.map.saved_line", capture.scannedChunks(), capture.queuedChunks(), capture.partialChunksView().size()), textX, top + 190, textW, WbTheme.TEXT_DIM);
        WbText.drawWrapped(context, font, capture.captureRouteHint(), textX, top + 214, textW, WbTheme.INFO, 2);
    }
private void drawArchiveSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY, boolean recoveryOnly) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int actionRows = width < 420 ? 3 : width < 720 ? 3 : 2;
        int actionsSpace = actionRows * 27 + 18;
        int cardH = Math.max(108, bottom - (top + 70) - actionsSpace);
        drawCard(context, x, top + 70, width, cardH, recoveryOnly ? Lang.text("worldbinder.recovery.sessions") : Lang.text("worldbinder.section.archives"), recoveryOnly ? Lang.text("worldbinder.recovery.sessions.tooltip") : Lang.text("worldbinder.archives.tooltip"), mouseX, mouseY);
        drawArchiveList(context, x, top, width, recoveryOnly, false);
    }
private void drawRecoverySection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawArchiveSection(context, x, top, width, mouseX, mouseY, true);
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int recoveries = library.recoveryCount();
        int color = recoveries > 0 ? WbTheme.WARN : WbTheme.OK;
        WbText.drawWrapped(context, font, recoveries > 0 ? Lang.string("worldbinder.recovery.available_hint") : Lang.string("worldbinder.recovery.empty"), x + 20, Math.max(top + 156, bottom - 110), width - 40, color, 2);
    }


private void drawValidationSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawCard(context, x, top + 76, width, 226, Lang.text("worldbinder.gui.validate"), Lang.text("worldbinder.tooltip.validate_selected"), mouseX, mouseY);
        drawArchiveList(context, x, top, width, false, true);
        Path selected = selectedArchive();
        String line = selected == null ? Lang.string("worldbinder.chat.no_archive_selected") : library.validationLine(selected);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.validation.selected_result", line), x + 20, top + 282, WbTheme.TEXT_MUTED);
    }
private void drawSettingsSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
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
            boolean hovered = WbChrome.contains(cx, cy, cardW, cardH, mouseX, mouseY);
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


    private void drawToolsSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawCard(context, x, top + 76, width, 210, Lang.text("worldbinder.section.tools"), Lang.text("worldbinder.section.tools.tooltip"), mouseX, mouseY);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.tools.description"), x + 20, top + 116, WbTheme.TEXT_SOFT);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.tools.save_folder", net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath()), x + 20, top + 146, WbTheme.TEXT_DIM);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.tools.current_operation", OperationStatus.visible() ? OperationStatus.detail() : Lang.string("worldbinder.common.none")), x + 20, top + 172, WbTheme.TEXT_MUTED);
    }
private void drawAboutSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
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
        y += WbText.drawWrapped(context, font, "Ideas, bugs and feedback: https://github.com/Philiipp06/WorldBinder/issues", x + 20, y, textW, WbTheme.INFO, 2);
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

private void drawArchiveList(GuiGraphicsExtractor context, int x, int top, int width, boolean recoveryOnly, boolean validationMode) {
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


    private List<Path> archiveRows() {
        return archiveRows(section == Section.RECOVERY);
    }

    private List<Path> archiveRows(boolean recoveryOnly) {
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
private int visibleArchiveRows() {
        int bottom = WbLayout.contentBottom(WbLayout.top(height, panelHeight()), panelHeight());
        int top = WbLayout.top(height, panelHeight());
        int listTop = top + 146;
        int listBottom = bottom - (contentWidth(panelWidth()) < 520 ? 112 : 84);
        return Math.max(1, (listBottom - listTop) / 20);
    }


    private void clampArchiveWindow(int size) {
        if (size <= 0) {
            archiveListOffset = 0;
            selectedArchiveIndex = 0;
            return;
        }
        int rows = visibleArchiveRows();
        int maxOffset = Math.max(0, size - rows);
        archiveListOffset = Math.max(0, Math.min(archiveListOffset, maxOffset));
    }
    private void drawShell(GuiGraphicsExtractor context, int x, int y, int width, int height) {
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
private void drawSidebar(GuiGraphicsExtractor context, int left, int top) {
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

private void drawSectionHeader(GuiGraphicsExtractor context, int x, int y, int width) {
        int reserved = panelWidth() < 620 && width > 320 ? 190 : 0;
        WbSectionHeader.draw(context, font, x, y, Math.max(80, width - reserved), section.title(), WbText.ellipsize(font, section.subtitle().getString(), Math.max(40, width - reserved - 8)), sectionAccent(section));
    }

    private void drawSectionDropdown(GuiGraphicsExtractor context, int left, int top, int panelWidth, int mouseX, int mouseY) {
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

    private int settingsCardIndex(int mouseX, int mouseY, int x, int top, int width) {
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
            case 4, 5, 6 -> WorldBinderConfigScreen.safety(this);
            default -> WorldBinderConfigScreen.general(this);
        };
    }

    private void drawCard(GuiGraphicsExtractor context, int x, int y, int width, int height, Component title, Component tooltip, int mouseX, int mouseY) {
        if (WbCard.draw(context, font, x, y, width, height, title, sectionAccent(section), mouseX, mouseY)) {
            customTooltip = tooltip;
        }
    }

    private int sectionAccent(Section value) {
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
    private void drawChip(GuiGraphicsExtractor context, int x, int y, int width, String label, String value, int accent) {
        WbStatusChip.draw(context, font, x, y, width, label, value, accent);
    }

    private void drawActivity(GuiGraphicsExtractor context, int x, int y) {
        drawActivity(context, x, y, 240, 4);
    }

    private void drawActivity(GuiGraphicsExtractor context, int x, int y, int width, int maxLines) {
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

    private void drawHints(GuiGraphicsExtractor context, int x, int y) {
        drawHints(context, x, y, 240, 4);
    }

    private void drawHints(GuiGraphicsExtractor context, int x, int y, int width, int maxLines) {
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

    private void drawMiniChunkMap(GuiGraphicsExtractor context, int x, int y, int size, int cell) {
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
private void drawLine(GuiGraphicsExtractor context, int x, int y, String label, String value, int color) {
        int max = Math.max(60, contentX(WbLayout.left(width, panelWidth())) + contentWidth(panelWidth()) - x - 16);
        WbText.drawClipped(context, font, "§7" + label + ": §f" + value, x, y, max, color);
    }

    private void drawProgress(GuiGraphicsExtractor context, int x, int y, int width, double progress, String detail) {
        WbProgressBar.draw(context, font, x, y, width, progress, detail);
    }

    private String targetSummaryLine() {
        String version = WorldBinder.config().targetMinecraftVersion;
        String normalized = TargetMinecraftVersion.normalize(version);
        return Lang.string("worldbinder.config.target_value", normalized + " • " + TargetMinecraftVersion.profileLabel(normalized));
    }

    private String presetLine() {
        String preset = WorldBinder.config().performancePreset == null ? "CUSTOM" : WorldBinder.config().performancePreset.name();
        return preset + " • " + WorldBinder.config().effectiveBlocksPerTick() + " blocks/tick";
    }

    private String lastSessionLine() {
        List<Path> scenes = library.scenes();
        return scenes.isEmpty() ? Lang.string("worldbinder.archive.none_yet") : shorten(scenes.get(0).getFileName().toString(), 32);
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}

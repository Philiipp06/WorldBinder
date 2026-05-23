package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
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
import net.worldbinder.version.TargetMinecraftVersion;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbCard;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbProgressBar;
import net.worldbinder.ui.component.WbSectionHeader;
import net.worldbinder.ui.component.WbSelectableList;
import net.worldbinder.ui.component.WbSidebar;
import net.worldbinder.ui.component.WbStatusChip;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbWarningBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class WorldBinderScreen extends Screen {
    private enum Section {
        OVERVIEW("Overview", "Live status and quick actions"),
        CAPTURE("Capture", "Start downloads and configure capture mode"),
        MAP("Map", "Open and inspect captured chunks"),
        ARCHIVES("Archives & Validation", "Manage archives, previews and validation"),
        RECOVERY("Recovery", "Continue or finalize recoveries"),
        SETTINGS("Settings", "Open WorldBinder configuration"),
        TOOLS("Tools", "Advanced utilities"),
        ABOUT("About", "Version and workflow help");

        final String title;
        final String subtitle;

        Section(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final SelectionManager selections;
    private final SceneCaptureService capture;
    private final ScenePlacementService placement;
    private final SceneLibrary library;
    private EditBox archiveName;
    private EditBox targetVersion;
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
    private List<String> cachedActivityEntries = List.of();

    public WorldBinderScreen(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library) {
        super(Component.translatable("worldbinder.gui.title"));
        this.selections = selections;
        this.capture = capture;
        this.placement = placement;
        this.library = library;
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        archiveName = null;
        targetVersion = null;
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);
        int contentX = contentX(left);
        int contentW = contentWidth(panelWidth);

        addSidebar(left, top);
        if (section == Section.CAPTURE) {
            int archiveFieldWidth = contentW < 500 ? Math.max(36, contentW - 36) : Math.min(300, Math.max(150, contentW - 420));
            archiveName = new EditBox(font, contentX + 18, top + 104, archiveFieldWidth, 22, Component.literal("Archive name"));
            archiveName.setMaxLength(64);
            archiveName.setValue(WorldBinder.config().defaultArchiveName);
            addRenderableWidget(archiveName);
            targetVersion = new EditBox(font, contentX + 18, top + 150, 96, 22, Component.literal("Target version"));
            targetVersion.setMaxLength(12);
            targetVersion.setValue(WorldBinder.config().targetMinecraftVersion);
            addRenderableWidget(targetVersion);
        }

        if (section == Section.OVERVIEW) {
            overviewWidgets(contentX, top, contentW);
        } else if (section == Section.CAPTURE) {
            captureWidgets(contentX, top, contentW);
        } else if (section == Section.MAP) {
            mapWidgets(contentX, top, contentW);
        } else if (section == Section.ARCHIVES) {
            archiveWidgets(contentX, top, false);
        } else if (section == Section.RECOVERY) {
            archiveWidgets(contentX, top, true);
        } else if (section == Section.SETTINGS) {
            settingsWidgets(contentX, top);
        } else if (section == Section.TOOLS) {
            toolsWidgets(contentX, top, contentW);
        }

        addRenderableWidget(button(left + panelWidth - 116, top + panelHeight - 32, 96, 22,
                "Close", Component.translatable("worldbinder.tooltip.close"), b -> onClose()));
    }
    private void addSidebar(int left, int top) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int sidebarWidth = WbLayout.sidebarWidth(panelWidth);
        int buttonX = left + 12;
        int buttonW = Math.max(68, sidebarWidth - 24);
        int buttonH = WbLayout.sidebarButtonHeight(panelHeight);
        int y = top + (panelHeight < 390 ? 58 : 76);
        int step = WbLayout.sidebarStep(panelHeight);
        boolean compact = panelWidth < 520;
        for (Section target : Section.values()) {
            String label = compact ? compactSectionLabel(target) : target.title;
            Button widget = Button.builder(Component.literal((section == target ? "◆ " : "") + label), button -> {
                section = target;
                pendingDeletePath = null;
                pendingDeleteMillis = 0L;
                rebuildWidgets();
            }).bounds(buttonX, y, buttonW, buttonH).tooltip(Tooltip.create(Component.literal(target.subtitle))).build();
            addRenderableWidget(widget);
            y += step;
        }
    }

    private String compactSectionLabel(Section target) {
        return switch (target) {
            case OVERVIEW -> "Home";
            case CAPTURE -> "Cap";
            case MAP -> "Map";
            case ARCHIVES -> "Archive";
            case RECOVERY -> "Recover";
            case SETTINGS -> "Config";
            case TOOLS -> "Tools";
            case ABOUT -> "About";
        };
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
                capture.isRoamingCapture() ? "Finish Export" : "Start Capture",
                Component.translatable("worldbinder.tooltip.start_download"),
                b -> { syncTargetVersionFromCaptureField(); startRoamingCaptureWithLegalReminder(); }));
        addRenderableWidget(button(x + 18 + (buttonW + gap), y, buttonW, rowH,
                capture.isCapturing() && capture.isPaused() ? "Resume Capture" : "Pause Capture",
                Component.translatable("worldbinder.tooltip.pause_capture"),
                b -> { capture.togglePause(); rebuildWidgets(); }));

        if (cols == 3) {
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 2, y, buttonW, rowH,
                    "Open F10 Map", Component.translatable("worldbinder.tooltip.open_map"),
                    b -> minecraft.setScreen(new WorldBinderMapScreen(this))));
            y += rowH + 6;
            addRenderableWidget(button(x + 18, y, buttonW, rowH,
                    "Place Latest", Component.translatable("worldbinder.tooltip.place_latest"),
                    b -> placement.placeLatestAtPlayer()));
            addRenderableWidget(button(x + 18 + (buttonW + gap), y, buttonW, rowH,
                    "Open Archives", Component.literal("Open archive manager."),
                    b -> { section = Section.ARCHIVES; rebuildWidgets(); }));
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 2, y, buttonW, rowH,
                    library.recoveryCount() > 0 ? "Recovery" : "Tools",
                    library.recoveryCount() > 0 ? Component.literal("Show available crash recoveries.") : Component.literal("Open tools."),
                    b -> { section = library.recoveryCount() > 0 ? Section.RECOVERY : Section.TOOLS; rebuildWidgets(); }));
        } else {
            addRenderableWidget(button(x + 18, y + rowH + 6, buttonW, rowH,
                    "F10 Map", Component.translatable("worldbinder.tooltip.open_map"),
                    b -> minecraft.setScreen(new WorldBinderMapScreen(this))));
            addRenderableWidget(button(x + 18 + (buttonW + gap), y + rowH + 6, buttonW, rowH,
                    "Archives", Component.literal("Open archive manager."),
                    b -> { section = Section.ARCHIVES; rebuildWidgets(); }));
        }
    }
private void captureWidgets(int x, int top, int width) {
        boolean narrow = width < 620;
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int fieldY = top + 104;
        int nameW = narrow ? Math.max(80, width - 36) : Math.min(340, Math.max(160, width - 420));
        int versionY = fieldY + 46;
        int versionButtonW = narrow ? Math.max(64, (width - 140) / 2) : 118;
        int versionButtonX = x + 124;
        addRenderableWidget(button(versionButtonX, versionY, versionButtonW, 22, "Prev", Component.literal("Previous final release"), b -> { cycleTargetVersion(true); rebuildWidgets(); }));
        addRenderableWidget(button(versionButtonX + versionButtonW + 8, versionY, versionButtonW, 22, "Next", Component.literal("Next final release"), b -> { cycleTargetVersion(false); rebuildWidgets(); }));

        int actionY = narrow ? versionY + 58 : versionY + 54;
        int actionW = Math.max(90, (width - 44) / 2);
        addRenderableWidget(button(x + 18, actionY, actionW, 23,
                capture.isRoamingCapture() ? "Finish" : "Start",
                Component.translatable("worldbinder.tooltip.start_download"),
                b -> { syncTargetVersionFromCaptureField(); startRoamingCaptureWithLegalReminder(); }));
        addRenderableWidget(button(x + 26 + actionW, actionY, actionW, 23,
                capture.isPaused() ? "Resume" : "Pause",
                Component.translatable("worldbinder.tooltip.pause_capture"),
                b -> { capture.togglePause(); rebuildWidgets(); }));

        int presetY = Math.min(bottom - 112, Math.max(top + 292, actionY + 78));
        int gap = 7;
        int presetCols = width < 430 ? 2 : 4;
        int presetW = Math.max(56, (width - 36 - gap * (presetCols - 1)) / presetCols);
        int presetButtonY = presetY + 66;
        String[] labels = {"Safe", "Balanced", "Fast", "Extreme"};
        net.worldbinder.config.WorldBinderConfig.PerformancePreset[] presets = {
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.SAFE,
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.BALANCED,
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.FAST,
                net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            int row = i / presetCols;
            int col = i % presetCols;
            addRenderableWidget(button(x + 18 + col * (presetW + gap), presetButtonY + row * 28, presetW, 22, labels[i], Component.translatable("worldbinder.tooltip.preset_" + labels[i].toLowerCase()),
                    b -> { WorldBinder.config().setPreset(presets[idx]); rebuildWidgets(); }));
        }
        int actionRowY = presetButtonY + (presetCols == 2 ? 64 : 38);
        int capW = Math.max(82, Math.min(170, (width - 44) / 2));
        addRenderableWidget(button(x + 18, actionRowY, capW, 22, "Capture Position", Component.translatable("worldbinder.tooltip.capture_position"),
                b -> { syncTargetVersionFromCaptureField(); captureWorldArchiveWithLegalReminder(); }));
        addRenderableWidget(button(x + 30 + capW, actionRowY, capW, 22, "Capture Scene", Component.translatable("worldbinder.tooltip.capture_scene"),
                b -> { syncTargetVersionFromCaptureField(); captureSceneWithLegalReminder(); }));
    }

private void mapWidgets(int x, int top, int width) {
        int y = Math.min(top + 328, top + panelHeight() - 70);
        int buttonW = Math.max(104, Math.min(150, (width - 44) / 2));
        addRenderableWidget(button(x + 18, y, buttonW, 25, "Open F10 Map", Component.translatable("worldbinder.tooltip.open_map"),
                b -> minecraft.setScreen(new WorldBinderMapScreen(this))));
        addRenderableWidget(button(x + 30 + buttonW, y, buttonW, 25, "Profiler", Component.translatable("worldbinder.tooltip.profiler"),
                b -> minecraft.setScreen(new WorldBinderProfilerScreen(this))));
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
        String[] labels = {"Place", "Folder", "Continue", "Finalize", "Preview", "Duplicate", "Validate", "Refresh", "Delete"};
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
        for (int i = 0; i < labels.length; i++) {
            int row = i / cols;
            int col = i % cols;
            Button created = addRenderableWidget(button(x + 18 + col * (buttonW + gap), y + row * (rowH + 6), buttonW, rowH, labels[i], tooltips[i], actions[i]));
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
        exportPreviewButton = addRenderableWidget(button(x + 18, top + 316, 130, 23, "Export Preview", Component.translatable("worldbinder.tooltip.export_preview"), b -> exportSelectedPreview()));
        validateSelectedButton = addRenderableWidget(button(x + 160, top + 316, 130, 23, "Validate Selected", Component.translatable("worldbinder.tooltip.validate_selected"), b -> validateSelectedArchive()));
        openSelectedButton = addRenderableWidget(button(x + 302, top + 316, 130, 23, "Open Folder", Component.translatable("worldbinder.tooltip.open_saves_folder"), b -> openSelectedArchive()));
        addRenderableWidget(button(x + 444, top + 316, 90, 23, "Refresh", Component.translatable("worldbinder.tooltip.refresh"), b -> { library.refresh(); rebuildWidgets(); }));
        updateArchiveButtonState();
    }
private void settingsWidgets(int x, int top) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int w = Math.min(170, Math.max(110, contentWidth(panelWidth()) - 36));
        addRenderableWidget(button(x + 18, bottom - 28, w, 23, "Open Full Settings", Component.translatable("worldbinder.tooltip.settings"),
                b -> minecraft.setScreen(new WorldBinderConfigScreen(this))));
    }
private void toolsWidgets(int x, int top, int width) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int gap = 6;
        int cols = width < 460 ? 2 : 4;
        int buttonW = Math.max(70, (width - 36 - gap * (cols - 1)) / cols);
        int y = bottom - (cols == 2 ? 52 : 26);
        addRenderableWidget(button(x + 18, y, buttonW, 23, "Map", Component.translatable("worldbinder.tooltip.open_map"), b -> minecraft.setScreen(new WorldBinderMapScreen(this))));
        addRenderableWidget(button(x + 18 + (buttonW + gap), y, buttonW, 23, "Profiler", Component.translatable("worldbinder.tooltip.profiler"), b -> minecraft.setScreen(new WorldBinderProfilerScreen(this))));
        if (cols == 4) {
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 2, y, buttonW, 23, "Open Saves", Component.translatable("worldbinder.tooltip.open_saves_folder"),
                    b -> net.worldbinder.util.PathOpener.open(net.worldbinder.io.WorldBinderPaths.WORLDS)));
            addRenderableWidget(button(x + 18 + (buttonW + gap) * 3, y, buttonW, 23, "Cancel", Component.translatable("worldbinder.tooltip.cancel_capture"),
                    b -> { capture.cancelActiveCapture(); rebuildWidgets(); }));
        } else {
            addRenderableWidget(button(x + 18, y + 29, buttonW, 23, "Open Saves", Component.translatable("worldbinder.tooltip.open_saves_folder"),
                    b -> net.worldbinder.util.PathOpener.open(net.worldbinder.io.WorldBinderPaths.WORLDS)));
            addRenderableWidget(button(x + 18 + (buttonW + gap), y + 29, buttonW, 23, "Cancel", Component.translatable("worldbinder.tooltip.cancel_capture"),
                    b -> { capture.cancelActiveCapture(); rebuildWidgets(); }));
        }
    }

    private void startRoamingCaptureWithLegalReminder() {
        if (capture.isRoamingCapture()) {
            capture.finishActiveCapture();
            rebuildWidgets();
            return;
        }
        runWithCapturePrompt(name -> {
            capture.toggleRoamingCapture(name);
            if (archiveName != null) archiveName.setValue(name);
            rebuildWidgets();
        }, "Start saving");
    }

    private void captureWorldArchiveWithLegalReminder() {
        runWithCapturePrompt(name -> {
            capture.captureWorldArchive(name);
            if (archiveName != null) archiveName.setValue(name);
            rebuildWidgets();
        }, "Start saving");
    }

    private void captureSceneWithLegalReminder() {
        runWithCapturePrompt(name -> {
            capture.captureScene(name);
            if (archiveName != null) archiveName.setValue(name);
            rebuildWidgets();
        }, "Start saving");
    }

    private void runWithCapturePrompt(java.util.function.Consumer<String> action, String label) {
        minecraft.setScreen(new WorldBinderLegalStartScreen(this, action, label, currentArchiveName()));
    }

    private Button button(int x, int y, int width, int height, String label, Component tooltip, Button.OnPress action) {
        return WbButton.create(x, y, width, height, label, tooltip, action);
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
            deleteSelectedButton.setMessage(Component.literal(armed ? "Confirm Delete" : "Delete"));
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
            Chat.warn("No saved archive found.");
            return;
        }
        placement.placeAtPlayer(selected);
    }

    private void openSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn("No saved archive selected.");
            return;
        }
        net.worldbinder.util.PathOpener.open(java.nio.file.Files.isDirectory(selected) ? selected : selected.getParent());
    }

    private void finalizeSelectedRecovery() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn("No recovery selected.");
            return;
        }
        if (!library.canFinalizeRecovery(selected)) {
            Chat.warn("Selected archive is not a completed recovery folder.");
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin("WorldBinder Recovery", "Finalizing recovery in the background...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.finalizeRecovery(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((finalized, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish("Recovery finalized");
                Chat.info("Finalized recovery as save folder: §f" + finalized.getFileName());
                selectedArchiveIndex = 0;
                rebuildWidgets();
            } else {
                OperationStatus.finish("Recovery finalize failed");
                Chat.error("Failed to finalize recovery. Check the log.");
                WorldBinder.LOGGER.warn("Failed to finalize recovery", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void continueSelectedRecovery() {
        Path selected = selectedArchive();
        if (selected == null || !library.isRecovery(selected)) {
            Chat.warn("No recovery selected.");
            return;
        }
        capture.continueRecovery(selected);
        rebuildWidgets();
    }

    private void saveSelectedAsArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn("No archive selected.");
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin("WorldBinder Archive", "Saving archive copy in the background...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.saveAsArchive(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((saved, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish("Archive copy saved");
                Chat.info("Saved archive copy: §f" + saved.getFileName());
                selectedArchiveIndex = 0;
                rebuildWidgets();
            } else {
                OperationStatus.finish("Archive copy failed");
                Chat.error("Failed to save archive copy. Check the log.");
                WorldBinder.LOGGER.warn("Failed to save archive copy", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void validateSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn("No archive selected.");
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin("WorldBinder Validation", "Validating selected archive in the background...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.validateArchive(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((report, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish("Validation complete");
                Chat.info("Validation complete: §f" + net.worldbinder.validation.ExportValidator.shortLine(report));
                rebuildWidgets();
            } else {
                OperationStatus.finish("Validation failed");
                Chat.error("Failed to validate archive. Check the log.");
                WorldBinder.LOGGER.warn("Failed to validate archive", throwable);
                setArchiveActionsActive(true);
            }
        }));
    }

    private void deleteSelectedArchive() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn("No archive selected.");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (!selected.equals(pendingDeletePath) || now - pendingDeleteMillis >= 5000L) {
                pendingDeletePath = selected;
                pendingDeleteMillis = now;
                Chat.warn("Click Delete again within 5 seconds to permanently remove: §f" + selected.getFileName());
                updateArchiveButtonState();
                return;
            }
            setArchiveActionsActive(false);
            OperationStatus.begin("WorldBinder Archive", "Deleting selected archive in the background...");
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
                    OperationStatus.finish("Archive deleted");
                    Chat.info("Deleted archive: §f" + selected.getFileName());
                    selectedArchiveIndex = 0;
                    archiveListOffset = 0;
                    rebuildWidgets();
                } else {
                    OperationStatus.finish("Archive delete failed");
                    Chat.error("Failed to delete archive. Check the log.");
                    WorldBinder.LOGGER.warn("Failed to delete archive", throwable);
                    setArchiveActionsActive(true);
                }
            }));
        } catch (Exception exception) {
            Chat.error("Failed to schedule archive delete. Check the log.");
            WorldBinder.LOGGER.warn("Failed to schedule archive delete", exception);
        }
    }

    private void exportSelectedPreview() {
        Path selected = selectedArchive();
        if (selected == null) {
            Chat.warn("No archive available for preview export.");
            return;
        }
        setArchiveActionsActive(false);
        OperationStatus.begin("WorldBinder Preview", "Generating preview in the background...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return library.exportPreviewThumbnail(selected);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((preview, throwable) -> runOnClient(() -> {
            if (throwable == null) {
                OperationStatus.finish("Preview exported");
                Chat.info("Exported archive preview: §f" + preview.toAbsolutePath());
                net.worldbinder.util.PathOpener.open(preview.getParent());
                rebuildWidgets();
            } else {
                OperationStatus.finish("Preview failed");
                Chat.error("Failed to export archive preview. Check the log.");
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

    private void syncTargetVersionFromCaptureField() {
        if (targetVersion != null) {
            WorldBinder.config().targetMinecraftVersion = TargetMinecraftVersion.normalize(targetVersion.getValue());
            WorldBinder.config().save();
        }
    }

    private void cycleTargetVersion(boolean backwards) {
        syncTargetVersionFromCaptureField();
        WorldBinder.config().cycleTargetVersion(backwards);
    }

    private String currentArchiveName() {
        if (archiveName == null || archiveName.getValue().isBlank()) {
            return WorldBinder.config().defaultArchiveName;
        }
        return archiveName.getValue();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (section == Section.ARCHIVES || section == Section.RECOVERY) {
            int panelWidth = panelWidth();
            int panelHeight = panelHeight();
            int left = WbLayout.left(width, panelWidth);
            int top = WbLayout.top(height, panelHeight);
            int contentX = contentX(left);
            int x = (int) event.x();
            int y = (int) event.y();
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (section == Section.ARCHIVES || section == Section.RECOVERY) {
            List<Path> scenes = archiveRows();
            int maxOffset = Math.max(0, scenes.size() - visibleArchiveRows());
            if (maxOffset > 0) {
                archiveListOffset = Math.max(0, Math.min(maxOffset, archiveListOffset + (verticalAmount < 0 ? 1 : -1)));
                updateArchiveButtonState();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        customTooltip = null;
        context.fill(0, 0, width, height, WbTheme.BACKDROP);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = WbLayout.left(width, panelWidth);
        int top = WbLayout.top(height, panelHeight);

        drawShell(context, left, top, panelWidth, panelHeight);
        drawSidebar(context, left, top);
        drawSectionContent(context, left, top, panelWidth, mouseX, mouseY);
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (customTooltip != null) {
            context.setTooltipForNextFrame(font, customTooltip, mouseX, mouseY);
        }
    }

    private void drawSectionContent(GuiGraphicsExtractor context, int left, int top, int panelWidth, int mouseX, int mouseY) {
        int x = contentX(left);
        int w = contentWidth(panelWidth);
        drawSectionHeader(context, x, top + 26, w);
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
        drawCard(context, x, heroY, width, heroH, Component.literal("WorldBinder Command Center"), Component.literal("Live WorldBinder status and primary actions."), mouseX, mouseY);

        String status = capture.isSaving() ? "Saving" : capture.isPaused() ? "Paused" : capture.isCapturing() ? "Capturing" : "Idle";
        int statusColor = capture.isSaving() || capture.isPaused() ? WbTheme.WARN : capture.isCapturing() ? WbTheme.OK : WbTheme.TEXT_MUTED;
        WbText.drawClipped(context, font, "◆ " + status, x + 20, heroY + 32, width - 40, statusColor);
        int recoveries = library.recoveryCount();
        if (recoveries > 0 && width > 420) {
            WbText.drawClipped(context, font, "⚠ Recovery available: " + recoveries, x + Math.max(20, width / 2), heroY + 32, width / 2 - 24, WbTheme.WARN);
        }

        int twoCol = width > 560 ? width / 2 : width;
        if (capture.isCapturing() && heroH > 178) {
            WbText.drawClipped(context, font, capture.captureRouteHint(), x + 20, heroY + 52, width - 40, WbTheme.INFO);
        }
        drawLine(context, x + 20, heroY + (capture.isCapturing() && heroH > 178 ? 72 : 56), "Archive", capture.isCapturing() ? capture.activeArchiveDisplayName() : currentArchiveName(), WbTheme.TEXT_SOFT);
        int infoBaseY = capture.isCapturing() && heroH > 178 ? heroY + 92 : heroY + 76;
        drawLine(context, x + 20, infoBaseY, "Preset", presetLine(), WbTheme.TEXT_MUTED);
        if (width > 560) {
            drawLine(context, x + width / 2, heroY + (capture.isCapturing() && heroH > 178 ? 72 : 56), "Mode", capture.modeName(), WbTheme.TEXT_SOFT);
            drawLine(context, x + width / 2, infoBaseY, "Last", lastSessionLine(), WbTheme.TEXT_MUTED);
        }

        int chipTop = heroY + (capture.isCapturing() && heroH > 178 ? 120 : 104);
        int chipCols = width < 520 ? 2 : 3;
        int chipGap = 8;
        int chipW = Math.max(64, (width - 40 - chipGap * (chipCols - 1)) / chipCols);
        String[][] chips = {
                {"Saved", Integer.toString(capture.scannedChunks())},
                {"Queued", Integer.toString(capture.queuedChunks())},
                {"Entities", Integer.toString(capture.capturedEntities())},
                {"ETA", capture.estimatedFinishText()},
                {"Throttle", capture.adaptiveThrottlePercent() + "%"},
                {"Last", lastSessionLine()}
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
            drawCard(context, x, cardsY, cardW, cardsH, Component.literal("Recent Activity"), Component.literal("Latest WorldBinder operations."), mouseX, mouseY);
            drawActivity(context, x + 20, cardsY + 34, Math.max(60, cardW - 40), Math.max(1, (cardsH - 42) / 16));
            if (width > 620) {
                drawCard(context, x + cardW + gap, cardsY, cardW, cardsH, Component.literal("Warnings / Hints"), Component.literal("Current performance and safety hints."), mouseX, mouseY);
                drawHints(context, x + cardW + gap + 20, cardsY + 34, Math.max(60, cardW - 40), Math.max(1, (cardsH - 42) / 16));
            }
        }

        WbText.drawClipped(context, font, "Quick actions", x + 20, actionsTop - 18, width - 40, WbTheme.TEXT_DIM);
    }

private void drawCapture(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean narrow = width < 620;
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int card1Y = top + 70;
        int card1H = narrow ? 222 : 210;
        drawCard(context, x, card1Y, width, card1H, Component.literal("Capture"), Component.literal("Start or pause capture for the current archive."), mouseX, mouseY);
        WbText.drawClipped(context, font, "Archive name", x + 20, card1Y + 58, width - 40, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, "Target output", x + 20, card1Y + 104, width - 40, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, WorldBinder.config().targetVersionLabel(), x + 20, card1Y + 128, width - 40, WbTheme.ACCENT);
        WbText.drawClipped(context, font, "Status: " + capture.modeName(), x + 20, card1Y + 176, width / 2 - 28, WbTheme.TEXT_SOFT);
        WbText.drawClipped(context, font, "Queue: " + capture.queuedChunks() + " chunks", x + Math.max(20, width / 2), card1Y + 176, width / 2 - 28, capture.highQueuePressure() ? WbTheme.ERROR : WbTheme.TEXT_MUTED);

        int presetY = card1Y + card1H + 18;
        int presetH = Math.min(190, Math.max(150, bottom - presetY - 86));
        drawCard(context, x, presetY, width, presetH, Component.literal("Performance Preset"), Component.literal("Capture pressure and adaptive throttle."), mouseX, mouseY);
        WbText.drawWrapped(context, font, "Preset: " + presetLine() + " • " + WorldBinder.config().presetDescription(), x + 20, presetY + 34, width - 40, WbTheme.TEXT_SOFT, 2);
        WbText.drawClipped(context, font, "Blocks/tick: " + WorldBinder.config().effectiveBlocksPerTick() + " • Budget: " + WorldBinder.config().effectiveTickBudgetMillis() + "ms • Radius: " + WorldBinder.config().effectiveRoamingRadiusChunks() + " chunks", x + 20, presetY + 56, width - 40, WbTheme.TEXT_MUTED);

        int selectionY = presetY + presetH + 18;
        if (selectionY + 48 < bottom - 8) {
            drawCard(context, x, selectionY, width, bottom - selectionY - 8, Component.literal("Selection Capture"), Component.literal("Capture a selected area."), mouseX, mouseY);
            WbText.drawClipped(context, font, selections.hasCompleteSelection() ? "Selection ready" : "No complete selection", x + 20, selectionY + 38, width - 40, selections.hasCompleteSelection() ? WbTheme.OK : WbTheme.TEXT_MUTED);
        }
    }

private void drawMapSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        boolean tiny = WbLayout.tiny(panelWidth(), panelHeight());
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int cardH = Math.max(128, bottom - top - 146);
        drawCard(context, x, top + 70, width, cardH, Component.literal("Map"), Component.literal("Open the full optimized F10 chunk map."), mouseX, mouseY);
        if (!tiny && width > 360) {
            drawMiniChunkMap(context, x + 24, top + 112, 9, Math.min(12, Math.max(7, (width - 220) / 24)));
        }
        int textX = tiny || width <= 360 ? x + 20 : x + 170;
        int textW = tiny || width <= 360 ? width - 40 : width - 190;
        WbText.drawWrapped(context, font, "F10 is the detailed chunk map with filters, inspector, status colors and LOD rendering.", textX, top + 112, textW, WbTheme.TEXT_SOFT, 3);
        WbText.drawClipped(context, font, "Layer: " + WorldBinder.config().f10MapLayerMode + " • Radar: " + WorldBinder.config().radarDetailMode, textX, top + 166, textW, WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, "Saved: " + capture.scannedChunks() + " • Queued: " + capture.queuedChunks() + " • Partial: " + capture.partialChunksView().size(), textX, top + 190, textW, WbTheme.TEXT_DIM);
        WbText.drawWrapped(context, font, capture.captureRouteHint(), textX, top + 214, textW, WbTheme.INFO, 2);
    }
private void drawArchiveSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY, boolean recoveryOnly) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int actionRows = width < 420 ? 3 : width < 720 ? 3 : 2;
        int actionsSpace = actionRows * 27 + 18;
        int cardH = Math.max(108, bottom - (top + 70) - actionsSpace);
        drawCard(context, x, top + 70, width, cardH, Component.literal(recoveryOnly ? "Recovery Sessions" : "Archives & Validation"), Component.literal(recoveryOnly ? "Crash recoveries that can be continued or finalized." : "Saved archives, previews and validation results."), mouseX, mouseY);
        drawArchiveList(context, x, top, width, recoveryOnly, false);
    }
private void drawRecoverySection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawArchiveSection(context, x, top, width, mouseX, mouseY, true);
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int recoveries = library.recoveryCount();
        int color = recoveries > 0 ? WbTheme.WARN : WbTheme.OK;
        WbText.drawWrapped(context, font, recoveries > 0 ? "Recovery available. Select a recovery and choose Continue or Finalize. Active/partial chunks stay separated from safe final data." : "No recovery sessions found.", x + 20, Math.max(top + 156, bottom - 110), width - 40, color, 2);
    }


private void drawValidationSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawCard(context, x, top + 76, width, 226, Component.literal("Validation"), Component.literal("Validate archive quality and export preview thumbnails."), mouseX, mouseY);
        drawArchiveList(context, x, top, width, false, true);
        Path selected = selectedArchive();
        String line = selected == null ? "No archive selected" : library.validationLine(selected);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Selected result: §f" + line), x + 20, top + 282, 0xFFBDB6D9);
    }
private void drawSettingsSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        boolean compact = WbLayout.compact(panelWidth(), panelHeight());
        int cols = width < 560 ? 1 : 2;
        int gap = 14;
        int cardW = cols == 1 ? width : (width - gap) / 2;
        String[][] cards = {
                {"General", "Archive defaults and normal workflow defaults."},
                {"Capture", "World radius, entities and capture modes."},
                {"Performance", "Preset, queue pressure, FPS target and budgets."},
                {"Map & Radar", "F10 detail, radar size and adaptive rendering."},
                {"Recovery", "Autosave interval and safe restore workflow."},
                {"Export", "Vanilla output, previews and validation."},
                {"Safety", "Server safety and operation guards."},
                {"Advanced", "Raw custom values stay untouched. -1 stays special."}
        };
        int startY = top + 70;
        int available = Math.max(80, bottom - startY - 78);
        int rowsNeeded = (int) Math.ceil(cards.length / (double) cols);
        int cardH = Math.max(46, Math.min(compact ? 58 : 68, (available - (rowsNeeded - 1) * 10) / rowsNeeded));
        for (int i = 0; i < cards.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = startY + row * (cardH + 10);
            if (cy + cardH > bottom - 78) {
                break;
            }
            drawCard(context, cx, cy, cardW, cardH, Component.literal(cards[i][0]), Component.literal(cards[i][1]), mouseX, mouseY);
            WbText.drawWrapped(context, font, cards[i][1], cx + 14, cy + 32, cardW - 28, i == 7 ? WbTheme.WARN : WbTheme.TEXT_MUTED, cardH < 56 ? 1 : 2);
        }
        WbText.drawClipped(context, font, "Current: " + presetLine() + " • Adaptive throttle: " + (WorldBinder.config().adaptiveThrottle ? "on" : "off") + " • Target FPS: " + WorldBinder.config().targetFps, x + 20, bottom - 78, width - 40, WbTheme.TEXT_SOFT);
        WbText.drawWrapped(context, font, "Advanced numeric values are edited in the full settings screen; raw custom values and -1 special modes stay untouched.", x + 20, bottom - 60, width - 40, WbTheme.WARN, 1);
    }


    private void drawToolsSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        drawCard(context, x, top + 76, width, 210, Component.literal("Tools"), Component.literal("Maintenance and diagnostic actions."), mouseX, mouseY);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Open folders, profiler, map tools or cancel active capture safely."), x + 20, top + 116, 0xFFE6E6F0);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Save folder: §f" + net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath()), x + 20, top + 146, 0xFF8F86B8);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("Current operation: §f" + (OperationStatus.visible() ? OperationStatus.detail() : "none")), x + 20, top + 172, 0xFFBDB6D9);
    }
private void drawAboutSection(GuiGraphicsExtractor context, int x, int top, int width, int mouseX, int mouseY) {
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int cardY = top + 70;
        int cardH = Math.max(150, bottom - cardY - 10);
        drawCard(context, x, cardY, width, cardH, Component.literal("About / Legal"), Component.literal("Project information, default keys, data paths and allowed usage."), mouseX, mouseY);
        int y = cardY + 36;
        int textW = width - 40;
        WbText.drawClipped(context, font, "WorldBinder • version 1.2.6 • Minecraft/Fabric target 26.1.2 / 0.19.2", x + 20, y, textW, WbTheme.ACCENT);
        y += 22;
        y += WbText.drawWrapped(context, font, "Author: Philiipp06. Client-side world capture, archive, preview and validation tool.", x + 20, y, textW, WbTheme.TEXT_SOFT, 2);
        y += 6;
        y += WbText.drawWrapped(context, font, "Default keys: F9 Control Center, F10 Map. Capture and selection keys are configurable in Minecraft controls.", x + 20, y, textW, WbTheme.TEXT_MUTED, 2);
        y += 6;
        y += WbText.drawWrapped(context, font, "Data: " + net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath(), x + 20, y, textW, WbTheme.TEXT_MUTED, 2);
        y += 12;
        if (y < bottom - 88) {
            WbText.draw(context, font, "Legal use notice", x + 20, y, WbTheme.WARN);
            y += 20;
            y += WbText.drawWrapped(context, font, "WorldBinder is intended only for servers, worlds or maps you own or where you have explicit permission.", x + 20, y, textW, WbTheme.TEXT_SOFT, 3);
            y += 4;
            y += WbText.drawWrapped(context, font, "Do not copy, archive, redistribute or extract third-party servers, maps, builds, resource packs or protected content without permission.", x + 20, y, textW, WbTheme.TEXT_SOFT, 3);
            y += 4;
            WbText.drawWrapped(context, font, "Users are responsible for lawful and permitted use. The developer does not endorse unauthorized copying.", x + 20, y, textW, WbTheme.ERROR, 2);
        }
    }

private void drawArchiveList(GuiGraphicsExtractor context, int x, int top, int width, boolean recoveryOnly, boolean validationMode) {
        List<Path> scenes = archiveRows(recoveryOnly);
        int bottom = WbLayout.contentBottom(top, panelHeight());
        int listTop = top + 146;
        int listBottom = bottom - (width < 420 ? 112 : width < 720 ? 104 : 74);
        if (scenes.isEmpty()) {
            WbText.drawWrapped(context, font, recoveryOnly ? "No recovery files available." : "No archives found.", x + 20, listTop, width - 40, WbTheme.TEXT_MUTED, 2);
            WbText.drawWrapped(context, font, "Folder: " + net.worldbinder.io.WorldBinderPaths.WORLDS.toAbsolutePath(), x + 20, listTop + 34, width - 40, WbTheme.TEXT_DIM, 3);
            return;
        }
        clampArchiveWindow(scenes.size());
        Path selectedPath = selectedArchiveForRows(scenes);
        boolean selectedRecovery = library.isRecovery(selectedPath);
        String selectedName = selectedPath == null ? "none" : selectedPath.getFileName().toString();
        String recoveryState = selectedRecovery ? " • State: " + library.recoveryState(selectedPath) : "";
        WbText.drawClipped(context, font, "Selected: " + shorten(selectedName, 54) + " • Recoveries: " + library.recoveryCount() + recoveryState, x + 20, top + 104, width - 40, selectedRecovery ? WbTheme.WARN : WbTheme.TEXT_MUTED);
        WbText.drawClipped(context, font, "Validation: " + (selectedPath == null ? "No selection" : library.validationLine(selectedPath)), x + 20, top + 120, width - 40, WbTheme.TEXT_DIM);
        if (selectedPath != null && selectedPath.equals(pendingDeletePath) && System.currentTimeMillis() - pendingDeleteMillis < 5000L) {
            WbText.drawClipped(context, font, "⚠ Delete armed: click Confirm Delete to remove selected archive.", x + 20, listBottom + 6, width - 40, WbTheme.ERROR);
        }
        int rowH = 20;
        int count = Math.min(Math.max(1, (listBottom - listTop) / rowH), scenes.size() - archiveListOffset);
        for (int i = 0; i < count; i++) {
            Path path = scenes.get(archiveListOffset + i);
            int rowY = listTop + i * rowH;
            boolean selected = path.equals(selectedPath);
            boolean recovery = library.isRecovery(path);
            WbSelectableList.drawRow(context, x + 18, rowY - 4, width - 36, 17, selected, i % 2 == 0);
            String name = (selected ? "◆ " : "• ") + (recovery ? "[RECOVERY] " : "[ARCHIVE] ") + path.getFileName();
            int metaW = width > 460 ? 220 : 0;
            WbText.drawClipped(context, font, name, x + 28, rowY, width - 56 - metaW, recovery ? WbTheme.WARN : WbTheme.TEXT_SOFT);
            if (metaW > 0) {
                WbText.drawClipped(context, font, library.validationLine(path), x + width - metaW - 18, rowY, metaW, WbTheme.TEXT_DIM);
            }
        }
        if (scenes.size() > count) {
            int from = archiveListOffset + 1;
            int to = archiveListOffset + count;
            WbText.drawClipped(context, font, "Showing " + from + "-" + to + " of " + scenes.size() + " • scroll to browse", x + 28, Math.min(listBottom - 2, listTop + count * rowH + 2), width - 56, WbTheme.TEXT_DIM);
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
    }
private void drawSidebar(GuiGraphicsExtractor context, int left, int top) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int sidebarWidth = WbLayout.sidebarWidth(panelWidth);
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
            WbText.drawClipped(context, font, "Status", left + 14, statusY, sidebarWidth - 22, WbTheme.TEXT_DIM);
            WbText.drawClipped(context, font, capture.isCapturing() ? "§aCapturing" : capture.isSaving() ? "§eSaving" : "§7Idle", left + 14, statusY + 16, sidebarWidth - 22, WbTheme.TEXT_SOFT);
            if (sidebarWidth > 96 && panelHeight > 390) {
                WbText.drawClipped(context, font, "Queue: " + capture.queuedChunks(), left + 14, statusY + 32, sidebarWidth - 22, capture.highQueuePressure() ? WbTheme.ERROR : WbTheme.TEXT_MUTED);
            }
        }
    }

private void drawSectionHeader(GuiGraphicsExtractor context, int x, int y, int width) {
        WbSectionHeader.draw(context, font, x, y, width, section.title, WbText.ellipsize(font, section.subtitle, Math.max(40, width - 8)));
    }

    private void drawCard(GuiGraphicsExtractor context, int x, int y, int width, int height, Component title, Component tooltip, int mouseX, int mouseY) {
        if (WbCard.draw(context, font, x, y, width, height, title, mouseX, mouseY)) {
            customTooltip = tooltip;
        }
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
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("No activity yet."), x, y, 0xFF8F86B8);
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
            hints.add("Large session detected - UI detail reduced");
            colors.add(WbTheme.WARN);
        }
        if (capture.isRecoverySaveRunning()) {
            hints.add("Recovery writing in safe mode");
            colors.add(WbTheme.INFO);
        }
        if (WorldBinder.config().f10MapLayerMode != net.worldbinder.config.WorldBinderConfig.MapLayerMode.BOTH) {
            hints.add("Map quality reduced for FPS");
            colors.add(WbTheme.WARN);
        }
        if (capture.highQueuePressure()) {
            hints.add("High queue pressure");
            colors.add(WbTheme.ERROR);
        }
        if (capture.multiplayerSafetyActive()) {
            hints.add("Server safety active");
            colors.add(WbTheme.INFO);
        }
        if (hints.isEmpty()) {
            hints.add("Everything looks stable");
            colors.add(WbTheme.OK);
            hints.add("F10 opens the live chunk map");
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
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.literal("§aSaved §eScanning §6Queued §dYou"), x, y, 0xFFBDB6D9);
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
        WbText.drawClipped(context, font, "§8" + label + ": §f" + value, x, y, max, color);
    }

    private void drawProgress(GuiGraphicsExtractor context, int x, int y, int width, double progress, String detail) {
        WbProgressBar.draw(context, font, x, y, width, progress, detail);
    }

    private String presetLine() {
        String preset = WorldBinder.config().performancePreset == null ? "CUSTOM" : WorldBinder.config().performancePreset.name();
        return preset + " • " + WorldBinder.config().effectiveBlocksPerTick() + " blocks/tick";
    }

    private String lastSessionLine() {
        List<Path> scenes = library.scenes();
        return scenes.isEmpty() ? "No archive yet" : shorten(scenes.get(0).getFileName().toString(), 32);
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}

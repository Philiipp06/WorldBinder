package net.worldbinder.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.hud.WorldBinderHud;
import net.worldbinder.hud.WorldBinderWidgetLayout;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbIntegerSlider;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbSounds;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.Lang;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Visual HUD layout editor: drag, resize and style every WorldBinder widget with snapping guides, anchor
 * presets, keyboard nudging, undo/redo, a clean preview mode and widget specific options.
 */
public final class WorldBinderWidgetEditorScreen extends Screen {
    private static final int[] PALETTE = {
            0xFF62C3EA, 0xFFA98BFF, 0xFF5FD39B, 0xFFFFB45E, 0xFFFF7FA8, 0xFFFF6464, 0xFFD5DEE8, 0xFF49D7BB
    };
    private static final int SNAP_DISTANCE = 5;
    private static final int HISTORY_LIMIT = 60;

    private enum Page { LAYOUT, STYLE, EXTRA }

    private static boolean showGrid = true;
    private static boolean snapping = true;

    private final Screen parent;
    private final WorldBinderConfig target;
    private WorldBinderConfig working;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private long lastCheckpointMillis;

    private WorldBinderWidgetLayout.Widget selected = WorldBinderWidgetLayout.Widget.NOTIFICATIONS;
    private Page page = Page.LAYOUT;
    private boolean panelCollapsed;
    private boolean previewMode;
    private boolean dragging;
    private boolean resizing;
    private boolean movingPanel;
    private boolean panelPositioned;
    private int panelLeft;
    private int panelTop;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeStartX;
    private int resizeStartY;
    private int resizeStartScale;
    private Integer guideX;
    private Integer guideY;
    private int contentTop;

    public WorldBinderWidgetEditorScreen(Screen parent, WorldBinderConfig target) {
        super(Component.translatable("worldbinder.widget_editor.title"));
        this.parent = parent;
        this.target = target;
        this.working = copy(target);
    }

    private static WorldBinderConfig copy(WorldBinderConfig source) {
        WorldBinderConfig copy = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(source), WorldBinderConfig.class);
        return copy == null ? new WorldBinderConfig() : copy;
    }

    // ------------------------------------------------------------------ history

    /** Saves an undo point; rapid successive edits (slider drags, nudges) are merged into one step. */
    private void checkpoint() {
        long now = System.currentTimeMillis();
        if (now - lastCheckpointMillis > 700L || undo.isEmpty()) {
            undo.push(WorldBinder.GSON.toJson(working));
            while (undo.size() > HISTORY_LIMIT) undo.removeLast();
            redo.clear();
        }
        lastCheckpointMillis = now;
    }

    private void undo() {
        if (undo.isEmpty()) {
            WbSounds.warning();
            return;
        }
        redo.push(WorldBinder.GSON.toJson(working));
        working = WorldBinder.GSON.fromJson(undo.pop(), WorldBinderConfig.class);
        lastCheckpointMillis = 0L;
        WbSounds.click();
        rebuildControls();
    }

    private void redo() {
        if (redo.isEmpty()) {
            WbSounds.warning();
            return;
        }
        undo.push(WorldBinder.GSON.toJson(working));
        working = WorldBinder.GSON.fromJson(redo.pop(), WorldBinderConfig.class);
        lastCheckpointMillis = 0L;
        WbSounds.click();
        rebuildControls();
    }

    // ------------------------------------------------------------------ controls

    @Override
    protected void init() {
        if (previewMode) {
            return;
        }
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int panelH = panelHeight();

        if (panelCollapsed) {
            addRenderableWidget(WbButton.iconOnly(WbButton.primary(WbButton.create(panelX + 4, panelY + 4, panelW - 8, panelH - 8,
                    Component.translatable("worldbinder.widget_editor.panel_title"),
                    Component.translatable("worldbinder.tooltip.widget_editor.expand"), button -> togglePanel())), WbIcon.HUD));
            return;
        }

        int innerX = panelX + 10;
        int innerW = panelW - 20;
        int gap = 4;

        addRenderableWidget(WbButton.iconOnly(WbButton.quiet(WbButton.create(panelX + panelW - 26, panelY + 6, 18, 16, "<",
                Component.translatable("worldbinder.tooltip.widget_editor.collapse"), button -> togglePanel())), WbIcon.MINUS));
        addRenderableWidget(WbButton.iconOnly(WbButton.quiet(WbButton.create(panelX + panelW - 48, panelY + 6, 18, 16,
                Lang.string("worldbinder.widget_editor.preview_mode"), Component.translatable("worldbinder.tooltip.widget_editor.preview_mode"),
                button -> togglePreview())), WbIcon.EYE));

        boolean compact = compact();
        int y = panelY + 28;
        int widgetCols = compact ? 4 : 2;
        int tabW = (innerW - gap * (widgetCols - 1)) / widgetCols;
        int index = 0;
        for (WorldBinderWidgetLayout.Widget widget : WorldBinderWidgetLayout.Widget.values()) {
            Button tab = WbButton.create(innerX + (index % widgetCols) * (tabW + gap), y + (index / widgetCols) * 22, tabW, 20,
                    Component.translatable(widgetKey(widget)), Component.translatable("worldbinder.tooltip.widget_editor.select"),
                    button -> select(widget));
            WbButton.tab(tab, opaque(WorldBinderWidgetLayout.accentColor(working, widget)), selected == widget);
            addRenderableWidget(compact ? WbButton.iconOnly(tab, widgetIcon(widget)) : WbButton.icon(tab, widgetIcon(widget)));
            index++;
        }
        y += compact ? 24 : 46;
        int pageW = (innerW - gap * 2) / 3;
        Page[] pages = Page.values();
        WbIcon[] pageIcons = {WbIcon.TARGET, WbIcon.PALETTE, WbIcon.SETTINGS};
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            Button pageButton = WbButton.create(innerX + i * (pageW + gap), y, pageW, 18,
                    Component.translatable("worldbinder.widget_editor.page." + target.name().toLowerCase(Locale.ROOT)),
                    Component.translatable("worldbinder.widget_editor.page." + target.name().toLowerCase(Locale.ROOT) + ".tooltip"),
                    button -> {
                        page = target;
                        rebuildControls();
                    });
            addRenderableWidget(WbButton.icon(WbButton.tab(pageButton, WbTheme.ACCENT, page == target), pageIcons[i]));
        }
        y += 24;
        contentTop = y;
        int contentBottom = panelY + panelH - (compact ? 30 : 56);
        switch (page) {
            case LAYOUT -> initLayoutPage(innerX, y, innerW, contentBottom);
            case STYLE -> initStylePage(innerX, y, innerW, contentBottom);
            case EXTRA -> initExtraPage(innerX, y, innerW, contentBottom);
        }

        int toolsY = compact ? panelY + panelH - 26 : panelY + panelH - 50;
        Button undoButton = WbButton.iconOnly(WbButton.quiet(WbButton.create(innerX, toolsY, 22, 18, Lang.string("worldbinder.widget_editor.undo"),
                Component.translatable("worldbinder.tooltip.widget_editor.undo"), button -> undo())), WbIcon.BACK);
        undoButton.active = !undo.isEmpty();
        WbTooltips.hint(undoButton, Component.literal("Ctrl + Z"));
        addRenderableWidget(undoButton);
        Button redoButton = WbButton.iconOnly(WbButton.quiet(WbButton.create(innerX + 26, toolsY, 22, 18, Lang.string("worldbinder.widget_editor.redo"),
                Component.translatable("worldbinder.tooltip.widget_editor.redo"), button -> redo())), WbIcon.CHEVRON_RIGHT);
        redoButton.active = !redo.isEmpty();
        WbTooltips.hint(redoButton, Component.literal("Ctrl + Y"));
        addRenderableWidget(redoButton);
        Button reset = WbButton.quiet(WbButton.create(innerX + 52, toolsY, compact ? 22 : innerW - 52, 18,
                Component.translatable("worldbinder.widget_editor.reset_widget"), Component.translatable("worldbinder.tooltip.widget_editor.reset"),
                button -> {
                    checkpoint();
                    WorldBinderWidgetLayout.reset(working, selected);
                    rebuildControls();
                }));
        addRenderableWidget(compact ? WbButton.iconOnly(reset, WbIcon.RESET) : WbButton.icon(reset, WbIcon.RESET));

        int buttonY = compact ? toolsY - 1 : panelY + panelH - 27;
        int actionsX = compact ? innerX + 80 : innerX;
        int actionW = ((compact ? innerW - 80 : innerW) - 6) / 2;
        Button apply = WbButton.icon(WbButton.primary(WbButton.create(actionsX, buttonY, actionW, 20,
                Component.translatable("worldbinder.widget_editor.apply"), Component.translatable("worldbinder.tooltip.widget_editor.apply"),
                button -> applyAndClose())), WbIcon.CHECK);
        WbTooltips.hint(apply, Component.literal("Enter"));
        addRenderableWidget(apply);
        Button cancel = WbButton.icon(WbButton.quiet(WbButton.create(actionsX + actionW + 6, buttonY, actionW, 20,
                Component.translatable("worldbinder.config.cancel"), Component.translatable("worldbinder.tooltip.config.back"),
                button -> onClose())), WbIcon.CLOSE);
        WbTooltips.hint(cancel, Component.literal("Esc"));
        addRenderableWidget(cancel);
    }

    private void initLayoutPage(int x, int y, int w, int bottom) {
        Button enabled = WbButton.create(x, y, w, 20,
                Component.translatable("worldbinder.widget_editor.enabled_value",
                        Component.translatable(WorldBinderWidgetLayout.enabled(working, selected) ? "worldbinder.common.on" : "worldbinder.common.off")),
                Component.translatable("worldbinder.tooltip.widget_editor.enabled"), button -> {
                    checkpoint();
                    WorldBinderWidgetLayout.setEnabled(working, selected, !WorldBinderWidgetLayout.enabled(working, selected));
                    rebuildControls();
                });
        WbTooltips.hint(enabled, Component.literal("Del"));
        addRenderableWidget(WbButton.toggle(enabled, accent(), WorldBinderWidgetLayout.enabled(working, selected)));
        y += 24;
        int maxScale = selected == WorldBinderWidgetLayout.Widget.NOTIFICATIONS ? 160 : 180;
        addRenderableWidget(WbTooltips.register(new WbIntegerSlider(font, x, y, w, 22, 50, maxScale, WorldBinderWidgetLayout.scale(working, selected),
                accent(), value -> {
                    checkpoint();
                    WorldBinderWidgetLayout.setScale(working, selected, value);
                }, value -> Component.translatable("worldbinder.widget_editor.scale_value", value)),
                Component.translatable("worldbinder.tooltip.widget_editor.scale")));
        y += 30;
        // 3x3 anchor grid
        int cellW = 26;
        int cellH = 15;
        int[] percents = {0, 50, 100};
        String[] names = {"top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right"};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int px = percents[col];
                int py = percents[row];
                boolean active = WorldBinderWidgetLayout.xPercent(working, selected) == px && WorldBinderWidgetLayout.yPercent(working, selected) == py;
                Button anchor = WbButton.create(x + col * (cellW + 3), y + row * (cellH + 3), cellW, cellH, Component.literal(""),
                        Component.translatable("worldbinder.widget_editor.anchor." + names[row * 3 + col]), button -> {
                            checkpoint();
                            setPercent(px, py);
                            rebuildControls();
                        });
                WbButton.style(anchor, WbButton.Style.DEFAULT, accent(), active);
                if (active) WbButton.iconOnly(anchor, WbIcon.TARGET);
                addRenderableWidget(anchor);
            }
        }
        int sideX = x + 3 * (cellW + 3) + 6;
        int sideW = w - (sideX - x);
        addRenderableWidget(WbButton.toggle(WbButton.create(sideX, y, sideW, 16, Component.translatable("worldbinder.widget_editor.snap"),
                Component.translatable("worldbinder.tooltip.widget_editor.snap"), button -> {
                    snapping = !snapping;
                    WbButton.selected(button, snapping);
                }), WbTheme.ACCENT, snapping));
        addRenderableWidget(WbButton.toggle(WbButton.create(sideX, y + 19, sideW, 16, Component.translatable("worldbinder.widget_editor.grid"),
                Component.translatable("worldbinder.tooltip.widget_editor.grid"), button -> {
                    showGrid = !showGrid;
                    WbButton.selected(button, showGrid);
                }), WbTheme.ACCENT, showGrid));
        WbTooltips.hint(addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(sideX, y + 38, sideW, 16,
                Component.translatable("worldbinder.widget_editor.preview_mode"), Component.translatable("worldbinder.tooltip.widget_editor.preview_mode"),
                button -> togglePreview())), WbIcon.EYE)), Component.literal("H"));
    }

    private void initStylePage(int x, int y, int w, int bottom) {
        addRenderableWidget(WbTooltips.register(new ColorActionButton(x, y, w, 22,
                Component.translatable("worldbinder.widget_editor.accent_value", hexRgb(WorldBinderWidgetLayout.accentColor(working, selected))),
                false, button -> openColorPicker(false)), Component.translatable("worldbinder.tooltip.widget_editor.color")));
        y += 26;
        addRenderableWidget(WbTooltips.register(new ColorActionButton(x, y, w, 22,
                Component.translatable("worldbinder.widget_editor.background_value", hexArgb(WorldBinderWidgetLayout.backgroundColor(working, selected))),
                true, button -> openColorPicker(true)), Component.translatable("worldbinder.tooltip.widget_editor.color")));
        y += 28;
        int opacity = Math.round((WorldBinderWidgetLayout.backgroundColor(working, selected) >>> 24) * 100.0F / 255.0F);
        addRenderableWidget(WbTooltips.register(new WbIntegerSlider(font, x, y, w, 22, 6, 100, opacity, accent(), value -> {
            checkpoint();
            int background = WorldBinderWidgetLayout.backgroundColor(working, selected);
            int alpha = Math.max(16, Math.min(255, Math.round(value * 255.0F / 100.0F)));
            WorldBinderWidgetLayout.setBackgroundColor(working, selected, (alpha << 24) | (background & 0x00FFFFFF));
        }, value -> Component.translatable("worldbinder.widget_editor.opacity_value", value)),
                Component.translatable("worldbinder.tooltip.widget_editor.opacity")));
        y += 30;
        int swatchGap = 3;
        int swatchW = (w - swatchGap * (PALETTE.length - 1)) / PALETTE.length;
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            addRenderableWidget(WbTooltips.register(new ColorSwatchButton(x + i * (swatchW + swatchGap), y, swatchW, 16, color,
                    button -> {
                        checkpoint();
                        WorldBinderWidgetLayout.setAccentColor(working, selected, color);
                        rebuildControls();
                    }), Component.literal(hexRgb(color))));
        }
        y += 22;
        addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(x, y, w, 18, Component.translatable("worldbinder.widget_editor.use_theme"),
                Component.translatable("worldbinder.tooltip.widget_editor.use_theme"), button -> {
                    checkpoint();
                    WorldBinderWidgetLayout.setAccentColor(working, selected, WbTheme.ACCENT);
                    rebuildControls();
                })), WbIcon.STAR));
        y += 22;
        if (y + 18 <= bottom) {
            addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(x, y, w, 18, Component.translatable("worldbinder.widget_editor.apply_all"),
                    Component.translatable("worldbinder.tooltip.widget_editor.apply_all"), button -> {
                        checkpoint();
                        int accent = WorldBinderWidgetLayout.accentColor(working, selected);
                        int background = WorldBinderWidgetLayout.backgroundColor(working, selected);
                        for (WorldBinderWidgetLayout.Widget widget : WorldBinderWidgetLayout.Widget.values()) {
                            WorldBinderWidgetLayout.setAccentColor(working, widget, accent);
                            WorldBinderWidgetLayout.setBackgroundColor(working, widget, background);
                        }
                        rebuildControls();
                    })), WbIcon.COPY));
        }
    }

    private void initExtraPage(int x, int y, int w, int bottom) {
        WorldBinderConfig c = working;
        switch (selected) {
            case RADAR -> {
                addRenderableWidget(WbTooltips.register(new WbIntegerSlider(font, x, y, w, 22, 1, 15, Math.max(1, (c.chunkRadarSize - 1) / 2), accent(), value -> {
                    checkpoint();
                    c.chunkRadarSize = value * 2 + 1;
                }, value -> Component.translatable("worldbinder.widget_editor.radar_size", value * 2 + 1)),
                        Component.translatable("worldbinder.tooltip.config.radar_size")));
                y += 26;
                addRenderableWidget(WbTooltips.register(new WbIntegerSlider(font, x, y, w, 22, 2, 16, c.chunkRadarCellSize, accent(), value -> {
                    checkpoint();
                    c.chunkRadarCellSize = value;
                }, value -> Component.translatable("worldbinder.widget_editor.radar_zoom", value)),
                        Component.translatable("worldbinder.tooltip.config.radar_cell")));
                y += 26;
                y = extraToggle(x, y, w, "worldbinder.widget_editor.radar_rotate", c.radarRotate, v -> c.radarRotate = v);
                y = extraToggle(x, y, w, "worldbinder.widget_editor.radar_entities", c.radarShowEntities, v -> c.radarShowEntities = v);
                y = extraToggle(x, y, w, "worldbinder.widget_editor.radar_coords", c.radarShowCoordinates, v -> c.radarShowCoordinates = v);
                y = extraToggle(x, y, w, "worldbinder.widget_editor.radar_always", c.radarShowOutsideCapture, v -> c.radarShowOutsideCapture = v);
                if (y + 18 <= bottom) {
                    WorldBinderConfig.MapLayerMode mode = c.radarLayerMode == null ? WorldBinderConfig.MapLayerMode.BOTH : c.radarLayerMode;
                    addRenderableWidget(WbButton.icon(WbButton.dropdown(WbButton.create(x, y, w, 18,
                            Component.translatable("worldbinder.widget_editor.radar_layers", layerLabel(mode)),
                            Component.translatable("worldbinder.tooltip.config.map_mode"), button -> {
                                checkpoint();
                                WorldBinderConfig.MapLayerMode[] modes = WorldBinderConfig.MapLayerMode.values();
                                c.radarLayerMode = modes[(mode.ordinal() + 1) % modes.length];
                                rebuildControls();
                            }), WbTheme.INFO, false), WbIcon.LAYERS));
                }
            }
            case MOVEMENT -> {
                WorldBinderConfig.MovementHudStyle style = c.effectiveMovementHudStyle();
                addRenderableWidget(WbButton.icon(WbButton.dropdown(WbButton.create(x, y, w, 20,
                        Component.translatable("worldbinder.config.movement.hud_style",
                                Component.translatable("worldbinder.config.movement.hud_style." + style.name().toLowerCase(Locale.ROOT))),
                        Component.translatable("worldbinder.tooltip.config.movement.hud_style"), button -> {
                            checkpoint();
                            WorldBinderConfig.MovementHudStyle[] styles = WorldBinderConfig.MovementHudStyle.values();
                            c.movementHudStyle = styles[(style.ordinal() + 1) % styles.length];
                            rebuildControls();
                        }), WbTheme.INFO, false), WbIcon.LAYERS));
                y += 24;
                extraToggle(x, y, w, "worldbinder.config.movement.show_values", c.movementHudShowValues, v -> c.movementHudShowValues = v);
            }
            case NOTIFICATIONS -> {
                addRenderableWidget(WbTooltips.register(new WbIntegerSlider(font, x, y, w, 22, 40, 250, c.effectiveNotificationDurationPercent(), accent(), value -> {
                    checkpoint();
                    c.notificationDurationPercent = value;
                }, value -> Component.translatable("worldbinder.widget_editor.duration", value)),
                        Component.translatable("worldbinder.tooltip.config.notification_duration")));
                y += 26;
                extraToggle(x, y, w, "worldbinder.config.display.notification_sounds", c.notificationSounds, v -> c.notificationSounds = v);
            }
            case STATUS -> {
                // Text-only hint is drawn by the panel.
            }
        }
    }

    private int extraToggle(int x, int y, int w, String key, boolean value, java.util.function.Consumer<Boolean> setter) {
        Button button = WbButton.create(x, y, w, 18, Component.translatable(key), Component.translatable(key + ".tooltip"), b -> {
            checkpoint();
            boolean next = !WbButton.isSelected(b);
            setter.accept(next);
            WbButton.selected(b, next);
        });
        addRenderableWidget(WbButton.toggle(button, accent(), value));
        return y + 21;
    }

    private static Component layerLabel(WorldBinderConfig.MapLayerMode mode) {
        return Component.translatable(switch (mode) {
            case BOTH -> "worldbinder.config.map_mode.both";
            case CHUNKS_ONLY -> "worldbinder.config.map_mode.chunks";
            case MAP_ONLY -> "worldbinder.config.map_mode.map";
        });
    }

    private void togglePanel() {
        panelCollapsed = !panelCollapsed;
        rebuildControls();
    }

    private void togglePreview() {
        previewMode = !previewMode;
        WbSounds.toggle(previewMode);
        rebuildControls();
    }

    private void openColorPicker(boolean background) {
        int color = background ? WorldBinderWidgetLayout.backgroundColor(working, selected) : WorldBinderWidgetLayout.accentColor(working, selected);
        Component pickerTitle = Component.translatable(background ? "worldbinder.color_picker.background_title" : "worldbinder.color_picker.accent_title");
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderColorPickerScreen(this, pickerTitle, color, background, value -> {
            lastCheckpointMillis = 0L;
            checkpoint();
            if (background) {
                WorldBinderWidgetLayout.setBackgroundColor(working, selected, value);
            } else {
                WorldBinderWidgetLayout.setAccentColor(working, selected, value);
            }
        }));
    }

    private void select(WorldBinderWidgetLayout.Widget widget) {
        if (selected != widget) {
            selected = widget;
            rebuildControls();
        }
    }

    private void rebuildControls() {
        setFocused(null);
        clearWidgets();
        init();
    }

    private void setPercent(int px, int py) {
        WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
        int x = WorldBinderWidgetLayout.positionX(width, bounds.width(), px);
        int y = WorldBinderWidgetLayout.positionY(height, bounds.height(), py);
        WorldBinderWidgetLayout.setPosition(working, selected, width, height, bounds.width(), bounds.height(), x, y);
    }

    private void moveSelectedBy(int dx, int dy) {
        checkpoint();
        WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
        WorldBinderWidgetLayout.setPosition(working, selected, width, height, bounds.width(), bounds.height(), bounds.x() + dx, bounds.y() + dy);
    }

    private int accent() {
        return opaque(WorldBinderWidgetLayout.accentColor(working, selected));
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (previewMode) {
            togglePreview();
            return true;
        }
        if (insidePanel(event.x(), event.y())) {
            if (!panelCollapsed && event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.y() < panelY() + 24 && event.x() < panelX() + panelWidth() - 52) {
                movingPanel = true;
                dragOffsetX = (int) Math.round(event.x()) - panelX();
                dragOffsetY = (int) Math.round(event.y()) - panelY();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            WorldBinderWidgetLayout.Widget hit = widgetAt(event.x(), event.y());
            if (hit != null) {
                boolean changedSelection = selected != hit;
                selected = hit;
                WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
                boolean handle = event.x() >= bounds.x() + bounds.width() - 10 && event.y() >= bounds.y() + bounds.height() - 10;
                lastCheckpointMillis = 0L;
                checkpoint();
                if (handle) {
                    resizing = true;
                    resizeStartX = (int) Math.round(event.x());
                    resizeStartY = (int) Math.round(event.y());
                    resizeStartScale = WorldBinderWidgetLayout.scale(working, selected);
                } else {
                    dragging = true;
                    dragOffsetX = (int) Math.round(event.x()) - bounds.x();
                    dragOffsetY = (int) Math.round(event.y()) - bounds.y();
                }
                if (changedSelection) {
                    WbSounds.tab();
                    rebuildControls();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && movingPanel) {
            panelLeft = clamp((int) Math.round(event.x()) - dragOffsetX, 4, width - panelWidth() - 4);
            panelTop = clamp((int) Math.round(event.y()) - dragOffsetY, 4, height - panelHeight() - 4);
            rebuildControls();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && resizing) {
            int deltaX = (int) Math.round(event.x()) - resizeStartX;
            int deltaY = (int) Math.round(event.y()) - resizeStartY;
            int delta = Math.round((deltaX + deltaY) / 2.0F);
            int nextScale = resizeStartScale + Math.round(delta * 100.0F / 220.0F);
            if (snapping && !event.hasShiftDown()) {
                nextScale = Math.round(nextScale / 5.0F) * 5;
            }
            WorldBinderWidgetLayout.setScale(working, selected, nextScale);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
            int x = (int) Math.round(event.x()) - dragOffsetX;
            int y = (int) Math.round(event.y()) - dragOffsetY;
            guideX = null;
            guideY = null;
            if (snapping && !event.hasShiftDown()) {
                int[] snappedX = snap(x, bounds.width(), width, true);
                int[] snappedY = snap(y, bounds.height(), height, false);
                x = snappedX[0];
                y = snappedY[0];
                guideX = snappedX[1] == Integer.MIN_VALUE ? null : snappedX[1];
                guideY = snappedY[1] == Integer.MIN_VALUE ? null : snappedY[1];
            }
            WorldBinderWidgetLayout.setPosition(working, selected, width, height, bounds.width(), bounds.height(), x, y);
            return true;
        }
        return super.mouseDragged(event, offsetX, offsetY);
    }

    /** Snaps a widget edge or center to the screen margins/center and to other widgets. Returns {pos, guide}. */
    private int[] snap(int pos, int size, int screenSize, boolean horizontal) {
        int best = pos;
        int guide = Integer.MIN_VALUE;
        int bestDistance = SNAP_DISTANCE + 1;
        int margin = WorldBinderWidgetLayout.SCREEN_MARGIN;
        int[][] candidates = {
                {margin, margin}, {screenSize - margin - size, screenSize - margin}, {(screenSize - size) / 2, screenSize / 2}
        };
        for (int[] candidate : candidates) {
            int distance = Math.abs(pos - candidate[0]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate[0];
                guide = candidate[1];
            }
        }
        for (WorldBinderWidgetLayout.Widget other : WorldBinderWidgetLayout.Widget.values()) {
            if (other == selected) continue;
            WorldBinderHud.WidgetBounds b = WorldBinderHud.previewBounds(minecraft, working, other);
            int start = horizontal ? b.x() : b.y();
            int end = start + (horizontal ? b.width() : b.height());
            int[][] edges = {{start, start}, {end - size, end}, {end + 4, end}, {start - size - 4, start}};
            for (int[] edge : edges) {
                int distance = Math.abs(pos - edge[0]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = edge[0];
                    guide = edge[1];
                }
            }
        }
        return new int[] {best, guide};
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (movingPanel) {
            movingPanel = false;
            return true;
        }
        if (dragging || resizing) {
            dragging = false;
            resizing = false;
            guideX = null;
            guideY = null;
            rebuildControls();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (previewMode && (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_H)) {
            togglePreview();
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_Z) {
            if (event.hasShiftDown()) redo(); else undo();
            return true;
        }
        if (event.hasControlDown() && key == InputConstants.KEY_Y) {
            redo();
            return true;
        }
        if (getFocused() == null || !(getFocused() instanceof net.minecraft.client.gui.components.AbstractSliderButton)) {
            int step = event.hasShiftDown() ? 10 : 1;
            if (key == InputConstants.KEY_LEFT) { moveSelectedBy(-step, 0); return true; }
            if (key == InputConstants.KEY_RIGHT) { moveSelectedBy(step, 0); return true; }
            if (key == InputConstants.KEY_UP) { moveSelectedBy(0, -step); return true; }
            if (key == InputConstants.KEY_DOWN) { moveSelectedBy(0, step); return true; }
        }
        if (key == InputConstants.KEY_H) {
            togglePreview();
            return true;
        }
        if (key == InputConstants.KEY_DELETE) {
            checkpoint();
            WorldBinderWidgetLayout.setEnabled(working, selected, !WorldBinderWidgetLayout.enabled(working, selected));
            rebuildControls();
            return true;
        }
        if (key == InputConstants.KEY_RETURN && !(getFocused() instanceof Button)) {
            applyAndClose();
            return true;
        }
        if (key == InputConstants.KEY_TAB && event.hasControlDown()) {
            WorldBinderWidgetLayout.Widget[] widgets = WorldBinderWidgetLayout.Widget.values();
            select(widgets[Math.floorMod(selected.ordinal() + (event.hasShiftDown() ? -1 : 1), widgets.length)]);
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            WbTooltips.noteKeyboardNavigation();
        }
        return super.keyPressed(event);
    }

    private WorldBinderWidgetLayout.Widget widgetAt(double mouseX, double mouseY) {
        WorldBinderWidgetLayout.Widget[] widgets = WorldBinderWidgetLayout.Widget.values();
        if (WorldBinderHud.previewBounds(minecraft, working, selected).contains(mouseX, mouseY)) {
            return selected;
        }
        for (int i = widgets.length - 1; i >= 0; i--) {
            WorldBinderWidgetLayout.Widget widget = widgets[i];
            if (widget != selected && WorldBinderHud.previewBounds(minecraft, working, widget).contains(mouseX, mouseY)) {
                return widget;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, previewMode ? 0x30000000 : 0x8C0A0E14);
        if (!previewMode && showGrid) {
            drawCanvasGrid(context);
        }
        for (WorldBinderWidgetLayout.Widget widget : WorldBinderWidgetLayout.Widget.values()) {
            if (widget != selected) {
                drawWidget(context, widget, mouseX, mouseY);
            }
        }
        drawWidget(context, selected, mouseX, mouseY);
        if (previewMode) {
            String hint = Lang.string("worldbinder.widget_editor.preview_hint");
            int w = font.width(hint) + 24;
            WbChrome.rounded(context, (width - w) / 2, 6, w, 16, 0xC0101720);
            WbIcon.EYE.draw(context, (width - w) / 2 + 6, 10, 1, WbTheme.ACCENT);
            context.text(font, hint, (width - w) / 2 + 17, 10, WbTheme.TEXT_SOFT, false);
            return;
        }
        drawGuides(context);
        drawPanel(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
        WbTooltips.showHovered(this, context, font, mouseX, mouseY, mouseX, mouseY);
    }

    private void drawCanvasGrid(GuiGraphicsExtractor context) {
        for (int x = 0; x < width; x += 10) {
            context.fill(x, 0, x + 1, height, x % 50 == 0 ? 0x1CFFFFFF : 0x0AFFFFFF);
        }
        for (int y = 0; y < height; y += 10) {
            context.fill(0, y, width, y + 1, y % 50 == 0 ? 0x1CFFFFFF : 0x0AFFFFFF);
        }
        int center = WbTheme.withAlpha(WbTheme.ACCENT, 0x40);
        for (int y = 0; y < height; y += 6) context.fill(width / 2, y, width / 2 + 1, y + 3, center);
        for (int x = 0; x < width; x += 6) context.fill(x, height / 2, x + 3, height / 2 + 1, center);
        int margin = WorldBinderWidgetLayout.SCREEN_MARGIN;
        WbChrome.roundedOutline(context, margin, margin, width - margin * 2, height - margin * 2, 0x22FFFFFF);
    }

    private void drawGuides(GuiGraphicsExtractor context) {
        int color = WbTheme.withAlpha(WbTheme.ACCENT, 0xD0);
        if (guideX != null) {
            for (int y = 0; y < height; y += 4) context.fill(guideX, y, guideX + 1, y + 2, color);
        }
        if (guideY != null) {
            for (int x = 0; x < width; x += 4) context.fill(x, guideY, x + 2, guideY + 1, color);
        }
    }

    private void drawWidget(GuiGraphicsExtractor context, WorldBinderWidgetLayout.Widget widget, int mouseX, int mouseY) {
        WorldBinderHud.drawWidgetPreview(context, minecraft, working, widget);
        if (previewMode) {
            return;
        }
        WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, widget);
        boolean active = selected == widget;
        boolean hovered = bounds.contains(mouseX, mouseY) && !insidePanel(mouseX, mouseY);
        int accent = opaque(WorldBinderWidgetLayout.accentColor(working, widget));
        if (!WorldBinderWidgetLayout.enabled(working, widget)) {
            WbChrome.rounded(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xA0060A10);
            String off = Lang.string("worldbinder.widget_editor.hidden");
            context.text(font, off, bounds.x() + (bounds.width() - font.width(off)) / 2, bounds.y() + bounds.height() / 2 - 4, WbTheme.TEXT_DIM, true);
        }
        if (active) {
            WbChrome.glow(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), accent, 1.0F);
            WbChrome.roundedOutline(context, bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2, accent);
        } else {
            int dash = hovered ? 0xC0FFFFFF : 0x50FFFFFF;
            for (int x = bounds.x(); x < bounds.x() + bounds.width(); x += 6) {
                context.fill(x, bounds.y() - 1, Math.min(x + 3, bounds.x() + bounds.width()), bounds.y(), dash);
                context.fill(x, bounds.y() + bounds.height(), Math.min(x + 3, bounds.x() + bounds.width()), bounds.y() + bounds.height() + 1, dash);
            }
            for (int y = bounds.y(); y < bounds.y() + bounds.height(); y += 6) {
                context.fill(bounds.x() - 1, y, bounds.x(), Math.min(y + 3, bounds.y() + bounds.height()), dash);
                context.fill(bounds.x() + bounds.width(), y, bounds.x() + bounds.width() + 1, Math.min(y + 3, bounds.y() + bounds.height()), dash);
            }
        }
        String label = Lang.string(widgetKey(widget));
        String state = WorldBinderWidgetLayout.enabled(working, widget) ? WorldBinderWidgetLayout.scale(working, widget) + "%" : Lang.string("worldbinder.common.off");
        int labelW = font.width(label) + font.width(state) + 26;
        int labelY = bounds.y() > 14 ? bounds.y() - 14 : bounds.y() + bounds.height() + 3;
        WbChrome.rounded(context, bounds.x(), labelY, labelW, 12, active ? WbMotion.mix(0xF0141B24, accent, 0.25F) : 0xD0141B24);
        widgetIcon(widget).draw(context, bounds.x() + 4, labelY + 3, 1, active ? accent : WbTheme.TEXT_DIM);
        context.text(font, label, bounds.x() + 14, labelY + 2, active ? WbTheme.TEXT : WbTheme.TEXT_MUTED, false);
        context.text(font, state, bounds.x() + 18 + font.width(label), labelY + 2, active ? accent : WbTheme.TEXT_DIM, false);
        if (active) {
            int handleX = bounds.x() + bounds.width() - 8;
            int handleY = bounds.y() + bounds.height() - 8;
            WbChrome.rounded(context, handleX - 1, handleY - 1, 10, 10, 0xFF05080C);
            WbChrome.rounded(context, handleX, handleY, 8, 8, accent);
            context.fill(handleX + 2, handleY + 5, handleX + 6, handleY + 6, 0xAA05080C);
            context.fill(handleX + 5, handleY + 2, handleX + 6, handleY + 6, 0xAA05080C);
            if (dragging || resizing) {
                String info = bounds.width() + "×" + bounds.height() + "  •  X " + WorldBinderWidgetLayout.xPercent(working, widget)
                        + "%  Y " + WorldBinderWidgetLayout.yPercent(working, widget) + "%";
                int infoW = font.width(info) + 10;
                int infoY = bounds.y() + bounds.height() + 4 + (labelY > bounds.y() ? 14 : 0);
                WbChrome.rounded(context, bounds.x(), infoY, infoW, 12, 0xE0101720);
                context.text(font, info, bounds.x() + 5, infoY + 2, WbTheme.TEXT_SOFT, false);
            }
        }
    }

    private void drawPanel(GuiGraphicsExtractor context) {
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int panelH = panelHeight();
        WbChrome.drawPanel(context, panelX, panelY, panelW, panelH);
        if (panelCollapsed) {
            return;
        }
        int innerX = panelX + 10;
        int innerW = panelW - 20;
        WbIcon.HUD.draw(context, innerX, panelY + 11, 1, WbTheme.ACCENT);
        context.text(font, Component.translatable("worldbinder.widget_editor.panel_title").withStyle(ChatFormatting.BOLD), innerX + 12, panelY + 10, WbTheme.TEXT, true);
        for (int i = 0; i < 3; i++) {
            context.fill(panelX + panelW / 2 - 8 + i * 6, panelY + 3, panelX + panelW / 2 - 6 + i * 6, panelY + 4, 0x50FFFFFF);
        }
        if (page == Page.LAYOUT) {
            WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
            int infoY = contentTop + 24 + 30 + 54;
            int limit = panelY + panelH - (compact() ? 30 : 56);
            if (infoY + 22 < limit) {
                WbChrome.rounded(context, innerX, infoY, innerW, 22, 0x30000000);
                WbText.drawClipped(context, font, Lang.string("worldbinder.widget_editor.position_line",
                        WorldBinderWidgetLayout.xPercent(working, selected), WorldBinderWidgetLayout.yPercent(working, selected),
                        bounds.width(), bounds.height()), innerX + 6, infoY + 7, innerW - 12, WbTheme.TEXT_MUTED);
            }
            int hintY = infoY + 28;
            if (hintY + 20 < limit) {
                WbText.drawWrapped(context, font, Lang.string("worldbinder.widget_editor.keys_hint"), innerX, hintY, innerW, WbTheme.TEXT_DIM, 3);
            }
        } else if (page == Page.EXTRA && selected == WorldBinderWidgetLayout.Widget.STATUS) {
            WbText.drawWrapped(context, font, Lang.string("worldbinder.widget_editor.status_extra"), innerX, contentTop + 4, innerW, WbTheme.TEXT_DIM, 4);
        }
        WbChrome.drawDivider(context, innerX, panelY + panelH - (compact() ? 31 : 55), innerW);
    }

    // ------------------------------------------------------------------ apply / layout

    private void applyAndClose() {
        copyWidgetSettings(working, target);
        WbSounds.success();
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }

    private static void copyWidgetSettings(WorldBinderConfig source, WorldBinderConfig target) {
        target.showBossbarOverlay = source.showBossbarOverlay;
        target.showChunkRadar = source.showChunkRadar;
        target.messageMode = source.messageMode;
        target.bossbarScalePercent = source.bossbarScalePercent;
        target.chunkRadarScalePercent = source.chunkRadarScalePercent;
        target.notificationScalePercent = source.notificationScalePercent;
        target.bossbarWidgetXPercent = source.bossbarWidgetXPercent;
        target.bossbarWidgetYPercent = source.bossbarWidgetYPercent;
        target.chunkRadarWidgetXPercent = source.chunkRadarWidgetXPercent;
        target.chunkRadarWidgetYPercent = source.chunkRadarWidgetYPercent;
        target.notificationWidgetXPercent = source.notificationWidgetXPercent;
        target.notificationWidgetYPercent = source.notificationWidgetYPercent;
        target.bossbarAccentColor = source.bossbarAccentColor;
        target.bossbarBackgroundColor = source.bossbarBackgroundColor;
        target.chunkRadarAccentColor = source.chunkRadarAccentColor;
        target.chunkRadarBackgroundColor = source.chunkRadarBackgroundColor;
        target.notificationAccentColor = source.notificationAccentColor;
        target.notificationBackgroundColor = source.notificationBackgroundColor;
        target.showMovementBadges = source.showMovementBadges;
        target.movementBadgeScalePercent = source.movementBadgeScalePercent;
        target.movementBadgeXPercent = source.movementBadgeXPercent;
        target.movementBadgeYPercent = source.movementBadgeYPercent;
        target.movementBadgeAccentColor = source.movementBadgeAccentColor;
        target.movementBadgeBackgroundColor = source.movementBadgeBackgroundColor;
        target.movementHudStyle = source.movementHudStyle;
        target.movementHudShowValues = source.movementHudShowValues;
        target.chunkRadarSize = source.chunkRadarSize;
        target.chunkRadarCellSize = source.chunkRadarCellSize;
        target.radarLayerMode = source.radarLayerMode;
        target.radarRotate = source.radarRotate;
        target.radarShowEntities = source.radarShowEntities;
        target.radarShowCoordinates = source.radarShowCoordinates;
        target.radarShowOutsideCapture = source.radarShowOutsideCapture;
        target.notificationDurationPercent = source.notificationDurationPercent;
        target.notificationSounds = source.notificationSounds;
    }

    private int panelX() {
        ensurePanelPosition();
        return clamp(panelLeft, 4, width - panelWidth() - 4);
    }

    private int panelY() {
        ensurePanelPosition();
        return clamp(panelTop, 4, height - panelHeight() - 4);
    }

    private void ensurePanelPosition() {
        if (!panelPositioned) {
            panelLeft = WorldBinderWidgetLayout.xPercent(working, selected) < 45 ? Math.max(4, width - panelWidth() - 8) : 8;
            panelTop = 8;
            panelPositioned = true;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int panelWidth() {
        return panelCollapsed ? 30 : Math.max(200, Math.min(236, width - 16));
    }

    private int panelHeight() {
        return panelCollapsed ? 30 : Math.max(200, Math.min(340, height - 8));
    }

    private boolean compact() {
        return panelHeight() < 300;
    }

    private boolean insidePanel(double mouseX, double mouseY) {
        return !previewMode && mouseX >= panelX() && mouseX < panelX() + panelWidth() && mouseY >= panelY() && mouseY < panelY() + panelHeight();
    }

    private static String widgetKey(WorldBinderWidgetLayout.Widget widget) {
        return switch (widget) {
            case STATUS -> "worldbinder.widget_editor.widget.status";
            case RADAR -> "worldbinder.widget_editor.widget.radar";
            case NOTIFICATIONS -> "worldbinder.widget_editor.widget.notifications";
            case MOVEMENT -> "worldbinder.widget_editor.widget.movement";
        };
    }

    private static WbIcon widgetIcon(WorldBinderWidgetLayout.Widget widget) {
        return switch (widget) {
            case STATUS -> WbIcon.CAPTURE;
            case RADAR -> WbIcon.MAP;
            case NOTIFICATIONS -> WbIcon.INFO;
            case MOVEMENT -> WbIcon.MOVEMENT;
        };
    }

    private static String hexRgb(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }

    private static String hexArgb(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (previewMode) {
            togglePreview();
            return;
        }
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }

    private final class ColorActionButton extends Button {
        private final boolean background;

        private ColorActionButton(int x, int y, int width, int height, Component label, boolean background, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.background = background;
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            WbSounds.click(soundManager);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int color = background ? WorldBinderWidgetLayout.backgroundColor(working, selected) : WorldBinderWidgetLayout.accentColor(working, selected);
            boolean hover = isHoveredOrFocused();
            WbChrome.rounded(context, x, y + 1, width, height, 0x55000000);
            WbChrome.rounded(context, x, y, width, height, hover ? WbMotion.mix(WbTheme.panelBorder(), accent(), 0.5F) : WbTheme.panelBorder());
            WbChrome.roundedGradient(context, x + 1, y + 1, width - 2, height - 2, hover ? WbTheme.BUTTON_HOVER : WbTheme.BUTTON_TOP, WbTheme.BUTTON);
            drawCheckerboard(context, x + 4, y + 4, 22, height - 8);
            WbChrome.rounded(context, x + 4, y + 4, 22, height - 8, color);
            WbChrome.roundedOutline(context, x + 4, y + 4, 22, height - 8, 0x60FFFFFF);
            WbText.drawClipped(context, font, getMessage(), x + 32, y + (height - 8) / 2, width - 46, WbTheme.TEXT_SOFT);
            WbIcon.PALETTE.draw(context, x + width - 12, y + (height - 7) / 2, 1, hover ? accent() : WbTheme.TEXT_DIM);
        }
    }

    private final class ColorSwatchButton extends Button {
        private final int color;

        private ColorSwatchButton(int x, int y, int width, int height, int color, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.color = color;
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            WbSounds.click(soundManager);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            boolean current = (WorldBinderWidgetLayout.accentColor(working, selected) & 0xFFFFFF) == (color & 0xFFFFFF);
            WbChrome.rounded(context, x, y, width, height, isHoveredOrFocused() || current ? 0xFFFFFFFF : 0xFF2A3542);
            WbChrome.rounded(context, x + 1, y + 1, width - 2, height - 2, color);
            context.fill(x + 3, y + 2, x + width - 3, y + 3, 0x55FFFFFF);
            if (current) {
                WbIcon.CHECK.draw(context, x + (width - 7) / 2, y + (height - 7) / 2, 1, 0xFF05080C);
            }
        }
    }

    private static void drawCheckerboard(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int size = 4;
        for (int row = 0; row * size < height; row++) {
            for (int col = 0; col * size < width; col++) {
                int left = x + col * size;
                int top = y + row * size;
                context.fill(left, top, Math.min(x + width, left + size), Math.min(y + height, top + size),
                        ((row + col) & 1) == 0 ? 0xFFB8C0C9 : 0xFF707985);
            }
        }
    }
}

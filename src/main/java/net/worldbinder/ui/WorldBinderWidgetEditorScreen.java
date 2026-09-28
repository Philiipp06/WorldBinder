package net.worldbinder.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.hud.WorldBinderHud;
import net.worldbinder.hud.WorldBinderWidgetLayout;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.GuiText;

import java.util.Locale;

public final class WorldBinderWidgetEditorScreen extends Screen {
    private static final int[] ACCENT_PRESETS = {
            WbTheme.ACCENT,
            WbTheme.INFO,
            WbTheme.WARN,
            WbTheme.OK,
            WbTheme.ERROR,
            0xFFC39BEE
    };

    private final Screen parent;
    private final WorldBinderConfig target;
    private final WorldBinderConfig working;

    private WorldBinderWidgetLayout.Widget selected = WorldBinderWidgetLayout.Widget.NOTIFICATIONS;
    private boolean panelCollapsed;
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

    public WorldBinderWidgetEditorScreen(Screen parent, WorldBinderConfig target) {
        super(Component.translatable("worldbinder.widget_editor.title"));
        this.parent = parent;
        this.target = target;
        WorldBinderConfig copy = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(target), WorldBinderConfig.class);
        this.working = copy == null ? new WorldBinderConfig() : copy;
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int panelH = panelHeight();

        if (panelCollapsed) {
            addRenderableWidget(WbButton.primary(WbButton.create(
                    panelX + 5,
                    panelY + 5,
                    panelW - 10,
                    panelH - 10,
                    ">",
                    Component.translatable("worldbinder.tooltip.widget_editor.expand"),
                    button -> togglePanel()
            )));
            return;
        }

        boolean shortPanel = panelH < 286;
        int innerX = panelX + 12;
        int innerW = panelW - 24;
        int gap = 4;

        addRenderableWidget(WbButton.quiet(WbButton.create(
                panelX + panelW - 42,
                panelY + 7,
                34,
                18,
                "<",
                Component.translatable("worldbinder.tooltip.widget_editor.collapse"),
                button -> togglePanel()
        )));

        int tabsY = panelY + (shortPanel ? 29 : 32);
        int tabW = Math.max(70, (innerW - gap) / 2);
        int index = 0;
        for (WorldBinderWidgetLayout.Widget widget : WorldBinderWidgetLayout.Widget.values()) {
            Button tab = WbButton.create(
                    innerX + (index % 2) * (tabW + gap),
                    tabsY + (index / 2) * 23,
                    tabW,
                    20,
                    Component.translatable(widgetKey(widget)),
                    Component.translatable("worldbinder.tooltip.widget_editor.select"),
                    button -> select(widget)
            );
            addRenderableWidget(WbButton.tab(tab, WorldBinderWidgetLayout.accentColor(working, widget), selected == widget));
            index++;
        }

        int enabledY = panelY + (shortPanel ? 73 : 82);
        Button enabled = WbButton.create(
                innerX,
                enabledY,
                innerW,
                20,
                Component.translatable(
                        "worldbinder.widget_editor.enabled_value",
                        Component.translatable(WorldBinderWidgetLayout.enabled(working, selected) ? "worldbinder.common.on" : "worldbinder.common.off")
                ),
                Component.translatable("worldbinder.tooltip.widget_editor.enabled"),
                button -> {
                    WorldBinderWidgetLayout.setEnabled(working, selected, !WorldBinderWidgetLayout.enabled(working, selected));
                    rebuildControls();
                }
        );
        addRenderableWidget(WbButton.toggle(enabled, WorldBinderWidgetLayout.accentColor(working, selected), WorldBinderWidgetLayout.enabled(working, selected)));

        int scaleY = panelY + (shortPanel ? 97 : 108);
        addRenderableWidget(WbTooltips.register(
                new WidgetSlider(innerX, scaleY, innerW, 20, SliderType.SCALE, sliderInitialValue(SliderType.SCALE)),
                Component.translatable("worldbinder.tooltip.widget_editor.scale")));
        int opacityY = panelY + (shortPanel ? 121 : 134);
        addRenderableWidget(WbTooltips.register(
                new WidgetSlider(innerX, opacityY, innerW, 20, SliderType.OPACITY, sliderInitialValue(SliderType.OPACITY)),
                Component.translatable("worldbinder.tooltip.widget_editor.opacity")));

        int accentY = panelY + (shortPanel ? 145 : 162);
        addRenderableWidget(WbTooltips.register(
                new ColorActionButton(
                        innerX,
                        accentY,
                        innerW,
                        21,
                        Component.translatable("worldbinder.widget_editor.accent_value", hexRgb(WorldBinderWidgetLayout.accentColor(working, selected))),
                        false,
                        button -> openColorPicker(false)
                ),
                Component.translatable("worldbinder.tooltip.widget_editor.color")
        ));

        int backgroundY = panelY + (shortPanel ? 170 : 188);
        addRenderableWidget(WbTooltips.register(
                new ColorActionButton(
                        innerX,
                        backgroundY,
                        innerW,
                        21,
                        Component.translatable("worldbinder.widget_editor.background_value", hexArgb(WorldBinderWidgetLayout.backgroundColor(working, selected))),
                        true,
                        button -> openColorPicker(true)
                ),
                Component.translatable("worldbinder.tooltip.widget_editor.color")
        ));

        if (!shortPanel) {
            int paletteY = panelY + 228;
            int swatchW = Math.max(18, (innerW - gap * (ACCENT_PRESETS.length - 1)) / ACCENT_PRESETS.length);
            for (int i = 0; i < ACCENT_PRESETS.length; i++) {
                int color = ACCENT_PRESETS[i];
                addRenderableWidget(WbTooltips.register(
                        new ColorSwatchButton(
                                innerX + i * (swatchW + gap),
                                paletteY,
                                swatchW,
                                20,
                                color,
                                button -> {
                                    WorldBinderWidgetLayout.setAccentColor(working, selected, color);
                                    rebuildControls();
                                }
                        ),
                        Component.literal(hexRgb(color))
                ));
            }
        }

        if (!shortPanel || panelH >= 260) {
            int resetY = panelY + (shortPanel ? 202 : 256);
            addRenderableWidget(WbButton.quiet(WbButton.create(
                    innerX,
                    resetY,
                    innerW,
                    20,
                    Component.translatable("worldbinder.widget_editor.reset_widget"),
                    Component.translatable("worldbinder.tooltip.widget_editor.reset"),
                    button -> {
                        WorldBinderWidgetLayout.reset(working, selected);
                        rebuildControls();
                    }
            )));
        }

        int buttonY = panelY + panelH - 27;
        int actionW = Math.max(68, (innerW - 6) / 2);
        addRenderableWidget(WbButton.primary(WbButton.create(
                innerX,
                buttonY,
                actionW,
                20,
                Component.translatable("worldbinder.widget_editor.apply"),
                Component.translatable("worldbinder.tooltip.widget_editor.apply"),
                button -> applyAndClose()
        )));
        addRenderableWidget(WbButton.quiet(WbButton.create(
                innerX + actionW + 6,
                buttonY,
                actionW,
                20,
                Component.translatable("worldbinder.config.cancel"),
                Component.translatable("worldbinder.tooltip.config.back"),
                button -> onClose()
        )));
    }

    private void togglePanel() {
        panelCollapsed = !panelCollapsed;
        rebuildControls();
    }

    private void openColorPicker(boolean background) {
        int color = background
                ? WorldBinderWidgetLayout.backgroundColor(working, selected)
                : WorldBinderWidgetLayout.accentColor(working, selected);
        Component pickerTitle = Component.translatable(background
                ? "worldbinder.color_picker.background_title"
                : "worldbinder.color_picker.accent_title");
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderColorPickerScreen(
                this,
                pickerTitle,
                color,
                background,
                value -> {
                    if (background) {
                        WorldBinderWidgetLayout.setBackgroundColor(working, selected, value);
                    } else {
                        WorldBinderWidgetLayout.setAccentColor(working, selected, value);
                    }
                }
        ));
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (insidePanel(event.x(), event.y())) {
            if (!panelCollapsed && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
                    && event.y() < panelY() + 28 && event.x() < panelX() + panelWidth() - 46) {
                movingPanel = true;
                dragOffsetX = (int) Math.round(event.x()) - panelX();
                dragOffsetY = (int) Math.round(event.y()) - panelY();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            WorldBinderWidgetLayout.Widget hit = widgetAt(event.x(), event.y());
            if (hit != null) {
                boolean changedSelection = selected != hit;
                selected = hit;
                WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
                boolean handle = event.x() >= bounds.x() + bounds.width() - 10
                        && event.y() >= bounds.y() + bounds.height() - 10;
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
                    rebuildControls();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && movingPanel) {
            panelLeft = clamp((int) Math.round(event.x()) - dragOffsetX, 8, width - panelWidth() - 8);
            panelTop = clamp((int) Math.round(event.y()) - dragOffsetY, 8, height - panelHeight() - 8);
            rebuildControls();
            return true;
        }
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && resizing) {
            int deltaX = (int) Math.round(event.x()) - resizeStartX;
            int deltaY = (int) Math.round(event.y()) - resizeStartY;
            int delta = Math.round((deltaX + deltaY) / 2.0F);
            int nextScale = resizeStartScale + Math.round(delta * 100.0F / 220.0F);
            WorldBinderWidgetLayout.setScale(working, selected, nextScale);
            return true;
        }
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, selected);
            WorldBinderWidgetLayout.setPosition(
                    working,
                    selected,
                    width,
                    height,
                    bounds.width(),
                    bounds.height(),
                    (int) Math.round(event.x()) - dragOffsetX,
                    (int) Math.round(event.y()) - dragOffsetY
            );
            return true;
        }
        return super.mouseDragged(event, offsetX, offsetY);
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
            rebuildControls();
            return true;
        }
        return super.mouseReleased(event);
    }

    private WorldBinderWidgetLayout.Widget widgetAt(double mouseX, double mouseY) {
        WorldBinderWidgetLayout.Widget[] widgets = WorldBinderWidgetLayout.Widget.values();
        WorldBinderHud.WidgetBounds selectedBounds = WorldBinderHud.previewBounds(minecraft, working, selected);
        if (selectedBounds.contains(mouseX, mouseY)) {
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB00D1515);
        drawCanvasGrid(context);

        for (WorldBinderWidgetLayout.Widget widget : WorldBinderWidgetLayout.Widget.values()) {
            if (widget != selected) {
                drawWidget(context, widget);
            }
        }
        drawWidget(context, selected);
        drawPanel(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
        WbTooltips.showHovered(this, context, font, mouseX, mouseY, mouseX, mouseY);
    }

    private void drawCanvasGrid(GuiGraphicsExtractor context) {
        for (int x = 0; x < width; x += 20) {
            context.fill(x, 0, x + 1, height, x % 100 == 0 ? 0x34547164 : 0x20394A40);
        }
        for (int y = 0; y < height; y += 20) {
            context.fill(0, y, width, y + 1, y % 100 == 0 ? 0x34547164 : 0x20394A40);
        }
        context.fill(width / 2, 0, width / 2 + 1, height, 0x556A8A76);
        context.fill(0, height / 2, width, height / 2 + 1, 0x556A8A76);
    }

    private void drawWidget(GuiGraphicsExtractor context, WorldBinderWidgetLayout.Widget widget) {
        WorldBinderHud.drawWidgetPreview(context, minecraft, working, widget);
        WorldBinderHud.WidgetBounds bounds = WorldBinderHud.previewBounds(minecraft, working, widget);
        boolean active = selected == widget;
        int accent = active ? WorldBinderWidgetLayout.accentColor(working, widget) : WbTheme.PANEL_BORDER;
        if (!WorldBinderWidgetLayout.enabled(working, widget)) {
            context.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0x88060A10);
        }
        drawOutline(context, bounds, accent, active ? 2 : 1);
        int labelWidth = Math.min(bounds.width(), Math.max(70, font.width(Component.translatable(widgetKey(widget))) + 44));
        int labelY = Math.max(1, bounds.y() - 13);
        context.fill(bounds.x(), labelY, bounds.x() + labelWidth, labelY + 12, active ? WbTheme.PANEL : WbTheme.CARD_ALT);
        context.fill(bounds.x(), labelY, bounds.x() + 2, labelY + 12, accent);
        String state = WorldBinderWidgetLayout.enabled(working, widget)
                ? WorldBinderWidgetLayout.scale(working, widget) + "%"
                : Component.translatable("worldbinder.common.off").getString();
        WbText.drawClipped(
                context,
                font,
                Component.translatable(widgetKey(widget)).getString() + "  " + state,
                bounds.x() + 6,
                labelY + 2,
                labelWidth - 10,
                active ? WbTheme.TEXT : WbTheme.TEXT_DIM
        );
        if (active) {
            int handleX = bounds.x() + bounds.width() - 8;
            int handleY = bounds.y() + bounds.height() - 8;
            context.fill(handleX, handleY, handleX + 8, handleY + 8, WbTheme.FIELD);
            context.fill(handleX + 2, handleY + 2, handleX + 7, handleY + 7, accent);
        }
    }

    private void drawOutline(GuiGraphicsExtractor context, WorldBinderHud.WidgetBounds bounds, int color, int thickness) {
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        int bottom = y + bounds.height();
        context.fill(x, y, right, y + thickness, color);
        context.fill(x, bottom - thickness, right, bottom, color);
        context.fill(x, y, x + thickness, bottom, color);
        context.fill(right - thickness, y, right, bottom, color);
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

        int innerX = panelX + 12;
        int innerW = panelW - 24;
        context.fill(panelX + 4, panelY + 3, panelX + panelW - 4, panelY + 5, WbTheme.ACCENT);
        GuiText.drawTextWithShadow(
                context,
                font,
                Component.translatable("worldbinder.widget_editor.panel_title"),
                innerX,
                panelY + 12,
                WbTheme.TEXT
        );
        if (panelH >= 286) {
            WbText.drawClipped(
                    context,
                    font,
                    Component.translatable("worldbinder.widget_editor.palette"),
                    innerX,
                    panelY + 214,
                    innerW,
                    WbTheme.TEXT_DIM
            );
        }
        WbChrome.drawDivider(context, innerX, panelY + panelH - 34, innerW);
    }

    private void applyAndClose() {
        copyWidgetSettings(working, target);
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
    }

    private int panelX() {
        ensurePanelPosition();
        return clamp(panelLeft, 8, width - panelWidth() - 8);
    }

    private int panelY() {
        ensurePanelPosition();
        return clamp(panelTop, 8, height - panelHeight() - 8);
    }

    private void ensurePanelPosition() {
        if (!panelPositioned) {
            panelLeft = WorldBinderWidgetLayout.xPercent(working, selected) < 45
                    ? Math.max(8, width - panelWidth() - 8) : 8;
            panelTop = 8;
            panelPositioned = true;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int panelWidth() {
        if (panelCollapsed) {
            return 44;
        }
        return Math.max(190, Math.min(200, width - 16));
    }

    private int panelHeight() {
        if (panelCollapsed) {
            return 32;
        }
        return Math.max(220, Math.min(314, height - 16));
    }

    private boolean insidePanel(double mouseX, double mouseY) {
        return mouseX >= panelX()
                && mouseX < panelX() + panelWidth()
                && mouseY >= panelY()
                && mouseY < panelY() + panelHeight();
    }

    private String widgetKey(WorldBinderWidgetLayout.Widget widget) {
        return switch (widget) {
            case STATUS -> "worldbinder.widget_editor.widget.status";
            case RADAR -> "worldbinder.widget_editor.widget.radar";
            case NOTIFICATIONS -> "worldbinder.widget_editor.widget.notifications";
            case MOVEMENT -> "worldbinder.widget_editor.widget.movement";
        };
    }

    private static String hexRgb(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }

    private static String hexArgb(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }

    private double sliderInitialValue(SliderType type) {
        if (type == SliderType.OPACITY) {
            return (WorldBinderWidgetLayout.backgroundColor(working, selected) >>> 24) / 255.0D;
        }
        int min = 50;
        int max = selected == WorldBinderWidgetLayout.Widget.NOTIFICATIONS ? 160 : 180;
        return (WorldBinderWidgetLayout.scale(working, selected) - min) / (double) (max - min);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }

    private enum SliderType {
        SCALE,
        OPACITY
    }

    private final class WidgetSlider extends AbstractSliderButton {
        private final SliderType type;
        private float shownValue;
        private float hoverProgress;
        private long lastFrameNanos;

        private WidgetSlider(int x, int y, int width, int height, SliderType type, double initialValue) {
            super(x, y, width, height, Component.empty(), initialValue);
            this.type = type;
            shownValue = (float) initialValue;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (type == SliderType.OPACITY) {
                setMessage(Component.translatable("worldbinder.widget_editor.opacity_value", Math.round(value * 100.0D)));
            } else {
                int min = 50;
                int max = selected == WorldBinderWidgetLayout.Widget.NOTIFICATIONS ? 160 : 180;
                int scale = min + (int) Math.round(value * (max - min));
                setMessage(Component.translatable("worldbinder.widget_editor.scale_value", scale));
            }
        }

        @Override
        protected void applyValue() {
            if (type == SliderType.OPACITY) {
                int background = WorldBinderWidgetLayout.backgroundColor(working, selected);
                int alpha = Math.max(16, Math.min(255, (int) Math.round(value * 255.0D)));
                WorldBinderWidgetLayout.setBackgroundColor(working, selected, (alpha << 24) | (background & 0x00FFFFFF));
            } else {
                int min = 50;
                int max = selected == WorldBinderWidgetLayout.Widget.NOTIFICATIONS ? 160 : 180;
                WorldBinderWidgetLayout.setScale(working, selected, min + (int) Math.round(value * (max - min)));
            }
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int accent = WorldBinderWidgetLayout.accentColor(working, selected);
            long now = System.nanoTime();
            long elapsed = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
            lastFrameNanos = now;
            shownValue = WbMotion.approach(shownValue, (float) value, elapsed, 20.0F);
            hoverProgress = WbMotion.approach(hoverProgress, isHoveredOrFocused() ? 1.0F : 0.0F, elapsed, 16.0F);
            context.fill(x, y, x + width, y + height, WbTheme.PANEL_BORDER);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    WbMotion.mix(WbTheme.BUTTON, WbTheme.BUTTON_HOVER, hoverProgress));
            int trackX = x + 8;
            int trackY = y + height - 7;
            int trackWidth = width - 16;
            int filled = Math.round(trackWidth * shownValue);
            context.fill(trackX, trackY, trackX + trackWidth, trackY + 3, WbTheme.FIELD);
            if (filled > 0) context.fill(trackX, trackY, trackX + filled, trackY + 3, accent);
            int knobX = Math.max(trackX, Math.min(trackX + trackWidth - 6, trackX + filled - 3));
            context.fill(knobX - 1, trackY - 3, knobX + 7, trackY + 5, WbTheme.FIELD);
            context.fill(knobX, trackY - 2, knobX + 6, trackY + 4, accent);
            GuiText.drawCenteredTextWithShadow(context, font, getMessage(), x + width / 2, y + 2, WbTheme.TEXT_SOFT);
        }
    }

    private final class ColorActionButton extends Button {
        private final boolean background;

        private ColorActionButton(int x, int y, int width, int height, Component label, boolean background, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.background = background;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int color = background
                    ? WorldBinderWidgetLayout.backgroundColor(working, selected)
                    : WorldBinderWidgetLayout.accentColor(working, selected);
            context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x66000000);
            context.fill(x, y, x + width, y + height, isHoveredOrFocused() ? WbTheme.BUTTON_HOVER : WbTheme.BUTTON);
            drawCheckerboard(context, x + 4, y + 4, 24, height - 8);
            context.fill(x + 4, y + 4, x + 28, y + height - 4, color);
            context.fill(x + 4, y + 4, x + 28, y + 5, 0x88FFFFFF);
            WbText.drawClipped(context, font, getMessage(), x + 34, y + 6, width - 41, WbTheme.TEXT_SOFT);
            context.fill(x + width - 5, y + height / 2 - 2, x + width - 3, y + height / 2 + 2, WbTheme.TEXT_DIM);
        }
    }

    private static final class ColorSwatchButton extends Button {
        private final int color;

        private ColorSwatchButton(int x, int y, int width, int height, int color, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.color = color;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int border = isHoveredOrFocused() ? WbTheme.TEXT : WbTheme.PANEL_BORDER;
            context.fill(x, y, x + width, y + height, border);
            context.fill(x + 2, y + 2, x + width - 2, y + height - 2, color);
            context.fill(x + 3, y + 3, x + width - 3, y + 4, 0x66FFFFFF);
        }
    }

    private static void drawCheckerboard(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int size = 4;
        for (int row = 0; row * size < height; row++) {
            for (int col = 0; col * size < width; col++) {
                int left = x + col * size;
                int top = y + row * size;
                context.fill(
                        left,
                        top,
                        Math.min(x + width, left + size),
                        Math.min(y + height, top + size),
                        ((row + col) & 1) == 0 ? 0xFFB8C0C9 : 0xFF707985
                );
            }
        }
    }
}

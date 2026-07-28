package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.GuiText;

import java.util.Locale;
import java.util.function.IntConsumer;

public final class WorldBinderColorPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 270;

    private final Screen parent;
    private final Component pickerTitle;
    private final boolean allowAlpha;
    private final IntConsumer onApply;

    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;
    private EditBox hexField;
    private DragTarget dragTarget = DragTarget.NONE;
    private boolean updatingHex;

    public WorldBinderColorPickerScreen(
            Screen parent,
            Component pickerTitle,
            int initialColor,
            boolean allowAlpha,
            IntConsumer onApply
    ) {
        super(pickerTitle);
        this.parent = parent;
        this.pickerTitle = pickerTitle;
        this.allowAlpha = allowAlpha;
        this.onApply = onApply;
        setFromColor(initialColor);
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int sideX = sideX();
        int sideW = sideWidth();

        hexField = new EditBox(font, sideX + 7, panelY + 104, Math.max(36, sideW - 14), 12, Component.translatable("worldbinder.color_picker.hex"));
        hexField.setBordered(false);
        hexField.setMaxLength(9);
        hexField.setTextColor(WbTheme.TEXT);
        hexField.setTextShadow(false);
        hexField.setValue(hexValue());
        hexField.setResponder(this::handleHexInput);
        addRenderableWidget(hexField);

        int buttonY = panelY + panelHeight() - 29;
        int innerX = panelX + 14;
        int innerW = panelW - 28;
        int actionW = Math.max(72, (innerW - 7) / 2);
        addRenderableWidget(WbButton.primary(WbButton.create(
                innerX,
                buttonY,
                actionW,
                21,
                Component.translatable("worldbinder.color_picker.apply"),
                Component.translatable("worldbinder.tooltip.color_picker.apply"),
                button -> applyAndClose()
        )));
        addRenderableWidget(WbButton.quiet(WbButton.create(
                innerX + actionW + 7,
                buttonY,
                actionW,
                21,
                Component.translatable("worldbinder.config.cancel"),
                Component.translatable("worldbinder.tooltip.config.back"),
                button -> onClose()
        )));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (inside(event.x(), event.y(), svX(), svY(), svWidth(), svHeight())) {
                dragTarget = DragTarget.SATURATION_VALUE;
                updateFromPointer(event.x(), event.y());
                return true;
            }
            if (inside(event.x(), event.y(), hueX(), svY(), 14, svHeight())) {
                dragTarget = DragTarget.HUE;
                updateFromPointer(event.x(), event.y());
                return true;
            }
            if (allowAlpha && inside(event.x(), event.y(), sideX(), alphaY(), sideWidth(), 16)) {
                dragTarget = DragTarget.ALPHA;
                updateFromPointer(event.x(), event.y());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (event.button() == 0 && dragTarget != DragTarget.NONE) {
            updateFromPointer(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragTarget != DragTarget.NONE) {
            dragTarget = DragTarget.NONE;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateFromPointer(double mouseX, double mouseY) {
        if (dragTarget == DragTarget.SATURATION_VALUE) {
            saturation = clamp01((float) ((mouseX - svX()) / Math.max(1.0D, svWidth())));
            brightness = 1.0F - clamp01((float) ((mouseY - svY()) / Math.max(1.0D, svHeight())));
        } else if (dragTarget == DragTarget.HUE) {
            hue = clamp01((float) ((mouseY - svY()) / Math.max(1.0D, svHeight())));
        } else if (dragTarget == DragTarget.ALPHA) {
            alpha = clamp01((float) ((mouseX - sideX()) / Math.max(1.0D, sideWidth())));
        }
        updateHexField();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        WbChrome.drawBackdrop(context, width, height);
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int panelH = panelHeight();
        WbChrome.drawPanel(context, panelX, panelY, panelW, panelH);

        GuiText.drawTextWithShadow(context, font, pickerTitle, panelX + 14, panelY + 14, WbTheme.TEXT);
        drawSaturationValue(context);
        drawHue(context);
        drawSidePanel(context);
        WbChrome.drawDivider(context, panelX + 14, panelY + panelH - 36, panelW - 28);

        if (hexField != null) {
            WbChrome.drawField(
                    context,
                    sideX(),
                    panelY + 98,
                    sideWidth(),
                    25,
                    currentColor(),
                    hexField.isFocused(),
                    hexField.isMouseOver(mouseX, mouseY)
            );
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
        WbTooltips.showHovered(this, context, font, mouseX, mouseY, mouseX, mouseY);
    }

    private void drawSaturationValue(GuiGraphicsExtractor context) {
        int x = svX();
        int y = svY();
        int w = svWidth();
        int h = svHeight();
        int columns = Math.min(56, Math.max(16, w / 3));
        int rows = Math.min(42, Math.max(14, h / 3));
        for (int row = 0; row < rows; row++) {
            float value = 1.0F - row / (float) Math.max(1, rows - 1);
            int top = y + row * h / rows;
            int bottom = y + (row + 1) * h / rows;
            for (int col = 0; col < columns; col++) {
                float sat = col / (float) Math.max(1, columns - 1);
                int left = x + col * w / columns;
                int right = x + (col + 1) * w / columns;
                context.fill(left, top, right, bottom, 0xFF000000 | hsvToRgb(hue, sat, value));
            }
        }
        drawBorder(context, x, y, w, h, 0xFF50657A);
        int markerX = x + Math.round(saturation * (w - 1));
        int markerY = y + Math.round((1.0F - brightness) * (h - 1));
        drawMarker(context, markerX, markerY);
    }

    private void drawHue(GuiGraphicsExtractor context) {
        int x = hueX();
        int y = svY();
        int h = svHeight();
        int segments = 36;
        for (int i = 0; i < segments; i++) {
            int top = y + i * h / segments;
            int bottom = y + (i + 1) * h / segments;
            context.fill(x, top, x + 14, bottom, 0xFF000000 | hsvToRgb(i / (float) segments, 1.0F, 1.0F));
        }
        drawBorder(context, x, y, 14, h, 0xFF50657A);
        int markerY = y + Math.round(hue * (h - 1));
        context.fill(x - 2, markerY - 1, x + 16, markerY + 2, 0xFFFFFFFF);
        context.fill(x - 1, markerY, x + 15, markerY + 1, 0xFF07101A);
    }

    private void drawSidePanel(GuiGraphicsExtractor context) {
        int x = sideX();
        int y = panelY();
        int w = sideWidth();
        WbText.drawClipped(context, font, Component.translatable("worldbinder.color_picker.preview"), x, y + 35, w, WbTheme.TEXT_DIM);
        drawCheckerboard(context, x, y + 48, w, 34);
        context.fill(x, y + 48, x + w, y + 82, currentColor());
        drawBorder(context, x, y + 48, w, 34, 0xFF50657A);

        WbText.drawClipped(context, font, Component.translatable("worldbinder.color_picker.hex"), x, y + 87, w, WbTheme.TEXT_DIM);
        if (allowAlpha) {
            WbText.drawClipped(
                    context,
                    font,
                    Component.translatable("worldbinder.color_picker.opacity_value", Math.round(alpha * 100.0F)),
                    x,
                    y + 132,
                    w,
                    WbTheme.TEXT_DIM
            );
            drawAlpha(context, x, alphaY(), w, 16);
        } else {
            WbText.drawWrapped(
                    context,
                    font,
                    Component.translatable("worldbinder.color_picker.opaque_hint"),
                    x,
                    y + 136,
                    w,
                    WbTheme.TEXT_DIM,
                    2
            );
        }
    }

    private void drawAlpha(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        drawCheckerboard(context, x, y, w, h);
        int rgb = currentColor() & 0x00FFFFFF;
        int segments = Math.min(48, Math.max(16, w / 3));
        for (int i = 0; i < segments; i++) {
            int left = x + i * w / segments;
            int right = x + (i + 1) * w / segments;
            int segmentAlpha = Math.round(255.0F * i / Math.max(1, segments - 1));
            context.fill(left, y, right, y + h, (segmentAlpha << 24) | rgb);
        }
        drawBorder(context, x, y, w, h, 0xFF50657A);
        int markerX = x + Math.round(alpha * (w - 1));
        context.fill(markerX - 1, y - 2, markerX + 2, y + h + 2, 0xFFFFFFFF);
        context.fill(markerX, y - 1, markerX + 1, y + h + 1, 0xFF07101A);
    }

    private void handleHexInput(String value) {
        if (updatingHex) {
            return;
        }
        Integer parsed = parseHex(value);
        if (parsed != null) {
            setFromColor(parsed);
        }
    }

    private Integer parseHex(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.strip().replace("#", "");
        try {
            if (clean.length() == 6) {
                int rgb = Integer.parseUnsignedInt(clean, 16);
                int a = allowAlpha ? Math.round(alpha * 255.0F) : 0xFF;
                return (a << 24) | rgb;
            }
            if (allowAlpha && clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void setFromColor(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        float[] hsv = rgbToHsv(red, green, blue);
        hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
        alpha = allowAlpha ? (color >>> 24) / 255.0F : 1.0F;
    }

    private void updateHexField() {
        if (hexField == null) {
            return;
        }
        updatingHex = true;
        hexField.setValue(hexValue());
        updatingHex = false;
    }

    private String hexValue() {
        return allowAlpha
                ? String.format(Locale.ROOT, "#%08X", currentColor())
                : String.format(Locale.ROOT, "#%06X", currentColor() & 0x00FFFFFF);
    }

    private int currentColor() {
        int rgb = hsvToRgb(hue, saturation, brightness);
        int colorAlpha = allowAlpha ? Math.round(alpha * 255.0F) : 0xFF;
        return (colorAlpha << 24) | rgb;
    }

    private void applyAndClose() {
        onApply.accept(currentColor());
        minecraft.gui.setScreen(parent);
    }

    private int panelWidth() {
        return Math.max(250, Math.min(PANEL_WIDTH, width - 20));
    }

    private int panelHeight() {
        return Math.max(220, Math.min(PANEL_HEIGHT, height - 20));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return (height - panelHeight()) / 2;
    }

    private int svX() {
        return panelX() + 14;
    }

    private int svY() {
        return panelY() + 40;
    }

    private int svWidth() {
        return Math.max(104, panelWidth() - 194);
    }

    private int svHeight() {
        return Math.max(112, panelHeight() - 104);
    }

    private int hueX() {
        return svX() + svWidth() + 7;
    }

    private int sideX() {
        return hueX() + 21;
    }

    private int sideWidth() {
        return Math.max(82, panelX() + panelWidth() - 14 - sideX());
    }

    private int alphaY() {
        return panelY() + 147;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void drawMarker(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x - 4, y - 1, x + 5, y + 2, 0xFFFFFFFF);
        context.fill(x - 1, y - 4, x + 2, y + 5, 0xFFFFFFFF);
        context.fill(x - 3, y, x + 4, y + 1, 0xFF07101A);
        context.fill(x, y - 3, x + 1, y + 4, 0xFF07101A);
    }

    private static void drawCheckerboard(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int size = 5;
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

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));
        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }
        return (Math.round(red * 255.0F) << 16)
                | (Math.round(green * 255.0F) << 8)
                | Math.round(blue * 255.0F);
    }

    private static float[] rgbToHsv(int red, int green, int blue) {
        float r = red / 255.0F;
        float g = green / 255.0F;
        float b = blue / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6.0F;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0F;
        } else {
            hue = (r - g) / delta + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max == 0.0F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private enum DragTarget {
        NONE,
        SATURATION_VALUE,
        HUE,
        ALPHA
    }
}

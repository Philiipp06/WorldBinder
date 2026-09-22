package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class WbIntegerSlider extends AbstractSliderButton {
    private final Font font;
    private final int min;
    private final int max;
    private final int accent;
    private final IntConsumer valueConsumer;
    private final IntFunction<Component> messageFactory;
    private int currentValue;

    public WbIntegerSlider(Font font, int x, int y, int width, int height, int min, int max, int initialValue,
                           int accent, IntConsumer valueConsumer, IntFunction<Component> messageFactory) {
        super(x, y, width, height, Component.empty(), normalized(min, max, initialValue));
        this.font = font;
        this.min = min;
        this.max = Math.max(min, max);
        this.accent = accent;
        this.valueConsumer = valueConsumer;
        this.messageFactory = messageFactory;
        currentValue = clamp(initialValue);
        updateMessage();
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        boolean highlighted = isHoveredOrFocused();
        int activeAccent = active ? accent : WbTheme.TEXT_DISABLED;
        int trackX = x + 8;
        int trackWidth = Math.max(1, width - 16);
        int trackY = y + height - 6;
        int filled = (int) Math.round(trackWidth * value);

        context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x66000000);
        context.fill(x, y, x + width, y + height, active ? 0xAA43566B : 0x55374655);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, highlighted ? WbTheme.BUTTON_HOVER : WbTheme.BUTTON);
        context.fill(trackX, trackY, trackX + trackWidth, trackY + 2, 0xAA293746);
        context.fill(trackX, trackY, trackX + filled, trackY + 2, activeAccent);
        int knobX = Math.max(trackX, Math.min(trackX + trackWidth - 4, trackX + filled - 2));
        context.fill(knobX, trackY - 2, knobX + 4, trackY + 4, activeAccent);
        if (isFocused() && active) {
            context.fill(x, y, x + width, y + 1, WbTheme.FOCUS_RING);
            context.fill(x, y + height - 1, x + width, y + height, WbTheme.FOCUS_RING);
        }
        GuiText.drawCenteredTextWithShadow(context, font, getMessage(), x + width / 2, y + 3,
                active ? WbTheme.TEXT_SOFT : WbTheme.TEXT_DISABLED);
    }

    @Override
    protected void updateMessage() {
        setMessage(messageFactory.apply(currentValue));
    }

    @Override
    protected void applyValue() {
        int next = clamp((int) Math.round(min + value * (max - min)));
        if (next != currentValue) {
            currentValue = next;
            valueConsumer.accept(next);
            updateMessage();
        }
    }

    private int clamp(int candidate) {
        return Math.max(min, Math.min(max, candidate));
    }

    private static double normalized(int min, int max, int value) {
        int safeMax = Math.max(min, max);
        if (safeMax == min) {
            return 0.0D;
        }
        int clamped = Math.max(min, Math.min(safeMax, value));
        return (clamped - min) / (double) (safeMax - min);
    }
}

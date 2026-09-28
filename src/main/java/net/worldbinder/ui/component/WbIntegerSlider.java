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
    private float displayedValue;
    private float hoverProgress;
    private long lastFrameNanos;

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
        displayedValue = (float) value;
        updateMessage();
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        long now = System.nanoTime();
        long elapsed = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
        lastFrameNanos = now;
        hoverProgress = WbMotion.approach(hoverProgress, isHoveredOrFocused() ? 1.0F : 0.0F, elapsed, 16.0F);
        displayedValue = WbMotion.approach(displayedValue, (float) value, elapsed, 20.0F);
        int activeAccent = active ? accent : WbTheme.TEXT_DISABLED;
        int trackX = x + 8;
        int trackWidth = Math.max(1, width - 16);
        int trackY = y + height - 7;
        int filled = Math.round(trackWidth * displayedValue);

        context.fill(x, y, x + width, y + height, active ? WbTheme.panelBorder() : WbTheme.BUTTON_DISABLED);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                WbMotion.mix(WbTheme.BUTTON, WbTheme.BUTTON_HOVER, hoverProgress));
        context.fill(trackX, trackY, trackX + trackWidth, trackY + 3, WbTheme.FIELD);
        for (int tick = 0; tick <= 4; tick++) {
            int tickX = trackX + (trackWidth - 1) * tick / 4;
            context.fill(tickX, trackY - 2, tickX + 1, trackY, WbTheme.TEXT_DISABLED);
        }
        if (filled > 0) context.fill(trackX, trackY, trackX + filled, trackY + 3, activeAccent);
        int knobX = Math.max(trackX, Math.min(trackX + trackWidth - 6, trackX + filled - 3));
        context.fill(knobX - 1, trackY - 3, knobX + 7, trackY + 5, WbTheme.FIELD);
        context.fill(knobX, trackY - 2, knobX + 6, trackY + 4, activeAccent);
        if (isFocused() && active) {
            context.fill(x, y, x + width, y + 1, WbTheme.focusRing());
            context.fill(x, y + height - 1, x + width, y + height, WbTheme.focusRing());
        }
        GuiText.drawCenteredTextWithShadow(context, font, getMessage(), x + width / 2, y + 2,
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

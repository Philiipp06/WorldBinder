package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;
import com.mojang.blaze3d.platform.InputConstants;

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
    private float dragProgress;
    private boolean dragging;
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

    public int currentValue() {
        return currentValue;
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
        hoverProgress = WbMotion.approach(hoverProgress, isHoveredOrFocused() && active ? 1.0F : 0.0F, elapsed, 14.0F);
        dragProgress = WbMotion.approach(dragProgress, dragging ? 1.0F : 0.0F, elapsed, 16.0F);
        displayedValue = WbMotion.approach(displayedValue, (float) value, elapsed, 22.0F);
        int activeAccent = active ? accent : WbTheme.TEXT_DISABLED;

        WbChrome.rounded(context, x, y + 1, width, height, 0x44000000);
        WbChrome.rounded(context, x, y, width, height,
                active ? WbMotion.mix(WbTheme.panelBorder(), activeAccent, hoverProgress * 0.5F) : WbTheme.BUTTON_DISABLED);
        WbChrome.roundedGradient(context, x + 1, y + 1, width - 2, height - 2,
                WbMotion.mix(WbTheme.BUTTON_TOP, WbTheme.BUTTON_HOVER, hoverProgress * 0.6F), WbTheme.BUTTON);

        int trackX = x + 8;
        int trackWidth = Math.max(1, width - 16);
        int trackY = y + height - 7;
        int filled = Math.round(trackWidth * displayedValue);
        WbChrome.rounded(context, trackX, trackY, trackWidth, 4, WbTheme.FIELD);
        int steps = Math.min(10, Math.max(1, max - min));
        for (int tick = 0; tick <= steps; tick++) {
            int tickX = trackX + (trackWidth - 1) * tick / steps;
            context.fill(tickX, trackY - 2, tickX + 1, trackY - 1, WbTheme.withAlpha(WbTheme.TEXT_DISABLED, 0x88));
        }
        if (filled > 1) {
            WbChrome.hGradient(context, trackX + 1, trackY + 1, filled - 1, 2, WbTheme.withAlpha(activeAccent, 0x99), activeAccent);
        }
        int knobSize = 8 + Math.round(dragProgress * 2);
        int knobX = Math.max(trackX - 1, Math.min(trackX + trackWidth - knobSize + 1, trackX + filled - knobSize / 2));
        int knobY = trackY + 2 - knobSize / 2;
        if (hoverProgress > 0.01F) {
            WbChrome.rounded(context, knobX - 2, knobY - 2, knobSize + 4, knobSize + 4,
                    WbTheme.withAlpha(activeAccent, Math.round(0x38 * hoverProgress)));
        }
        WbChrome.rounded(context, knobX, knobY, knobSize, knobSize, 0xFF0A0E14);
        WbChrome.rounded(context, knobX + 1, knobY + 1, knobSize - 2, knobSize - 2, WbMotion.mix(activeAccent, 0xFFFFFFFF, 0.25F + hoverProgress * 0.3F));
        if (isFocused() && active) {
            WbChrome.glow(context, x, y, width, height, activeAccent, 1.0F);
        }
        GuiText.drawCenteredTextWithShadow(context, font, getMessage(), x + width / 2, y + 3,
                active ? WbMotion.mix(WbTheme.TEXT_SOFT, WbTheme.TEXT, hoverProgress) : WbTheme.TEXT_DISABLED);

        if (dragProgress > 0.05F) {
            String bubble = getMessage().getString();
            int colon = bubble.lastIndexOf(':');
            if (colon >= 0 && colon + 1 < bubble.length()) bubble = bubble.substring(colon + 1).strip();
            int bw = font.width(bubble) + 10;
            int bx = Math.max(x, Math.min(x + width - bw, knobX + knobSize / 2 - bw / 2));
            int by = y - 15 - Math.round((1.0F - dragProgress) * 4);
            context.nextStratum();
            WbChrome.rounded(context, bx, by, bw, 13, WbTheme.alpha(WbMotion.mix(0xF0121921, activeAccent, 0.2F), dragProgress));
            WbChrome.roundedOutline(context, bx, by, bw, 13, WbTheme.alpha(activeAccent, dragProgress));
            context.text(font, bubble, bx + 5, by + 3, WbTheme.alpha(WbTheme.TEXT, dragProgress), false);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        dragging = true;
        super.onClick(event, doubleClick);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = false;
        super.onRelease(event);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        WbSounds.click(soundManager);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT) {
            WbTooltips.noteKeyboardNavigation();
        }
        return super.keyPressed(event);
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
            WbSounds.sliderTick();
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

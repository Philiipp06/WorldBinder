package net.worldbinder.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

public final class WbButton {
    public record MotionState(float hover, float selection) {
    }

    public enum Style {
        DEFAULT,
        PRIMARY,
        QUIET,
        TAB,
        TOGGLE,
        DROPDOWN,
        DANGER
    }

    private WbButton() {
    }

    public static Button create(int x, int y, int width, int height, String label, Component tooltip, Button.OnPress action) {
        return create(x, y, width, height, Component.literal(label), tooltip, action);
    }

    public static Button create(int x, int y, int width, int height, Component label, Component tooltip, Button.OnPress action) {
        int safeWidth = Math.max(34, width);
        return WbTooltips.register(new StyledButton(x, y, safeWidth, Math.max(18, height), label, action), tooltip);
    }

    public static Button style(Button button, Style style, int accent, boolean selected) {
        if (button instanceof StyledButton styled) {
            styled.style = style == null ? Style.DEFAULT : style;
            styled.accent = accent;
            styled.selected = selected;
        }
        return button;
    }

    public static Button selected(Button button, boolean selected) {
        if (button instanceof StyledButton styled) {
            styled.selected = selected;
        }
        return button;
    }

    public static MotionState motionState(Button button) {
        return button instanceof StyledButton styled
                ? new MotionState(styled.hoverProgress, styled.selectionProgress) : null;
    }

    public static void restoreMotion(Button button, MotionState state) {
        if (button instanceof StyledButton styled && state != null) {
            styled.hoverProgress = state.hover();
            styled.selectionProgress = state.selection();
        }
    }

    public static Button primary(Button button) {
        return style(button, Style.PRIMARY, WbTheme.ACCENT, false);
    }

    public static Button quiet(Button button) {
        return style(button, Style.QUIET, WbTheme.INFO, false);
    }

    public static Button dropdown(Button button, int accent, boolean open) {
        return style(button, Style.DROPDOWN, accent, open);
    }

    public static Button tab(Button button, int accent, boolean selected) {
        return style(button, Style.TAB, accent, selected);
    }

    public static Button toggle(Button button, int accent, boolean enabled) {
        return style(button, Style.TOGGLE, accent, enabled);
    }

    public static String fitLabel(String label, int width) {
        if (label == null) {
            return "";
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft == null ? null : minecraft.font;
        if (font != null) {
            return WbText.ellipsize(font, label, Math.max(1, width - 16));
        }
        int maxChars = Math.max(3, (width - 16) / 6);
        if (label.length() <= maxChars || maxChars <= 3) {
            return label;
        }
        return label.substring(0, Math.max(1, maxChars - 3)).stripTrailing() + "...";
    }

    private static final class StyledButton extends Button {
        private Style style = Style.DEFAULT;
        private int accent = WbTheme.ACCENT;
        private boolean selected;
        private float hoverProgress;
        private float selectionProgress;
        private long lastFrameNanos;

        private StyledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            boolean highlighted = isHoveredOrFocused() && active;
            long now = System.nanoTime();
            long elapsed = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
            lastFrameNanos = now;
            hoverProgress = WbMotion.approach(hoverProgress, highlighted ? 1.0F : 0.0F, elapsed, 5.0F);
            selectionProgress = WbMotion.approach(selectionProgress, selected ? 1.0F : 0.0F, elapsed, 6.0F);
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int effectiveAccent = active ? accent : WbTheme.TEXT_DISABLED;
            int fill = buttonFill();

            context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x55000000);
            context.fill(x, y, x + width, y + height, active ? WbTheme.panelBorder() : WbTheme.BUTTON_DISABLED);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);

            if (style == Style.PRIMARY) {
                context.fill(x, y, x + 3, y + height, effectiveAccent);
                context.fill(x + 3, y + height - 2, x + width, y + height, WbMotion.mix(WbTheme.ACCENT_MUTED, effectiveAccent, hoverProgress));
            } else if (style == Style.DANGER) {
                context.fill(x, y, x + 3, y + height, WbTheme.ERROR);
            } else if (style == Style.TAB) {
                context.fill(x, y, x + 2 + Math.round(2 * selectionProgress), y + height,
                        WbMotion.mix(WbTheme.DIVIDER, effectiveAccent, Math.max(hoverProgress * 0.55F, selectionProgress)));
                int underline = Math.round(width * Math.max(selectionProgress, hoverProgress));
                if (underline > 0) context.fill(x, y + height - 2, x + underline, y + height, effectiveAccent);
            } else if (style == Style.TOGGLE) {
                drawToggleIndicator(context, x, y, width, height, effectiveAccent);
            } else if (style == Style.DROPDOWN && !hasTextualChevron()) {
                drawChevron(context, x + width - 12, y + height / 2 - 1, effectiveAccent);
            } else if (style != Style.QUIET && (selected || highlighted || hoverProgress > 0.01F)) {
                context.fill(x, y, x + 2, y + height,
                        WbMotion.mix(WbTheme.DIVIDER, effectiveAccent, Math.max(selectionProgress, hoverProgress)));
            }

            if (isFocused() && active) {
                int focus = WbTheme.focusRing();
                context.fill(x, y, x + width, y + 1, focus);
                context.fill(x, y + height - 1, x + width, y + height, focus);
                context.fill(x, y, x + 1, y + height, focus);
                context.fill(x + width - 1, y, x + width, y + height, focus);
            }

            Font font = Minecraft.getInstance().font;
            int rightReserved = style == Style.TOGGLE ? 24 : style == Style.DROPDOWN && !hasTextualChevron() ? 18 : 0;
            int horizontalPadding = 6;
            int labelWidth = Math.max(1, width - horizontalPadding * 2 - rightReserved);
            String label = WbText.ellipsize(font, getMessage().getString(), labelWidth);
            int textColor = active ? selected ? WbTheme.TEXT : WbTheme.TEXT_SOFT : WbTheme.TEXT_DISABLED;
            int labelCenter = x + horizontalPadding + labelWidth / 2;
            GuiText.drawCenteredTextWithShadow(context, font, label, labelCenter, y + (height - font.lineHeight) / 2, textColor);
        }

        private boolean hasTextualChevron() {
            String label = getMessage().getString().stripTrailing();
            return label.endsWith("▼") || label.endsWith("▾");
        }

        private int buttonFill() {
            if (!active) {
                return WbTheme.BUTTON_DISABLED;
            }
            if (style == Style.QUIET) {
                return WbMotion.mix(0x66252F39, WbTheme.BUTTON_HOVER, hoverProgress);
            }
            int base = WbMotion.mix(WbTheme.BUTTON, WbTheme.CARD_SELECTED, selectionProgress);
            return WbMotion.mix(base, selected ? WbTheme.BUTTON_PRESSED : WbTheme.BUTTON_HOVER, hoverProgress);
        }

        private void drawToggleIndicator(GuiGraphicsExtractor context, int x, int y, int width, int height, int effectiveAccent) {
            int pillW = 12;
            int pillH = 7;
            int px = x + width - pillW - 5;
            int py = y + (height - pillH) / 2;
            context.fill(px, py, px + pillW, py + pillH,
                    WbMotion.mix(WbTheme.FIELD, effectiveAccent, selectionProgress * 0.55F));
            int knobX = px + 2 + Math.round((pillW - 7) * selectionProgress);
            context.fill(knobX, py + 2, knobX + 3, py + 5,
                    WbMotion.mix(WbTheme.TEXT_DIM, WbTheme.TEXT, selectionProgress));
        }

        private void drawChevron(GuiGraphicsExtractor context, int x, int y, int color) {
            context.fill(x, y, x + 7, y + 1, color);
            context.fill(x + 1, y + 1, x + 6, y + 2, color);
            context.fill(x + 2, y + 2, x + 5, y + 3, color);
            if (selected) {
                context.fill(x + 2, y - 1, x + 5, y, color);
            }
        }
    }
}

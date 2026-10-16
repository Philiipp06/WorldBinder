package net.worldbinder.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
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
        int safeWidth = Math.max(20, width);
        return WbTooltips.register(new StyledButton(x, y, safeWidth, Math.max(16, height), label, action), tooltip);
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

    public static boolean isSelected(Button button) {
        return button instanceof StyledButton styled && styled.selected;
    }

    /** Adds a pixel icon in front of the label (hidden when icons are disabled in the Interface settings). */
    public static Button icon(Button button, WbIcon icon) {
        if (button instanceof StyledButton styled) {
            styled.icon = icon;
        }
        return button;
    }

    /** Icon-only square button; the label stays available for narration and tests. */
    public static Button iconOnly(Button button, WbIcon icon) {
        if (button instanceof StyledButton styled) {
            styled.icon = icon;
            styled.iconOnly = true;
        }
        return button;
    }

    /** Transparent hit area: no surface or label, only the toggle switch (if any) and focus glow. */
    public static Button ghost(Button button) {
        if (button instanceof StyledButton styled) {
            styled.ghost = true;
        }
        return button;
    }

    public static Button alignLeft(Button button) {
        if (button instanceof StyledButton styled) {
            styled.leftAligned = true;
        }
        return button;
    }

    /** Small right-aligned badge text (for counts or shortcut hints). */
    public static Button badge(Button button, String badge) {
        if (button instanceof StyledButton styled) {
            styled.badge = badge == null || badge.isBlank() ? null : badge;
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

    public static Button danger(Button button) {
        return style(button, Style.DANGER, WbTheme.ERROR, false);
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
        private boolean leftAligned;
        private boolean iconOnly;
        private boolean ghost;
        private WbIcon icon;
        private String badge;
        private float hoverProgress;
        private float selectionProgress;
        private float pressProgress = 1.0F;
        private long lastFrameNanos;
        private boolean wasHovered;

        private StyledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            pressProgress = 0.0F;
            super.onPress(input);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (style == Style.TOGGLE) {
                WbSounds.toggle(!selected);
            } else if (style == Style.TAB) {
                WbSounds.tab();
            } else if (style == Style.DROPDOWN) {
                WbSounds.dropdown(!selected);
            } else {
                WbSounds.click(soundManager);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            boolean highlighted = isHoveredOrFocused() && active;
            if (isHovered() && active && !wasHovered) {
                WbSounds.hover();
            }
            wasHovered = isHovered() && active;
            long now = System.nanoTime();
            long elapsed = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
            lastFrameNanos = now;
            hoverProgress = WbMotion.approach(hoverProgress, highlighted ? 1.0F : 0.0F, elapsed, 14.0F);
            selectionProgress = WbMotion.approach(selectionProgress, selected ? 1.0F : 0.0F, elapsed, 12.0F);
            pressProgress = WbMotion.approach(pressProgress, 1.0F, elapsed, 7.0F);
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int effectiveAccent = active ? (style == Style.DANGER ? WbTheme.ERROR : accent) : WbTheme.TEXT_DISABLED;
            if (ghost) {
                if (hoverProgress > 0.01F) {
                    WbChrome.rounded(context, x, y, width, height, WbTheme.withAlpha(effectiveAccent, Math.round(0x16 * hoverProgress)));
                }
                if (style == Style.TOGGLE) {
                    WbChrome.drawSwitch(context, x + width - 27, y + (height - 10) / 2, selectionProgress, effectiveAccent, active);
                }
                if (isFocused() && active) {
                    WbChrome.glow(context, x, y, width, height, effectiveAccent, 1.0F);
                }
                return;
            }

            drawSurface(context, x, y, width, height, effectiveAccent);

            if (pressProgress < 0.99F && !WbMotion.reduced()) {
                float ripple = WbMotion.easeOutCubic(pressProgress);
                int alpha = Math.round(0x50 * (1.0F - ripple));
                int inset = Math.round((1.0F - ripple) * Math.min(width, height) / 3.0F);
                WbChrome.rounded(context, x + inset, y + Math.min(inset, height / 3), width - inset * 2,
                        height - Math.min(inset, height / 3) * 2, WbTheme.withAlpha(effectiveAccent, alpha));
            }

            if (isFocused() && active) {
                WbChrome.glow(context, x, y, width, height, effectiveAccent, 1.0F);
            }

            drawLabel(context, x, y, width, height, effectiveAccent);
        }

        private void drawSurface(GuiGraphicsExtractor context, int x, int y, int width, int height, int effectiveAccent) {
            if (style == Style.TAB) {
                float emphasis = Math.max(selectionProgress, hoverProgress * 0.55F);
                if (emphasis > 0.01F) {
                    WbChrome.rounded(context, x, y, width, height,
                            WbTheme.alpha(WbMotion.mix(WbTheme.withAlpha(effectiveAccent, 0x30), WbTheme.withAlpha(effectiveAccent, 0x48), selectionProgress), emphasis));
                }
                int barHeight = Math.round((height - 6) * Math.max(selectionProgress, hoverProgress * 0.4F));
                if (barHeight > 0) {
                    int barY = y + (height - barHeight) / 2;
                    WbChrome.rounded(context, x, barY, 3, barHeight, effectiveAccent);
                }
                return;
            }
            if (style == Style.QUIET) {
                int fill = WbMotion.mix(0x40202A36, WbTheme.BUTTON_HOVER, hoverProgress);
                WbChrome.rounded(context, x, y, width, height, active ? fill : WbTheme.BUTTON_DISABLED);
                if (hoverProgress > 0.01F) {
                    WbChrome.roundedOutline(context, x, y, width, height, WbTheme.alpha(WbTheme.panelBorder(), hoverProgress));
                }
                return;
            }
            WbChrome.rounded(context, x, y + 1, width, height, 0x55000000);
            int border = !active ? WbTheme.BUTTON_DISABLED
                    : WbMotion.mix(WbTheme.panelBorder(), effectiveAccent, Math.max(hoverProgress * 0.6F, selectionProgress * 0.75F));
            WbChrome.rounded(context, x, y, width, height, border);
            int top;
            int bottom;
            if (!active) {
                top = bottom = WbTheme.BUTTON_DISABLED;
            } else if (style == Style.PRIMARY) {
                int base = WbMotion.mix(WbTheme.ACCENT_DEEP, effectiveAccent, 0.15F + hoverProgress * 0.25F);
                top = WbTheme.brighten(base, 0.08F);
                bottom = WbTheme.darken(base, 0.12F);
            } else if (style == Style.DANGER) {
                int base = WbMotion.mix(0xF0402026, 0xFA5A2A32, hoverProgress);
                top = WbTheme.brighten(base, 0.06F);
                bottom = base;
            } else {
                int base = WbMotion.mix(WbTheme.BUTTON, WbTheme.CARD_SELECTED, selectionProgress);
                base = WbMotion.mix(base, selected ? WbTheme.BUTTON_PRESSED : WbTheme.BUTTON_HOVER, hoverProgress);
                top = WbMotion.mix(base, 0xFFFFFFFF, 0.05F);
                bottom = base;
            }
            WbChrome.roundedGradient(context, x + 1, y + 1, width - 2, height - 2, top, bottom);
            context.fill(x + 3, y + 1, x + width - 3, y + 2, active ? 0x1AFFFFFF : 0x08FFFFFF);
            if (style == Style.PRIMARY && active) {
                context.fill(x + 3, y + height - 2, x + width - 3, y + height - 1,
                        WbTheme.alpha(WbTheme.withAlpha(effectiveAccent, 0xFF), 0.45F + hoverProgress * 0.55F));
            } else if (style == Style.DANGER) {
                context.fill(x + 1, y + 3, x + 3, y + height - 3, effectiveAccent);
            } else if (style == Style.DEFAULT && (selected || hoverProgress > 0.01F)) {
                int barHeight = Math.round((height - 6) * Math.max(selectionProgress, hoverProgress));
                context.fill(x + 1, y + (height - barHeight) / 2, x + 3, y + (height + barHeight) / 2, effectiveAccent);
            }
        }

        private void drawLabel(GuiGraphicsExtractor context, int x, int y, int width, int height, int effectiveAccent) {
            Font font = Minecraft.getInstance().font;
            boolean showIcon = icon != null && (iconOnly || WorldBinder.config().uiShowIcons);
            int textColor = active
                    ? (selected || style == Style.PRIMARY ? WbTheme.TEXT : WbMotion.mix(WbTheme.TEXT_SOFT, WbTheme.TEXT, hoverProgress))
                    : WbTheme.TEXT_DISABLED;
            int iconColor = active ? WbMotion.mix(WbMotion.mix(WbTheme.TEXT_MUTED, effectiveAccent, 0.55F), effectiveAccent,
                    Math.max(hoverProgress, selectionProgress)) : WbTheme.TEXT_DISABLED;
            if (iconOnly && icon != null) {
                int scale = height >= 22 && width >= 22 ? 2 : 1;
                int size = WbIcon.SIZE * scale;
                icon.drawShadowed(context, x + (width - size) / 2, y + (height - size) / 2, scale,
                        active ? WbMotion.mix(WbTheme.TEXT_SOFT, effectiveAccent, Math.max(hoverProgress, selectionProgress)) : WbTheme.TEXT_DISABLED);
                return;
            }
            int leftPad = style == Style.TAB ? 9 : 7;
            int rightReserved = 0;
            if (style == Style.TOGGLE) {
                rightReserved = 26;
                WbChrome.drawSwitch(context, x + width - 25, y + (height - 10) / 2, selectionProgress, effectiveAccent, active);
            } else if (style == Style.DROPDOWN && !hasTextualChevron()) {
                rightReserved = 16;
                int cx = x + width - 13;
                int cy = y + (height - WbIcon.SIZE) / 2;
                if (selected) {
                    context.pose().pushMatrix();
                    context.pose().translate(cx, cy + WbIcon.SIZE);
                    context.pose().scale(1.0F, -1.0F);
                    WbIcon.CHEVRON_DOWN.draw(context, 0, 0, 1, effectiveAccent);
                    context.pose().popMatrix();
                } else {
                    WbIcon.CHEVRON_DOWN.draw(context, cx, cy, 1, WbMotion.mix(WbTheme.TEXT_MUTED, effectiveAccent, 0.4F + hoverProgress * 0.6F));
                }
            }
            if (badge != null) {
                int badgeW = font.width(badge) + 8;
                int bx = x + width - rightReserved - badgeW - 4;
                WbChrome.rounded(context, bx, y + (height - 11) / 2, badgeW, 11, WbTheme.withAlpha(effectiveAccent, 0x40));
                context.text(font, badge, bx + 4, y + (height - 11) / 2 + 2, WbMotion.mix(effectiveAccent, WbTheme.TEXT, 0.5F), false);
                rightReserved += badgeW + 6;
            }
            int iconSpace = showIcon ? WbIcon.SIZE + 5 : 0;
            int available = Math.max(1, width - leftPad * 2 - rightReserved - iconSpace);
            String label = WbText.ellipsize(font, getMessage().getString(), available);
            int labelWidth = font.width(label);
            int textY = y + (height - font.lineHeight) / 2 + 1;
            boolean left = leftAligned || style == Style.TAB || style == Style.TOGGLE;
            int contentX = left ? x + leftPad
                    : x + leftPad + Math.max(0, (width - leftPad * 2 - rightReserved - iconSpace - labelWidth) / 2);
            if (showIcon) {
                icon.draw(context, contentX, y + (height - WbIcon.SIZE) / 2, 1, iconColor);
                contentX += iconSpace;
            }
            GuiText.drawTextWithShadow(context, font, label, contentX, textY, textColor);
        }

        private boolean hasTextualChevron() {
            String label = getMessage().getString().stripTrailing();
            return label.endsWith("▼") || label.endsWith("▾");
        }
    }
}

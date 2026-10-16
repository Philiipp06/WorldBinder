package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared surface painter: panels, cards, fields, rows and indicators for every WorldBinder screen. */
public final class WbChrome {
    private WbChrome() {
    }

    // ---------------------------------------------------------------- primitives

    /** Rounded rectangle (radius 2) built from non-overlapping fills so translucent colors stay even. */
    public static void rounded(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width < 5 || height < 5) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x + 2, y, x + width - 2, y + 1, color);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, color);
        context.fill(x, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, color);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
    }

    /** Rounded rectangle with a vertical gradient body. */
    public static void roundedGradient(GuiGraphicsExtractor context, int x, int y, int width, int height, int top, int bottom) {
        if (width < 5 || height < 5) {
            context.fillGradient(x, y, x + width, y + height, top, bottom);
            return;
        }
        float span = Math.max(1, height - 1);
        context.fill(x + 2, y, x + width - 2, y + 1, top);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, WbMotion.mix(top, bottom, 1.0F / span));
        context.fillGradient(x, y + 2, x + width, y + height - 2,
                WbMotion.mix(top, bottom, 2.0F / span), WbMotion.mix(top, bottom, (height - 3) / span));
        context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, WbMotion.mix(top, bottom, (height - 2) / span));
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, bottom);
    }

    /** One pixel rounded outline. */
    public static void roundedOutline(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        if (width < 5 || height < 5) {
            context.outline(x, y, width, height, color);
            return;
        }
        context.fill(x + 2, y, x + width - 2, y + 1, color);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 1, y + height - 2, color);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    /** Horizontal gradient strip, approximated with vertical slices. */
    public static void hGradient(GuiGraphicsExtractor context, int x, int y, int width, int height, int left, int right) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int slices = Math.max(1, Math.min(32, width / 3));
        for (int i = 0; i < slices; i++) {
            int x1 = x + width * i / slices;
            int x2 = x + width * (i + 1) / slices;
            context.fill(x1, y, x2, y + height, WbMotion.mix(left, right, slices == 1 ? 0.0F : i / (float) (slices - 1)));
        }
    }

    /** Soft drop shadow made from stacked translucent rounded layers. */
    public static void softShadow(GuiGraphicsExtractor context, int x, int y, int width, int height, int strength) {
        int alpha = Math.max(0, Math.min(255, strength));
        rounded(context, x - 2, y, width + 4, height + 5, (alpha / 5) << 24);
        rounded(context, x - 1, y + 1, width + 2, height + 3, (alpha / 3) << 24);
        rounded(context, x, y + 2, width, height + 1, (alpha / 2) << 24);
    }

    /** Outer accent glow used for focused and selected elements. */
    public static void glow(GuiGraphicsExtractor context, int x, int y, int width, int height, int color, float amount) {
        if (amount <= 0.01F) {
            return;
        }
        roundedOutline(context, x - 2, y - 2, width + 4, height + 4, WbTheme.alpha(WbTheme.withAlpha(color, 0x30), amount));
        roundedOutline(context, x - 1, y - 1, width + 2, height + 2, WbTheme.alpha(WbTheme.withAlpha(color, 0x70), amount));
    }

    // ---------------------------------------------------------------- surfaces

    public static void drawBackdrop(GuiGraphicsExtractor context, int width, int height) {
        context.fillGradient(0, 0, width, height, WbTheme.BACKDROP_SOFT, WbTheme.BACKDROP);
        int band = Math.max(8, height / 6);
        context.fillGradient(0, 0, width, band, WbTheme.withAlpha(WbTheme.ACCENT, 0x10), 0x00000000);
        context.fillGradient(0, height - band, width, height, 0x00000000, 0x30000000);
    }

    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        softShadow(context, x, y, width, height, 0x90);
        rounded(context, x, y, width, height, WbTheme.panelBorder());
        roundedGradient(context, x + 1, y + 1, width - 2, height - 2, WbTheme.PANEL_TOP, WbTheme.PANEL_INNER);
        context.fill(x + 3, y + 1, x + width - 3, y + 2, 0x14FFFFFF);
        int line = Math.min(width - 6, Math.max(60, width / 3));
        hGradient(context, x + 3, y + 1, line, 2, WbTheme.ACCENT, WbTheme.withAlpha(WbTheme.ACCENT_DARK, 0x00));
    }

    public static boolean drawCard(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, Component title, int accent, boolean hover) {
        drawCardSurface(context, x, y, width, height, accent, hover, false);
        hGradient(context, x + 12, y + 29, width - 24, 1, WbTheme.withAlpha(accent, 0x88), WbTheme.withAlpha(WbTheme.DIVIDER, 0x00));
        String text = title == null ? "" : title.getString();
        WbText.drawClipped(context, font, text, x + 14, y + 11, Math.max(1, width - 28), hover ? WbTheme.TEXT : WbMotion.mix(accent, WbTheme.TEXT, 0.35F));
        return hover;
    }

    public static void drawCardSurface(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean hover, boolean selected) {
        softShadow(context, x, y, width, height, hover ? 0x70 : 0x50);
        int border = selected ? WbTheme.withAlpha(accent, 0xCC) : hover ? WbMotion.mix(WbTheme.panelBorder(), accent, 0.45F) : WbTheme.panelBorder();
        rounded(context, x, y, width, height, border);
        roundedGradient(context, x + 1, y + 1, width - 2, height - 2,
                hover ? WbTheme.CARD_HOVER : WbTheme.CARD_TOP, hover ? WbMotion.mix(WbTheme.CARD, WbTheme.CARD_HOVER, 0.5F) : WbTheme.CARD);
        context.fill(x + 1, y + 3, x + 3, y + height - 3, accent);
        context.fill(x + 3, y + 3, x + 5, y + height - 3, WbTheme.withAlpha(accent, 0x2A));
    }

    public static void drawInset(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean active) {
        rounded(context, x, y, width, height, active ? WbTheme.CARD_HOVER : WbTheme.CARD_ALT);
        context.fill(x, y + 2, x + 2, y + height - 2, accent);
        context.fill(x + 3, y + height - 1, x + width - 3, y + height, WbTheme.divider());
    }

    public static void drawField(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean focused, boolean hovered) {
        int fill = focused ? WbTheme.FIELD_FOCUS : hovered ? WbTheme.FIELD_HOVER : WbTheme.FIELD;
        int border = focused ? WbMotion.mix(WbTheme.focusRing(), accent, 0.35F) : hovered ? WbMotion.mix(WbTheme.panelBorder(), accent, 0.35F) : WbTheme.panelBorder();
        if (focused) {
            glow(context, x, y, width, height, accent, 0.8F);
        }
        rounded(context, x, y, width, height, border);
        rounded(context, x + 1, y + 1, width - 2, height - 2, fill);
        context.fill(x + 2, y + 1, x + width - 2, y + 2, 0x22000000);
        if (focused) {
            context.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, accent);
        }
    }

    public static void drawSectionSurface(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent) {
        rounded(context, x, y, width, height, 0x30202B38);
        context.fill(x, y + 2, x + 2, y + height - 2, WbTheme.withAlpha(accent, 0xCC));
        context.fillGradient(x + 2, y, x + width, y + Math.min(height, 14), WbTheme.withAlpha(accent, 0x12), 0x00000000);
    }

    public static void drawDivider(GuiGraphicsExtractor context, int x, int y, int width) {
        int edge = Math.min(24, width / 4);
        hGradient(context, x, y, edge, 1, WbTheme.withAlpha(WbTheme.divider(), 0x00), WbTheme.divider());
        context.fill(x + edge, y, x + width - edge, y + 1, WbTheme.divider());
        hGradient(context, x + width - edge, y, edge, 1, WbTheme.divider(), WbTheme.withAlpha(WbTheme.divider(), 0x00));
    }

    public static void drawScrollBar(GuiGraphicsExtractor context, int x, int y, int height, int scroll, int maxScroll, int viewportHeight) {
        if (maxScroll <= 0 || height <= 8) {
            return;
        }
        rounded(context, x, y, 4, height, WbTheme.SCROLL_TRACK);
        int contentHeight = Math.max(viewportHeight + maxScroll, viewportHeight);
        int thumbHeight = Math.max(18, Math.min(height, Math.round(height * (viewportHeight / (float) contentHeight))));
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + Math.round(travel * (scroll / (float) maxScroll));
        rounded(context, x, thumbY, 4, thumbHeight, WbMotion.mix(WbTheme.SCROLL_THUMB, WbTheme.ACCENT, 0.35F));
        context.fill(x + 1, thumbY + 2, x + 2, thumbY + thumbHeight - 2, 0x55FFFFFF);
    }

    public static void drawProgressTrack(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int accent) {
        rounded(context, x, y, width, height, WbTheme.FIELD);
        int fillWidth = Math.max(0, Math.min(width - 2, fill));
        if (fillWidth > 0) {
            context.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + height - 1,
                    WbTheme.brighten(accent, 0.25F), accent);
            if (height >= 5) {
                context.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2, 0x33FFFFFF);
            }
        }
    }

    /** Animated indeterminate shimmer over a progress track. */
    public static void drawProgressShimmer(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent) {
        if (WbMotion.reduced() || width < 20) {
            return;
        }
        float phase = (System.currentTimeMillis() % 1400L) / 1400.0F;
        int band = Math.max(10, width / 5);
        int start = x + Math.round((width + band) * phase) - band;
        int from = Math.max(x + 1, start);
        int to = Math.min(x + width - 1, start + band);
        if (to > from) {
            hGradient(context, from, y + 1, to - from, Math.max(1, height - 2), WbTheme.withAlpha(accent, 0x00), WbTheme.withAlpha(0xFFFFFFFF, 0x30));
        }
    }

    public static void drawDropdownPanel(GuiGraphicsExtractor context, int x, int y, int width, int rowHeight, int rows) {
        drawDropdownPanel(context, x, y, width, rowHeight, rows, WbTheme.ACCENT);
    }

    public static void drawDropdownPanel(GuiGraphicsExtractor context, int x, int y, int width, int rowHeight, int rows, int accent) {
        int height = rowHeight * rows + 6;
        softShadow(context, x, y, width, height, 0xB0);
        rounded(context, x, y, width, height, WbMotion.mix(WbTheme.panelBorder(), accent, 0.4F));
        roundedGradient(context, x + 1, y + 1, width - 2, height - 2, WbTheme.PANEL_TOP, WbTheme.PANEL_INNER);
        context.fill(x + 2, y + 1, x + width - 2, y + 2, accent);
    }

    public static void drawDropdownRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active, boolean hovered, int accent) {
        if (active || hovered) {
            rounded(context, x + 2, y, width - 4, height, active ? WbTheme.withAlpha(accent, 0x44) : WbTheme.ROW_HOVER);
        }
        if (active) {
            context.fill(x + 2, y + 3, x + 4, y + height - 3, accent);
            WbIcon.CHECK.draw(context, x + width - 13, y + (height - WbIcon.SIZE) / 2, 1, accent);
        }
    }

    /** Small rounded status pill with optional icon. Returns the pill width. */
    public static int drawPill(GuiGraphicsExtractor context, Font font, int x, int y, String text, int accent, WbIcon icon) {
        int iconW = icon == null ? 0 : WbIcon.SIZE + 4;
        int width = font.width(text) + 12 + iconW;
        rounded(context, x, y, width, 14, WbTheme.withAlpha(accent, 0x2C));
        roundedOutline(context, x, y, width, 14, WbTheme.withAlpha(accent, 0x88));
        if (icon != null) {
            icon.draw(context, x + 5, y + 4, 1, accent);
        }
        WbText.draw(context, font, text, x + 6 + iconW, y + 3, WbMotion.mix(accent, WbTheme.TEXT, 0.55F));
        return width;
    }

    public static int pillWidth(Font font, String text, boolean icon) {
        return font.width(text) + 12 + (icon ? WbIcon.SIZE + 4 : 0);
    }

    /** Keyboard key cap used for shortcut hints. Returns the cap width. */
    public static int drawKeycap(GuiGraphicsExtractor context, Font font, int x, int y, String key) {
        int width = Math.max(14, font.width(key) + 8);
        rounded(context, x, y + 1, width, 13, 0xAA05080C);
        rounded(context, x, y, width, 12, 0xFF3A4757);
        rounded(context, x + 1, y + 1, width - 2, 10, 0xFF26303C);
        context.text(font, key, x + (width - font.width(key)) / 2, y + 2, WbTheme.TEXT_SOFT, false);
        return width;
    }

    /** Animated on/off switch. {@code progress} 0 = off, 1 = on. */
    public static void drawSwitch(GuiGraphicsExtractor context, int x, int y, float progress, int accent, boolean active) {
        int width = 20;
        int height = 10;
        int on = active ? accent : WbTheme.TEXT_DISABLED;
        rounded(context, x, y, width, height, WbMotion.mix(0xFF0D1218, WbTheme.withAlpha(on, 0xFF), progress * 0.75F));
        roundedOutline(context, x, y, width, height, WbMotion.mix(WbTheme.panelBorder(), on, progress));
        int knob = x + 1 + Math.round((width - 10) * WbMotion.easeOutCubic(progress));
        rounded(context, knob, y + 1, 8, 8, WbMotion.mix(0xFF8D9DAE, 0xFFFFFFFF, progress));
    }

    /** Filled circle built from horizontal spans (works under rotated/scaled poses). */
    public static void fillCircle(GuiGraphicsExtractor context, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int dy = -radius; dy < radius; dy++) {
            double y = dy + 0.5D;
            int half = (int) Math.round(Math.sqrt(Math.max(0.0D, radius * radius - y * y)));
            if (half > 0) {
                context.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
            }
        }
    }

    /** One pixel circle outline built from horizontal spans. */
    public static void circleOutline(GuiGraphicsExtractor context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy < radius; dy++) {
            double y = dy + 0.5D;
            int outer = (int) Math.round(Math.sqrt(Math.max(0.0D, radius * radius - y * y)));
            int inner = (int) Math.round(Math.sqrt(Math.max(0.0D, (radius - 1) * (radius - 1) - y * y)));
            if (outer <= 0) continue;
            if (inner <= 0 || Math.abs(dy) >= radius - 1) {
                context.fill(cx - outer, cy + dy, cx + outer, cy + dy + 1, color);
            } else {
                context.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                context.fill(cx + inner, cy + dy, cx + outer, cy + dy + 1, color);
            }
        }
    }

    /** Scanline filled triangle in local (pose) coordinates. */
    public static void fillTriangle(GuiGraphicsExtractor context, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        for (int y = minY; y < maxY; y++) {
            float sample = y + 0.5F;
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float[][] edges = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
            for (float[] e : edges) {
                if ((sample >= e[1] && sample < e[3]) || (sample >= e[3] && sample < e[1])) {
                    float t = (sample - e[1]) / (e[3] - e[1]);
                    float x = e[0] + t * (e[2] - e[0]);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (right > left) {
                int x1 = Math.round(left);
                int x2 = Math.round(right);
                if (x2 > x1) context.fill(x1, y, x2, y + 1, color);
            }
        }
    }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}

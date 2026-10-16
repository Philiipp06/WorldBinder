package net.worldbinder.ui.component;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * WorldBinder tooltips: delayed, animated, multi-part cards (title / body / hint) that always render in their
 * own GUI stratum, so button labels and fields never shine through them.
 * <p>
 * A tooltip text may contain a title line separated by {@code \n}. Optional hints (shortcut or action info) can be
 * attached per widget with {@link #hint(AbstractWidget, Component)}.
 */
public final class WbTooltips {
    private static final Map<AbstractWidget, Component> TOOLTIP_BY_WIDGET = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AbstractWidget, Component> HINT_BY_WIDGET = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_TEXT_WIDTH = 220;
    private static final int MAX_LINES = 12;
    private static final long FADE_MILLIS = 140L;

    private static Object activeKey;
    private static long activeSinceMillis;
    private static long lastPresentedMillis;
    private static long keyboardNavigationMillis;
    private static float smoothX = Float.NaN;
    private static float smoothY = Float.NaN;

    private WbTooltips() {
    }

    public static <T extends AbstractWidget> T register(T widget, Component tooltip) {
        if (widget == null) {
            return null;
        }
        widget.setTooltip(null);
        if (tooltip != null && !tooltip.getString().isBlank()) {
            TOOLTIP_BY_WIDGET.put(widget, tooltip);
        } else {
            TOOLTIP_BY_WIDGET.remove(widget);
        }
        return widget;
    }

    /** Registers a tooltip with an explicit title line. */
    public static <T extends AbstractWidget> T register(T widget, Component title, Component body) {
        if (title == null) {
            return register(widget, body);
        }
        return register(widget, Component.empty().append(title).append("\n").append(body == null ? Component.empty() : body));
    }

    /** Attaches a small hint line (for example a shortcut) shown below the tooltip body. */
    public static <T extends AbstractWidget> T hint(T widget, Component hint) {
        if (widget != null) {
            if (hint == null) {
                HINT_BY_WIDGET.remove(widget);
            } else {
                HINT_BY_WIDGET.put(widget, hint);
            }
        }
        return widget;
    }

    public static Component tooltipOf(AbstractWidget widget) {
        return widget == null ? null : TOOLTIP_BY_WIDGET.get(widget);
    }

    /** Screens call this when Tab/arrow navigation happens so focus tooltips appear only for keyboard users. */
    public static void noteKeyboardNavigation() {
        keyboardNavigationMillis = System.currentTimeMillis();
    }

    public static void showHovered(Screen screen, GuiGraphicsExtractor context, Font font, double virtualMouseX, double virtualMouseY, int realMouseX, int realMouseY) {
        if (screen == null || context == null || font == null) {
            return;
        }
        List<? extends GuiEventListener> children = screen.children();
        AbstractWidget focusedWidget = null;
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiEventListener child = children.get(i);
            if (!(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            Component tooltip = TOOLTIP_BY_WIDGET.get(widget);
            if (tooltip == null) {
                continue;
            }
            if (widget.isMouseOver(virtualMouseX, virtualMouseY)) {
                present(context, font, tooltip.getString(), tooltip, HINT_BY_WIDGET.get(widget), WbTheme.ACCENT,
                        realMouseX, realMouseY, screen.width, screen.height, false);
                return;
            }
            if (widget.isFocused()) {
                focusedWidget = widget;
            }
        }
        boolean keyboardMode = System.currentTimeMillis() - keyboardNavigationMillis < 6000L;
        if (focusedWidget != null && keyboardMode) {
            WbLayout.UiScale scale = WbLayout.uiScale(screen.width, screen.height);
            int x = scale.offsetX() + Math.round((focusedWidget.getX() + focusedWidget.getWidth() / 2.0F) * scale.scale());
            int y = scale.offsetY() + Math.round((focusedWidget.getY() + focusedWidget.getHeight()) * scale.scale());
            present(context, font, TOOLTIP_BY_WIDGET.get(focusedWidget).getString(), TOOLTIP_BY_WIDGET.get(focusedWidget), HINT_BY_WIDGET.get(focusedWidget),
                    WbTheme.ACCENT, x, y, screen.width, screen.height, false);
        }
    }

    /** Draws a free-standing tooltip (cards, list rows) with the same delay and animation as widget tooltips. */
    public static void draw(GuiGraphicsExtractor context, Font font, Component tooltip,
                            int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (tooltip == null) {
            return;
        }
        present(context, font, tooltip.getString(), tooltip, null, WbTheme.ACCENT, mouseX, mouseY, screenWidth, screenHeight, false);
    }

    /** Immediate information card (no delay), used by the live map for hover details. */
    public static void drawCard(GuiGraphicsExtractor context, Font font, String title, List<String> lines, int accent,
                                int mouseX, int mouseY, int screenWidth, int screenHeight) {
        context.nextStratum();
        Layout layout = layout(font, title, lines, null);
        int[] position = position(layout, mouseX, mouseY, screenWidth, screenHeight);
        paint(context, font, layout, position[0], position[1], accent, 1.0F);
    }

    private static void present(GuiGraphicsExtractor context, Font font, Object key, Component tooltip, Component hint,
                                int accent, int mouseX, int mouseY, int screenWidth, int screenHeight, boolean immediate) {
        if (tooltip == null || !WorldBinder.config().tooltipsEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!key.equals(activeKey) || now - lastPresentedMillis > 120L) {
            activeKey = key;
            activeSinceMillis = now;
            smoothX = Float.NaN;
        }
        lastPresentedMillis = now;
        long delay = immediate ? 0L : Math.max(0, WorldBinder.config().tooltipDelayMs);
        long visibleFor = now - activeSinceMillis - delay;
        if (visibleFor < 0L) {
            return;
        }
        float fade = WbMotion.reduced() ? 1.0F : WbMotion.easeOutCubic(Math.min(1.0F, visibleFor / (float) FADE_MILLIS));

        String raw = tooltip.getString().replace('\r', ' ');
        String title = null;
        String body = raw;
        int newline = raw.indexOf('\n');
        if (newline > 0) {
            title = raw.substring(0, newline).strip();
            body = raw.substring(newline + 1);
        }
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : body.split("\n", -1)) {
            paragraphs.add(paragraph.strip());
        }
        String hintText = hint != null && WorldBinder.config().tooltipShowHints ? hint.getString() : null;
        Layout layout = layout(font, title, paragraphs, hintText);
        int[] target = position(layout, mouseX, mouseY, screenWidth, screenHeight);
        if (Float.isNaN(smoothX) || WbMotion.reduced()) {
            smoothX = target[0];
            smoothY = target[1];
        } else {
            smoothX += (target[0] - smoothX) * 0.45F;
            smoothY += (target[1] - smoothY) * 0.45F;
        }
        context.nextStratum();
        int slide = Math.round((1.0F - fade) * 4.0F);
        paint(context, font, layout, Math.round(smoothX), Math.round(smoothY) + slide, accent, fade);
    }

    private record Layout(String title, List<String> lines, String hint, int width, int height) {
    }

    private static Layout layout(Font font, String title, List<String> paragraphs, String hint) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph == null) {
                continue;
            }
            if (paragraph.isBlank()) {
                if (!lines.isEmpty()) lines.add("");
                continue;
            }
            lines.addAll(wrap(font, paragraph, MAX_TEXT_WIDTH));
            if (lines.size() >= MAX_LINES) break;
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
            lines.set(MAX_LINES - 1, WbText.ellipsize(font, lines.get(MAX_LINES - 1) + " ...", MAX_TEXT_WIDTH));
        }
        int widest = title == null ? 0 : Math.min(MAX_TEXT_WIDTH, font.width(title) + 2);
        for (String line : lines) widest = Math.max(widest, font.width(line));
        if (hint != null) widest = Math.max(widest, Math.min(MAX_TEXT_WIDTH, font.width(hint) + WbIcon.SIZE + 5));
        int height = 8;
        if (title != null) height += 12 + (lines.isEmpty() ? 0 : 4);
        height += lines.size() * 10;
        if (hint != null) height += 15;
        return new Layout(title == null ? null : WbText.ellipsize(font, title, MAX_TEXT_WIDTH), lines, hint, widest + 18, height + 2);
    }

    private static int[] position(Layout layout, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int x = mouseX + 12;
        int y = mouseY + 14;
        if (x + layout.width() + 4 > screenWidth) x = mouseX - layout.width() - 10;
        if (y + layout.height() + 4 > screenHeight) y = mouseY - layout.height() - 8;
        x = Math.max(4, Math.min(x, screenWidth - layout.width() - 4));
        y = Math.max(4, Math.min(y, screenHeight - layout.height() - 4));
        return new int[] {x, y};
    }

    private static void paint(GuiGraphicsExtractor context, Font font, Layout layout, int x, int y, int accent, float alpha) {
        int w = layout.width();
        int h = layout.height();
        WbChrome.softShadow(context, x, y, w, h, Math.round(0xA0 * alpha));
        WbChrome.rounded(context, x, y, w, h, WbTheme.alpha(WbMotion.mix(WbTheme.panelBorder(), accent, 0.35F), alpha));
        WbChrome.roundedGradient(context, x + 1, y + 1, w - 2, h - 2,
                WbTheme.alpha(0xFA1D2733, alpha), WbTheme.alpha(0xFA121921, alpha));
        WbChrome.hGradient(context, x + 3, y + 1, Math.max(8, w - 6), 1,
                WbTheme.alpha(accent, alpha), WbTheme.alpha(WbTheme.withAlpha(accent, 0x00), alpha));
        int cursor = y + 6;
        if (layout.title() != null) {
            context.text(font, Component.literal(layout.title()).withStyle(ChatFormatting.BOLD), x + 9, cursor,
                    WbTheme.alpha(WbMotion.mix(accent, WbTheme.TEXT, 0.6F), alpha), true);
            cursor += 12;
            if (!layout.lines().isEmpty()) {
                context.fill(x + 9, cursor, x + w - 9, cursor + 1, WbTheme.alpha(WbTheme.DIVIDER, alpha));
                cursor += 4;
            }
        }
        for (int i = 0; i < layout.lines().size(); i++) {
            String line = layout.lines().get(i);
            int color = layout.title() == null && i == 0 ? WbTheme.TEXT_SOFT : WbTheme.mutedText();
            if (!line.isEmpty()) {
                context.text(font, line, x + 9, cursor, WbTheme.alpha(color, alpha), true);
            }
            cursor += 10;
        }
        if (layout.hint() != null) {
            cursor += 3;
            context.fill(x + 9, cursor - 2, x + w - 9, cursor - 1, WbTheme.alpha(0x30FFFFFF, alpha));
            WbIcon.KEY.draw(context, x + 9, cursor + 1, 1, WbTheme.alpha(accent, alpha));
            context.text(font, WbText.ellipsize(font, layout.hint(), w - 22 - WbIcon.SIZE), x + 9 + WbIcon.SIZE + 5, cursor,
                    WbTheme.alpha(WbTheme.TEXT_DIM, alpha), true);
        }
    }

    public static Component clickHint() {
        return Lang.text("worldbinder.tooltip.hint.click");
    }

    private static List<String> wrap(Font font, String value, int width) {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : value.strip().split("\\s+")) {
            if (word.isBlank()) continue;
            String next = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(next) > width) {
                lines.add(current);
                current = word;
            } else {
                current = next;
            }
            while (font.width(current) > width && current.length() > 4) {
                int cut = Math.max(1, current.length() * width / Math.max(1, font.width(current)) - 1);
                lines.add(current.substring(0, cut));
                current = current.substring(cut);
            }
        }
        if (!current.isEmpty()) lines.add(current);
        return lines;
    }
}

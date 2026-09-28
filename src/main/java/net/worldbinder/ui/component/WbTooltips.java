package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WbTooltips {
    private static final Map<AbstractWidget, Component> TOOLTIP_BY_WIDGET = Collections.synchronizedMap(new WeakHashMap<>());

    private WbTooltips() {
    }

    public static <T extends AbstractWidget> T register(T widget, Component tooltip) {
        if (widget == null) {
            return null;
        }
        widget.setTooltip(null);
        if (tooltip != null) {
            TOOLTIP_BY_WIDGET.put(widget, tooltip);
        } else {
            TOOLTIP_BY_WIDGET.remove(widget);
        }
        return widget;
    }

    public static void showHovered(Screen screen, GuiGraphicsExtractor context, Font font, double virtualMouseX, double virtualMouseY, int realMouseX, int realMouseY) {
        if (screen == null || context == null || font == null) {
            return;
        }
        List<? extends GuiEventListener> children = screen.children();
        Component focused = null;
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
                draw(context, font, tooltip, realMouseX, realMouseY, screen.width, screen.height);
                return;
            }
            if (widget.isFocused()) {
                focused = tooltip;
                focusedWidget = widget;
            }
        }
        if (focused != null && focusedWidget != null) {
            WbLayout.UiScale scale = WbLayout.uiScale(screen.width, screen.height);
            int x = scale.offsetX() + Math.round((focusedWidget.getX() + focusedWidget.getWidth() / 2.0F) * scale.scale());
            int y = scale.offsetY() + Math.round((focusedWidget.getY() + focusedWidget.getHeight()) * scale.scale());
            draw(context, font, focused, x, y, screen.width, screen.height);
        }
    }

    public static void draw(GuiGraphicsExtractor context, Font font, Component tooltip,
                            int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (tooltip == null) return;
        int textWidth = Math.max(60, Math.min(260, screenWidth - 36));
        List<String> lines = wrap(font, tooltip.getString(), textWidth, 5);
        if (lines.isEmpty()) return;
        int widest = 0;
        for (String line : lines) widest = Math.max(widest, font.width(line));
        int boxW = Math.min(screenWidth - 8, widest + 20);
        int boxH = lines.size() * 12 + 12;
        int x = mouseX + 12;
        int y = mouseY > screenHeight / 2 ? mouseY - boxH - 20 : mouseY + 12;
        if (x + boxW + 4 > screenWidth) x = mouseX - boxW - 12;
        if (y + boxH + 4 > screenHeight) y = mouseY - boxH - 12;
        x = Math.max(4, Math.min(x, screenWidth - boxW - 4));
        y = Math.max(4, Math.min(y, screenHeight - boxH - 4));
        context.fill(x + 3, y + 4, x + boxW + 3, y + boxH + 4, 0x88000000);
        context.fill(x, y, x + boxW, y + boxH, WbTheme.panelBorder());
        context.fill(x + 1, y + 1, x + boxW - 1, y + boxH - 1, WbTheme.PANEL);
        context.fill(x, y, x + Math.min(boxW, 36), y + 2, WbTheme.ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            WbText.drawClipped(context, font, lines.get(i), x + 10, y + 7 + i * 12, boxW - 20,
                    i == 0 ? WbTheme.TEXT : WbTheme.mutedText());
        }
    }

    private static List<String> wrap(Font font, String value, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.replace('\r', ' ').split("\n", -1)) {
            String current = "";
            for (String word : paragraph.strip().split("\\s+")) {
                if (word.isBlank()) continue;
                String next = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && font.width(next) > width) {
                    lines.add(current);
                    current = word;
                } else {
                    current = next;
                }
                if (lines.size() == maxLines) break;
            }
            if (!current.isEmpty() && lines.size() < maxLines) lines.add(current);
            if (lines.size() >= maxLines) break;
        }
        if (lines.size() == maxLines) {
            lines.set(maxLines - 1, WbText.ellipsize(font, lines.get(maxLines - 1), width));
        }
        return lines;
    }
}

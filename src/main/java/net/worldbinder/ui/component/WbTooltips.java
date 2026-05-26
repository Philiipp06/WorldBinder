package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
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
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible || !widget.active) {
                continue;
            }
            Component tooltip = TOOLTIP_BY_WIDGET.get(widget);
            if (tooltip == null) {
                continue;
            }
            if (widget.isMouseOver(virtualMouseX, virtualMouseY)) {
                context.setTooltipForNextFrame(font, tooltip, realMouseX, realMouseY);
                return;
            }
        }
    }
}

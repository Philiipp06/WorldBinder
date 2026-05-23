package net.worldbinder.ui.component;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class WbButton {
    private WbButton() {
    }

    public static Button create(int x, int y, int width, int height, String label, Component tooltip, Button.OnPress action) {
        return create(x, y, width, height, Component.literal(label), tooltip, action);
    }

    public static Button create(int x, int y, int width, int height, Component label, Component tooltip, Button.OnPress action) {
        int safeWidth = Math.max(34, width);
        return Button.builder(Component.literal(fitLabel(label.getString(), safeWidth)), action)
                .bounds(x, y, safeWidth, Math.max(18, height))
                .tooltip(Tooltip.create(tooltip))
                .build();
    }

    public static String fitLabel(String label, int width) {
        if (label == null) {
            return "";
        }
        int maxChars = Math.max(3, (width - 12) / 6);
        if (label.length() <= maxChars) {
            return label;
        }
        return label.substring(0, Math.max(1, maxChars - 3)).stripTrailing() + "...";
    }
}

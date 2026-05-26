package net.worldbinder.ui.component;

import net.minecraft.client.input.MouseButtonEvent;

public final class WbLayout {
    public static final int DESIGN_WIDTH = 960;
    public static final int DESIGN_HEIGHT = 540;
    private static final int MIN_OUTER_MARGIN = 4;

    private WbLayout() {
    }

    public record UiScale(float scale, int offsetX, int offsetY) {
        public int toVirtualX(double x) {
            return Math.max(0, Math.min(DESIGN_WIDTH, Math.round((float) ((x - offsetX) / scale))));
        }

        public int toVirtualY(double y) {
            return Math.max(0, Math.min(DESIGN_HEIGHT, Math.round((float) ((y - offsetY) / scale))));
        }

        public double toVirtualDelta(double value) {
            return value / scale;
        }
    }

    public static UiScale uiScale(int realWidth, int realHeight) {
        int safeWidth = Math.max(1, realWidth - MIN_OUTER_MARGIN * 2);
        int safeHeight = Math.max(1, realHeight - MIN_OUTER_MARGIN * 2);
        float scale = Math.min(safeWidth / (float) DESIGN_WIDTH, safeHeight / (float) DESIGN_HEIGHT);
        scale = Math.max(0.25F, scale);
        int scaledWidth = Math.round(DESIGN_WIDTH * scale);
        int scaledHeight = Math.round(DESIGN_HEIGHT * scale);
        int offsetX = Math.max(0, (realWidth - scaledWidth) / 2);
        int offsetY = Math.max(0, (realHeight - scaledHeight) / 2);
        return new UiScale(scale, offsetX, offsetY);
    }

    public static MouseButtonEvent virtualMouseEvent(MouseButtonEvent event, int realWidth, int realHeight) {
        UiScale scale = uiScale(realWidth, realHeight);
        return new MouseButtonEvent(scale.toVirtualX(event.x()), scale.toVirtualY(event.y()), event.buttonInfo());
    }

    public static int virtualMouseX(double x, int realWidth, int realHeight) {
        return uiScale(realWidth, realHeight).toVirtualX(x);
    }

    public static int virtualMouseY(double y, int realWidth, int realHeight) {
        return uiScale(realWidth, realHeight).toVirtualY(y);
    }

    public static int panelWidth(int screenWidth) {
        int margin = screenWidth < 700 ? 6 : 12;
        return Math.max(1, screenWidth - margin * 2);
    }

    public static int panelHeight(int screenHeight) {
        int margin = screenHeight < 520 ? 6 : 12;
        return Math.max(1, screenHeight - margin * 2);
    }

    public static int left(int screenWidth, int panelWidth) {
        return Math.max(0, (screenWidth - panelWidth) / 2);
    }

    public static int top(int screenHeight, int panelHeight) {
        return Math.max(0, (screenHeight - panelHeight) / 2);
    }

    public static int sidebarWidth(int panelWidth) {
        if (panelWidth < 430) {
            return Math.max(64, Math.min(74, panelWidth / 4));
        }
        if (panelWidth < 620) {
            return 104;
        }
        if (panelWidth < 820) {
            return 150;
        }
        return 186;
    }

    public static int contentX(int left, int panelWidth) {
        return left + sidebarWidth(panelWidth) + 14;
    }

    public static int contentWidth(int panelWidth) {
        int width = panelWidth - sidebarWidth(panelWidth) - 32;
        return Math.max(80, width);
    }

    public static int contentBottom(int top, int panelHeight) {
        return top + panelHeight - 42;
    }

    public static int sidebarStep(int panelHeight) {
        if (panelHeight < 350) {
            return 20;
        }
        if (panelHeight < 460) {
            return 24;
        }
        return 29;
    }

    public static int sidebarButtonHeight(int panelHeight) {
        if (panelHeight < 350) {
            return 17;
        }
        if (panelHeight < 460) {
            return 19;
        }
        return 22;
    }

    public static boolean compact(int panelWidth, int panelHeight) {
        return panelWidth < 760 || panelHeight < 470;
    }

    public static boolean tiny(int panelWidth, int panelHeight) {
        return panelWidth < 560 || panelHeight < 370;
    }
}

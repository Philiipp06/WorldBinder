package net.worldbinder.ui.component;

public final class WbLayout {
    private WbLayout() {
    }

    public static int panelWidth(int screenWidth) {
        int margin = screenWidth < 700 ? 6 : 12;
        return Math.max(220, screenWidth - margin * 2);
    }

    public static int panelHeight(int screenHeight) {
        int margin = screenHeight < 520 ? 6 : 12;
        return Math.max(210, screenHeight - margin * 2);
    }

    public static int left(int screenWidth, int panelWidth) {
        return Math.max(3, (screenWidth - panelWidth) / 2);
    }

    public static int top(int screenHeight, int panelHeight) {
        return Math.max(3, (screenHeight - panelHeight) / 2);
    }

    public static int sidebarWidth(int panelWidth) {
        if (panelWidth < 430) {
            return 74;
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
        return Math.max(1, panelWidth - sidebarWidth(panelWidth) - 32);
    }

    public static int contentBottom(int top, int panelHeight) {
        return top + panelHeight - 42;
    }

    public static int sidebarStep(int panelHeight) {
        return panelHeight < 390 ? 22 : 27;
    }

    public static int sidebarButtonHeight(int panelHeight) {
        return panelHeight < 390 ? 18 : 22;
    }

    public static boolean compact(int panelWidth, int panelHeight) {
        return panelWidth < 760 || panelHeight < 470;
    }

    public static boolean tiny(int panelWidth, int panelHeight) {
        return panelWidth < 560 || panelHeight < 370;
    }
}

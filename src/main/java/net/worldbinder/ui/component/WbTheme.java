package net.worldbinder.ui.component;

import net.worldbinder.WorldBinder;

public final class WbTheme {
    private WbTheme() {
    }

    public static final int BACKDROP = 0xD90C1015;
    public static final int BACKDROP_SOFT = 0xAA151A20;
    public static final int PANEL = 0xF4161B21;
    public static final int PANEL_INNER = 0xF01D242C;
    public static final int PANEL_SHADOW = 0x88000000;
    public static final int PANEL_BORDER = 0x77657686;
    public static final int CARD = 0xD7242C35;
    public static final int CARD_ALT = 0xAA28313B;
    public static final int CARD_HOVER = 0xED34404D;
    public static final int CARD_SELECTED = 0xF0344050;
    public static final int FIELD = 0xDF11171E;
    public static final int FIELD_HOVER = 0xEF1B242E;
    public static final int FIELD_FOCUS = 0xF024303C;
    public static final int BUTTON = 0xEE2A333D;
    public static final int BUTTON_HOVER = 0xFA3A4856;
    public static final int BUTTON_PRESSED = 0xFA435363;
    public static final int BUTTON_DISABLED = 0x881E252C;
    public static final int ROW = 0x552B3642;
    public static final int ROW_ALT = 0x44364553;
    public static final int ROW_HOVER = 0x77536A80;
    public static final int ROW_SELECTED = 0x8874C8EC;

    public static final int ACCENT = 0xFF74C8EC;
    public static final int ACCENT_DARK = 0xFF9C9BE8;
    public static final int ACCENT_RIGHT = 0xFFFFB66F;
    public static final int ACCENT_SOFT = 0x5574C8EC;
    public static final int ACCENT_MUTED = 0x3374C8EC;
    public static final int ACCENT_GLOW = 0x2274C8EC;
    public static final int FOCUS_RING = 0xFFD7EDFA;
    public static final int DIVIDER = 0x66657686;
    public static final int SCROLL_TRACK = 0x88121820;
    public static final int SCROLL_THUMB = 0xDD8496A7;

    public static final int TEXT = 0xFFF4F7FA;
    public static final int TEXT_SOFT = 0xFFE3EAF0;
    public static final int TEXT_MUTED = 0xFFB6C3D0;
    public static final int TEXT_DIM = 0xFF93A3B3;
    public static final int TEXT_DISABLED = 0xFF718092;
    public static final int OK = 0xFF8BCBA9;
    public static final int WARN = 0xFFFFBE79;
    public static final int ERROR = 0xFFFF8D95;
    public static final int INFO = 0xFF9DC9FA;

    public static boolean highContrast() {
        return WorldBinder.config().highContrastUi;
    }

    public static int panelBorder() {
        return highContrast() ? 0xFFB7C8D9 : PANEL_BORDER;
    }

    public static int divider() {
        return highContrast() ? 0xFF94A9BB : DIVIDER;
    }

    public static int mutedText() {
        return highContrast() ? TEXT_SOFT : TEXT_MUTED;
    }

    public static int focusRing() {
        return highContrast() ? 0xFFFFFFFF : FOCUS_RING;
    }
}

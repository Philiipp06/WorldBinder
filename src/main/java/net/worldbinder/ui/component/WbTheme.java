package net.worldbinder.ui.component;

import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;

/**
 * Central WorldBinder palette. Accent-derived tokens are refreshed through {@link #apply(WorldBinderConfig)}
 * so every menu, HUD widget and the live map follow the selected interface theme.
 */
public final class WbTheme {
    private WbTheme() {
    }

    public static int BACKDROP = 0xC40A0E14;
    public static int BACKDROP_SOFT = 0x66101722;
    public static final int PANEL = 0xF6121820;
    public static final int PANEL_INNER = 0xF2161D26;
    public static final int PANEL_TOP = 0xF61B2430;
    public static final int PANEL_SHADOW = 0x66000000;
    public static final int PANEL_BORDER = 0x6E5D7086;
    public static final int CARD = 0xD21C242E;
    public static final int CARD_TOP = 0xDC222C38;
    public static final int CARD_ALT = 0xA8222B36;
    public static final int CARD_HOVER = 0xEA2A3542;
    public static final int CARD_SELECTED = 0xF02E3A4A;
    public static final int FIELD = 0xE00D1218;
    public static final int FIELD_HOVER = 0xEE141B24;
    public static final int FIELD_FOCUS = 0xF2192330;
    public static final int BUTTON = 0xEC232C37;
    public static final int BUTTON_TOP = 0xEE2A3542;
    public static final int BUTTON_HOVER = 0xF8334152;
    public static final int BUTTON_PRESSED = 0xFA3B4B5E;
    public static final int BUTTON_DISABLED = 0x861B222A;
    public static final int ROW = 0x4A26313D;
    public static final int ROW_ALT = 0x3A303D4B;
    public static final int ROW_HOVER = 0x704A5E74;
    public static int ROW_SELECTED = 0x7A62C3EA;

    public static int ACCENT = 0xFF62C3EA;
    public static int ACCENT_DARK = 0xFF9C9BE8;
    public static int ACCENT_RIGHT = 0xFFFFB66F;
    public static int ACCENT_SOFT = 0x5562C3EA;
    public static int ACCENT_MUTED = 0x3362C3EA;
    public static int ACCENT_GLOW = 0x2262C3EA;
    public static int ACCENT_DEEP = 0xFF2E7FA5;
    public static int FOCUS_RING = 0xFFD7EDFA;
    public static final int DIVIDER = 0x5A5D7086;
    public static final int SCROLL_TRACK = 0x66101720;
    public static final int SCROLL_THUMB = 0xCC7D90A4;

    public static final int TEXT = 0xFFF4F7FA;
    public static final int TEXT_SOFT = 0xFFE1E8EF;
    public static final int TEXT_MUTED = 0xFFB2C0CE;
    public static final int TEXT_DIM = 0xFF8D9DAE;
    public static final int TEXT_DISABLED = 0xFF6C7A8B;
    public static final int OK = 0xFF7FD3A6;
    public static final int WARN = 0xFFFFC27D;
    public static final int ERROR = 0xFFFF8A93;
    public static final int INFO = 0xFF94C6FA;

    private static int appliedSignature = Integer.MIN_VALUE;

    public enum Accent {
        AQUA(0xFF62C3EA, 0xFF9C9BE8),
        VIOLET(0xFFA98BFF, 0xFF62C3EA),
        EMERALD(0xFF5FD39B, 0xFF62C3EA),
        AMBER(0xFFFFB45E, 0xFFFF8A93),
        ROSE(0xFFFF7FA8, 0xFFA98BFF),
        CRIMSON(0xFFFF6464, 0xFFFFB45E),
        MONO(0xFFD5DEE8, 0xFF8D9DAE),
        CUSTOM(0xFF62C3EA, 0xFF9C9BE8);

        public final int primary;
        public final int secondary;

        Accent(int primary, int secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    /** Re-derives all accent based tokens. Cheap enough to call every frame; work only happens on change. */
    public static void apply(WorldBinderConfig config) {
        if (config == null) {
            return;
        }
        Accent accent = config.effectiveUiAccent();
        int primary = accent == Accent.CUSTOM ? 0xFF000000 | (config.uiCustomAccentColor & 0xFFFFFF) : accent.primary;
        int secondary = accent == Accent.CUSTOM ? shift(primary, 0.35F) : accent.secondary;
        int opacity = Math.max(20, Math.min(100, config.uiBackdropOpacityPercent));
        int signature = primary * 31 + secondary * 17 + opacity * 7 + (config.highContrastUi ? 1 : 0);
        if (signature == appliedSignature) {
            return;
        }
        appliedSignature = signature;
        ACCENT = primary;
        ACCENT_DARK = secondary;
        ACCENT_SOFT = withAlpha(primary, 0x55);
        ACCENT_MUTED = withAlpha(primary, 0x33);
        ACCENT_GLOW = withAlpha(primary, 0x22);
        ACCENT_DEEP = WbMotion.mix(primary, 0xFF0A0E14, 0.45F);
        ROW_SELECTED = withAlpha(primary, 0x7A);
        FOCUS_RING = WbMotion.mix(primary, 0xFFFFFFFF, 0.72F);
        int backdropAlpha = Math.round(0xF0 * opacity / 100.0F);
        BACKDROP = withAlpha(0xFF0A0E14, backdropAlpha);
        BACKDROP_SOFT = withAlpha(0xFF101722, Math.round(backdropAlpha * 0.45F));
    }

    public static void refresh() {
        apply(WorldBinder.config());
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static int alpha(int color, float factor) {
        int a = Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, factor)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int brighten(int color, float amount) {
        return WbMotion.mix(color, (color & 0xFF000000) | 0x00FFFFFF, amount);
    }

    public static int darken(int color, float amount) {
        return WbMotion.mix(color, color & 0xFF000000, amount);
    }

    private static int shift(int color, float amount) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        return 0xFF000000 | (Math.round(r + (b - r) * amount) << 16) | (Math.round(g + (r - g) * amount) << 8)
                | Math.round(b + (g - b) * amount);
    }

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

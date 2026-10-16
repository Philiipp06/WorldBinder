package net.worldbinder.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Screen-level motion: a short zoom/fade when a WorldBinder screen opens from the game and a soft content
 * cross-fade when switching between WorldBinder pages, so navigation never "jumps".
 */
public final class WbTransition {
    private static final long OPEN_MILLIS = 220L;
    private static final long SWITCH_MILLIS = 170L;
    private static final Map<Screen, Entry> ENTRIES = Collections.synchronizedMap(new WeakHashMap<>());
    private static long lastFrameMillis;

    private record Entry(long startMillis, boolean fromGame) {
    }

    private WbTransition() {
    }

    private static Entry entry(Screen screen) {
        return ENTRIES.computeIfAbsent(screen, ignored -> {
            long now = System.currentTimeMillis();
            boolean fromGame = now - lastFrameMillis > 250L;
            if (fromGame) {
                WbSounds.open();
            }
            return new Entry(now, fromGame);
        });
    }

    /** Pushes the opening transform. Always pair with {@link #end(GuiGraphicsExtractor, boolean)}. */
    public static boolean begin(GuiGraphicsExtractor context, Screen screen, int realWidth, int realHeight) {
        WbTheme.refresh();
        Entry entry = entry(screen);
        lastFrameMillis = System.currentTimeMillis();
        if (!enabled() || !entry.fromGame()) {
            return false;
        }
        float t = WbMotion.easeOutCubic(WbMotion.progress(entry.startMillis(), OPEN_MILLIS));
        if (t >= 1.0F) {
            return false;
        }
        float scale = 0.965F + 0.035F * t;
        context.pose().pushMatrix();
        context.pose().translate(realWidth / 2.0F, realHeight / 2.0F + (1.0F - t) * 6.0F);
        context.pose().scale(scale, scale);
        context.pose().translate(-realWidth / 2.0F, -realHeight / 2.0F);
        return true;
    }

    public static void end(GuiGraphicsExtractor context, boolean pushed) {
        if (pushed) {
            context.pose().popMatrix();
        }
    }

    /** Draws the fade veil over the whole screen while it opens (called after everything else). */
    public static void veil(GuiGraphicsExtractor context, Screen screen, int realWidth, int realHeight) {
        if (!enabled()) {
            return;
        }
        Entry entry = entry(screen);
        float t = WbMotion.progress(entry.startMillis(), entry.fromGame() ? OPEN_MILLIS : SWITCH_MILLIS);
        if (t >= 1.0F) {
            return;
        }
        int alpha = Math.round((entry.fromGame() ? 0xC8 : 0x90) * (1.0F - WbMotion.easeOutCubic(t)));
        if (alpha <= 2) {
            return;
        }
        context.nextStratum();
        context.fill(0, 0, realWidth, realHeight, WbTheme.withAlpha(0xFF0A0E14, alpha));
    }

    /** Local fade for a content area after an in-screen page change. */
    public static void contentFade(GuiGraphicsExtractor context, int x, int y, int width, int height, long startMillis) {
        if (!enabled() || startMillis <= 0L) {
            return;
        }
        float t = WbMotion.progress(startMillis, SWITCH_MILLIS);
        if (t >= 1.0F) {
            return;
        }
        int alpha = Math.round(0xE0 * (1.0F - WbMotion.easeOutCubic(t)));
        if (alpha <= 2) {
            return;
        }
        context.nextStratum();
        context.fill(x, y, x + width, y + height, WbTheme.withAlpha(WbTheme.PANEL_INNER, alpha));
    }

    public static boolean enabled() {
        return WorldBinder.config().uiScreenTransitions && !WbMotion.reduced();
    }
}

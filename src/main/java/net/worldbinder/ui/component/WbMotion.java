package net.worldbinder.ui.component;

import net.worldbinder.WorldBinder;

public final class WbMotion {
    private WbMotion() {
    }

    public static float approach(float current, float target, long elapsedNanos, float speed) {
        if (WorldBinder.config().reducedUiMotion) {
            return target;
        }
        float seconds = Math.min(0.05F, Math.max(0.0F, elapsedNanos / 1_000_000_000.0F));
        float blend = 1.0F - (float) Math.exp(-speed * seconds);
        return current + (target - current) * blend;
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int a = channel(from >>> 24, to >>> 24, t);
        int r = channel((from >>> 16) & 255, (to >>> 16) & 255, t);
        int g = channel((from >>> 8) & 255, (to >>> 8) & 255, t);
        int b = channel(from & 255, to & 255, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount) & 255;
    }
}

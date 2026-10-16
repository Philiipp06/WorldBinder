package net.worldbinder.ui.component;

import net.worldbinder.WorldBinder;

public final class WbMotion {
    private WbMotion() {
    }

    public static boolean reduced() {
        return WorldBinder.config().reducedUiMotion;
    }

    /** Global animation speed factor from the Interface settings (0.5x .. 2.0x). */
    public static float speedFactor() {
        return Math.max(50, Math.min(200, WorldBinder.config().uiAnimationSpeedPercent)) / 100.0F;
    }

    public static float approach(float current, float target, long elapsedNanos, float speed) {
        if (reduced()) {
            return target;
        }
        float seconds = Math.min(0.05F, Math.max(0.0F, elapsedNanos / 1_000_000_000.0F));
        float blend = 1.0F - (float) Math.exp(-speed * speedFactor() * seconds);
        float next = current + (target - current) * blend;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    /** Linear progress of an animation that started at {@code startMillis} and lasts {@code durationMillis}. */
    public static float progress(long startMillis, long durationMillis) {
        if (reduced() || startMillis <= 0L) {
            return 1.0F;
        }
        float duration = Math.max(1.0F, durationMillis / speedFactor());
        return clamp01((System.currentTimeMillis() - startMillis) / duration);
    }

    public static float easeOutCubic(float t) {
        float inv = 1.0F - clamp01(t);
        return 1.0F - inv * inv * inv;
    }

    public static float easeInOutCubic(float t) {
        float x = clamp01(t);
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0F) / 2.0F;
    }

    public static float easeOutBack(float t) {
        float x = clamp01(t);
        float c1 = 1.40158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3.0F) + c1 * (float) Math.pow(x - 1.0F, 2.0F);
    }

    /** Smooth 0..1..0 pulse with the given period, used for subtle "live" indicators. */
    public static float pulse(long periodMillis) {
        if (reduced()) {
            return 0.5F;
        }
        double phase = (System.currentTimeMillis() % Math.max(1L, periodMillis)) / (double) periodMillis;
        return (float) (0.5D - 0.5D * Math.cos(phase * Math.PI * 2.0D));
    }

    public static int mix(int from, int to, float amount) {
        float t = clamp01(amount);
        int a = channel(from >>> 24, to >>> 24, t);
        int r = channel((from >>> 16) & 255, (to >>> 16) & 255, t);
        int g = channel((from >>> 8) & 255, (to >>> 8) & 255, t);
        int b = channel(from & 255, to & 255, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int channel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount) & 255;
    }
}

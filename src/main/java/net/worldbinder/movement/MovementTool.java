package net.worldbinder.movement;

import java.util.Locale;

public enum MovementTool {
    SPEED(1, 5, 1), FLY(0.5, 5, 1), SPIDER, JESUS, NO_FALL,
    STEP(1, 3, 1), HIGH_JUMP(1, 3, 1), GLIDE(1, 5, 2),
    FAST_LADDER(1, 5, 2), AUTO_SPRINT, SAFE_WALK, AUTO_JUMP,
    WATER_SPEED(1, 5, 1), AIR_CONTROL(1, 5, 1);

    public final double min;
    public final double max;
    public final double initial;

    MovementTool() { this(1, 1, 1); }
    MovementTool(double min, double max, double initial) {
        this.min = min;
        this.max = max;
        this.initial = initial;
    }
    public boolean adjustable() { return max > min; }
    public String key() { return "worldbinder.movement." + name().toLowerCase(Locale.ROOT); }
    public double clamp(double value) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : initial;
    }
}

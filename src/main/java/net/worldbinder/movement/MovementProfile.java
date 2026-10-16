package net.worldbinder.movement;

import java.util.EnumMap;
import java.util.Map;

/** One-click movement presets shown in the Movement Tools settings. */
public enum MovementProfile {
    EXPLORER("worldbinder.movement.profile.explorer", Map.of(
            MovementTool.SPEED, 1.6, MovementTool.STEP, 1.0, MovementTool.AUTO_SPRINT, 1.0,
            MovementTool.NO_FALL, 1.0, MovementTool.FAST_LADDER, 2.0, MovementTool.AUTO_SURFACE, 1.0)),
    BUILDER("worldbinder.movement.profile.builder", Map.of(
            MovementTool.FLY, 1.5, MovementTool.SAFE_WALK, 1.0, MovementTool.NO_FALL, 1.0,
            MovementTool.NO_SLOW, 1.0, MovementTool.SNEAK_SPEED, 2.0)),
    PARKOUR("worldbinder.movement.profile.parkour", Map.of(
            MovementTool.AUTO_SPRINT, 1.0, MovementTool.PARKOUR, 1.0, MovementTool.LONG_JUMP, 1.6,
            MovementTool.AIR_CONTROL, 2.0, MovementTool.NO_JUMP_DELAY, 1.0)),
    AQUATIC("worldbinder.movement.profile.aquatic", Map.of(
            MovementTool.JESUS, 1.0, MovementTool.WATER_SPEED, 2.5, MovementTool.AUTO_SURFACE, 1.0)),
    SKY("worldbinder.movement.profile.sky", Map.of(
            MovementTool.ELYTRA_BOOST, 2.0, MovementTool.GLIDE, 2.0, MovementTool.LOW_GRAVITY, 0.6,
            MovementTool.NO_FALL, 1.0, MovementTool.JETPACK, 2.0));

    public final String key;
    private final Map<MovementTool, Double> values;

    MovementProfile(String key, Map<MovementTool, Double> values) {
        this.key = key;
        this.values = new EnumMap<>(values);
    }

    public Map<MovementTool, Double> toolValues() {
        return values;
    }

    /** Disables every tool, then enables the profile tools with their profile values. */
    public void applyTo(MovementSettings settings) {
        for (MovementTool tool : MovementTool.values()) {
            settings.enabled(tool, false);
        }
        values.forEach((tool, value) -> {
            settings.enabled(tool, true);
            if (tool.adjustable()) {
                settings.value(tool, value);
            }
        });
    }
}

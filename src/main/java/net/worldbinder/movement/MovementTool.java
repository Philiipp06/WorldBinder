package net.worldbinder.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum MovementTool {
    SPEED(Category.GROUND, Unit.MULTIPLIER, 1, 5, 1),
    FLY(Category.VERTICAL, Unit.MULTIPLIER, 0.5, 5, 1),
    SPIDER(Category.VERTICAL),
    JESUS(Category.WATER),
    NO_FALL(Category.VERTICAL),
    STEP(Category.GROUND, Unit.BLOCKS, 1, 3, 1),
    HIGH_JUMP(Category.GROUND, Unit.MULTIPLIER, 1, 3, 1),
    GLIDE(Category.VERTICAL, Unit.MULTIPLIER, 1, 5, 2),
    FAST_LADDER(Category.VERTICAL, Unit.MULTIPLIER, 1, 5, 2),
    AUTO_SPRINT(Category.GROUND),
    SAFE_WALK(Category.GROUND),
    AUTO_JUMP(Category.GROUND),
    WATER_SPEED(Category.WATER, Unit.MULTIPLIER, 1, 5, 1),
    AIR_CONTROL(Category.AIR, Unit.MULTIPLIER, 1, 5, 1),
    BUNNY_HOP(Category.GROUND, Unit.MULTIPLIER, 1, 3, 1.4),
    LONG_JUMP(Category.AIR, Unit.MULTIPLIER, 1, 4, 2),
    SNEAK_SPEED(Category.GROUND, Unit.MULTIPLIER, 1, 3, 2),
    NO_SLOW(Category.GROUND),
    PARKOUR(Category.GROUND),
    NO_JUMP_DELAY(Category.AIR),
    JETPACK(Category.VERTICAL, Unit.MULTIPLIER, 1, 5, 2),
    LOW_GRAVITY(Category.AIR, Unit.GRAVITY, 0.1, 1, 0.5),
    ELYTRA_BOOST(Category.AIR, Unit.MULTIPLIER, 1, 5, 2),
    AUTO_SURFACE(Category.WATER);

    public enum Category {
        GROUND("worldbinder.config.movement.group.ground"),
        VERTICAL("worldbinder.config.movement.group.vertical"),
        WATER("worldbinder.config.movement.group.water"),
        AIR("worldbinder.config.movement.group.control");

        public final String titleKey;

        Category(String titleKey) {
            this.titleKey = titleKey;
        }

        public List<MovementTool> tools() {
            List<MovementTool> result = new ArrayList<>();
            for (MovementTool tool : MovementTool.values()) {
                if (tool.category == this) result.add(tool);
            }
            return result;
        }
    }

    public enum Unit {
        NONE("worldbinder.movement.multiplier"),
        MULTIPLIER("worldbinder.movement.multiplier"),
        BLOCKS("worldbinder.movement.blocks"),
        GRAVITY("worldbinder.movement.gravity");

        public final String key;

        Unit(String key) {
            this.key = key;
        }
    }

    public final Category category;
    public final Unit unit;
    public final double min;
    public final double max;
    public final double initial;

    MovementTool(Category category) {
        this(category, Unit.NONE, 1, 1, 1);
    }

    MovementTool(Category category, Unit unit, double min, double max, double initial) {
        this.category = category;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.initial = initial;
    }

    public boolean adjustable() { return max > min; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String key() { return "worldbinder.movement." + id(); }
    public String tooltipKey() { return key() + ".tooltip"; }
    public String keybindKey() { return "key.worldbinder.toggle." + id(); }
    public double clamp(double value) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : initial;
    }

    /** True for tools added in WorldBinder 1.6.0 (shown with a "New" marker in the settings). */
    public boolean isNew() {
        return ordinal() >= BUNNY_HOP.ordinal();
    }
}

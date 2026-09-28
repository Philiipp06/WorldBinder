package net.worldbinder.ui;

import net.worldbinder.movement.MovementTool;

import java.util.List;

final class MovementSettingsLayout {
    static final int ROW_HEIGHT = 78;
    static final int HEADING_HEIGHT = 24;
    static final List<Group> GROUPS = List.of(
            new Group("worldbinder.config.movement.group.ground", new MovementTool[] {
                    MovementTool.SPEED, MovementTool.STEP, MovementTool.HIGH_JUMP,
                    MovementTool.AUTO_SPRINT, MovementTool.SAFE_WALK, MovementTool.AUTO_JUMP
            }),
            new Group("worldbinder.config.movement.group.vertical", new MovementTool[] {
                    MovementTool.FLY, MovementTool.SPIDER, MovementTool.GLIDE,
                    MovementTool.FAST_LADDER, MovementTool.NO_FALL
            }),
            new Group("worldbinder.config.movement.group.water", new MovementTool[] {
                    MovementTool.JESUS, MovementTool.WATER_SPEED
            }),
            new Group("worldbinder.config.movement.group.control", new MovementTool[] {
                    MovementTool.AIR_CONTROL
            })
    );

    private MovementSettingsLayout() {
    }

    static int height() {
        return 22 + GROUPS.size() * HEADING_HEIGHT + MovementTool.values().length * ROW_HEIGHT;
    }

    static int height(int groupIndex) {
        Group group = GROUPS.get(Math.max(0, Math.min(groupIndex, GROUPS.size() - 1)));
        return 40 + group.tools().length * ROW_HEIGHT;
    }

    record Group(String titleKey, MovementTool[] tools) {
    }
}

package net.worldbinder.movement;

import java.util.EnumMap;

public final class MovementSettings {
    private EnumMap<MovementTool, Setting> tools = new EnumMap<>(MovementTool.class);

    public MovementSettings() { normalize(); }

    public void normalize() {
        if (tools == null) tools = new EnumMap<>(MovementTool.class);
        for (MovementTool tool : MovementTool.values()) {
            Setting setting = tools.get(tool);
            if (setting == null) {
                setting = new Setting();
                setting.value = tool.initial;
                tools.put(tool, setting);
            }
            setting.value = tool.clamp(setting.value);
        }
    }

    public boolean enabled(MovementTool tool) {
        return tools != null && tools.get(tool) != null && tools.get(tool).enabled;
    }

    public double value(MovementTool tool) {
        return tool.clamp(tools == null || tools.get(tool) == null ? tool.initial : tools.get(tool).value);
    }

    public void enabled(MovementTool tool, boolean enabled) { normalize(); tools.get(tool).enabled = enabled; }
    public void value(MovementTool tool, double value) { normalize(); tools.get(tool).value = tool.clamp(value); }

    public int enabledCount() {
        int count = 0;
        for (MovementTool tool : MovementTool.values()) {
            if (enabled(tool)) count++;
        }
        return count;
    }

    public void disableAll() {
        for (MovementTool tool : MovementTool.values()) enabled(tool, false);
    }

    public void resetValues() {
        for (MovementTool tool : MovementTool.values()) value(tool, tool.initial);
    }

    private static final class Setting {
        boolean enabled;
        double value;
    }
}

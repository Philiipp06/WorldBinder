package net.worldbinder.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.hud.MovementHud;
import net.worldbinder.movement.MovementTool;
import net.worldbinder.ui.component.WbSounds;

import java.util.EnumMap;
import java.util.Map;

/** Optional per-tool toggle keys (unbound by default) and a master switch for all movement tools. */
public final class MovementKeys {
    private static KeyMapping master;
    private static final Map<MovementTool, KeyMapping> TOOL_KEYS = new EnumMap<>(MovementTool.class);

    private MovementKeys() {
    }

    static void register(KeyMapping masterKey, Map<MovementTool, KeyMapping> toolKeys) {
        master = masterKey;
        TOOL_KEYS.clear();
        TOOL_KEYS.putAll(toolKeys);
    }

    static void tick(Minecraft client) {
        if (master == null) {
            return;
        }
        WorldBinderConfig config = WorldBinder.config();
        while (master.consumeClick()) {
            config.movementMasterEnabled = !config.movementMasterEnabled;
            config.save();
            feedback(Component.translatable(config.movementMasterEnabled
                    ? "worldbinder.movement.master.on" : "worldbinder.movement.master.off"), null, config.movementMasterEnabled);
        }
        for (Map.Entry<MovementTool, KeyMapping> entry : TOOL_KEYS.entrySet()) {
            while (entry.getValue().consumeClick()) {
                MovementTool tool = entry.getKey();
                boolean next = !config.movement.enabled(tool);
                config.movement.enabled(tool, next);
                config.save();
                feedback(Component.translatable(next ? "worldbinder.movement.toggled.on" : "worldbinder.movement.toggled.off",
                        Component.translatable(tool.key())), tool, next);
            }
        }
    }

    /** Returns the bound key name for a tool, or {@code null} when the tool has no toggle key. */
    public static String boundKey(MovementTool tool) {
        KeyMapping mapping = TOOL_KEYS.get(tool);
        if (mapping == null || mapping.isUnbound()) {
            return null;
        }
        return mapping.getTranslatedKeyMessage().getString();
    }

    private static void feedback(Component message, MovementTool tool, boolean enabled) {
        if (!WorldBinder.config().movementToggleFeedback) {
            return;
        }
        WbSounds.movementToggle(enabled);
        MovementHud.flash(message, tool, enabled);
    }
}

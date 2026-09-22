package net.worldbinder.movement;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class MovementAttributes {
    private MovementAttributes() {}

    public static void tick(Player player) {
        if (!MovementController.ownedPlayer(player)) return;
        MovementSettings settings = MovementController.settings();
        if (settings == null) return;
        boolean walking = MovementController.walking(player);
        update(player, Attributes.MOVEMENT_SPEED, MovementTool.SPEED,
                walking && settings.enabled(MovementTool.SPEED), settings.value(MovementTool.SPEED) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        update(player, Attributes.STEP_HEIGHT, MovementTool.STEP,
                walking && settings.enabled(MovementTool.STEP), Math.max(0, settings.value(MovementTool.STEP) - player.getAttributeBaseValue(Attributes.STEP_HEIGHT)),
                AttributeModifier.Operation.ADD_VALUE);
        update(player, Attributes.JUMP_STRENGTH, MovementTool.HIGH_JUMP,
                walking && !MovementController.spiderActive(player) && settings.enabled(MovementTool.HIGH_JUMP), settings.value(MovementTool.HIGH_JUMP) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void update(Player player, Holder<Attribute> attribute, MovementTool tool, boolean enabled,
                               double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = Identifier.fromNamespaceAndPath("worldbinder", "movement/" + tool.name().toLowerCase(java.util.Locale.ROOT));
        if (!enabled) { instance.removeModifier(id); return; }
        AttributeModifier current = instance.getModifier(id);
        if (current == null || current.amount() != amount || current.operation() != operation) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }
}

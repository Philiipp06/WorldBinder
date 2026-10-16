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
        boolean eligible = MovementController.eligible(player);
        update(player, Attributes.MOVEMENT_SPEED, MovementTool.SPEED,
                walking && MovementController.enabled(player, MovementTool.SPEED), settings.value(MovementTool.SPEED) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        update(player, Attributes.STEP_HEIGHT, MovementTool.STEP,
                walking && MovementController.enabled(player, MovementTool.STEP), Math.max(0, settings.value(MovementTool.STEP) - player.getAttributeBaseValue(Attributes.STEP_HEIGHT)),
                AttributeModifier.Operation.ADD_VALUE);
        update(player, Attributes.JUMP_STRENGTH, MovementTool.HIGH_JUMP,
                walking && !MovementController.spiderActive(player) && MovementController.enabled(player, MovementTool.HIGH_JUMP), settings.value(MovementTool.HIGH_JUMP) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        update(player, Attributes.SNEAKING_SPEED, MovementTool.SNEAK_SPEED,
                walking && MovementController.enabled(player, MovementTool.SNEAK_SPEED), settings.value(MovementTool.SNEAK_SPEED) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        update(player, Attributes.MOVEMENT_EFFICIENCY, MovementTool.NO_SLOW,
                walking && MovementController.enabled(player, MovementTool.NO_SLOW),
                Math.max(0, 1.0 - player.getAttributeBaseValue(Attributes.MOVEMENT_EFFICIENCY)),
                AttributeModifier.Operation.ADD_VALUE);
        update(player, Attributes.GRAVITY, MovementTool.LOW_GRAVITY,
                eligible && MovementController.enabled(player, MovementTool.LOW_GRAVITY), settings.value(MovementTool.LOW_GRAVITY) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (MovementController.local(player) && walking && MovementController.enabled(player, MovementTool.NO_JUMP_DELAY)) {
            ((net.worldbinder.mixin.LivingEntityAccessor) player).worldbinder$setNoJumpDelay(0);
        }
    }

    private static void update(Player player, Holder<Attribute> attribute, MovementTool tool, boolean enabled,
                               double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = Identifier.fromNamespaceAndPath("worldbinder", "movement/" + tool.id());
        if (!enabled) { instance.removeModifier(id); return; }
        AttributeModifier current = instance.getModifier(id);
        if (current == null || current.amount() != amount || current.operation() != operation) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }
}

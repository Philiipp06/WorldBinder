package net.worldbinder.test;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.movement.*;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.ui.WorldBinderConfigScreen;
import net.worldbinder.util.Chat;

public final class WorldBinderClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WorldBinderConfig config = new WorldBinderConfig();
            check(net.worldbinder.version.TargetMinecraftVersion.resolve("26.2").effectiveDataVersion() == 4903,
                    "26.2 export target retained independently from 26.3 runtime");
            for (MovementTool tool : MovementTool.values()) {
                check(!config.movement.enabled(tool), tool + " must default off");
                config.movement.value(tool, Double.NaN);
                check(config.movement.value(tool) == tool.initial, tool + " nonfinite clamp");
                config.movement.value(tool, 999);
                check(config.movement.value(tool) == tool.max, tool + " upper clamp");
                config.movement.value(tool, -999);
                check(config.movement.value(tool) == tool.min, tool + " lower clamp");
                config.movement.enabled(tool, true);
            }
            config.messageMode = WorldBinderConfig.MessageMode.CHAT;
            config.save();
            WorldBinderConfig loaded = WorldBinderConfig.load();
            check(loaded.messageMode == config.messageMode, "Message mode save/load");
            for (MovementTool tool : MovementTool.values()) check(loaded.movement.enabled(tool), tool + " save/load");
            WorldBinder.replaceConfig(new WorldBinderConfig());
            WorldBinder.saveConfig();
        });
        context.setScreen(() -> WorldBinderConfigScreen.general(new TitleScreen()));
        context.clickScreenButton("Movement Tools");
        context.waitTicks(3);
        context.takeScreenshot("movement-settings-top");
        context.runOnClient(client -> {
            for (MovementTool tool : MovementTool.values()) {
                String name = net.minecraft.network.chat.Component.translatable(tool.key()).getString();
                Button toggle = (Button) client.gui.screen().children().stream()
                        .filter(child -> child instanceof Button button && button.getMessage().getString().startsWith(name + " "))
                        .findFirst().orElseThrow();
                toggle.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,
                        com.mojang.blaze3d.platform.InputConstants.KEYCODE_RETURN, 0));
            }
            var sliders = client.gui.screen().children().stream().filter(AbstractSliderButton.class::isInstance).toList();
            check(sliders.size() == java.util.Arrays.stream(MovementTool.values()).filter(MovementTool::adjustable).count(), "All movement sliders present");
            try {
                var setValue = AbstractSliderButton.class.getDeclaredMethod("setValue", double.class);
                setValue.setAccessible(true);
                for (var child : sliders) {
                    AbstractSliderButton slider = (AbstractSliderButton)child;
                    check(slider.getWidth() > 0, "Movement slider bounds");
                    setValue.invoke(slider, 0.75);
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        context.clickScreenButton("worldbinder.config.save");
        context.runOnClient(client -> {
            var saved = WorldBinderConfig.load().movement;
            for (MovementTool tool : MovementTool.values()) {
                check(saved.enabled(tool), tool + " UI toggle/save");
                if (tool.adjustable()) {
                    double expected = Math.round((tool.min + 0.75 * (tool.max - tool.min)) * 10) / 10.0;
                    check(Math.abs(saved.value(tool) - expected) < 0.001, tool + " UI slider/save");
                }
            }
            WorldBinder.replaceConfig(new WorldBinderConfig());
        });
        context.setScreen(() -> WorldBinderConfigScreen.hud(new TitleScreen()));
        context.runOnClient(client -> {
            for (int i = 0; i < 60; i++) client.gui.screen().mouseScrolled(400, 150, 0, -1);
        });
        context.waitTicks(2);
        context.takeScreenshot("message-settings");
        context.setScreen(TitleScreen::new);
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("fill -8 99 -8 8 99 8 minecraft:stone");
            world.getServer().runCommand("tp @a 0 100 0");
            context.waitTicks(10);
            testMessages(context);
            context.runOnClient(client -> testMovement(client));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                WorldBinder.config().movement.enabled(MovementTool.NO_FALL, true);
                float health = player.getHealth();
                player.causeFallDamage(20, 1, player.damageSources().fall());
                check(player.getHealth() == health, "No Fall integrated server");
                WorldBinder.config().movement.enabled(MovementTool.NO_FALL, false);
                player.causeFallDamage(8, 1, player.damageSources().fall());
                check(player.getHealth() < health, "No Fall disabled restores damage");
                player.setHealth(health);
            });
            context.takeScreenshot("movement-world-smoke");
            CaptureRegressionTest.run(context);
        }
        context.runOnClient(client -> { WorldBinder.replaceConfig(new WorldBinderConfig()); WorldBinder.saveConfig(); });
        context.setScreen(TitleScreen::new);
        WorldBinder.LOGGER.info("WORLDBINDER_CLIENT_TEST_PASSED: configuration, UI, message routing, movement and capture/cache/recovery/export smoke checks");
    }

    private static void testMessages(ClientGameTestContext context) {
        for (WorldBinderConfig.MessageMode mode : WorldBinderConfig.MessageMode.values()) {
            context.setScreen(() -> WorldBinderConfigScreen.hud(null));
            context.runOnClient(client -> {
                String label = net.minecraft.network.chat.Component.translatable(
                        "worldbinder.messages." + mode.name().toLowerCase(java.util.Locale.ROOT)).getString();
                Button choice = (Button) client.gui.screen().children().stream()
                        .filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                        .findFirst().orElseThrow();
                choice.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,
                        com.mojang.blaze3d.platform.InputConstants.KEYCODE_RETURN, 0));
            });
            context.clickScreenButton("worldbinder.config.save");
            context.runOnClient(client -> {
                check(WorldBinder.config().effectiveMessageMode() == mode, "Message mode UI selection");
                check(WorldBinderConfig.load().effectiveMessageMode() == mode, "Message mode persistence");
                WorldBinderNotifications.clear();
                int before = chatCount(client);
                Chat.info("routing-info-" + mode);
                Chat.warn("routing-warn-" + mode);
                Chat.error("routing-error-" + mode);
                Chat.savedArchive("world", "routing-success-" + mode, 1, 0, 0, null);
                check(chatCount(client) - before == (mode.chat() ? 4 : 0), "Chat routing: " + mode);
                check(WorldBinderNotifications.visible(4).size() == (mode.toast() ? 4 : 0), "Toast routing: " + mode);
            });
        }
        context.runOnClient(client -> WorldBinderNotifications.clear());
    }

    private static int chatCount(Minecraft client) {
        try {
            Object hud = client.gui.hud;
            Object chat = hud.getClass().getMethod("getChat").invoke(hud);
            var field = chat.getClass().getDeclaredField("allMessages");
            field.setAccessible(true);
            return ((List<?>)field.get(chat)).size();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void testMovement(Minecraft client) {
        var player = client.player;
        MovementSettings settings = new MovementSettings();
        WorldBinder.config().movement = settings;
        double originalSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double originalStep = player.getAttributeValue(Attributes.STEP_HEIGHT);
        double originalJump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
        settings.enabled(MovementTool.SPEED, true);
        settings.value(MovementTool.SPEED, 5);
        settings.enabled(MovementTool.STEP, true);
        settings.value(MovementTool.STEP, 3);
        settings.enabled(MovementTool.HIGH_JUMP, true);
        settings.value(MovementTool.HIGH_JUMP, 2);
        MovementAttributes.tick(player);
        check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed * 5) < 0.001, "Speed 5x");
        check(player.getAttributeValue(Attributes.STEP_HEIGHT) >= 3, "Step height");
        check(player.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.8, "High Jump");
        for (MovementTool tool : MovementTool.values()) settings.enabled(tool, false);
        MovementAttributes.tick(player);
        check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed) < 0.001, "Speed restores");
        check(player.getAttributeValue(Attributes.STEP_HEIGHT) == originalStep, "Step restores");
        check(player.getAttributeValue(Attributes.JUMP_STRENGTH) == originalJump, "High Jump restores");
        settings.enabled(MovementTool.JESUS, true);
        check(!player.canStandOnFluid(Blocks.WATER.defaultBlockState().getFluidState()), "No water support on dry ground");
        settings.enabled(MovementTool.JESUS, false);
        check(!player.canStandOnFluid(Blocks.WATER.defaultBlockState().getFluidState()), "Water surface off");
        player.setPos(0, 110, 0);
        player.setOnGround(false);
        settings.enabled(MovementTool.FLY, true);
        boolean mayFly = player.getAbilities().mayfly;
        boolean flying = player.getAbilities().flying;
        double start = player.getZ();
        player.travel(new Vec3(0, 0, 1));
        check(player.getZ() != start, "Flight moves");
        check(Math.abs(player.getY() - 110) < 0.001, "Flight holds altitude");
        client.options.keyJump.setDown(true);
        player.travel(Vec3.ZERO);
        client.options.keyJump.setDown(false);
        check(player.getY() > 110, "Flight ascends");
        client.options.keyShift.setDown(true);
        player.travel(Vec3.ZERO);
        client.options.keyShift.setDown(false);
        check(Math.abs(player.getY() - 110) < 0.001, "Flight descends");
        client.gui.setScreen(new TitleScreen());
        Vec3 pausedPosition = player.position();
        client.options.keyJump.setDown(true);
        player.travel(new Vec3(0, 0, 1));
        client.options.keyJump.setDown(false);
        check(player.position().equals(pausedPosition), "Flight ignores menu input");
        client.gui.setScreen(null);
        settings.enabled(MovementTool.FLY, false);
        check(player.getAbilities().mayfly == mayFly && player.getAbilities().flying == flying, "Flight does not mutate abilities");
        settings.enabled(MovementTool.GLIDE, true);
        player.setDeltaMovement(0, -1, 0);
        MovementController.afterTravel(player);
        check(player.getDeltaMovement().y >= -0.15, "Glide caps fall speed");
        player.setDeltaMovement(0, 0.4, 0);
        MovementController.afterTravel(player);
        check(player.getDeltaMovement().y == 0.4, "Glide preserves ascent");
        settings.enabled(MovementTool.GLIDE, false);
        ClientInput originalInput = player.input;
        player.input = new ClientInput() {
            @Override public boolean hasForwardImpulse() { return true; }
        };
        player.input.keyPresses = new Input(true, false, false, false, false, false, false);
        settings.enabled(MovementTool.SPIDER, true);
        player.horizontalCollision = true;
        player.setDeltaMovement(0, -0.1, 0);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y == -0.1, "Stale collision flag does not cause Spider climb");
        player.setPos(4.5, 120, 4.72);
        client.level.setBlock(new BlockPos(4, 120, 5), Blocks.STONE.defaultBlockState(), 3);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y > 0, "Spider climbs");
        player.setDeltaMovement(Vec3.ZERO);
        player.jumpFromGround();
        check(player.getDeltaMovement().y == 0, "Spider suppresses ground jump");
        settings.enabled(MovementTool.SPIDER, false);
        settings.enabled(MovementTool.AUTO_SPRINT, true);
        player.horizontalCollision = false;
        SurfaceMotion.beforeTravel(player, settings);
        check(player.isSprinting(), "Auto Sprint");
        settings.enabled(MovementTool.AUTO_SPRINT, false);
        SurfaceMotion.updateSprint(player);
        check(!player.isSprinting(), "Auto Sprint releases its sprint");
        settings.enabled(MovementTool.AUTO_JUMP, true);
        player.setPos(0.5, 100, 0.65);
        player.setYRot(0);
        client.level.setBlock(new BlockPos(0, 100, 1), Blocks.STONE.defaultBlockState(), 3);
        player.setOnGround(true);
        player.horizontalCollision = true;
        player.setDeltaMovement(Vec3.ZERO);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y > 0, "Auto Jump");
        settings.enabled(MovementTool.STEP, true);
        player.setDeltaMovement(Vec3.ZERO);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y == 0, "Step suppresses Auto Jump");
        settings.enabled(MovementTool.STEP, false);
        settings.enabled(MovementTool.AUTO_JUMP, false);
        settings.enabled(MovementTool.AIR_CONTROL, true);
        settings.value(MovementTool.AIR_CONTROL, 5);
        player.setOnGround(false);
        player.horizontalCollision = false;
        MovementController.travel(player, new Vec3(0, 0, 1));
        check(player.getDeltaMovement().horizontalDistanceSqr() > 0, "Air Control");
        settings.enabled(MovementTool.AIR_CONTROL, false);
        testSurfaces(client, settings);
        player.input = originalInput;
        for (MovementTool tool : MovementTool.values()) settings.enabled(tool, false);
        MovementAttributes.tick(player);
        player.setPos(0, 100, 0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void testSurfaces(Minecraft client, MovementSettings settings) {
        var player = client.player;
        try {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                client.level.setBlock(new BlockPos(x, 109, z), Blocks.WATER.defaultBlockState(), 3);
                client.level.setBlock(new BlockPos(x, 110, z), Blocks.WATER.defaultBlockState(), 3);
            }
            settings.enabled(MovementTool.JESUS, true);
            check(Blocks.LAVA.defaultBlockState().getCollisionShape(client.level, new BlockPos(0, 110, 0),
                    net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(), "Jesus never adds lava collision");
            player.setPos(0, 111.1, 0);
            player.setOnGround(false);
            player.move(MoverType.SELF, new Vec3(0, -0.5, 0));
            check(player.getY() > 110.7, "Jesus collides with water surface");
            check(net.worldbinder.movement.WaterSurface.supported(player), "Water support at surface");
            player.input.keyPresses = new Input(true, false, false, false, false, true, false);
            player.move(MoverType.SELF, new Vec3(0, -0.5, 0));
            check(player.getY() < 110.7, "Sneaking enters water with Jesus enabled");
            player.input.keyPresses = new Input(true, false, false, false, false, false, false);
            check(!net.worldbinder.movement.WaterSurface.supported(player), "Underwater release does not push player up");
            player.setPos(0, 111.1, 0);
            settings.enabled(MovementTool.JESUS, false);
            player.move(MoverType.SELF, new Vec3(0, -0.6, 0));
            check(player.getY() < 110.7, "Jesus off enters water");
            player.setPos(0, 109.2, 0);
            var fluidUpdate = net.minecraft.world.entity.Entity.class.getDeclaredMethod("updateFluidInteraction");
            fluidUpdate.setAccessible(true);
            fluidUpdate.invoke(player);
            check(player.isInWater(), "Water fixture");
            player.setDeltaMovement(Vec3.ZERO);
            settings.enabled(MovementTool.WATER_SPEED, true);
            settings.value(MovementTool.WATER_SPEED, 5);
            MovementController.travel(player, new Vec3(0, 0, 1));
            check(player.getDeltaMovement().horizontalDistanceSqr() > 0.005, "Water Speed acceleration");
            settings.enabled(MovementTool.WATER_SPEED, false);
            settings.enabled(MovementTool.JESUS, true);
            var flowingPos = new BlockPos(7, 115, 7);
            client.level.setBlock(flowingPos, Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3), 3);
            player.setPos(7.5, 116.1, 7.5);
            player.move(MoverType.SELF, new Vec3(0, -1.0, 0));
            check(player.getY() > 115.3, "Flowing water collision");
            player.setPos(9, 120, 9);
            check(!net.worldbinder.movement.WaterSurface.supported(player), "Leaving water clears support without stored state");
            settings.enabled(MovementTool.JESUS, false);

            client.level.setBlock(new BlockPos(4, 110, 0), Blocks.LADDER.defaultBlockState(), 3);
            player.setPos(4.5, 110, 0.5);
            fluidUpdate.invoke(player);
            check(player.onClimbable(), "Ladder fixture");
            settings.enabled(MovementTool.FAST_LADDER, true);
            settings.value(MovementTool.FAST_LADDER, 3);
            var climb = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("handleOnClimbable", Vec3.class);
            climb.setAccessible(true);
            Vec3 up = (Vec3)climb.invoke(player, new Vec3(0, 0.1, 0));
            check(up.y >= 0.59, "Fast Ladder ascent");
            player.input = new ClientInput();
            Vec3 down = (Vec3)climb.invoke(player, new Vec3(0, -0.2, 0));
            check(down.y <= -0.44, "Fast Ladder descent");
            settings.enabled(MovementTool.FAST_LADDER, false);
            Vec3 vanillaDown = (Vec3)climb.invoke(player, new Vec3(0, -0.2, 0));
            check(vanillaDown.y > -0.16, "Fast Ladder off restores vanilla clamp");

            client.level.setBlock(new BlockPos(0, 119, 0), Blocks.STONE.defaultBlockState(), 3);
            player.setPos(0.5, 120, 0.5);
            player.setOnGround(true);
            settings.enabled(MovementTool.SAFE_WALK, true);
            player.move(MoverType.SELF, new Vec3(1.5, 0, 0));
            check(player.getX() < 1.3, "Safe Walk stops at edge");
            settings.enabled(MovementTool.SAFE_WALK, false);
            player.move(MoverType.SELF, new Vec3(1.5, 0, 0));
            check(player.getX() > 1.5, "Safe Walk off releases movement");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

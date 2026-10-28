package net.worldbinder.export.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.worldbinder.config.GameRuleSettings;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorldExportGameRulesTest {
    private WorldExportGameRulesTest() {
    }

    public static void run() {
        check(TargetMinecraftVersion.resolve("26.1").effectiveDataVersion() == 4786, "26.1 DataVersion");
        check(TargetMinecraftVersion.resolve("26.1.1").effectiveDataVersion() == 4788, "26.1.1 DataVersion");
        check(TargetMinecraftVersion.resolve("26.1.2").effectiveDataVersion() == 4790, "26.1.2 DataVersion");
        check(TargetMinecraftVersion.resolve("26.2").effectiveDataVersion() == 4903, "26.2 DataVersion");
        check(TargetMinecraftVersion.resolve("26.3").effectiveDataVersion() == 5023, "26.3 DataVersion");
        check(!GameRuleSettings.supports("1.13", "disableRaids"), "1.13 hides future raid rule");
        check(!GameRuleSettings.supports("1.14.2", "disableRaids"), "1.14.2 hides future raid rule");
        check(GameRuleSettings.supports("1.14.3", "disableRaids"), "1.14.3 exposes raid rule");
        check(!GameRuleSettings.supports("1.18.2", "doWardenSpawning"), "1.18 hides future warden rule");
        check(GameRuleSettings.supports("26.1", "doWardenSpawning"), "26.1 exposes warden rule");
        check(!GameRuleSettings.supports("1.19.3", "doVinesSpread"), "1.19.3 hides future vine rule");
        check(GameRuleSettings.supports("1.19.4", "doVinesSpread"), "1.19.4 exposes vine rule");
        check(GameRuleSettings.supports("26.3", "pvp") && !GameRuleSettings.supports("1.21.8", "pvp"), "26.x-only pvp rule");
        check(GameRuleSettings.supports("1.20.5", "spawnChunkRadius") && !GameRuleSettings.supports("26.3", "spawnChunkRadius"), "Spawn chunk radius only before 26.x");

        WorldScene scene = new WorldScene();
        scene.gameRulesNbt = "pvp=false;spawnMonsters=false;freezeDamage=false;maxMinecartSpeed=20;doDaylightCycle=false;keepInventory=true;randomTickSpeed=0;disableRaids=true;doFireTick=false;maxCommandChainLength=32000;maxEntityCramming=16;playersSleepingPercentage=50";
        try {
            Path root = Path.of("game-rule-regression");
            Files.createDirectories(root);
            for (String version : new String[] {"26.1", "26.1.1", "26.1.2", "26.2", "26.3"}) {
                scene.targetMinecraftVersion = version;
                Path world = Files.createTempDirectory(root, "target-");
                WorldExportGameRules.write(scene, world);
                CompoundTag file = NbtIo.readCompressed(world.resolve("data/minecraft/game_rules.dat"), NbtAccounter.unlimitedHeap());
                CompoundTag data = file.getCompoundOrEmpty("data");
                check(file.getInt("DataVersion").orElse(-1) == TargetMinecraftVersion.resolve(version).effectiveDataVersion(),
                        version + " game rule DataVersion");
                check(!data.getBoolean("minecraft:advance_time").orElse(true), version + " daylight rule");
                check(data.getBoolean("minecraft:keep_inventory").orElse(false), version + " inventory rule");
                check(data.getInt("minecraft:random_tick_speed").orElse(-1) == 0, version + " random tick rule");
                check(!data.getBoolean("minecraft:raids").orElse(true), version + " inverted raid rule");
                check(data.getInt("minecraft:fire_spread_radius_around_player").orElse(-1) == 0,
                        version + " fire spread rule");
                check(data.getInt("minecraft:max_command_sequence_length").orElse(-1) == 32000,
                        version + " command limit rule");
                check(data.getInt("minecraft:max_entity_cramming").orElse(-1) == 16,
                        version + " entity cramming rule");
                check(data.getInt("minecraft:players_sleeping_percentage").orElse(-1) == 50,
                        version + " sleeping percentage rule");
                check(!data.getBoolean("minecraft:pvp").orElse(true) && !data.getBoolean("minecraft:spawn_monsters").orElse(true)
                        && !data.getBoolean("minecraft:freeze_damage").orElse(true), version + " new 26.x rules");
                check(data.getInt("minecraft:max_minecart_speed").orElse(-1) == 20, version + " experimental integer rule");
                for (String key : data.keySet()) check(key.startsWith("minecraft:"), version + " only registry keys: " + key);
            }
        } catch (IOException exception) {
            throw new AssertionError("Game rule export regression", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

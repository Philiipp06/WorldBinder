package net.worldbinder.validation;

import com.google.gson.*;
import net.minecraft.nbt.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.export.common.*;
import net.worldbinder.scene.*;
import java.nio.file.*;
import java.util.*;

/** Compare received contents, not merely parsability. Check each player copy and each JSON file independently. */
final class PlayerReadback {
    private PlayerReadback() {}
    static void check(WorldScene scene, Path folder, int version, ExportValidationReport report) {
        if (!scene.exportSettings.includePlayer || scene.player == null || scene.player.nbt == null) return;
        CompoundTag original = AuxiliaryDataWriter.parse(scene.player.nbt);
        List<String> paths = new ArrayList<>(List.of("playerdata/" + scene.player.uuid + ".dat"));
        if (version >= 4786) paths.add("players/data/" + scene.player.uuid + ".dat");
        for (String path : paths) try { compare(scene, original, read(folder.resolve(path)), version); }
        catch (Exception exception) { report.errors.add(Finding.of("worldbinder.finding.own_player_value_value", path, Finding.detail(exception))); }
        if (version < 4786) try { compare(scene, original, read(folder.resolve("level.dat")).getCompound("Data").orElseThrow().getCompound("Player").orElseThrow(), version); }
        catch (Exception exception) { report.errors.add(Finding.of("worldbinder.finding.own_player_level_dat_value", Finding.detail(exception))); }
        String prefix = version >= 4786 ? "players/" : "";
        if (scene.player.statsReceived) try {
            JsonObject actual = JsonParser.parseString(Files.readString(folder.resolve(prefix + "stats/" + scene.player.uuid + ".json"))).getAsJsonObject();
            require(actual.get("DataVersion").getAsInt() == version, Finding.of("worldbinder.finding.statistics_dataversion_differs"));
            require(PlayerProgressConverter.statistics(scene.player,version).equals(actual.get("stats")), Finding.of("worldbinder.finding.received_statistics_categories_values_missing_or_different"));
        } catch (Exception exception) { report.errors.add(Finding.of("worldbinder.finding.own_player_statistics_value", Finding.detail(exception))); }
        if (scene.player.advancementsReceived) try {
            JsonObject actual = JsonParser.parseString(Files.readString(folder.resolve(prefix + "advancements/" + scene.player.uuid + ".json"))).getAsJsonObject();
            require(actual.get("DataVersion").getAsInt() == version, Finding.of("worldbinder.finding.advancements_dataversion_differs"));
            for (var entry : scene.player.advancements.entrySet())
                require(JsonParser.parseString(entry.getValue()).equals(actual.get(entry.getKey())), Finding.of("worldbinder.finding.received_advancement_criteria_missing_or_different_value", entry.getKey()));
            require(actual.size() == scene.player.advancements.size() + 1, Finding.of("worldbinder.finding.unexpected_advancement_records"));
        } catch (Exception exception) { report.errors.add(Finding.of("worldbinder.finding.own_player_advancements_value", Finding.detail(exception))); }
    }

    private static void compare(WorldScene scene, CompoundTag original, CompoundTag actual, int version) {
        for (String key : List.of("Health", "foodLevel", "XpP", "XpLevel", "XpTotal", "Pos", "Rotation"))
            if (original.contains(key)) require(Objects.equals(original.get(key), actual.get(key)), Finding.of("worldbinder.finding.player_value_differs", key));
        Map<Integer, CompoundTag> expected = inventory(original.getList("Inventory").orElseGet(ListTag::new), version);
        if (original.get("equipment") instanceof CompoundTag equipment) {
            if (version >= 4325) {
                CompoundTag converted = equipment.copy();
                for (String key : converted.keySet()) if (converted.get(key) instanceof CompoundTag item) converted.put(key, ItemDataConverter.item(item, version));
                require(converted.equals(actual.get("equipment")), Finding.of("worldbinder.finding.equipment_items_counts_components_missing_or_different"));
            } else {
                String[] armor = {"feet", "legs", "chest", "head", "offhand"};
                for (int i = 0; i < armor.length; i++) if (equipment.get(armor[i]) instanceof CompoundTag item) {
                    CompoundTag converted = item.copy();int slot = (byte) (i < 4 ? 100 + i : 150);
                    converted.putByte("Slot", (byte) slot);expected.put(slot, ItemDataConverter.item(converted, version));
                }
            }
        }
        Map<Integer, CompoundTag> received = inventory(actual.getList("Inventory").orElseThrow(), Integer.MAX_VALUE);
        for (var entry : expected.entrySet()) require(entry.getValue().equals(received.get(entry.getKey())), Finding.of("worldbinder.finding.inventory_item_count_components_missing_or_different_in_slot_value", entry.getKey()));
        require(expected.size() == received.size(), Finding.of("worldbinder.finding.unexpected_missing_inventory_slots"));
        if (scene.player.enderChest != null) require(ItemDataConverter.knownSlotsMatch(scene.player.enderChest,version,actual.get("EnderItems")), Finding.of("worldbinder.finding.ender_chest_slots_items_differ"));
    }
    private static Map<Integer, CompoundTag> inventory(ListTag list, int version) {
        Map<Integer, CompoundTag> result = new LinkedHashMap<>();
        for (Tag tag : list) {
            require(tag instanceof CompoundTag, Finding.of("worldbinder.finding.invalid_inventory_item"));CompoundTag item = (CompoundTag) tag;
            int slot = item.getByte("Slot").orElseThrow();
            require(result.put(slot, version == Integer.MAX_VALUE ? item : ItemDataConverter.item(item, version)) == null, Finding.of("worldbinder.finding.duplicate_inventory_slot_value", slot));
        }
        return result;
    }
    private static CompoundTag read(Path path) throws java.io.IOException { return NbtIo.readCompressed(path, NbtAccounter.create(64 * 1024 * 1024L)); }
    private static void require(boolean value, Finding message) { if (!value) throw new FindingException(message); }
}

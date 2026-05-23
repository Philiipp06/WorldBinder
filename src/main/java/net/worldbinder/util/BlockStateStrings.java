package net.worldbinder.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.Optional;
import java.util.StringJoiner;

public final class BlockStateStrings {
    private BlockStateStrings() {
    }

    public static String toCommandString(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (state.getProperties().isEmpty()) {
            return id;
        }

        StringJoiner joiner = new StringJoiner(",", "[", "]");
        state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .forEach(property -> joiner.add(property.getName() + "=" + propertyValueName(state, property)));
        return id + joiner;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyValueName(BlockState state, Property property) {
        Comparable value = state.getValue(property);
        return property.getName(value);
    }

    public static BlockState parse(String value) {
        String blockId = value;
        String properties = "";
        int propertyStart = value.indexOf('[');
        if (propertyStart >= 0 && value.endsWith("]")) {
            blockId = value.substring(0, propertyStart);
            properties = value.substring(propertyStart + 1, value.length() - 1);
        }

        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null) {
            return null;
        }

        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        BlockState state = block.defaultBlockState();
        if (properties.isBlank()) {
            return state;
        }

        for (String entry : properties.split(",")) {
            String[] split = entry.split("=", 2);
            if (split.length != 2) {
                continue;
            }
            state = applyProperty(state, split[0], split[1]);
        }
        return state;
    }

    private static BlockState applyProperty(BlockState state, String propertyName, String valueName) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return applyProperty(state, property, valueName);
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, Property property, String valueName) {
        Optional parsed = property.getValue(valueName);
        if (parsed.isPresent()) {
            return state.setValue(property, (Comparable) parsed.get());
        }
        return state;
    }
}

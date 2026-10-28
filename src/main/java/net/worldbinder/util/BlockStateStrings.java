package net.worldbinder.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.Optional;
import java.util.HashSet;
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
        try { return parseStrict(value); }
        catch (ValidationException exception) { return null; }
    }

    /** User input must never use the defaulted registry's air fallback or ignore invalid properties. */
    public static BlockState parseStrict(String value) {
        if (value == null || value.isBlank()) throw new ValidationException("syntax", String.valueOf(value));
        value = value.trim();
        String blockId = value;
        String properties = "";
        int propertyStart = value.indexOf('[');
        if (propertyStart >= 0) {
            if (!value.endsWith("]") || value.indexOf('[', propertyStart + 1) >= 0
                    || value.indexOf(']') != value.length() - 1) throw new ValidationException("syntax", value);
            blockId = value.substring(0, propertyStart);
            properties = value.substring(propertyStart + 1, value.length() - 1);
        }

        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) throw new ValidationException("id", blockId);

        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        BlockState state = block.defaultBlockState();
        if (properties.isEmpty()) {
            return state;
        }

        var assigned = new HashSet<String>();
        for (String entry : properties.split(",", -1)) {
            String[] split = entry.split("=", 2);
            if (split.length != 2 || split[0].isBlank() || split[1].isBlank() || !assigned.add(split[0])) throw new ValidationException("syntax", entry);
            Property<?> property = block.getStateDefinition().getProperty(split[0]);
            if (property == null) throw new ValidationException("property", blockId, split[0]);
            if (property.getValue(split[1]).isEmpty()) throw new ValidationException("value", blockId, split[0], split[1]);
            state = applyProperty(state, property, split[1]);
        }
        return state;
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final String key;
        private final Object[] arguments;
        public ValidationException(String reason, Object... arguments) {
            super(reason + ": " + java.util.Arrays.toString(arguments));
            this.key = "worldbinder.export.replacement.invalid_" + reason; this.arguments = arguments;
        }
        public String localizedMessage() { return Lang.string(key, arguments); }
        public String storedMessage(){return Lang.stored(key,arguments);}
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

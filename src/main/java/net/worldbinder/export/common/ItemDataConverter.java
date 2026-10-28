package net.worldbinder.export.common;

import net.minecraft.nbt.*;
import net.worldbinder.scene.ContainerRecord;
import java.util.Map;

/** Converts supported components; the full source stays in the archive and unknown conversions are reported. */
public final class ItemDataConverter {
    private ItemDataConverter() {}
    public static ListTag items(ContainerRecord record, int version) {
        return items(record,version,new ListTag());
    }
    public static ListTag items(ContainerRecord record,int version,ListTag previous) {
        Map<Integer,CompoundTag> slots=new java.util.TreeMap<>();
        if(!record.complete())for(var raw:previous)if(raw instanceof CompoundTag item){int slot=item.getByte("Slot").orElse((byte)-1)&255;if(slot<record.slotCount&&!record.known(slot))slots.put(slot,item(item,version));}
        for(String snbt:record.items)try{var item=TagParser.parseCompoundFully(snbt);slots.put(item.getByte("Slot").orElseThrow()&255,item(item,version));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException exception){throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_captured_item"),exception);}
        ListTag list = new ListTag();
        slots.values().forEach(list::add);
        return list;
    }
    public static boolean knownSlotsMatch(ContainerRecord record,int version,Tag actual) {
        if(!(actual instanceof ListTag list))return false;
        Map<Integer,Tag> observed=new java.util.HashMap<>();
        for(Tag raw:list){if(!(raw instanceof CompoundTag item))return false;int slot=item.getByte("Slot").orElse((byte)-1)&255;if(slot>=record.slotCount||observed.put(slot,item)!=null)return false;}
        Map<Integer,Tag> expected=new java.util.HashMap<>();for(Tag raw:items(record,version)){var item=(CompoundTag)raw;expected.put(item.getByte("Slot").orElseThrow()&255,item);}
        for(int slot=0;slot<record.slotCount;slot++)if(record.known(slot)&&!java.util.Objects.equals(expected.get(slot),observed.get(slot)))return false;
        return true;
    }

    public static CompoundTag item(CompoundTag source, int version) {
        CompoundTag item = source.copy();
        if (version >= 3837) {
            if (version < 4325 && item.get("components") instanceof CompoundTag components) {
                for (String key : new String[]{"minecraft:enchantments","minecraft:stored_enchantments"}) {
                    if (components.get(key) instanceof CompoundTag levels && !levels.contains("levels")) {
                        CompoundTag wrapped=new CompoundTag();wrapped.put("levels",levels.copy());components.put(key,wrapped);
                    }
                }
                for (String key : new String[]{"minecraft:custom_name","minecraft:item_name"})
                    if (components.contains(key)) components.putString(key,textJson(components.get(key)));
                if (components.get("minecraft:lore") instanceof ListTag lore) {
                    ListTag converted=new ListTag();for(Tag line:lore) converted.add(StringTag.valueOf(textJson(line)));components.put("minecraft:lore",converted);
                }
                if (components.get("minecraft:dyed_color") instanceof NumericTag color) {
                    CompoundTag wrapped=new CompoundTag();wrapped.putInt("rgb",color.intValue());components.put("minecraft:dyed_color",wrapped);
                }
                if (version < 4189 && components.get("minecraft:custom_model_data") instanceof CompoundTag model && model.get("floats") instanceof ListTag floats && !floats.isEmpty()
                        && floats.getFirst() instanceof NumericTag value) components.putInt("minecraft:custom_model_data",value.intValue());
            }
            return item;
        }
        item.putByte("Count", item.getInt("count").orElse(item.getInt("Count").orElse(1)).byteValue());
        item.remove("count");
        if (!(item.get("components") instanceof CompoundTag components)) return item;
        CompoundTag tag = item.getCompound("tag").orElseGet(CompoundTag::new).copy();
        if (components.get("minecraft:custom_data") instanceof CompoundTag custom) tag.merge(custom);
        Map.of("minecraft:damage", "Damage", "minecraft:repair_cost", "RepairCost", "minecraft:map_id", "map",
                "minecraft:custom_model_data", "CustomModelData", "minecraft:block_entity_data", "BlockEntityTag")
                .forEach((key, target) -> { if (components.contains(key)) tag.put(target, components.get(key).copy()); });
        CompoundTag display = tag.getCompound("display").orElseGet(CompoundTag::new);
        if (components.contains("minecraft:custom_name")) display.putString("Name", textJson(components.get("minecraft:custom_name")));
        if (components.contains("minecraft:lore") && components.get("minecraft:lore") instanceof ListTag lore) {
            ListTag lines = new ListTag(); for (Tag line : lore) lines.add(StringTag.valueOf(textJson(line))); display.put("Lore", lines);
        }
        if (components.contains("minecraft:dyed_color")) display.put("color", components.get("minecraft:dyed_color").copy());
        if (!display.isEmpty()) tag.put("display", display);
        if (components.contains("minecraft:unbreakable")) tag.putBoolean("Unbreakable", true);
        enchantments(components, tag, "minecraft:enchantments", "Enchantments");
        enchantments(components, tag, "minecraft:stored_enchantments", "StoredEnchantments");
        if (components.get("minecraft:potion_contents") instanceof CompoundTag potion) {
            if (potion.contains("potion")) tag.put("Potion", potion.get("potion").copy());
            if (potion.contains("custom_color")) tag.put("CustomPotionColor", potion.get("custom_color").copy());
        }
        if (components.get("minecraft:container") instanceof ListTag contents) {
            CompoundTag blockEntity=tag.getCompound("BlockEntityTag").orElseGet(CompoundTag::new);ListTag slots=new ListTag();
            for (Tag raw:contents) if(raw instanceof CompoundTag entry&&entry.get("item") instanceof CompoundTag nested) {
                CompoundTag converted=item(nested,version);converted.putByte("Slot",entry.getInt("slot").orElse(0).byteValue());slots.add(converted);
            }
            blockEntity.put("Items",slots);tag.put("BlockEntityTag",blockEntity);
        }
        for (String[] entry:new String[][]{{"minecraft:charged_projectiles","ChargedProjectiles"},{"minecraft:bundle_contents","Items"}}) {
            if(components.get(entry[0]) instanceof ListTag contents){ListTag converted=new ListTag();for(Tag nested:contents)if(nested instanceof CompoundTag stack)converted.add(item(stack,version));tag.put(entry[1],converted);}
        }
        if(tag.contains("ChargedProjectiles"))tag.putBoolean("Charged",true);
        if (!tag.isEmpty()) item.put("tag", tag);
        item.remove("components");
        return item;
    }

    public static String textJson(Tag tag) {
        if (tag instanceof StringTag string && (string.value().startsWith("{") || string.value().startsWith("[") || string.value().startsWith("\""))) {
            try { return com.google.gson.JsonParser.parseString(string.value()).toString(); } catch (com.google.gson.JsonParseException ignored) {}
        }
        return NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, tag).toString();
    }
    private static void enchantments(CompoundTag components, CompoundTag target, String key, String destination) {
        if (!(components.get(key) instanceof CompoundTag values)) return;
        if (values.get("levels") instanceof CompoundTag levels) values = levels;
        ListTag list = new ListTag();
        for (String id : values.keySet()) {
            if (!(values.get(id) instanceof NumericTag level)) continue;
            CompoundTag entry = new CompoundTag(); entry.putString("id", id); entry.putShort("lvl", level.shortValue()); list.add(entry);
        }
        target.put(destination, list);
    }

    public static void inventoryLists(CompoundTag nbt, int version) {
        for (String key : new String[]{"Items", "Inventory", "EnderItems", "HandItems", "ArmorItems"}) {
            if (nbt.get(key) instanceof ListTag items) {
                ListTag converted = new ListTag();
                for (Tag tag : items) converted.add(tag instanceof CompoundTag item ? item(item, version) : tag.copy());
                nbt.put(key, converted);
            }
        }
        for (String key : new String[]{"Item", "item", "SaddleItem", "ArmorItem"})
            if (nbt.get(key) instanceof CompoundTag stack) nbt.put(key, item(stack, version));
        if (nbt.get("equipment") instanceof CompoundTag equipment) {
            for (String key : equipment.keySet()) if (equipment.get(key) instanceof CompoundTag stack) equipment.put(key, item(stack, version));
            if (version < 4325) {
                ListTag armor = new ListTag(), hands = new ListTag();
                for (String key : new String[]{"feet", "legs", "chest", "head"}) armor.add(equipment.getCompound(key).orElseGet(CompoundTag::new));
                for (String key : new String[]{"mainhand", "offhand"}) hands.add(equipment.getCompound(key).orElseGet(CompoundTag::new));
                nbt.put("ArmorItems", armor); nbt.put("HandItems", hands); nbt.remove("equipment");
            }
        }
    }
}

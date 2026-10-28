package net.worldbinder.export.common;

import net.minecraft.nbt.*;
import java.util.*;

/** Only known structural migrations are reversed. Full received NBT remains in the project archive. */
public final class BlockEntityDataConverter {
    private BlockEntityDataConverter(){}
    public static boolean compatible(String source,String target,int version){
        return source.equals(target)||version<1952&&(source.equals("minecraft:oak_sign")&&target.equals("minecraft:sign")||source.equals("minecraft:oak_wall_sign")&&target.equals("minecraft:wall_sign"));
    }
    public static CompoundTag convert(CompoundTag source,int version){
        var nbt=source.copy();
        String id=nbt.getString("id").orElse("");
        if(!id.startsWith("minecraft:"))return nbt;
        if(version<4325&&nbt.contains("CustomName"))nbt.putString("CustomName",ItemDataConverter.textJson(nbt.get("CustomName")));
        if(version<3463&&(id.equals("minecraft:sign")||id.equals("minecraft:hanging_sign"))&&nbt.get("front_text") instanceof CompoundTag front){
            var messages=front.getList("messages").orElseGet(ListTag::new);
            for(int i=0;i<Math.min(4,messages.size());i++)nbt.putString("Text"+(i+1),ItemDataConverter.textJson(messages.get(i)));
            if(front.contains("color"))nbt.put("Color",front.get("color").copy());
            if(version>=2724&&front.contains("has_glowing_text"))nbt.put("GlowingText",front.get("has_glowing_text").copy());
            if(version>=2724&&front.get("filtered_messages") instanceof ListTag filtered)
                for(int i=0;i<Math.min(4,filtered.size());i++)nbt.putString("FilteredText"+(i+1),ItemDataConverter.textJson(filtered.get(i)));
            nbt.remove("front_text");nbt.remove("back_text");nbt.remove("is_waxed");
        }
        if(version<2834&&id.equals("minecraft:mob_spawner")){
            if(nbt.get("SpawnData") instanceof CompoundTag spawn&&spawn.get("entity") instanceof CompoundTag entity)nbt.put("SpawnData",entity.copy());
            if(nbt.get("SpawnPotentials") instanceof ListTag potentials){
                var legacy=new ListTag();
                for(var raw:potentials){
                    if(raw instanceof CompoundTag entry&&entry.get("data") instanceof CompoundTag data&&data.get("entity") instanceof CompoundTag entity){
                        var converted=new CompoundTag();converted.put("Entity",entity.copy());if(entry.contains("weight"))converted.put("Weight",entry.get("weight").copy());legacy.add(converted);
                    }else legacy.add(raw.copy());
                }
                nbt.put("SpawnPotentials",legacy);
            }
        }
        return nbt;
    }
    public static List<String> losses(String snbt,int version){
        if(version>=4325||snbt==null||snbt.isBlank())return List.of();
        var nbt=AuxiliaryDataWriter.parse(snbt);var losses=new ArrayList<String>();
        String id=nbt.getString("id").orElse("");
        if(version<3463&&(id.equals("minecraft:sign")||id.equals("minecraft:hanging_sign"))){
            if(version<2724&&nbt.get("front_text") instanceof CompoundTag front&&front.getBoolean("has_glowing_text").orElse(false))losses.add("worldbinder.legacy.sign_glow");
            if(nbt.get("back_text") instanceof CompoundTag back){
                boolean content=back.getBoolean("has_glowing_text").orElse(false)||!back.getString("color").orElse("black").equals("black");
                for(var message:back.getList("messages").orElseGet(ListTag::new)){String json=ItemDataConverter.textJson(message);content|=!json.equals("\"\"")&&!json.equals("{\"text\":\"\"}");}
                if(content)losses.add("worldbinder.legacy.sign_back");
            }
            if(nbt.getBoolean("is_waxed").orElse(false))losses.add("worldbinder.legacy.sign_wax");
        }
        if(version<2834&&id.equals("minecraft:mob_spawner")&&nbt.toString().contains("custom_spawn_rules"))losses.add("worldbinder.legacy.spawn_rules");
        return losses;
    }
}

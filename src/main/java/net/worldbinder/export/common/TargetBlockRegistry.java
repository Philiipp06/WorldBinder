package net.worldbinder.export.common;

import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.worldbinder.WorldBinder;
import net.worldbinder.util.BlockStateStrings.ValidationException;
import java.util.*;

/** Exact target facts from Mojang's --reports output. No nearest-version registry guesses. */
public final class TargetBlockRegistry {
    private static final Set<Integer> VERSIONS=Set.of(1631,2586,3700);
    private static final Map<Integer,Map<String,Definition>> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Definition {Map<String,List<String>> properties;Map<String,String> defaults;}
    private record Parsed(String id,Map<String,String> properties){}
    private TargetBlockRegistry(){}
    public static boolean available(int version){return VERSIONS.contains(version);}
    private static Map<String,Definition> registry(int version){
        if(!available(version))return Map.of();
        return CACHE.computeIfAbsent(version,key->{
            String resource="/data/worldbinder/target-registry/"+key+".json";
            try(var stream=Objects.requireNonNull(TargetBlockRegistry.class.getResourceAsStream(resource));var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){
                return WorldBinder.GSON.fromJson(reader,new com.google.gson.reflect.TypeToken<Map<String,Definition>>(){}.getType());
            }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        });
    }
    private static Parsed parse(String value){
        if(value==null||value.isBlank())throw new ValidationException("syntax",String.valueOf(value));value=value.trim();
        int start=value.indexOf('[');String rawId=start<0?value:value.substring(0,start);
        var id=Identifier.tryParse(rawId);if(id==null)throw new ValidationException("id",rawId);
        var props=new TreeMap<String,String>();
        if(start>=0){
            if(!value.endsWith("]")||value.indexOf('[',start+1)>=0||value.indexOf(']')!=value.length()-1)throw new ValidationException("syntax",value);
            String properties=value.substring(start+1,value.length()-1);
            if(!properties.isEmpty())for(String entry:properties.split(",",-1)){var parts=entry.split("=",-1);if(parts.length!=2||parts[0].isBlank()||parts[1].isBlank()||props.put(parts[0],parts[1])!=null)throw new ValidationException("syntax",entry);}
        }
        return new Parsed(id.toString(),props);
    }
    private static String state(String id,Map<String,String> props){return id+(props.isEmpty()?"":"["+String.join(",",props.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]");}
    public static String replacement(String value,int version){
        var parsed=parse(value);var definition=registry(version).get(parsed.id);
        if(definition==null)throw new ValidationException("id",parsed.id);
        for(var property:parsed.properties.entrySet()){
            var allowed=definition.properties.get(property.getKey());
            if(allowed==null)throw new ValidationException("property",parsed.id,property.getKey());
            if(!allowed.contains(property.getValue()))throw new ValidationException("value",parsed.id,property.getKey(),property.getValue());
        }
        var props=new TreeMap<>(definition.defaults);props.putAll(parsed.properties);return state(parsed.id,props);
    }
    /** Drop only known obsolete properties; preview comparison reports every dropped property. */
    public static String constrain(String raw,int version){
        if(!available(version))return raw;
        var parsed=parse(raw);var definition=registry(version).get(parsed.id);if(definition==null)return raw;
        var props=new TreeMap<>(parsed.properties);props.keySet().removeIf(name->!definition.properties.containsKey(name));
        return state(parsed.id,props);
    }
    public static Boolean readable(String state,int version){
        if(!available(version)||!ExportCompatibility.id(state).startsWith("minecraft:"))return null;
        try{replacement(state,version);return true;}catch(ValidationException invalid){return false;}
    }
    public static void requireReadable(String state,int version){
        if(Boolean.FALSE.equals(readable(state,version)))throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.legacy.invalid_palette",state,version));
    }
    public static void checkEntry(Tag entry,int version){
        if(!available(version))return;
        if(entry instanceof StringTag text){requireReadable(text.value(),version);return;}
        if(!(entry instanceof CompoundTag compound))return;
        String id=compound.getString("Name").orElse(compound.getString("id").orElse(""));
        var properties=compound.getCompound("Properties").orElse(compound.getCompound("properties").orElseGet(CompoundTag::new));
        var props=new TreeMap<String,String>();for(String key:properties.keySet())props.put(key,properties.getString(key).orElseThrow());
        requireReadable(state(id,props),version);
    }
}

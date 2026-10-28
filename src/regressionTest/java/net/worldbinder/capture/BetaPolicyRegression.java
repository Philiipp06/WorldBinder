package net.worldbinder.capture;

import net.worldbinder.WorldBinder;
import net.worldbinder.export.common.*;
import net.worldbinder.scene.*;
import java.util.*;

final class BetaPolicyRegression {
    private static int checks;
    static int run(){
        var before=record(new boolean[]{true,true,false,false},0,"minecraft:diamond");String saved=WorldBinder.GSON.toJson(before);
        var fresh=record(new boolean[]{true,true,true,false},1,"minecraft:emerald");
        var result=ContainerMerging.merge(before,fresh,ContainerMerging.Strategy.KEEP);check(result==before&&WorldBinder.GSON.toJson(before).equals(saved),"Keep retains the original confirmed inventory");
        result=ContainerMerging.merge(before,fresh,ContainerMerging.Strategy.OVERWRITE);
        check(result.known(0)&&ContainerMerging.slots(result).get(0)==null&&ContainerMerging.slots(result).get(1).contains("emerald"),"Confirmed empty removes an old item and confirmed contents replace slots");
        check(result.known(2)&&!result.known(3),"Empty received slots are known; unreceived slots remain unknown");
        result=ContainerMerging.merge(before,fresh,ContainerMerging.Strategy.FILL_MISSING);
        check(ContainerMerging.slots(result).get(0).contains("diamond")&&!ContainerMerging.slots(result).containsKey(1)&&result.known(2)&&!result.known(3),"Fill missing does not overwrite existing items or verified empty slots");
        var partial=record(new boolean[]{false,false,true,false},2,"minecraft:gold_ingot");result=ContainerMerging.merge(before,partial,ContainerMerging.Strategy.OVERWRITE);
        check(ContainerMerging.slots(result).get(0).contains("diamond")&&result.known(1)&&ContainerMerging.slots(result).get(2).contains("gold_ingot"),"Partial updates never erase older confirmed data");
        check(WorldBinder.GSON.toJson(before).equals(saved),"Every merge leaves its input unchanged");
        var legacy=record(null,0,"minecraft:diamond");check(legacy.complete()&&legacy.known(3),"Old complete menu records retain verified empty slots");
        var reload=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(result),ContainerRecord.class);check(!reload.known(3)&&reload.known(1),"Per-slot knowledge persists over serialization");
        var oldItems=new net.minecraft.nbt.ListTag();try{oldItems.add(net.minecraft.nbt.TagParser.parseCompoundFully("{Slot:0b,id:\"minecraft:diamond\",count:1}"));}catch(Exception failure){throw new AssertionError(failure);}
        var exported=ItemDataConverter.items(partial,5023,oldItems);check(exported.size()==2&&ItemDataConverter.knownSlotsMatch(partial,5023,exported),"Export preserves old positive data in unreceived slots while validating confirmed updates");
        var broken=record(new boolean[]{true,true,false,false},0,"minecraft:diamond");broken.items.add(broken.items.getFirst());boolean rejected=false;try{ContainerMerging.merge(before,broken,ContainerMerging.Strategy.OVERWRITE);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected&&WorldBinder.GSON.toJson(before).equals(saved),"Duplicate slot input is rejected without changing saved data");
        var settings=new CaptureSettings();settings.entities.droppedItems=true;settings.entities.experienceOrbs=true;settings.entities.armorStands=true;settings.entities.hostileMobs=true;settings.entities.entityIds.add("minecraft:cow");
        for(String type:List.of("minecraft:item","minecraft:experience_orb","minecraft:armor_stand","minecraft:zombie","minecraft:cow"))check(ExportFiltering.excludedType(type,settings.entities),"Live type filter: "+type);
        check(!ExportFiltering.excludedType("minecraft:item_frame",settings.entities),"Live filters retain unrelated entities");
        settings.liveEntityFilters=true;var profile=settings.copy();check(profile.liveEntityFilters&&profile.entities.entityIds.contains("minecraft:cow")&&profile.containerStrategy==ContainerMerging.Strategy.OVERWRITE,"Project input profile survives serialization independently of export filters");
        try{
            var file=java.nio.file.Files.createTempFile(java.nio.file.Path.of("."),"replay-identity-",".mcpr");
            try{
                class Zip {private final java.io.File input=file.toFile();}class Subclass extends Zip{}class Wrapper {private Object delegate;Wrapper(Object next){delegate=next;}}
                var method=net.worldbinder.replay.ReplayPlayback.class.getDeclaredMethod("filePath",Object.class);method.setAccessible(true);
                check(file.toAbsolutePath().normalize().equals(method.invoke(null,new Wrapper(new Wrapper(new Subclass())))),"Replay identity resolves the real recording through current managed wrappers and inherited ZIP fields");
                var cycle=new Wrapper(null);cycle.delegate=cycle;check(method.invoke(null,cycle)==null,"Broken replay wrapper cycles fail closed without blocking the client");
            }finally{java.nio.file.Files.delete(file);}
        }catch(Exception error){throw new AssertionError(error);}
        return checks;
    }
    private static ContainerRecord record(boolean[] known,int slot,String item){var value=new ContainerRecord();value.type="minecraft:chest";value.slotCount=4;value.knownSlots=known;value.items.add("{Slot:"+slot+"b,id:\""+item+"\",count:1}");return value;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}

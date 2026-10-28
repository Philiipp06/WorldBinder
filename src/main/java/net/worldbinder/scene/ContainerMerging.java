package net.worldbinder.scene;

import net.minecraft.nbt.TagParser;
import net.worldbinder.validation.Finding;
import net.worldbinder.validation.FindingException;
import java.util.*;

/** Slot knowledge is independent of item presence. A confirmed empty slot is never a missing slot. */
public final class ContainerMerging {
    public enum Strategy { KEEP, OVERWRITE, FILL_MISSING }
    private ContainerMerging() {}

    public static ContainerRecord merge(ContainerRecord previous,ContainerRecord received,Strategy strategy) {
        if(received==null)return previous;
        validate(received);
        if(previous!=null)validate(previous);
        if(strategy==Strategy.KEEP)return previous;
        boolean same=previous!=null&&Objects.equals(previous.type,received.type)&&previous.slotCount==received.slotCount;
        Map<Integer,String> old=same?slots(previous):Map.of(),fresh=slots(received);
        var result=new ContainerRecord();result.type=received.type;result.slotCount=received.slotCount;
        result.observedAt=Math.max(same?previous.observedAt:0,received.observedAt);result.knownSlots=new boolean[result.slotCount];
        for(int slot=0;slot<result.slotCount;slot++) {
            boolean knownOld=same&&previous.known(slot),knownNew=received.known(slot);
            boolean takeNew=knownNew&&(strategy==Strategy.OVERWRITE||!knownOld);
            result.knownSlots[slot]=takeNew||knownOld;
            String item=takeNew?fresh.get(slot):knownOld?old.get(slot):null;
            if(item!=null)result.items.add(item);
        }
        return result;
    }

    public static Map<Integer,String> slots(ContainerRecord record) {
        Map<Integer,String> result=new TreeMap<>();
        for(String item:record.items)try {
            int slot=TagParser.parseCompoundFully(item).getByte("Slot").orElseThrow()&255;
            if(slot>=record.slotCount||result.putIfAbsent(slot,item)!=null||!record.known(slot))throw invalid();
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException|NoSuchElementException exception){throw invalid();}
        return result;
    }
    public static void validate(ContainerRecord record) {
        if(record.type==null||record.slotCount<0||record.slotCount>256||record.items==null
                ||record.knownSlots!=null&&record.knownSlots.length!=record.slotCount)throw invalid();
        slots(record);
    }
    private static FindingException invalid(){return new FindingException(Finding.of("worldbinder.containers.invalid_slots"));}
}

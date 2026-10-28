package net.worldbinder.scene;

import java.util.ArrayList;
import java.util.List;

/** Exists only after a complete server menu update, including an explicitly empty inventory. */
public final class ContainerRecord {
    public String type;
    public int slotCount;
    public long observedAt;
    public List<String> items = new ArrayList<>();
    /** Null in legacy records means a complete authoritative menu, including its empty slots. */
    public boolean[] knownSlots;
    public boolean known(int slot){return slot>=0&&slot<slotCount&&(knownSlots==null||knownSlots.length==slotCount&&knownSlots[slot]);}
    public int knownCount(){int count=0;for(int slot=0;slot<slotCount;slot++)if(known(slot))count++;return count;}
    public boolean complete(){return knownCount()==slotCount;}

    public static String blockKey(int x, int y, int z) { return "block:" + x + "," + y + "," + z; }
    public static String entityKey(String uuid) { return "entity:" + uuid; }
}

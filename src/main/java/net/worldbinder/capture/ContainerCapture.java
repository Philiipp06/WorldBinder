package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.worldbinder.scene.*;
import java.util.*;

/** Associates a server menu with a recent interaction; stores only authoritative slot packets, not predictions. */
public final class ContainerCapture {
    private BlockPos block;
    private Entity entity;
    private long interactionAt;
    private int menuId = -1;
    private int stateId = -1;
    private List<Integer> menuSlots = List.of();
    private List<String> items;
    private boolean[] known;
    private final Map<String,ContainerRecord> before=new HashMap<>();
    private boolean manual,decided,recompute;
    private ContainerMerging.Strategy strategy;
    private String targetType;
    private boolean ambiguous;
    private int verifiedMenu=-1;
    boolean verified(int id){return verifiedMenu==id&&menuId==id;}
    boolean attached(int id){return menuId==id;}

    void target(BlockPos pos) {
        boolean conflict = menuId < 0 && System.currentTimeMillis() - interactionAt < 3000L && (ambiguous || entity != null || block != null && !block.equals(pos));
        clear(); ambiguous = conflict; block = pos.immutable(); interactionAt = System.currentTimeMillis();
    }
    void target(Entity value) {
        boolean conflict = menuId < 0 && System.currentTimeMillis() - interactionAt < 3000L && (ambiguous || block != null || entity != null && entity != value);
        clear(); ambiguous = conflict; entity = value; interactionAt = System.currentTimeMillis();
    }
    void clear() { block = null; entity = null; menuId = -1; items = null; known=null;before.clear();decided=false;recompute=false;menuSlots = List.of(); targetType = null; stateId = -1; ambiguous = false; verifiedMenu=-1; }

    void opened(WorldScene scene,Minecraft client, int id) {
        if (ambiguous || client.player == null || client.level == null || System.currentTimeMillis() - interactionAt > 6500L) { clear(); return; }
        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu.containerId != id) { clear(); return; }
        if (block != null) targetType = BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(block).getBlock()).toString();
        else if (entity instanceof Container) targetType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        else { clear(); return; }
        MenuType<?> type = menu.getType();
        if (!supported(targetType, type)) { clear(); return; }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory) && !slot.isFake()) indices.add(i);
        }
        if (indices.isEmpty()) { clear(); return; }
        menuSlots = indices;
        items=new ArrayList<>(Collections.nCopies(indices.size(),null));known=new boolean[indices.size()];
        manual=scene.captureSettings.manualContainerMerge;strategy=scene.captureSettings.containerStrategy;
        menuId = id;
    }

    private boolean supported(String type, MenuType<?> menu) {
        if (type.equals("minecraft:chest") || type.equals("minecraft:trapped_chest") || type.equals("minecraft:barrel")
                || type.equals("minecraft:ender_chest") || type.contains("chest_boat") || type.contains("chest_raft")
                || type.equals("minecraft:chest_minecart")) return menu == MenuType.GENERIC_9x3 || menu == MenuType.GENERIC_9x6;
        if (type.endsWith("shulker_box")) return menu == MenuType.SHULKER_BOX;
        return switch (type) {
            case "minecraft:furnace" -> menu == MenuType.FURNACE;
            case "minecraft:blast_furnace" -> menu == MenuType.BLAST_FURNACE;
            case "minecraft:smoker" -> menu == MenuType.SMOKER;
            case "minecraft:hopper", "minecraft:hopper_minecart" -> menu == MenuType.HOPPER;
            case "minecraft:dispenser", "minecraft:dropper" -> menu == MenuType.GENERIC_3x3;
            case "minecraft:brewing_stand" -> menu == MenuType.BREWING_STAND;
            default -> false; // Crafting/trading/output slots must never be mistaken for stored inventory.
        };
    }

    void content(WorldScene scene, Minecraft client, int id, int state, List<ItemStack> packetItems) {
        if (id != menuId || menuSlots.isEmpty() || stale(state)) return;
        List<String> received = new ArrayList<>();
        for (int index : menuSlots) {
            if (index >= packetItems.size()) return; // A partial menu is not evidence of emptiness.
            received.add(encode(client, packetItems.get(index)));
        }
        items = received;
        Arrays.fill(known,true);
        stateId = state;
        commit(scene, client);
    }

    void slot(WorldScene scene, Minecraft client, int id, int state, int slot, ItemStack item) {
        if (id != menuId || items == null || stale(state)) return;
        int index = menuSlots.indexOf(slot);
        if (index < 0) return;
        items.set(index, encode(client, item));
        known[index]=true;
        stateId = state;
        commit(scene, client);
    }

    private boolean stale(int incoming) { return stateId >= 0 && incoming != stateId && ((incoming - stateId) & 32767) > 16384; }

    private void commit(WorldScene scene, Minecraft client) {
        verifiedMenu=-1;
        boolean complete=true;for(boolean slot:known)complete&=slot;
        if(manual&&!decided)return;
        if (block != null) {
            if(scene.selectionBounds!=null&&!scene.selectionBounds.contains(block.getX(),block.getY(),block.getZ()))return;
            var state = client.level.getBlockState(block);
            if (!targetType.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) return;
            if (targetType.equals("minecraft:ender_chest")) {
                if (items.size() == 27) {
                    var player=AuxiliaryCapture.player(scene,client);String key="ender_chest";
                    if(!before.containsKey(key))before.put(key,player.enderChest);
                    player.enderChest=ContainerMerging.merge(strategy==ContainerMerging.Strategy.KEEP||recompute?before.get(key):player.enderChest,record(targetType,0,27),strategy);
                    if(complete)verifiedMenu=menuId;
                }
                return;
            }
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                if (items.size() != 54) return;
                BlockPos other = block.relative(ChestBlock.getConnectedDirection(state));
                var neighbor = client.level.getBlockState(other);
                if (neighbor.getBlock() != state.getBlock() || neighbor.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                        || !other.relative(ChestBlock.getConnectedDirection(neighbor)).equals(block)) return;
                // Vanilla menu order is RIGHT half first, LEFT half second, independently of clicked half.
                BlockPos first = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT ? block : other;
                BlockPos second = first.equals(block) ? other : block;
                if(scene.selectionBounds==null||scene.selectionBounds.contains(first.getX(),first.getY(),first.getZ()))store(scene,ContainerRecord.blockKey(first.getX(),first.getY(),first.getZ()),record(targetType,0,27));
                if(scene.selectionBounds==null||scene.selectionBounds.contains(second.getX(),second.getY(),second.getZ()))store(scene,ContainerRecord.blockKey(second.getX(),second.getY(),second.getZ()),record(targetType,27,27));
                if(complete)verifiedMenu=menuId;
            } else {
                int expected = expectedSlots(targetType);
                if (expected == items.size()) {store(scene,ContainerRecord.blockKey(block.getX(),block.getY(),block.getZ()),record(targetType,0,expected));if(complete)verifiedMenu=menuId;}
            }
        } else if (entity != null && items.size() == expectedSlots(targetType)
                &&(scene.selectionBounds==null||scene.selectionBounds.contains(entity.getX(),entity.getY(),entity.getZ()))) {
            if(scene.captureSettings.excludes(entity))return;
            store(scene,ContainerRecord.entityKey(entity.getUUID().toString()),record(targetType,0,items.size()));
            if(complete)verifiedMenu=menuId;
        }
    }

    private void store(WorldScene scene,String key,ContainerRecord received) {
        if(!before.containsKey(key))before.put(key,scene.containers.get(key));
        var value=ContainerMerging.merge(strategy==ContainerMerging.Strategy.KEEP||recompute?before.get(key):scene.containers.get(key),received,strategy);
        if(value==null)scene.containers.remove(key);else scene.containers.put(key,value);
    }
    boolean apply(WorldScene scene,Minecraft client,ContainerMerging.Strategy choice,boolean projectDefault) {
        if(menuId<0||client.player==null||client.player.containerMenu.containerId!=menuId||known==null)return false;
        int count=0;for(boolean slot:known)if(slot)count++;if(count==0)return false;
        strategy=choice;decided=true;recompute=true;
        try{commit(scene,client);}finally{recompute=false;}
        if(projectDefault)scene.captureSettings.containerStrategy=choice;
        return true;
    }
    int knownSlots(){int count=0;if(known!=null)for(boolean slot:known)if(slot)count++;return count;}
    int totalSlots(){return known==null?0:known.length;}

    public static int expectedSlots(String type) {
        if (type == null) return -1;
        if (type.endsWith("shulker_box") || type.contains("chest") || type.equals("minecraft:barrel")) return 27;
        return switch (type) {
            case "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker" -> 3;
            case "minecraft:hopper", "minecraft:hopper_minecart", "minecraft:brewing_stand" -> 5;
            case "minecraft:dispenser", "minecraft:dropper" -> 9;
            default -> -1;
        };
    }

    private ContainerRecord record(String type, int start, int size) {
        ContainerRecord result = new ContainerRecord();
        result.type = type; result.slotCount = size; result.observedAt = System.currentTimeMillis();
        result.knownSlots=new boolean[size];
        for (int slot = 0; slot < size; slot++) {
            result.knownSlots[slot]=known[start+slot];
            String encoded = items.get(start + slot);
            if (encoded == null) continue;
            try {
                CompoundTag item = net.minecraft.nbt.TagParser.parseCompoundFully(encoded).copy();
                item.putByte("Slot", (byte) slot);
                result.items.add(item.toString());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
        }
        return result;
    }

    private static String encode(Minecraft client, ItemStack item) {
        if (item.isEmpty()) return null;
        var ops = client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, item).getOrThrow().toString();
    }
}

package net.worldbinder.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.*;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method="handleLogin",at=@At("HEAD"))
    private void worldbinder$login(ClientboundLoginPacket packet,CallbackInfo ci){worldbinder$boundary("login");}
    @Inject(method="handleRespawn",at=@At("HEAD"))
    private void worldbinder$respawn(ClientboundRespawnPacket packet,CallbackInfo ci){worldbinder$boundary("respawn");}
    @Inject(method="handleConfigurationStart",at=@At("HEAD"))
    private void worldbinder$configuration(ClientboundStartConfigurationPacket packet,CallbackInfo ci){worldbinder$boundary("configuration");}
    @Inject(method="handleSetEntityData",at=@At("RETURN"))
    private void worldbinder$entityData(ClientboundSetEntityDataPacket packet,CallbackInfo ci){worldbinder$entityState(packet.id());}
    @Inject(method="handleSetEquipment",at=@At("RETURN"))
    private void worldbinder$entityEquipment(ClientboundSetEquipmentPacket packet,CallbackInfo ci){worldbinder$entityState(packet.getEntity());}
    @org.spongepowered.asm.mixin.Unique private static void worldbinder$entityState(int id){
        var client=net.minecraft.client.Minecraft.getInstance();
        if(client.level!=null&&WorldBinderClient.capture()!=null){
            var entity=client.level.getEntity(id);
            if(entity!=null)WorldBinderClient.capture().cacheEntityHot(entity);
        }
    }
    @org.spongepowered.asm.mixin.Unique private static void worldbinder$boundary(String reason){
        if(net.minecraft.client.Minecraft.getInstance().isSameThread()&&WorldBinderClient.capture()!=null)WorldBinderClient.capture().worldBoundary(reason);
    }
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void worldbinder$mapBlock(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        var pos=packet.getPos();net.worldbinder.render.MapData.invalidate(pos.getX()>>4,pos.getZ()>>4);
        if(WorldBinderClient.capture()!=null)WorldBinderClient.capture().onReplayBlockChange(pos.getX()>>4,pos.getZ()>>4);
    }
    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void worldbinder$mapBlocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        java.util.Set<Long> chunks=new java.util.HashSet<>();packet.runUpdates((pos,state)->chunks.add(net.minecraft.world.level.ChunkPos.pack(pos.getX()>>4,pos.getZ()>>4)));
        for(long key:chunks){int x=net.minecraft.world.level.ChunkPos.getX(key),z=net.minecraft.world.level.ChunkPos.getZ(key);net.worldbinder.render.MapData.invalidate(x,z);if(WorldBinderClient.capture()!=null)WorldBinderClient.capture().onReplayBlockChange(x,z);}
    }
    @Inject(method = "handleAwardStats", at = @At("RETURN"))
    private void worldbinder$statistics(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onStatsPacket(packet);
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void worldbinder$open(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) WorldBinderClient.capture().onContainerOpened(packet.getContainerId());
    }
    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void worldbinder$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) WorldBinderClient.capture().onContainerContent(packet);
    }
    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void worldbinder$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) WorldBinderClient.capture().onContainerSlot(packet);
    }
    @Inject(method = "handleMapItemData", at = @At("RETURN"))
    private void worldbinder$map(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) WorldBinderClient.capture().onMapPacket(packet);
    }
    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("RETURN"))
    private void worldbinder$advancements(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) WorldBinderClient.capture().onAdvancementsPacket(packet);
    }
    @Inject(method = "handleLightUpdatePacket", at = @At("RETURN"))
    private void worldbinder$light(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) WorldBinderClient.capture().onLightPacket(net.worldbinder.compat.PacketAccess.get(packet,"x","getX"), net.worldbinder.compat.PacketAccess.get(packet,"z","getZ"), net.worldbinder.compat.PacketAccess.get(packet,"lightData","getLightData"));
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"), require = 0)
    private void worldbinder$chunkLoaded(@Coerce Object packet, CallbackInfo ci) {
        int[] chunk = chunkCoordinates(packet);
        if (chunk != null && WorldBinderClient.capture() != null) {
            net.worldbinder.render.MapData.invalidate(chunk[0],chunk[1]);
            if (packet instanceof ClientboundLevelChunkWithLightPacket light)
                WorldBinderClient.capture().onLightPacket(chunk[0], chunk[1], net.worldbinder.compat.PacketAccess.get(light,"lightData","getLightData"));
            WorldBinderClient.capture().onChunkLoadedOrUpdated(chunk[0], chunk[1]);
            WorldBinderClient.capture().onReplayBlockChange(chunk[0],chunk[1]);
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"), require = 0)
    private void worldbinder$chunkUnloaded(@Coerce Object packet, CallbackInfo ci) {
        int[] chunk = chunkCoordinates(packet);
        if (chunk != null && WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onChunkUnloaded(chunk[0], chunk[1]);
        }
    }

    private static int[] chunkCoordinates(Object packet) {
        if (packet == null) {
            return null;
        }
        Integer x = intValue(packet, "getX", "x");
        Integer z = intValue(packet, "getZ", "z");
        if (x != null && z != null) {
            return new int[]{x, z};
        }
        Object pos = value(packet, "pos", "getPos", "chunkPos", "getChunkPos");
        if (pos != null) {
            x = intValue(pos, "x", "getX");
            z = intValue(pos, "z", "getZ");
            if (x != null && z != null) {
                return new int[]{x, z};
            }
        }
        return null;
    }

    private static Integer intValue(Object target, String... names) {
        Object value = value(target, names);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Object value(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}

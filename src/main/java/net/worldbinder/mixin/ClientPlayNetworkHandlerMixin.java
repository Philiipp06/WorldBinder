package net.worldbinder.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onStatistics", at = @At("RETURN"), require = 0)
    private void worldbinder$statistics(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onStatisticsPacketSeen();
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"), require = 0)
    private void worldbinder$chunkLoaded(@Coerce Object packet, CallbackInfo ci) {
        int[] chunk = chunkCoordinates(packet);
        if (chunk != null && WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onChunkLoadedOrUpdated(chunk[0], chunk[1]);
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

package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import java.util.*;

/** QA only: delay delivery of actual received packets, and prove that a partial slot list is not authoritative. */
public final class DelayedContainerPackets {
    private record Pending(ClientPacketListener listener,ClientboundContainerSetContentPacket packet,long at){}
    private static final List<Pending> pending=new ArrayList<>();
    public static volatile boolean enabled;
    private static boolean replaying;
    private static long ticks;
    public static int partialChecks,delayedPackets;
    public static int nextDelayTicks=20;
    public static int minimumDelayTicks;
    public static synchronized boolean intercept(ClientPacketListener listener,ClientboundContainerSetContentPacket packet){
        if(!enabled||replaying||packet.containerId()<=0)return false;
        pending.add(new Pending(listener,packet,ticks+Math.max(nextDelayTicks,minimumDelayTicks)));nextDelayTicks=20;delayedPackets++;return true;
    }
    public static synchronized void tick(Minecraft client){
        ticks++;
        if(client.player==null||client.level==null){pending.clear();return;}
        var capture=net.worldbinder.client.WorldBinderClient.capture();
        for(var entry:List.copyOf(pending)){
            var packet=entry.packet();
            if(capture.isCapturing()&&client.player.containerMenu.containerId==packet.containerId()&&packet.items().size()>1&&entry.at()==ticks+19){
                String before=net.worldbinder.WorldBinder.GSON.toJson(capture.exportPreview().containers);
                capture.onContainerContent(new ClientboundContainerSetContentPacket(packet.containerId(),packet.stateId(),packet.items().subList(0,1),packet.carriedItem()));
                if(!before.equals(net.worldbinder.WorldBinder.GSON.toJson(capture.exportPreview().containers)))throw new AssertionError("Partial actual container packet replaced authoritative contents");partialChecks++;
            }
            if(entry.at()>ticks)continue;
            pending.remove(entry);replaying=true;try{entry.listener().handleContainerContent(packet);}finally{replaying=false;}
        }
    }
}

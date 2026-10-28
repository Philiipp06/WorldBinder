package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.*;
import net.worldbinder.util.Lang;

final class ContainerScanner {
    record Target(BlockPos block,Entity entity,String key) {}
    private final ContainerScanQueue<Target> queue=new ContainerScanQueue<>();
    private boolean enabled,issuing,closing,force,armed;
    private int discovery;
    private long ticks;
    boolean issuing(){return issuing;} boolean enabled(){return enabled;}
    int retries(){return queue.retries();}
    void toggle(){enabled=!enabled;armed=enabled;if(enabled)queue.resume();else queue.suspend();}
    void resume(){enabled=true;armed=true;queue.resume();}
    void rescan(){queue.rescan();discovery=0;enabled=true;force=true;armed=true;}
    void suspend(){queue.suspend();}
    String status(){return Lang.string("worldbinder.scanner.progress",Lang.string("worldbinder.scanner."+(enabled?queue.phase().name().toLowerCase(java.util.Locale.ROOT):"off")),queue.confirmed(),queue.pending(),queue.failed())+" · "+Lang.string("worldbinder.scanner.retries",queue.retries());}
    void opened(int id){if(enabled&&!queue.opened(id,ticks,120))queue.suspend();}
    void verified(int id){if(enabled)queue.received(id);}
    void manual(){if(enabled&&!issuing&&!closing)queue.suspend();}
    void screenClosed(){if(!enabled)return;if(closing)queue.closed(queue.menu(),ticks);else manual();}
    void tick(Minecraft client,WorldScene scene,boolean allowed){
        ticks++;
        if(!enabled)return;
        if(armed&&client.level!=null&&ClientCompat.screen(client)!=null)return;
        if(!allowed||client.player==null||client.level==null||client.gameMode==null){queue.suspend();return;}
        if(queue.phase()==ContainerScanQueue.Phase.SUSPENDED)return;
        armed=false;
        var screen=ClientCompat.screen(client);
        if(screen!=null&&!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)){queue.suspend();return;}
        if(queue.phase()==ContainerScanQueue.Phase.IDLE&&(screen!=null||client.player.containerMenu!=client.player.inventoryMenu)){queue.suspend();return;}
        if(queue.phase()==ContainerScanQueue.Phase.IDLE)discover(client,scene);
        var action=queue.tick(ticks,120,2);
        if(action==ContainerScanQueue.Action.CLOSE){
            if(client.player.containerMenu.containerId!=queue.menu()){queue.suspend();return;}
            closing=true;try{client.player.closeContainer();}finally{closing=false;}
            return;
        }
        if(action!=ContainerScanQueue.Action.OPEN)return;
        var target=queue.current();
        if(ClientCompat.screen(client)!=null||!reachable(client,target)){queue.suspend();return;}
        InteractionHand hand=client.player.getMainHandItem().isEmpty()?InteractionHand.MAIN_HAND:client.player.getOffhandItem().isEmpty()?InteractionHand.OFF_HAND:null;
        if(hand==null){queue.suspend();return;} // Never place/use a held item as an automated fallback.
        issuing=true;
        try{
            if(target.block()!=null)client.gameMode.useItemOn(client.player,hand,new BlockHitResult(Vec3.atCenterOf(target.block()),Direction.UP,target.block(),false));
            else {
                // A normal secondary interaction opens a chest boat instead of mounting it. Only the
                // interaction reads this temporary input; movement and subsequent user input are unchanged.
                var input=client.player.input;var previous=input.keyPresses;
                String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.entity().getType()).toString();
                try{if(type.contains("chest_boat")||type.contains("chest_raft"))input.keyPresses=new net.minecraft.world.entity.player.Input(previous.forward(),previous.backward(),previous.left(),previous.right(),previous.jump(),true,previous.sprint());
                    client.gameMode.interact(client.player,target.entity(),new EntityHitResult(target.entity()),hand);
                }finally{input.keyPresses=previous;}
            }
        }finally{issuing=false;}
    }
    private void discover(Minecraft client,WorldScene scene){
        var center=client.player.blockPosition();int radius=4,side=9;
        for(int checked=0;checked<32;checked++){
            int index=discovery++%(side*side*side);
            var pos=center.offset(index%side-radius,(index/side)%side-radius,index/(side*side)-radius);
            if(scene.selectionBounds!=null&&!scene.selectionBounds.contains(pos.getX(),pos.getY(),pos.getZ())||net.worldbinder.compat.ReceivedChunks.chunk(client.level,pos.getX()>>4,pos.getZ()>>4)==null)continue;
            var state=client.level.getBlockState(pos);String type=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if(ContainerCapture.expectedSlots(type)<0||!type.equals("minecraft:ender_chest")&&!net.worldbinder.validation.ExportValidator.isContainer(type))continue;
            var canonical=pos;BlockPos other=null;
            if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
                other=pos.relative(ChestBlock.getConnectedDirection(state));if(other.asLong()<pos.asLong())canonical=other;
            }
            String key=ContainerRecord.blockKey(canonical.getX(),canonical.getY(),canonical.getZ());
            boolean known=type.equals("minecraft:ender_chest")?scene.player!=null&&scene.player.enderChest!=null&&scene.player.enderChest.complete():complete(scene,ContainerRecord.blockKey(pos.getX(),pos.getY(),pos.getZ()))&&(other==null||complete(scene,ContainerRecord.blockKey(other.getX(),other.getY(),other.getZ())));
            if(known&&WorldBinder.config().scannerSkipVerified&&!force)continue;
            var target=new Target(pos.immutable(),null,key);if(reachable(client,target))queue.offer(key,target);
        }
        int checked=0;for(var entity:client.level.entitiesForRendering()){
            if(scene.captureSettings.excludes(entity))continue;
            if(++checked>128)break;String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if(ContainerCapture.expectedSlots(type)<0||!net.worldbinder.validation.ExportValidator.isContainer(type))continue;
            if(scene.selectionBounds!=null&&!scene.selectionBounds.contains(entity.getX(),entity.getY(),entity.getZ()))continue;
            String key=ContainerRecord.entityKey(entity.getUUID().toString());if(WorldBinder.config().scannerSkipVerified&&!force&&complete(scene,key))continue;
            var target=new Target(null,entity,key);if(reachable(client,target))queue.offer(key,target);
        }
    }
    private boolean reachable(Minecraft client,Target target){
        if(target==null)return false;
        double range=Math.min(4.0,target.block()!=null?client.player.blockInteractionRange():client.player.entityInteractionRange());
        Vec3 end=target.block()!=null?Vec3.atCenterOf(target.block()):target.entity().getBoundingBox().getCenter();
        if(client.player.getEyePosition().distanceToSqr(end)>range*range)return false;
        if(target.entity()!=null)return target.entity().isAlive()&&client.player.hasLineOfSight(target.entity());
        var hit=client.level.clip(new ClipContext(client.player.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        return hit.getBlockPos().equals(target.block());
    }
    private static boolean complete(WorldScene scene,String key){var value=scene.containers.get(key);return value!=null&&value.complete();}
}

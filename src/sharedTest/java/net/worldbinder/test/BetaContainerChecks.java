package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.*;
import net.worldbinder.util.Lang;

/** Real authoritative menus and actual mouse events: merger buttons must never reach inventory slots. */
final class BetaContainerChecks {
    private int stage,ticks,since;int checks;
    private CaptureSettings original;
    private java.util.concurrent.CompletableFuture<WorldScene> frozen;
    private void next(int value){stage=value;since=ticks;}
    private void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    private boolean click(Minecraft client,String key){
        var screen=ClientCompat.screen(client);if(screen==null)return false;
        for(var child:screen.children())if(child instanceof Button button&&button.active&&button.visible&&button.getMessage().getString().equals(Lang.string(key))) {
            boolean consumed=screen.mouseClicked(new MouseButtonEvent(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            screen.mouseReleased(new MouseButtonEvent(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)));check(consumed,"Container overlay consumes its mouse input before inventory handling");return true;
        }return false;
    }
    boolean tick(Minecraft client)throws Exception{
        ticks++;if(ticks-since>600)throw new AssertionError("Beta container checks timed out at "+stage+" controls="+(ClientCompat.screen(client)==null?"no screen":ClientCompat.screen(client).children().stream().filter(Button.class::isInstance).map(Button.class::cast).map(b->b.getMessage().getString()+" visible="+b.visible+" active="+b.active).toList()));
        var capture=WorldBinderClient.capture();
        switch(stage){
            case 0->{BetaUiChecks.prepareImages(client);original=WorldBinder.config().captureDefaults.copy();var policy=new CaptureSettings();policy.liveEntityFilters=true;policy.entities.armorStands=true;policy.entities.droppedItems=true;policy.entities.experienceOrbs=true;policy.entities.entityIds.add("minecraft:chest_minecart");WorldBinder.config().captureDefaults=policy;
                ClientCompat.setScreen(client,null);ExpansionScenario.command(client,"tp @a 1.5 65 1.5","setblock 3 64 1 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:2}]}");capture.startRoamingCapture("beta_container_"+System.currentTimeMillis());next(1);}
            case 1->{if(capture.scannedChunks()==0||!client.level.getBlockState(new BlockPos(3,64,1)).is(net.minecraft.world.level.block.Blocks.CHEST))return false;
                frozen=capture.prepareAreaExport();next(2);}
            case 2->{if(!frozen.isDone()||capture.isSaving())return false;var scene=frozen.join();
                check(scene.entities.stream().noneMatch(e->e.type.equals("minecraft:armor_stand")||e.type.equals("minecraft:chest_minecart")||e.type.equals("minecraft:item")),"Live filters exclude entities from the actual frozen cache before export");
                check(client.level.entitiesForRendering().iterator().hasNext(),"WorldBinder input filters do not suppress Minecraft entity packets");open(client);next(3);}
            case 3->{if(!(ClientCompat.screen(client) instanceof AbstractContainerScreen)||capture.containerKnownSlots()!=27)return false;
                check(record().items.getFirst().contains("diamond"),"Initial complete server menu stores the original inventory");client.player.closeContainer();ExpansionScenario.command(client,"data merge block 3 64 1 {Items:[{Slot:1b,id:\"minecraft:emerald\",count:3}]}");capture.exportPreview().captureSettings.manualContainerMerge=true;next(4);}
            case 4->{if(ticks-since<8)return false;open(client);next(5);}
            case 5->{if(!(ClientCompat.screen(client) instanceof AbstractContainerScreen)||capture.containerKnownSlots()!=27)return false;
                check(record().items.getFirst().contains("diamond"),"Manual mode stages new server data without overwriting existing contents");if(click(client,"worldbinder.containers.menu"))next(6);}
            case 6->{if(!click(client,"worldbinder.containers.strategy.fill_missing"))return false;check(record().items.size()==1&&record().items.getFirst().contains("diamond"),"Filling missing leaves the previously verified empty slot unchanged");next(7);}
            case 7->{if(!click(client,"worldbinder.containers.strategy.overwrite"))return false;check(record().items.size()==1&&record().items.getFirst().contains("emerald")&&record().items.getFirst().contains("Slot:1b"),"Overwrite accepts the server-confirmed empty old slot and full new item");next(8);}
            case 8->{if(!click(client,"worldbinder.containers.strategy.keep"))return false;check(record().items.size()==1&&record().items.getFirst().contains("diamond"),"Keep restores the pre-menu saved contents after another manual choice");
                check(client.player.containerMenu.slots.get(1).getItem().is(net.minecraft.world.item.Items.EMERALD)&&client.player.containerMenu.slots.get(0).getItem().isEmpty(),"Actual Minecraft slots are unchanged; merger mouse clicks move no items");ClientCompat.chat(client).clearMessages(true);ExpansionUiChecks.shot(client,"container-merging");client.player.closeContainer();capture.cancelActiveCapture();WorldBinder.config().captureDefaults=original;return true;}
        }return false;
    }
    private ContainerRecord record(){return WorldBinderClient.capture().exportPreview().containers.get(ContainerRecord.blockKey(3,64,1));}
    private void open(Minecraft client){client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(new BlockPos(3,64,1)),Direction.UP,new BlockPos(3,64,1),false));}
}

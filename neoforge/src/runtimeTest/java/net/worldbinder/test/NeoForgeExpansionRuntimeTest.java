package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import java.nio.file.*;

final class NeoForgeExpansionRuntimeTest {
    private int stage,ticks;private final String phase=System.getProperty("worldbinder.expansionPhase");
    private net.minecraft.client.gui.screens.Screen acceptedWarning;
    private final ExpansionScenario scenario=new ExpansionScenario(phase.equals("resume"));
    NeoForgeExpansionRuntimeTest(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick(Minecraft.getInstance()));}
    private void tick(Minecraft client){
        if(stage==99)return;ticks++;
        try{
            if((stage==2||stage==5||stage==6)&&net.worldbinder.compat.ClientCompat.screen(client) instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen){
                if(acceptedWarning==screen)return;
                for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.active&&b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString())){acceptedWarning=screen;b.onPress(new net.minecraft.client.input.KeyEvent(257,0,0));WorldBinder.LOGGER.info("QA accepted the vanilla experimental-world warning for its own generated fixture");return;}
            }
            if(ticks>6000)throw new AssertionError("NeoForge expansion timed out at "+stage+"/"+scenario.stage);
            if(stage==0){
                if(ticks<30)return;client.options.pauseOnLostFocus=false;
                String name;
                if(phase.equals("resume")){var json=com.google.gson.JsonParser.parseString(Files.readString(WorldBinderPaths.GAME_DIR.resolve("expansion-fixture.json"))).getAsJsonObject();name=Path.of(json.get("world").getAsString()).getFileName().toString();}
                else name=Files.readString(WorldBinderPaths.GAME_DIR.resolve("fixture-name.txt"));
                client.createWorldOpenFlows().openWorld(name,()->{throw new AssertionError("Expansion source world could not open");});stage=1;
            }else if(stage==1){
                if(client.level==null||client.player==null||client.getSingleplayerServer()==null)return;
                scenario.tick(client);
                if(scenario.done){
                    if(scenario.exported!=null){client.disconnectWithSavingScreen();stage=2;}else if(scenario.betaExported!=null){client.disconnectWithSavingScreen();stage=5;}else finish(client);
                }
            }else if(stage==2){
                if(client.getSingleplayerServer()!=null)return;
                client.createWorldOpenFlows().openWorld(scenario.exported.getFileName().toString(),()->{throw new AssertionError("Resumed export could not open");});stage=3;
            }else if(stage==3){
                if(client.level==null||client.player==null)return;
                if(!client.level.getBlockState(new net.minecraft.core.BlockPos(15,63,1)).is(net.minecraft.world.level.block.Blocks.STONE))throw new AssertionError("Actual NeoForge resumed export lost its recorded block");
                if(scenario.betaExported!=null){client.disconnectWithSavingScreen();stage=5;}else finish(client);
            }else if(stage==5){
                if(client.getSingleplayerServer()!=null)return;
                client.createWorldOpenFlows().openWorld(scenario.betaExported.getFileName().toString(),()->{throw new AssertionError("Multi-dimension export could not open");});stage=6;
            }else if(stage==6){
                if(client.level==null||client.player==null||net.worldbinder.compat.ReceivedChunks.chunk(client.level,0,0)==null)return;
                if(!client.level.getBlockState(new net.minecraft.core.BlockPos(1,64,1)).is(net.minecraft.world.level.block.Blocks.STONE))throw new AssertionError("Actual NeoForge multi-dimension export lost Overworld data");
                ExpansionScenario.command(client,"execute in minecraft:the_nether run tp @a 1.5 65 1.5");stage=7;
            }else if(stage==7){
                if(client.level==null||!client.level.dimension().identifier().toString().equals("minecraft:the_nether")||net.worldbinder.compat.ReceivedChunks.chunk(client.level,0,0)==null)return;
                if(!client.level.getBlockState(new net.minecraft.core.BlockPos(1,64,1)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK))throw new AssertionError("Actual NeoForge multi-dimension export lost Nether data");finish(client);
            }
        }catch(Throwable error){
            try{Files.writeString(WorldBinderPaths.GAME_DIR.resolve("expansion-"+phase+"-RESULT.txt"),"FAIL "+stage+"/"+scenario.stage+": "+error);}catch(Exception ignored){}
            WorldBinder.LOGGER.error("WORLDBINDER_NEOFORGE_EXPANSION_FAIL",error);stage=99;client.stop();
        }
    }
    private void finish(Minecraft client)throws Exception{Files.writeString(WorldBinderPaths.GAME_DIR.resolve("expansion-"+phase+"-RESULT.txt"),"PASS actual NeoForge shared expansion "+phase+": "+scenario.checks+" checks\n");WorldBinder.LOGGER.info("WORLDBINDER_NEOFORGE_EXPANSION_PASS {}",phase);stage=99;client.stop();}
}

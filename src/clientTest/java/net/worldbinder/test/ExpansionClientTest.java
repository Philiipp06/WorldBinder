package net.worldbinder.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.worldbinder.io.WorldBinderPaths;
import java.nio.file.*;

final class ExpansionClientTest {
    static void run(ClientGameTestContext context,String phase){
        var scenario=new ExpansionScenario(phase.equals("resume"));
        try{
            var existing=phase.equals("resume")?com.google.gson.JsonParser.parseString(Files.readString(WorldBinderPaths.GAME_DIR.resolve("expansion-fixture.json"))).getAsJsonObject():null;
            try(var world=existing==null?context.worldBuilder().create():new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,Path.of(existing.get("world").getAsString())).open()){
                context.waitFor(client->client.player!=null&&client.level!=null);
                while(!scenario.done){context.runOnClient(client->{try{scenario.tick(client);}catch(Exception error){throw new AssertionError(error);}});context.waitTicks(1);}
                context.takeScreenshot("expansion-"+phase+"-complete");
            }
            if(scenario.exported!=null)try(var export=new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,scenario.exported).open()){
                context.waitFor(client->client.player!=null&&client.level!=null);context.runOnClient(client->{if(!client.level.getBlockState(new net.minecraft.core.BlockPos(15,63,1)).is(net.minecraft.world.level.block.Blocks.STONE))throw new AssertionError("Actual resumed export lost its recorded block");});context.takeScreenshot("expansion-export-opened");
            }
            if(scenario.betaExported!=null)try(var export=new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,scenario.betaExported).open()){
                context.waitFor(client->client.player!=null&&client.level!=null&&net.worldbinder.compat.ReceivedChunks.chunk(client.level,0,0)!=null);
                context.runOnClient(client->{if(!client.level.getBlockState(new net.minecraft.core.BlockPos(1,64,1)).is(net.minecraft.world.level.block.Blocks.STONE))throw new AssertionError("Actual multi-dimension export lost its Overworld block");ExpansionScenario.command(client,"execute in minecraft:the_nether run tp @a 1.5 65 1.5");});
                context.waitFor(client->client.level!=null&&client.level.dimension().identifier().toString().equals("minecraft:the_nether")&&net.worldbinder.compat.ReceivedChunks.chunk(client.level,0,0)!=null);
                context.runOnClient(client->{if(!client.level.getBlockState(new net.minecraft.core.BlockPos(1,64,1)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK))throw new AssertionError("Actual multi-dimension export lost its Nether block at equal coordinates");});context.takeScreenshot("beta-multi-dimension-export-nether");
            }
        }catch(Exception error){throw new AssertionError(error);}
    }
}

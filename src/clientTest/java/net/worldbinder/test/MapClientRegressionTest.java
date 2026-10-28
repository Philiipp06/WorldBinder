package net.worldbinder.test;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.RemotePlayer;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.render.*;
import net.worldbinder.ui.*;

final class MapClientRegressionTest {
    static void run(ClientGameTestContext context) {
        context.runOnClient(c->{
            check(!WorldBinderClient.capture().isCapturing(),"Map test starts without recording");
            WorldBinder.config().mapExploreMode=false;WorldBinder.config().map3d=false;
            MapExplorer.get().clear();MapVoxels.get().clear();
            c.getConnection().sendCommand("worldbinder");
        });
        context.waitFor(c->ClientCompat.screen(c) instanceof WorldBinderScreen);
        context.runOnClient(c->ClientCompat.setScreen(c,new WorldBinderMapScreen(null)));
        context.waitFor(c->MapExplorer.get().size()>0&&MapVoxels.get().size()>0);
        context.waitFor(c->MapExplorer.get().snapshot(0L)!=null&&MapVoxels.get().columns(0L)!=null);
        context.runOnClient(c->{
            check(c.level.getBlockState(new net.minecraft.core.BlockPos(1,63,1)).is(net.minecraft.world.level.block.Blocks.STONE),"Received fixture surface is stone at Y63");
            check(MapExplorer.get().snapshot(0L).heights[17]==63,"2D surface uses the received block height");
            var columns=MapVoxels.get().columns(0L);
            check(columns.data[columns.offsets[17]]==63,"3D surface uses the received block height");
            ClientCompat.setScreen(c,null); // Let the integrated server tick and deliver its block packet.
            c.getSingleplayerServer().execute(()->c.getSingleplayerServer().overworld().setBlockAndUpdate(new net.minecraft.core.BlockPos(1,64,1),net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()));
        });
        context.waitFor(c->c.level.getBlockState(new net.minecraft.core.BlockPos(1,64,1)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK));
        context.runOnClient(c->ClientCompat.setScreen(c,new WorldBinderMapScreen(null)));
        context.waitFor(c->MapExplorer.get().snapshot(0L).heights[17]==64&&MapVoxels.get().columns(0L).data[MapVoxels.get().columns(0L).offsets[17]]==64);
        context.runOnClient(c->{
            ClientCompat.setScreen(c,null);
            c.getSingleplayerServer().execute(()->c.getSingleplayerServer().overworld().setBlockAndUpdate(new net.minecraft.core.BlockPos(1,64,1),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
        });
        context.waitFor(c->c.level.getBlockState(new net.minecraft.core.BlockPos(1,64,1)).isAir());
        context.runOnClient(c->ClientCompat.setScreen(c,new WorldBinderMapScreen(null)));
        context.waitFor(c->MapExplorer.get().snapshot(0L).heights[17]==63&&MapVoxels.get().columns(0L).data[MapVoxels.get().columns(0L).offsets[17]]==63);
        context.runOnClient(c->{
            check(!WorldBinder.config().mapExploreMode&&!WorldBinderClient.capture().isCapturing(),"Both map sources populate in 2D without explore/recording");
            var received=net.worldbinder.compat.ReceivedChunks.loaded(c.level);
            check(received.size()>1,"Map uses received chunk cache, not rendered sections");
            var other=new RemotePlayer(c.level,new GameProfile(java.util.UUID.randomUUID(),"HeadingQA"));other.setId(2_000_000);
            other.setPos(c.player.getX()+4,c.player.getY(),c.player.getZ()+4);other.yHeadRotO=359;other.setYHeadRot(1);c.level.addEntity(other);
            check(Math.abs(MarkerAngles.look(other,.5f))<.01,"Remote-player heading follows short 359/1 crossing");
            check(Math.abs(MarkerAngles.look(other,120f)-1)<.01,"Partial tick cannot extrapolate into repeated rotations");
        });
        context.takeScreenshot("release-map-2d-no-capture");
        final int[] count={0};
        context.runOnClient(c->{count[0]=MapExplorer.get().size();WorldBinder.config().map3d=true;WorldBinder.config().map3dYaw=359;});
        context.waitTicks(20);context.takeScreenshot("release-map-3d-no-capture");
        context.runOnClient(c->{
            check(MapExplorer.get().size()>=count[0],"2D/3D switch preserves received surface data");
            WorldBinder.config().map3dYaw=1;WorldBinder.config().map3d=false;
            c.level.removeEntity(2_000_000,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            WorldBinderClient.selections().setSelection(new net.worldbinder.selection.CaptureBounds(-2,63,1,3,65,3).selection());
            var map=new WorldBinderMapScreen(null);ClientCompat.setScreen(c,map);press(map,"worldbinder.map.export");press(map,"worldbinder.area.select_map");
            check(map.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_W,0,0)),"Packaged jar accepts actual runtime map keyboard input");
            double x=map.width*.38,y=map.height*.55;var button=new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0);
            check(map.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x,y,button),false),"Packaged jar accepts the actual runtime left mouse button");
            map.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(x+24,y+12,button),24,12);map.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x+24,y+12,button));
            var bounds=net.worldbinder.selection.CaptureBounds.of(WorldBinderClient.selections().getSelection());
            check(ClientCompat.screen(c) instanceof WorldBinderRegionScreen&&bounds.minY()==63&&bounds.maxY()==65&&bounds.width()>1&&bounds.length()>1,"Packaged jar map drag opens the shared Y editor on the actual Minecraft runtime");
            ClientCompat.setScreen(c,null);
        });
        WorldBinder.LOGGER.info("WORLDBINDER_MAP_COMMAND_PASS: client-only command, both map providers without capture/explore, remote heading wrap, retained surfaces");
    }
    private static void press(net.minecraft.client.gui.screens.Screen screen,String key){for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.active&&button.getMessage().getString().equals(net.worldbinder.util.Lang.string(key))){button.onPress(new net.minecraft.client.input.KeyEvent(257,0,0));return;}throw new AssertionError("Missing active map action "+key);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

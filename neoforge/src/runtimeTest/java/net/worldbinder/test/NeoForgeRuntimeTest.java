package net.worldbinder.test;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.*;
import net.worldbinder.ui.*;
import net.worldbinder.validation.ExportValidator;
import net.worldbinder.movement.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Loaded only by runSmokeTest; never included in the published NeoForge mod. */
@Mod(value="worldbinder_qa",dist=Dist.CLIENT)
public final class NeoForgeRuntimeTest {
    private int stage,ticks,stageTick;
    private CompletableFuture<Void> reload;
    private final String name="neoforge_packet_smoke_"+java.util.UUID.randomUUID().toString().substring(0,8);
    private double startX,startZ,startY;
    public NeoForgeRuntimeTest() { if(System.getProperty("worldbinder.proxyPhase")!=null){
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->{DelayedContainerPackets.tick(Minecraft.getInstance());if(++ticks!=40)return;var client=Minecraft.getInstance();client.options.renderDistance().set(4);client.options.simulationDistance().set(4);Thread.ofPlatform().daemon().name("WorldBinder-Neo-Proxy-QA").start(()->{try{NativeArtifactChecks.neoForge();ProxyNetworkChecks.run(new NativeClientContext(),System.getProperty("worldbinder.proxyPhase"));client.execute(client::stop);}catch(Throwable error){WorldBinder.LOGGER.error("WORLDBINDER_NEO_PROXY_FAIL",error);client.execute(()->System.exit(1));}});});return;
    }if(System.getProperty("worldbinder.expansionPhase")!=null){new NeoForgeExpansionRuntimeTest();return;}if(Boolean.getBoolean("worldbinder.multiplayerOnly")){new NeoForgeMultiplayerTest();return;}NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick(Minecraft.getInstance())); }
    private void next(int value){stage=value;stageTick=ticks;WorldBinder.LOGGER.info("NeoForge QA stage {}",stage);}
    private void tick(Minecraft client) {
        if(stage==99)return;
        ticks++;
        try {
            if(ticks>3600)throw new AssertionError("Runtime test timed out at stage "+stage);
            if(stage>1&&ticks-stageTick>400)throw new AssertionError("Stage "+stage+" timed out; screen="+ClientCompat.screen(client)+", loaded="+(client.getConnection()!=null&&client.getConnection().hasClientLoaded()));
            switch(stage) {
                case 0 -> {
                    if(ticks<30)return;
                    client.options.pauseOnLostFocus=false;
                    check(WorldBinderClient.capture()!=null,"Common initializer ran on NeoForge");
                    check(KeyMapping.get("key.worldbinder.open_menu")!=null,"NeoForge key adapter registered bindings");
                    client.createWorldOpenFlows().openWorld(Files.readString(WorldBinderPaths.GAME_DIR.resolve("fixture-name.txt")),()->{throw new AssertionError("Fixture could not be opened");});next(1);
                }
                case 1 -> {
                    if(client.player==null||client.level==null||ticks-stageTick<30)return;
                    if(client.getConnection()==null||!client.getConnection().hasClientLoaded())return;
                    if(!client.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.STONE))return;
                    check(client.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.STONE),"Exported vanilla blocks load on NeoForge");
                    client.getConnection().sendCommand("worldbinder");next(10);
                }
                case 10 -> {
                    if(!(ClientCompat.screen(client) instanceof WorldBinderScreen))return;
                    WorldBinder.config().mapExploreMode=false;WorldBinder.config().map3d=false;
                    net.worldbinder.render.MapExplorer.get().clear();net.worldbinder.render.MapVoxels.get().clear();
                    ClientCompat.setScreen(client,new WorldBinderMapScreen(null));next(11);
                }
                case 11 -> {
                    if(net.worldbinder.render.MapExplorer.get().size()==0||net.worldbinder.render.MapVoxels.get().size()==0)return;
                    check(!WorldBinderClient.capture().isCapturing(),"NeoForge 2D and 3D sources populate without recording or explore mode");
                    check(net.worldbinder.render.MarkerAngles.interpolate(359,1,.5f)==0,"NeoForge marker wrap is stable");
                    WorldBinder.config().map3d=true;next(12);
                }
                case 12 -> {
                    if(!net.worldbinder.render.Map3DView.shared().hasFrame()||ticks-stageTick<10)return;
                    check(net.worldbinder.render.MapExplorer.get().size()>0,"NeoForge rendered 3D without losing 2D terrain");
                    WorldBinder.config().map3d=false;
                    var config=WorldBinder.config();config.crashRecovery=true;config.recoveryAutosaveSeconds=1;config.autoDeleteRecovery=false;config.appendTimestampToArchiveName=false;config.includeServerResourcePack=false;
                    config.captureMinY=64;config.captureMaxY=65;config.roamingRadiusChunks=1;
                    WorldBinderClient.capture().startRoamingCapture(name);
                    ClientCompat.setScreen(client,null);
                    WorldBinder.LOGGER.info("NeoForge QA player {} alive {} block entity {}",client.player.position(),client.player.isAlive(),client.level.getBlockEntity(new BlockPos(2,64,2)));
                    var result=client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(2.5,64.5,2.5),Direction.UP,new BlockPos(2,64,2),false));
                    WorldBinder.LOGGER.info("NeoForge QA use chest result {}",result);next(2);
                }
                case 2 -> {
                    if(!(ClientCompat.screen(client) instanceof AbstractContainerScreen<?>)||ticks-stageTick<8)return;
                    var record=WorldBinderClient.capture().exportPreview().containers.get(ContainerRecord.blockKey(2,64,2));
                    check(record!=null&&record.items.size()==1&&record.items.getFirst().contains("Slot:5b"),"NeoForge receives and associates complete container contents");
                    client.player.closeContainer();next(3);
                }
                case 3 -> {
                    if(ticks-stageTick<4)return;
                    ClientCompat.setScreen(client,new WorldBinderMapScreen(null));next(4);
                }
                case 4 -> {
                    if(ticks-stageTick<10)return;
                    check(WorldBinderClient.capture().chunkSnapshotsView().values().stream().anyMatch(s->s.hasBiomeData),"NeoForge captures original biomes");
                    check(WorldBinderClient.capture().chunkSnapshotsView().values().stream().anyMatch(s->!s.lightEstimated),"NeoForge captures received light");
                    ClientCompat.setScreen(client,WorldBinderConfigScreen.movement(null));
                    var config=WorldBinder.config();
                    for(MovementTool tool:MovementTool.values()){config.movement.enabled(tool,true);config.movement.value(tool,tool.initial);}
                    WorldBinder.saveConfig();
                    check(net.worldbinder.config.WorldBinderConfig.load().movement.enabled(MovementTool.FLY),"NeoForge movement settings roundtrip");next(5);
                }
                case 5 -> {
                    if(ticks-stageTick<10)return;
                    for(MovementTool tool:MovementTool.values())WorldBinder.config().movement.enabled(tool,false);
                    ClientCompat.setScreen(client,null);
                    WorldBinder.config().movementMasterEnabled=true;
                    client.getSingleplayerServer().execute(()->{
                        var server=client.getSingleplayerServer();var source=server.createCommandSourceStack();
                        server.getCommands().performPrefixedCommand(source,"fill -8 63 -8 -2 63 -2 minecraft:stone");
                        server.getCommands().performPrefixedCommand(source,"gamemode survival @a");
                        server.getCommands().performPrefixedCommand(source,"tp @a -4.5 64 -4.5");
                    });next(50);
                }
                case 50 -> {
                    if(ticks-stageTick<10||client.player.getAbilities().mayfly||!client.player.onGround())return;
                    WorldBinder.config().movement.enabled(MovementTool.SPEED,true);WorldBinder.config().movement.value(MovementTool.SPEED,2);
                    startX=client.player.getX();startZ=client.player.getZ();client.options.keyUp.setDown(true);next(51);
                }
                case 51 -> {
                    if(ticks-stageTick<5)return;
                    check(client.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)>.19,"NeoForge speed modifier runs through real player mixins");
                    check(Math.hypot(client.player.getX()-startX,client.player.getZ()-startZ)>.01,"NeoForge movement input moves the real player");
                    client.options.keyUp.setDown(false);WorldBinder.config().movement.enabled(MovementTool.SPEED,false);
                    WorldBinder.config().movement.enabled(MovementTool.HIGH_JUMP,true);WorldBinder.config().movement.value(MovementTool.HIGH_JUMP,2);
                    startY=client.player.getY();client.options.keyJump.setDown(true);next(52);
                }
                case 52 -> {
                    if(ticks-stageTick<2)return;
                    check(client.player.getY()>startY+.3,"NeoForge high jump changes actual vertical movement");
                    client.options.keyJump.setDown(false);WorldBinder.config().movement.enabled(MovementTool.HIGH_JUMP,false);next(53);
                }
                case 53 -> {
                    if(!client.player.onGround()||ticks-stageTick<4)return;
                    WorldBinder.config().movement.enabled(MovementTool.FLY,true);client.options.keyJump.setDown(true);next(54);
                }
                case 54 -> {if(ticks-stageTick<2)return;client.options.keyJump.setDown(false);next(55);}
                case 55 -> {if(ticks-stageTick<1)return;client.options.keyJump.setDown(true);next(56);}
                case 56 -> {
                    if(ticks-stageTick<2)return;
                    check(FlightMotion.active(client.player),"NeoForge double-jump flight activates on real input");
                    client.options.keyJump.setDown(false);WorldBinder.config().movement.disableAll();reload=client.reloadResourcePacks();next(6);
                }
                case 6 -> {
                    if(!reload.isDone())return;
                    check(!reload.isCompletedExceptionally(),"NeoForge resource reload completes with common loading-overlay hooks");
                    WorldBinderClient.capture().abortQueueAndSaveNow();next(7);
                }
                case 7 -> {
                    if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return;
                    Button button=(Button)ClientCompat.screen(client).children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("Save and export")).findFirst().orElseThrow();
                    button.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,0,0));next(8);
                }
                case 8 -> {
                    if(WorldBinderClient.capture().isCapturing()||WorldBinderClient.capture().isSaving())return;
                    Path folder=WorldBinderPaths.MINECRAFT_SAVES.resolve(name);WorldScene scene=WorldBinderClient.scenes().read(folder);
                    check(ExportValidator.validate(scene,folder).filesValid,"NeoForge capture/archive/export/read-back roundtrip");
                    Path recovery=net.worldbinder.scene.RecoveryProjects.folder(WorldBinderPaths.RECOVERY_ROOT,scene).resolve("worldbinder/worldbinder_archive.json");
                    check(Files.isRegularFile(recovery),"NeoForge recorded a background recovery snapshot while capturing");
                    Files.writeString(WorldBinderPaths.GAME_DIR.resolve("RESULT.txt"),"PASS NeoForge 26.3.0.95 / Minecraft 26.3: native world load, packet capture, container slots, original biomes/light, keybindings, map/HUD and 24 movement settings; real speed, high-jump and double-jump flight physics; resource reload; archive/export read-back. Exhaustive 24-tool combinations and external-server/manual visual review remain separate.\n");
                    WorldBinder.LOGGER.info("WORLDBINDER_NEOFORGE_RUNTIME_PASS");stage=99;client.stop();
                }
            }
        }catch(Throwable error){
            try{Files.writeString(WorldBinderPaths.GAME_DIR.resolve("RESULT.txt"),"FAIL stage "+stage+": "+error);}catch(Exception ignored){}
            WorldBinder.LOGGER.error("WORLDBINDER_NEOFORGE_RUNTIME_FAIL stage "+stage,error);stage=99;client.stop();
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

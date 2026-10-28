package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.worldbinder.WorldBinder;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.ui.WorldBinderScreen;
import java.nio.file.*;

final class NeoForgeMultiplayerTest {
    private int stage,ticks;
    private com.google.gson.JsonObject control;
    private final CommandAndStorageChecks commands=new CommandAndStorageChecks();
    private String captureName;
    NeoForgeMultiplayerTest(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick(Minecraft.getInstance()));}
    private void tick(Minecraft client){if(stage==99)return;ticks++;try{
        if(ticks>2400)throw new AssertionError("NeoForge multiplayer command/pack timed out at "+stage);
        if(stage==0&&ticks>30){
            client.options.pauseOnLostFocus=false;control=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("worldbinder.qaControl")))).getAsJsonObject();
            var server=new ServerData("Vanilla server QA",control.get("host").getAsString(),ServerData.Type.OTHER);server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
            ConnectScreen.startConnecting(new TitleScreen(),client,ServerAddress.parseString(server.ip),server,false,null);stage=1;
        }else if(stage==1&&client.player!=null&&client.getConnection()!=null&&client.getConnection().hasClientLoaded()){
            String hash;try(var input=client.getResourceManager().getResource(Identifier.parse("minecraft:textures/block/stone.png")).orElseThrow().open()){
                hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            }
            if(!hash.equals(control.get("textureSHA256").getAsString()))return;
            client.getConnection().sendCommand("worldbinder");stage=2;
        }else if(stage==2&&ClientCompat.screen(client) instanceof WorldBinderScreen){
            var ops=com.google.gson.JsonParser.parseString(Files.readString(Path.of(control.get("root").getAsString()).resolve("ops.json"))).getAsJsonArray();
            if(!ops.isEmpty())throw new AssertionError("Test server must have no operators");
            stage=3;
        }else if(stage==3){
            commands.tick(client,new net.worldbinder.scene.WorldScene(),WorldBinderPaths.MINECRAFT_SAVES,"en");
            if(!commands.commandsDone())return;
            ClientCompat.setScreen(client,null);var config=WorldBinder.config();config.targetMinecraftVersion="26.3";config.captureMinY=63;config.captureMaxY=66;config.roamingRadiusChunks=2;config.appendTimestampToArchiveName=false;config.crashRecovery=false;config.autoSaveOnDisconnect=true;
            captureName="neoforge_disconnect_"+System.currentTimeMillis();net.worldbinder.client.WorldBinderClient.capture().startRoamingCapture(captureName);stage=4;
        }else if(stage==4&&net.worldbinder.client.WorldBinderClient.capture().scannedChunks()>0){
            client.disconnect(new TitleScreen(),false);stage=5;
        }else if(stage==5&&client.player==null&&!net.worldbinder.client.WorldBinderClient.capture().isCapturing()&&!net.worldbinder.client.WorldBinderClient.capture().isSaving()){
            var path=WorldBinderPaths.MINECRAFT_SAVES.resolve(captureName);var scene=net.worldbinder.client.WorldBinderClient.scenes().read(path);
            if(scene.blockCount()==0||!net.worldbinder.validation.ExportValidator.validate(scene,path).filesValid||!net.worldbinder.storage.StorageFlow.progress().context().disconnected())throw new AssertionError("Disconnect export did not retain received data and pass read-back validation");
            ExpansionUiChecks.shot(client,"neoforge-dedicated-disconnect");
            Files.writeString(WorldBinderPaths.GAME_DIR.resolve("RESULT.txt"),"PASS NeoForge 26.3: root and all nine client subcommands on an unmodded dedicated vanilla server without operators; actual HTTP server pack active; capture/disconnect/automatic world export and full read-back validation.\n");
            WorldBinder.LOGGER.info("WORLDBINDER_NEOFORGE_MULTIPLAYER_PASS");stage=6;
        }else if(stage==6&&ticks%20==0){stage=99;client.stop();}
    }catch(Throwable error){try{Files.writeString(WorldBinderPaths.GAME_DIR.resolve("RESULT.txt"),"FAIL "+error);}catch(Exception ignored){}WorldBinder.LOGGER.error("NeoForge multiplayer test failed",error);stage=99;client.stop();}}
}

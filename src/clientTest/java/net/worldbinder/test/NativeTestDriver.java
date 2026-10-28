package net.worldbinder.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.worldbinder.WorldBinder;
import java.nio.file.*;

/** QA-only entrypoint; never packaged in the release mod. */
public final class NativeTestDriver implements ClientModInitializer {
    private int ticks;private boolean started;
    @Override public void onInitializeClient(){
        if(System.getProperty("worldbinder.replayProvider")==null&&System.getProperty("worldbinder.proxyPhase")==null)return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(System.getProperty("worldbinder.proxyPhase")!=null)DelayedContainerPackets.tick(client);if(started||++ticks<40)return;started=true;
            client.options.renderDistance().set(4);client.options.simulationDistance().set(5);
            Thread.ofPlatform().name("WorldBinder-Native-QA").daemon().start(()->{
                try{
                    String expected=System.getProperty("worldbinder.compatibilityJar");
                    Path artifact=Path.of(WorldBinder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    if(expected!=null&&!artifact.getFileName().toString().equals(expected))throw new AssertionError("Native QA must load the packaged artifact: "+artifact);
                    WorldBinder.LOGGER.info("WORLDBINDER_RELEASE_ARTIFACT {} SHA-256 {}",artifact,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))));
                    if(System.getProperty("worldbinder.proxyPhase")!=null)ProxyNetworkChecks.run(new NativeClientContext(),System.getProperty("worldbinder.proxyPhase"));
                    else ReplayCaptureClientTest.run(new NativeClientContext(),System.getProperty("worldbinder.replayProvider"),System.getProperty("worldbinder.replayPhase","record"));client.execute(client::stop);
                }
                catch(Throwable error){String kind=System.getProperty("worldbinder.proxyPhase")!=null?"proxy":"replay";String phase=System.getProperty("worldbinder."+kind+"Phase");WorldBinder.LOGGER.error("WORLDBINDER_NATIVE_{}_FAIL",kind.toUpperCase(java.util.Locale.ROOT),error);try{Files.writeString(net.worldbinder.io.WorldBinderPaths.GAME_DIR.resolve(kind+"-"+phase+"-RESULT.txt"),"FAIL "+error);}catch(Exception ignored){}client.execute(()->System.exit(1));}
            });
        });
    }
}

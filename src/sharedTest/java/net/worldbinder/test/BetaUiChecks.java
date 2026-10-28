package net.worldbinder.test;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.ui.*;
import net.worldbinder.util.Lang;
import java.util.concurrent.CompletableFuture;

/** Actual language reload and native screenshots of the beta menus on either loader. */
final class BetaUiChecks {
    static void prepareImages(net.minecraft.client.Minecraft c){
        c.options.guiScale().set(2);c.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);WorldBinder.config().tooltipsEnabled=false;WorldBinder.config().mapZoom=8;
            try{
                for(String name:new String[]{"resizeGui","resizeDisplay"})try{c.getClass().getMethod(name).invoke(c);break;}catch(NoSuchMethodException ignored){}
                for(Object owner:new Object[]{c,c.getClass().getField("gui").get(c)})for(var method:owner.getClass().getMethods())if(method.getParameterCount()==0&&method.getReturnType().getSimpleName().equals("ToastManager")){Object manager=method.invoke(owner);manager.getClass().getMethod("clear").invoke(manager);break;}
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    static int run(NativeClientContext context)throws Exception{
        int checks=0;
        context.runOnClient(BetaUiChecks::prepareImages);
        for(String language:net.worldbinder.util.Lang.LANGUAGES){
            var reload=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<?>>();
            context.runOnClient(c->{c.options.languageCode=language;c.getLanguageManager().setSelected(language);reload.set(c.reloadResourcePacks());});
            for(int i=0;!reload.get().isDone()&&i<1200;i++)context.waitTicks(1);reload.get().get(2,java.util.concurrent.TimeUnit.SECONDS);
            int[] languageChecks={0};context.runOnClient(c->{try{languageChecks[0]=LanguageRuntimeChecks.verify(c,language);}catch(Exception error){throw new AssertionError(error);}});checks+=languageChecks[0];
            for(String menu:new String[]{"control-center","capture-profile","network-projects","tools","map-2d","map-3d","export-preview"}){
                context.runOnClient(c->{
                    switch(menu){
                        case "control-center"->WorldBinderClient.openControlCenter();
                        case "capture-profile"->ClientCompat.setScreen(c,new WorldBinderCaptureSettingsScreen(null));
                        case "network-projects"->{ClientCompat.setScreen(c,new WorldBinderNetworkProjectsScreen(null));var screen=ClientCompat.screen(c);String networkName=WorldBinderClient.capture().networkProfile().name;for(var child:screen.children())if(child instanceof Button b&&b.active&&b.getMessage().getString().equals(networkName)){b.onPress(new net.minecraft.client.input.KeyEvent(257,0,0));break;}}
                        case "tools"->ClientCompat.setScreen(c,new WorldBinderRecordingToolsScreen(null));
                        case "map-2d","map-3d"->{WorldBinder.config().map3d=menu.equals("map-3d");ClientCompat.setScreen(c,new WorldBinderMapScreen(null));}
                        case "export-preview"->ClientCompat.setScreen(c,new WorldBinderExportScreen(null,WorldBinderClient.capture().exportPreview(),WorldBinderClient.capture().projectChangesText(),()->{}));
                    }
                });
                context.waitTicks(menu.equals("map-3d")?40:8);
                context.runOnClient(c->{var screen=ClientCompat.screen(c);if(screen==null||screen.getTitle().getString().contains("worldbinder."))throw new AssertionError("Untranslated beta title: "+menu);for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible&&child instanceof Button&&w.getMessage().getString().contains("worldbinder."))throw new AssertionError("Untranslated beta button: "+menu+" "+w.getMessage());});checks++;
                context.takeScreenshot("beta-"+language+"-"+menu);
            }
            // Use the real scenic backend chest, after the actual language reload. Wait for
            // full server contents and another rendered frame instead of photographing stale chat.
            boolean[] settings=new boolean[3];var mode=new java.util.concurrent.atomic.AtomicReference<net.worldbinder.config.WorldBinderConfig.MessageMode>();
            context.runOnClient(c->{var config=WorldBinder.config();settings[0]=config.showBossbarOverlay;settings[1]=config.showChunkRadar;settings[2]=WorldBinderClient.capture().exportPreview().captureSettings.manualContainerMerge;mode.set(config.messageMode);
                config.showBossbarOverlay=false;config.showChunkRadar=false;config.messageMode=net.worldbinder.config.WorldBinderConfig.MessageMode.NONE;
                WorldBinderClient.capture().exportPreview().captureSettings.manualContainerMerge=true;ClientCompat.chat(c).clearMessages(true);ClientCompat.setScreen(c,null);
                var pos=new net.minecraft.core.BlockPos(2,64,2);c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));
            });
            boolean ready=false;
            for(int i=0;i<600;i++){boolean[] complete={false};context.runOnClient(c->complete[0]=ClientCompat.screen(c) instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen&&WorldBinderClient.capture().containerTotalSlots()==27&&WorldBinderClient.capture().containerKnownSlots()==27);if(complete[0]){ready=true;break;}context.waitTicks(1);}
            if(!ready)throw new AssertionError("Gallery requires an actual full server chest reply");
            context.runOnClient(c->{for(var child:ClientCompat.screen(c).children())if(child instanceof Button b&&b.getMessage().getString().equals(Lang.string("worldbinder.containers.menu"))){b.onPress(new net.minecraft.client.input.KeyEvent(257,0,0));return;}throw new AssertionError("Missing actual container controls");});
            context.takeScreenshot("beta-"+language+"-container-merging");checks++;
            context.runOnClient(c->{c.player.closeContainer();var config=WorldBinder.config();config.showBossbarOverlay=settings[0];config.showChunkRadar=settings[1];config.messageMode=mode.get();WorldBinderClient.capture().exportPreview().captureSettings.manualContainerMerge=settings[2];});
        }
        context.runOnClient(c->{ClientCompat.setScreen(c,null);WorldBinder.config().map3d=false;});
        WorldBinder.LOGGER.info("WORLDBINDER_BETA_UI_PASS {} checks with actual DE/EN reload",checks);return checks;
    }
}

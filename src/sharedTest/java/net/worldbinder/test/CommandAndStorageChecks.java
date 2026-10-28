package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.*;
import net.worldbinder.ui.*;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.util.Lang;
import java.nio.file.Path;

/** Actual registered client commands, real fonts/widgets, and rendered dashboard states in all bundled languages. */
final class CommandAndStorageChecks {
    private final String[] commands={"","settings","map","archives","capture","movement","tools","profiler","status","help"};
    private int index,wait,state;int checks;
    private final StorageProgress progress=new StorageProgress();
    private long stopped;
    boolean commandsDone(){return index>=commands.length;}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}

    boolean tick(Minecraft client,WorldScene scene,Path source,String language)throws Exception {
        if(index<commands.length) {
            if(wait++==0){ClientCompat.setScreen(client,null);client.getConnection().sendCommand("worldbinder"+(commands[index].isEmpty()?"":" "+commands[index]));return false;}
            if(wait<4)return false;
            Screen screen=ClientCompat.screen(client);
            Class<?> type=switch(commands[index]) {
                case "settings","movement" -> WorldBinderConfigScreen.class;
                case "map" -> WorldBinderMapScreen.class;
                case "tools" -> WorldBinderRecordingToolsScreen.class;
                case "profiler" -> WorldBinderProfilerScreen.class;
                case "status" -> StorageFlow.progress().hasActivity()?WorldBinderStorageProgressScreen.class:null;
                case "help" -> null;
                default -> WorldBinderScreen.class;
            };
            check(type==null?screen==null:type.isInstance(screen),"Registered /worldbinder "+commands[index]+" opens its actual local destination: "+screen);
            if(commands[index].equals("archives")||commands[index].equals("capture")) {
                var field=screen.getClass().getSuperclass().getDeclaredField("section");field.setAccessible(true);
                check(field.get(screen).toString().equalsIgnoreCase(commands[index]),"Subcommand selects its actual Control Center section");
            }
            index++;wait=0;return false;
        }
        if(wait++<8)return false;wait=0;
        switch(state++) {
            case 0 -> {
                progress.start(source,scene,true);progress.update(StorageStage.VANILLA_WORLD,Lang.stored("worldbinder.export.progress.writing_chunks",45,100,"26x/overworld"),.55);
                var screen=new WorldBinderStorageProgressScreen(null,progress);
                for(int[] size:new int[][]{{427,240},{853,480},{260,320}}) {
                    screen.init(size[0],size[1]);var scale=WbLayout.uiScale(size[0],size[1]);
                    for(var child:screen.children())if(child instanceof AbstractWidget widget) {
                        check(widget.getX()>=0&&widget.getY()>=0&&widget.getX()+widget.getWidth()<=scale.virtualWidth()&&widget.getY()+widget.getHeight()<=scale.virtualHeight(),"Dashboard widgets fit scaled viewport");
                        check(client.font.width(widget.getMessage())+12<=widget.getWidth(),"Dashboard action fits actual "+language+" font at "+size[0]+" pixels");
                    }
                }
                ClientCompat.setScreen(client,screen);
            }
            case 1 -> {
                ExpansionUiChecks.shot(client,language+"-export-running");
                ClientCompat.screen(client).onClose();check(ClientCompat.screen(client)==null&&progress.isRunning(),"Closing the dashboard does not cancel or restart its export");
                progress.fail(Lang.stored("worldbinder.error.missing_chunk_payload"));stopped=progress.elapsedMillis();ClientCompat.setScreen(client,new WorldBinderStorageProgressScreen(null,progress));
            }
            case 2 -> {
                ExpansionUiChecks.shot(client,language+"-export-failed");check(progress.progress()<1&&progress.elapsedMillis()==stopped,"Failure retains actual progress and freezes elapsed time");
                progress.finish(source);ClientCompat.setScreen(client,new WorldBinderStorageProgressScreen(null,progress));
            }
            case 3 -> {
                ExpansionUiChecks.shot(client,language+"-export-finished");
                var widgets=ClientCompat.screen(client).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
                check(widgets.stream().anyMatch(w->w.active&&w.getMessage().getString().equals(Lang.string("worldbinder.storage.open_report"))),"Completed dashboard enables the actual written export report");
                Screen nested=new WorldBinderFinishProgressScreen(new WorldBinderFinishScreen(new WorldBinderStorageProgressScreen(null,progress),net.worldbinder.client.WorldBinderClient.capture()),net.worldbinder.client.WorldBinderClient.capture());
                check(WorldBinderStorageProgressScreen.returnScreen(nested)==null,"Temporary finish/progress screens cannot reappear as return destinations");
                ClientCompat.setScreen(client,null);return true;
            }
        }
        return false;
    }
}

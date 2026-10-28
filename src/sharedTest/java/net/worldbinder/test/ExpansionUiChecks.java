package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.selection.*;
import net.worldbinder.ui.*;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.util.Lang;
import java.util.concurrent.CompletableFuture;

/** Runs real UI callbacks, resource-language reloads, scaled map mouse events and rendered screenshot capture. */
final class ExpansionUiChecks {
    private int step,wait,language;int checks;
    private CompletableFuture<?> reload;private final WorldScene scene;
    private GameRuleUiChecks gameRuleChecks;
    private CommandAndStorageChecks commandStorageChecks;
    private CaptureBounds beforeDrag;private int[] start,end;
    private final java.nio.file.Path sourceArchive;private final byte[] originalArchive;private java.util.Set<java.nio.file.Path> schematics;
    ExpansionUiChecks(WorldScene scene,java.nio.file.Path source)throws java.io.IOException{this.scene=scene;sourceArchive=source.resolve("worldbinder/worldbinder_archive.json");originalArchive=java.nio.file.Files.readAllBytes(sourceArchive);}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private boolean click(Screen screen,String key){for(var child:screen.children())if(child instanceof Button button&&button.active&&button.getMessage().getString().equals(Lang.string(key))){button.onPress(new KeyEvent(257,0,0));return true;}return false;}
    static void shot(Minecraft client,String name){
        try{Object target;try{target=client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);}catch(NoSuchMethodException old){target=client.getClass().getMethod("getMainRenderTarget").invoke(client);}
            for(var method:Screenshot.class.getMethods())if(method.getName().equals("grab")&&method.getParameterCount()>=4&&method.getParameterTypes()[1]==String.class){java.util.function.Consumer<net.minecraft.network.chat.Component> callback=message->{};if(method.getParameterCount()==5)method.invoke(null,client.gameDirectory,"expansion-"+name+".png",target,1,callback);else method.invoke(null,client.gameDirectory,"expansion-"+name+".png",target,callback);return;}
            throw new AssertionError("No compatible named screenshot API");
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private void controls(Minecraft client){
        Screen screen=ClientCompat.screen(client);check(!screen.getTitle().getString().contains("worldbinder."),"Translated title: "+screen.getClass().getSimpleName());
        var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();
        for(var widget:widgets){check(widget.getX()>=0&&widget.getY()>=0&&widget.getRight()<=screen.width&&widget.getBottom()<=screen.height,"Control fits the actual game window");if(widget instanceof Button)check(!widget.getMessage().getString().contains("worldbinder."),"No raw translation key in a visible button");}
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);check(!(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY()),"Visible controls do not overlap");}
        shot(client,Lang.LANGUAGES[language].substring(0,2)+"-"+screen.getClass().getSimpleName());
    }
    private void languageControls(Minecraft client) {
        var screen=ClientCompat.screen(client);
        check(!screen.getTitle().getString().contains("worldbinder."),"No raw key in an existing screen title");
        for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible)
            check(!widget.getMessage().getString().contains("worldbinder.")&&!widget.getMessage().getString().contains("\u001eworldbinder:"),"No raw key/token in an existing screen control");
        shot(client,Lang.LANGUAGES[language].substring(0,2)+"-"+screen.getClass().getSimpleName());
    }
    private static int[] coordinate(WorldBinderMapScreen screen,double x,double y)throws Exception{
        var scale=WbLayout.uiScale(screen.width,screen.height);int w=screen.width,h=screen.height;screen.width=scale.virtualWidth();screen.height=scale.virtualHeight();
        try{var mx=WorldBinderMapScreen.class.getDeclaredMethod("blockAtScreenX",double.class);var mz=WorldBinderMapScreen.class.getDeclaredMethod("blockAtScreenY",double.class);mx.setAccessible(true);mz.setAccessible(true);return new int[]{(int)Math.floor((double)mx.invoke(screen,(double)scale.toVirtualX(x))),(int)Math.floor((double)mz.invoke(screen,(double)scale.toVirtualY(y)))};}finally{screen.width=w;screen.height=h;}
    }
    boolean tick(Minecraft client)throws Exception{
        // The command/dashboard driver has its own frame waits; do not multiply those by this screen delay.
        if(step!=13&&wait++<8)return false;wait=0;
        switch(step++){
            case 0->{String code=Lang.LANGUAGES[language];client.options.languageCode=code;client.getLanguageManager().setSelected(code);reload=client.reloadResourcePacks();}
            case 1->{if(!reload.isDone()){step--;return false;}reload.join();checks+=LanguageRuntimeChecks.verify(client,Lang.LANGUAGES[language]);check(Lang.string("worldbinder.filters.title").equals(Lang.in(Lang.LANGUAGES[language],"worldbinder.filters.title")),"Actual resource language reload applies to the new menus");ClientCompat.setScreen(client,new WorldBinderRecordingToolsScreen(null));}
            case 2->{controls(client);WorldBinderClient.selections().setSelection(new CaptureBounds(-2,63,1,3,65,3).selection());ClientCompat.setScreen(client,new WorldBinderRegionScreen(null));}
            case 3->{controls(client);ClientCompat.setScreen(client,new WorldBinderExportFiltersScreen(null,scene,()->{}));}
            case 4->{controls(client);ClientCompat.setScreen(client,new WorldBinderExportScreen(null,scene,"",()->{}));}
            case 5->{controls(client);WorldBinder.config().map3d=false;ClientCompat.setScreen(client,new WorldBinderMapScreen(null));}
            case 6->{var map=(WorldBinderMapScreen)ClientCompat.screen(client);check(click(map,"worldbinder.map.export"),"Actual F10 export menu opens");check(click(map,"worldbinder.area.select_map"),"Actual F10 area tool is available");beforeDrag=CaptureBounds.of(WorldBinderClient.selections().getSelection());
                double x=map.width*.38,y=map.height*.55;start=coordinate(map,x,y);shot(client,"drag-start");boolean consumed=map.mouseClicked(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
                var dragging=WorldBinderMapScreen.class.getDeclaredField("areaDragging");dragging.setAccessible(true);var choosing=WorldBinderMapScreen.class.getDeclaredField("selectingArea");choosing.setAccessible(true);
                check(dragging.getBoolean(map),"F10 press starts selection: consumed="+consumed+" choosing="+choosing.getBoolean(map)+" dimensions="+map.width+","+map.height+" point="+x+","+y+" children="+map.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).map(w->w.getMessage().getString()+"@"+w.getX()+","+w.getY()+","+w.getWidth()+","+w.getHeight()).toList());
                x+=24;y+=12;end=coordinate(map,x,y);map.mouseDragged(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),24,12);map.mouseReleased(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)));
                var selected=CaptureBounds.of(WorldBinderClient.selections().getSelection());check(selected.minX()==Math.min(start[0],end[0])&&selected.maxX()==Math.max(start[0],end[0])&&selected.minZ()==Math.min(start[1],end[1])&&selected.maxZ()==Math.max(start[1],end[1]),"Scaled F10 click, drag and release preserve actual selected X/Z coordinates: start="+java.util.Arrays.toString(start)+" end="+java.util.Arrays.toString(end)+" selected="+selected+" screen="+ClientCompat.screen(client));
                check(selected.minY()==beforeDrag.minY()&&selected.maxY()==beforeDrag.maxY()&&ClientCompat.screen(client) instanceof WorldBinderRegionScreen,"F10 and F7/F8 share the same inclusive 3D selection and Y editor");}
            case 7->{controls(client);ClientCompat.setScreen(client,new WorldBinderMapScreen(null));}
            case 8->{shot(client,Lang.LANGUAGES[language].substring(0,2)+"-map-selection-2d");WorldBinder.config().map3d=true;ClientCompat.setScreen(client,new WorldBinderMapScreen(null));}
            case 9->{shot(client,Lang.LANGUAGES[language].substring(0,2)+"-map-selection-3d");ClientCompat.setScreen(client,new WorldBinderConfigScreen(null));}
            case 10->{
                if(gameRuleChecks==null){languageControls(client);gameRuleChecks=new GameRuleUiChecks();}
                if(!gameRuleChecks.tick(client,Lang.LANGUAGES[language].substring(0,2))){step--;return false;}
                checks+=gameRuleChecks.checks;gameRuleChecks=null;
                checks+=ExportFooterChecks.run(client,scene);
                ClientCompat.setScreen(client,new WorldBinderWidgetEditorScreen(null,WorldBinder.config()));
            }
            case 11->{languageControls(client);ClientCompat.setScreen(client,new WorldBinderProfilerScreen(null));}
            case 12->{languageControls(client);ClientCompat.setScreen(client,new WorldBinderLegalStartScreen(null,()->{},Lang.string("worldbinder.gui.start_saving")));}
            case 13->{
                if(commandStorageChecks==null){languageControls(client);commandStorageChecks=new CommandAndStorageChecks();}
                if(!commandStorageChecks.tick(client,scene,sourceArchive.getParent().getParent(),Lang.LANGUAGES[language].substring(0,2))){step--;return false;}
                checks+=commandStorageChecks.checks;commandStorageChecks=null;
                if(++language<Lang.LANGUAGES.length){step=0;return false;}ClientCompat.setScreen(client,null);
            }
            case 14->{shot(client,"world-overlay");client.options.languageCode="en_us";client.getLanguageManager().setSelected("en_us");reload=client.reloadResourcePacks();}
            case 15->{if(!reload.isDone()){step--;return false;}reload.join();WorldBinderClient.selections().setSelection(new CaptureBounds(-2,63,1,3,65,3).selection());ClientCompat.setScreen(client,new WorldBinderRegionScreen(null));var directory=net.worldbinder.io.WorldBinderPaths.RECOVERY_ROOT.resolve("area_exports");schematics=new java.util.HashSet<>();if(java.nio.file.Files.isDirectory(directory))try(var files=java.nio.file.Files.list(directory)){schematics.addAll(files.toList());}}
            case 16->{check(click(ClientCompat.screen(client),"worldbinder.area.schematic"),"The actual region menu starts schematic export");}
            case 17->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen)||!click(ClientCompat.screen(client),"worldbinder.export.review.export")){step--;return false;}check(true,"Schematic export uses the actual version/filter/count review before writing");}
            case 18->{if(!(ClientCompat.screen(client) instanceof WorldBinderRegionScreen region)){step--;return false;}var busy=WorldBinderRegionScreen.class.getDeclaredField("busy");busy.setAccessible(true);if(busy.getBoolean(region)){step--;return false;}
                var status=WorldBinderRegionScreen.class.getDeclaredField("status");status.setAccessible(true);check(Lang.resolve((String)status.get(region)).startsWith(Lang.string("worldbinder.area.saved","")),"Schematic UI completed successfully: "+Lang.resolve((String)status.get(region)));
                try(var files=java.nio.file.Files.list(net.worldbinder.io.WorldBinderPaths.RECOVERY_ROOT.resolve("area_exports"))){var file=files.filter(p->p.toString().endsWith(".schem")&&!schematics.contains(p)).findFirst().orElseThrow();var nbt=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("Schematic").orElseThrow();check(nbt.getShort("Width").orElseThrow()==6&&nbt.getShort("Height").orElseThrow()==3&&nbt.getShort("Length").orElseThrow()==3,"Real UI writes the complete selected Sponge v3 dimensions");check(nbt.getCompound("Blocks").orElseThrow().getCompound("Palette").orElseThrow().keySet().stream().noneMatch(id->id.startsWith("minecraft:barrel")),"Project filters also affect schematic output after the review");}
                check(java.util.Arrays.equals(originalArchive,java.nio.file.Files.readAllBytes(sourceArchive)),"Schematic UI leaves the full source project archive byte-for-byte unchanged");ClientCompat.setScreen(client,null);return true;
            }
        }return false;
    }
}

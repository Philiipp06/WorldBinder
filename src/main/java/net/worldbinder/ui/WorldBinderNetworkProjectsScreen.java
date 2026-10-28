package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.*;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import java.util.*;

/** Network -> explicit backend -> recorded dimensions, without loading full archives on the UI thread. */
public final class WorldBinderNetworkProjectsScreen extends WbScaledScreen {
    private final Screen parent;
    private String network;
    private int page;
    public WorldBinderNetworkProjectsScreen(Screen parent){super(Lang.text("worldbinder.network.projects"));this.parent=parent;}
    @Override protected void initContent(){
        int w=Math.min(540,width-28),x=(width-w)/2;
        var all=WorldBinder.config().networkProfiles;
        if(network==null){
            var entries=all.entrySet().stream().filter(e->!e.getValue().backends.isEmpty()).sorted(Comparator.comparing(e->Objects.toString(e.getValue().name,e.getKey()))).toList();
            page=Math.max(0,Math.min(page,Math.max(0,(entries.size()-1)/4)));
            for(int i=page*4;i<Math.min(entries.size(),page*4+4);i++){
                var entry=entries.get(i);int y=48+(i-page*4)*38;var profile=entry.getValue();
                String label=Objects.toString(profile.name,Objects.toString(profile.address,entry.getKey().substring(0,Math.min(12,entry.getKey().length()))));
                addRenderableWidget(WbButton.create(x,y,w,20,label,Lang.text("worldbinder.network.backend_count",profile.backends.size()),b->{network=entry.getKey();page=0;rebuild();}));
            }
            pager(x,w,entries.size());
        }else{
            var profile=all.get(network);if(profile==null){network=null;rebuild();return;}
            var entries=profile.backends.values().stream().sorted(Comparator.comparing(b->Objects.toString(b.name,b.id))).toList();
            page=Math.max(0,Math.min(page,Math.max(0,(entries.size()-1)/4)));
            for(int i=page*4;i<Math.min(entries.size(),page*4+4);i++){
                var backend=entries.get(i);int y=48+(i-page*4)*38;
                var button=WbButton.create(x,y,w,20,Objects.toString(backend.name,backend.id),Lang.text("worldbinder.network.choose_help",backend.id),b->{
                    var screen=new WorldBinderScreen(WorldBinderClient.selections(),WorldBinderClient.capture(),WorldBinderClient.placement(),WorldBinderClient.scenes(),this);screen.openProject(java.nio.file.Path.of(backend.projectPath));ClientCompat.setScreen(minecraft,screen);
                });button.active=backend.projectPath!=null&&java.nio.file.Files.exists(java.nio.file.Path.of(backend.projectPath));addRenderableWidget(button);
            }
            pager(x,w,entries.size());
        }
        addRenderableWidget(WbButton.create(x,height-28,w,20,Lang.text("worldbinder.gui.back"),null,b->onClose()));
    }
    private void pager(int x,int w,int count){
        var prev=addRenderableWidget(WbButton.create(x,208,32,20,"‹",null,b->{page--;rebuild();}));prev.active=page>0;
        var next=addRenderableWidget(WbButton.create(x+38,208,32,20,"›",null,b->{page++;rebuild();}));next.active=(page+1)*4<count;
    }
    @Override protected void renderContent(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        int w=Math.min(540,width-28),x=(width-w)/2;WbChrome.drawPanel(graphics,x-8,8,w+16,height-16);GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        var all=WorldBinder.config().networkProfiles;var profile=network==null?null:all.get(network);
        WbText.drawClipped(graphics,font,profile==null?Lang.text("worldbinder.network.projects_help"):net.minecraft.network.chat.Component.literal(Objects.toString(profile.name,Objects.toString(profile.address,network))),x,34,w,WbTheme.TEXT_SOFT);
        if(profile!=null){var entries=profile.backends.values().stream().sorted(Comparator.comparing(b->Objects.toString(b.name,b.id))).toList();for(int i=page*4;i<Math.min(entries.size(),page*4+4);i++){
            var backend=entries.get(i);String dimensions=backend.dimensions==null?"":String.join(" · ",backend.dimensions);
            WbText.drawClipped(graphics,font,dimensions.isBlank()?Lang.text("worldbinder.network.no_snapshot"):net.minecraft.network.chat.Component.literal(dimensions),x+4,72+(i-page*4)*38,w-8,WbTheme.TEXT_MUTED);
        }}
    }
    @Override public void onClose(){if(network!=null){network=null;page=0;rebuild();}else ClientCompat.setScreen(minecraft,parent);}
}

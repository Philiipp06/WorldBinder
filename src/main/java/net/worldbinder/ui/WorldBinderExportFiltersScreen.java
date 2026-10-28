package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.export.common.ExportFiltering;
import net.worldbinder.scene.*;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import java.util.concurrent.CompletableFuture;

public final class WorldBinderExportFiltersScreen extends Screen {
    private final Screen parent;private final WorldScene scene;private final Runnable changed;
    private final ExportFilterSettings draft;
    private EditBox entities,blocks;private String entityText,blockText,error="";
    private CompletableFuture<ExportFiltering.Counts> counts;
    public WorldBinderExportFiltersScreen(Screen parent,WorldScene scene,Runnable changed){
        super(Lang.text("worldbinder.filters.title"));this.parent=parent;this.scene=scene;this.changed=changed;
        draft=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(scene.exportSettings.filters),ExportFilterSettings.class);
        entityText=String.join(", ",draft.entityIds);blockText=String.join(", ",draft.blockIds);recount();
    }
    private void recount(){if(counts!=null)counts.cancel(false);var copy=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(draft),ExportFilterSettings.class);counts=CompletableFuture.supplyAsync(()->ExportFiltering.count(scene,copy),WorldBinderExportScreen.ANALYSIS);}
    private void previewIds(){
        try{var entityIds=ExportFiltering.parseIds(scene,entityText,false);var blockIds=ExportFiltering.parseIds(scene,blockText,true);draft.entityIds=entityIds;draft.blockIds=blockIds;error="";recount();}
        catch(IllegalArgumentException exception){error=exception instanceof net.worldbinder.validation.FindingException found?Lang.stored(found.finding().key(),found.finding().arguments().toArray()):exception.getMessage();}
    }
    private void rebuild(){clearWidgets();init();}
    private void toggle(int x,int y,String key,boolean value,Runnable update){addRenderableWidget(WbButton.create(x,y,Math.min(440,width-36)/2-4,20,Lang.text(key,Lang.string(value?"worldbinder.export.enabled":"worldbinder.export.disabled")),null,b->{update.run();recount();rebuild();}));}
    @Override protected void init(){
        int w=Math.min(440,width-36),x=(width-w)/2,y=38,half=(w-8)/2;
        toggle(x,y,"worldbinder.filters.items",draft.droppedItems,()->draft.droppedItems=!draft.droppedItems);
        toggle(x+half+8,y,"worldbinder.filters.xp",draft.experienceOrbs,()->draft.experienceOrbs=!draft.experienceOrbs);
        toggle(x,y+26,"worldbinder.filters.armor",draft.armorStands,()->draft.armorStands=!draft.armorStands);
        toggle(x+half+8,y+26,"worldbinder.filters.hostile",draft.hostileMobs,()->draft.hostileMobs=!draft.hostileMobs);
        entities=new EditBox(font,x,y+64,w,20,Lang.text("worldbinder.filters.entity_ids"));entities.setMaxLength(4096);entities.setValue(entityText);entities.setResponder(v->{entityText=v;previewIds();});addRenderableWidget(entities);
        blocks=new EditBox(font,x,y+104,w,20,Lang.text("worldbinder.filters.block_ids"));blocks.setMaxLength(4096);blocks.setValue(blockText);blocks.setResponder(v->{blockText=v;previewIds();});addRenderableWidget(blocks);
        addRenderableWidget(WbButton.create(x,height-28,half,20,Lang.text("worldbinder.gui.cancel"),null,b->onClose()));
        addRenderableWidget(WbButton.create(x+half+8,height-28,half,20,Lang.text("worldbinder.filters.apply"),null,b->{
            try{var entityIds=ExportFiltering.parseIds(scene,entityText,false);var blockIds=ExportFiltering.parseIds(scene,blockText,true);
                draft.entityIds=entityIds;draft.blockIds=blockIds;scene.exportSettings.filters=draft;changed.run();onClose();
            }catch(IllegalArgumentException exception){error=exception instanceof net.worldbinder.validation.FindingException found?Lang.stored(found.finding().key(),found.finding().arguments().toArray()):exception.getMessage();}
        }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);int w=Math.min(440,width-36),x=(width-w)/2;
        WbChrome.drawPanel(graphics,x-10,8,w+20,height-16);GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.filters.entity_ids"),x,90,w,WbTheme.TEXT_SOFT);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.filters.block_ids"),x,130,w,WbTheme.TEXT_SOFT);
        String summary=Lang.string("worldbinder.filters.counting");if(counts.isDone()&&!counts.isCompletedExceptionally()){var value=counts.join();summary=Lang.string("worldbinder.filters.summary",value.blocks(),value.blockEntities(),value.entities());}
        WbText.drawClipped(graphics,font,Component.literal(error.isEmpty()?summary:Lang.resolve(error)),x,170,w,error.isEmpty()?WbTheme.ACCENT:WbTheme.ERROR);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.filters.original_kept"),x,190,w,WbTheme.TEXT_SOFT);
        super.extractRenderState(graphics,mouseX,mouseY,partial);
    }
    @Override public void onClose(){ClientCompat.setScreen(minecraft,parent);}
}

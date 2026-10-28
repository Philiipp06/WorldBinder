package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.export.common.ExportFiltering;
import net.worldbinder.scene.*;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;

/** Input policies are explicit, project-owned and independent of the export view. */
public final class WorldBinderCaptureSettingsScreen extends Screen {
    private final Screen parent;
    private final WorldScene source;
    private final CaptureSettings draft;
    private String ids,error="";
    private boolean defaults,autoJoin,serverProfile;
    public WorldBinderCaptureSettingsScreen(Screen parent){
        super(Lang.text("worldbinder.capture_profile.title"));this.parent=parent;
        source=WorldBinderClient.capture().exportPreview();
        draft=(source==null?WorldBinder.config().captureDefaults:source.captureSettings).copy();
        ids=String.join(", ",draft.entities.entityIds);defaults=source==null;autoJoin=WorldBinder.config().autoCaptureOnJoin;
    }
    private void rebuild(){clearWidgets();init();}
    private void toggle(int x,int y,int w,String key,boolean value,Runnable action){addRenderableWidget(WbButton.create(x,y,w,20,Lang.text(key,Lang.string(value?"worldbinder.export.enabled":"worldbinder.export.disabled")),Lang.text("worldbinder.capture_profile.help"),b->{action.run();rebuild();}));}
    @Override protected void init(){
        int realWidth=width,realHeight=height;var scale=WbLayout.uiScale(width,height);width=scale.virtualWidth();height=scale.virtualHeight();
        try{initScaled();}finally{width=realWidth;height=realHeight;}
    }
    private void initScaled(){
        int w=Math.min(500,width-28),x=(width-w)/2,half=(w-8)/2;
        toggle(x,42,w,"worldbinder.capture_profile.enabled",draft.liveEntityFilters,()->draft.liveEntityFilters=!draft.liveEntityFilters);
        toggle(x,68,half,"worldbinder.filters.items",draft.entities.droppedItems,()->draft.entities.droppedItems=!draft.entities.droppedItems);
        toggle(x+half+8,68,half,"worldbinder.filters.xp",draft.entities.experienceOrbs,()->draft.entities.experienceOrbs=!draft.entities.experienceOrbs);
        toggle(x,94,half,"worldbinder.filters.armor",draft.entities.armorStands,()->draft.entities.armorStands=!draft.entities.armorStands);
        toggle(x+half+8,94,half,"worldbinder.filters.hostile",draft.entities.hostileMobs,()->draft.entities.hostileMobs=!draft.entities.hostileMobs);
        var field=new EditBox(font,x,126,w,20,Lang.text("worldbinder.filters.entity_ids"));field.setMaxLength(4096);field.setValue(ids);field.setResponder(value->ids=value);addRenderableWidget(field);
        toggle(x,152,half,"worldbinder.capture_profile.manual_merge",draft.manualContainerMerge,()->draft.manualContainerMerge=!draft.manualContainerMerge);
        addRenderableWidget(WbButton.create(x+half+8,152,half,20,Lang.text("worldbinder.containers.strategy."+draft.containerStrategy.name().toLowerCase(java.util.Locale.ROOT)),Lang.text("worldbinder.containers.help"),b->{var all=ContainerMerging.Strategy.values();draft.containerStrategy=all[(draft.containerStrategy.ordinal()+1)%all.length];rebuild();}));
        toggle(x,178,half,"worldbinder.capture_profile.defaults",defaults,()->defaults=!defaults);
        toggle(x+half+8,178,half,"worldbinder.capture_profile.auto_join",autoJoin,()->{
            if(!autoJoin&&WorldBinder.config().showLegalStartReminder)ClientCompat.setScreen(minecraft,new WorldBinderLegalStartScreen(this,()->{autoJoin=true;rebuild();},Lang.string("worldbinder.capture_profile.enable_auto")));
            else autoJoin=!autoJoin;
        });
        toggle(x,204,half,"worldbinder.dimensions.capture",draft.multiDimension,()->draft.multiDimension=!draft.multiDimension);
        var server=WbButton.create(x+half+8,204,half,20,Lang.text("worldbinder.capture_profile.server",Lang.string(serverProfile?"worldbinder.export.enabled":"worldbinder.export.disabled")),Lang.text("worldbinder.network.remember_help"),b->{serverProfile=!serverProfile;rebuild();});server.active=minecraft.level!=null&&!net.worldbinder.replay.ReplayPlayback.state().present();addRenderableWidget(server);
        addRenderableWidget(WbButton.create(x,height-28,half,20,Lang.text("worldbinder.gui.cancel"),null,b->onClose()));
        addRenderableWidget(WbButton.create(x+half+8,height-28,half,20,Lang.text("worldbinder.capture_profile.apply"),null,b->save()));
    }
    private void save(){
        try{
            draft.entities.entityIds=ExportFiltering.parseIds(source==null?new WorldScene():source,ids,false);
            if(source!=null){source.captureSettings=draft.copy();if(draft.liveEntityFilters)source.storageNotes.add(Lang.stored("worldbinder.capture_profile.history"));}
            if(defaults||source==null)WorldBinder.config().captureDefaults=draft.copy();
            if(serverProfile){var profile=WorldBinderClient.capture().networkProfile();profile.captureSettings=draft.copy();profile.autoCapture=autoJoin;}
            else WorldBinder.config().autoCaptureOnJoin=autoJoin;
            WorldBinder.config().save();onClose();
        }catch(IllegalArgumentException exception){error=net.worldbinder.validation.Finding.detail(exception).toString();}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        int realWidth=width,realHeight=height;var scale=WbLayout.uiScale(width,height);int mx=scale.toVirtualX(mouseX),my=scale.toVirtualY(mouseY);
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);graphics.pose().pushMatrix();graphics.pose().translate(scale.offsetX(),scale.offsetY());graphics.pose().scale(scale.scale(),scale.scale());width=scale.virtualWidth();height=scale.virtualHeight();
        try{int w=Math.min(500,width-28),x=(width-w)/2;
        WbChrome.drawPanel(graphics,x-8,8,w+16,height-16);GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.capture_profile.retained"),x,31,w,WbTheme.TEXT_MUTED);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.filters.entity_ids"),x,115,w,WbTheme.TEXT_SOFT);
        if(!error.isEmpty())WbText.drawClipped(graphics,font,error,x,227,w,WbTheme.ERROR);
        super.extractRenderState(graphics,mx,my,partial);
        }finally{width=realWidth;height=realHeight;graphics.pose().popMatrix();}
        WbTooltips.showHovered(this,graphics,font,mx,my,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){return super.mouseClicked(WbLayout.virtualMouseEvent(event,width,height),doubleClick);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){return super.mouseReleased(WbLayout.virtualMouseEvent(event,width,height));}
    @Override public void onClose(){ClientCompat.setScreen(minecraft,parent);}
}

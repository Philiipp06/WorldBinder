package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.storage.*;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import java.nio.file.*;

/** A non-blocking job dashboard, also available after the server connection has ended. */
public final class WorldBinderStorageProgressScreen extends Screen {
    private final Screen parent;
    private final StorageProgress progress;
    private Button folder, report, close;
    private record Layout(int x, int y, int w, int h, boolean compact) {}

    public WorldBinderStorageProgressScreen(Screen parent) {
        this(parent, StorageFlow.progress());
    }

    public WorldBinderStorageProgressScreen(Screen parent, StorageProgress progress) {
        super(Lang.text("worldbinder.storage.title"));
        this.parent = returnScreen(parent);
        this.progress = progress;
    }

    /** Temporary finish/progress screens are never navigation destinations. */
    public static Screen returnScreen(Screen screen) {
        while (true) {
            if (screen instanceof WorldBinderStorageProgressScreen progress) screen = progress.parent;
            else if (screen instanceof WorldBinderFinishProgressScreen finish) screen = finish.returnScreen();
            else if (screen instanceof WorldBinderFinishScreen finish) screen = finish.returnScreen();
            else return screen;
        }
    }

    private Layout layout() {
        int w=Math.min(700,width-24),h=Math.min(386,height-20);
        return new Layout((width-w)/2,(height-h)/2,w,h,h<340);
    }

    @Override protected void init() {
        int realWidth=width,realHeight=height;
        var scale=WbLayout.uiScale(width,height);width=scale.virtualWidth();height=scale.virtualHeight();
        try {
            var l=layout();int gap=8,bw=(l.w()-40-gap*2)/3,x=l.x()+20,y=l.y()+l.h()-32;
            folder=addRenderableWidget(WbButton.create(x,y,bw,22,Lang.text("worldbinder.storage.open_folder"),
                    Lang.text("worldbinder.storage.open_folder.tooltip"),b->{Path target=progress.target();if(target!=null)PathOpener.open(Files.isDirectory(target)?target:target.getParent());}));
            report=addRenderableWidget(WbButton.create(x+bw+gap,y,bw,22,Lang.text("worldbinder.storage.open_report"),
                    Lang.text("worldbinder.storage.open_report_help"),b->{Path path=reportPath();if(path!=null&&Files.isRegularFile(path))PathOpener.open(path);}));
            close=addRenderableWidget(WbButton.create(x+2*(bw+gap),y,bw,22,Lang.text("worldbinder.storage.background"),
                    Lang.text("worldbinder.storage.background_help"),b->onClose()));
            updateActions();
        } finally {width=realWidth;height=realHeight;}
    }

    private Path reportPath() {
        Path target=progress.target();
        return target==null?null:target.resolve("worldbinder/EXPORT_VALIDATION.txt");
    }

    private void updateActions() {
        Path target=progress.target(),path=reportPath();
        folder.active=target!=null&&(Files.isDirectory(target)||target.getParent()!=null&&Files.isDirectory(target.getParent()));
        report.active=progress.stage()==StorageStage.FINISHED&&path!=null&&Files.isRegularFile(path);
        close.setMessage(progress.isRunning()?Lang.text("worldbinder.storage.background"):Component.translatable("gui.done"));
        if(target!=null)WbTooltips.register(folder,Lang.text("worldbinder.storage.target",target.toAbsolutePath()));
    }

    @Override public void tick() {updateActions();}
    public boolean shouldPause() {return false;}
    @Override public void onClose() {net.worldbinder.compat.ClientCompat.setScreen(minecraft,parent);}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        int realWidth=width,realHeight=height;var scale=WbLayout.uiScale(width,height);
        int mx=scale.toVirtualX(mouseX),my=scale.toVirtualY(mouseY);
        Component detailTooltip=null;
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);
        graphics.pose().pushMatrix();graphics.pose().translate(scale.offsetX(),scale.offsetY());graphics.pose().scale(scale.scale(),scale.scale());
        width=scale.virtualWidth();height=scale.virtualHeight();
        try {
            var l=layout();var job=progress.context();
            boolean failed=progress.stage()==StorageStage.FAILED,done=progress.stage()==StorageStage.FINISHED;
            int color=failed?WbTheme.ERROR:done?WbTheme.OK:WbTheme.ACCENT;
            int x=l.x()+20,w=l.w()-40,top=l.y();
            WbChrome.drawPanel(graphics,l.x(),top,l.w(),l.h());graphics.fill(l.x()+1,top+1,l.x()+l.w()-1,top+3,color);
            GuiText.drawCenteredTextWithShadow(graphics,font,Lang.text(failed?"worldbinder.storage.header.failed":done?"worldbinder.storage.header.finished":"worldbinder.storage.title"),width/2,top+14,color);
            WbText.drawClipped(graphics,font,Lang.text("worldbinder.storage.project",job.name(),job.version(),job.dimension()),x,top+32,w,WbTheme.TEXT_SOFT);
            WbChrome.drawDivider(graphics,x,top+45,w);
            WbText.drawWrapped(graphics,font,Lang.text(job.disconnected()?"worldbinder.storage.disconnected":"worldbinder.storage.recorded_only"),x,top+51,w,WbTheme.TEXT_MUTED,l.compact()?1:2);

            int statsY=top+(l.compact()?71:91),cardH=l.compact()?33:46,gap=6,cardW=(w-gap*3)/4;
            statistic(graphics,x,statsY,cardW,cardH,"worldbinder.common.chunks",job.chunks(),color);
            statistic(graphics,x+cardW+gap,statsY,cardW,cardH,"worldbinder.storage.metric.blocks",job.blocks(),color);
            statistic(graphics,x+2*(cardW+gap),statsY,cardW,cardH,"worldbinder.storage.metric.entities",job.entities(),color);
            statistic(graphics,x+3*(cardW+gap),statsY,cardW,cardH,"worldbinder.storage.metric.maps",job.maps(),color);

            StorageStage workStage=failed?progress.failureStage():progress.stage();
            int phaseY=top+(l.compact()?112:149),phase=workStage==StorageStage.VALIDATE&&progress.progress()<.16D?0:phase(workStage);
            phase(graphics,x,phaseY,cardW,0,phase,"worldbinder.storage.phase.prepare",color);
            phase(graphics,x+cardW+gap,phaseY,cardW,1,phase,"worldbinder.storage.phase.archive",color);
            phase(graphics,x+2*(cardW+gap),phaseY,cardW,2,phase,"worldbinder.storage.phase.world",color);
            phase(graphics,x+3*(cardW+gap),phaseY,cardW,3,phase,"worldbinder.storage.phase.check",color);
            int stageY=top+(l.compact()?141:187),barY=top+(l.compact()?175:246);
            WbText.drawClipped(graphics,font,Lang.text("worldbinder.storage.stage",workStage.label()),x,stageY,w,color);
            WbText.drawWrapped(graphics,font,progress.detail(),x,stageY+16,w,failed?WbTheme.ERROR:WbTheme.TEXT_SOFT,l.compact()?1:2);
            if(mx>=x&&mx<=x+w&&my>=stageY+14&&my<barY)detailTooltip=Component.literal(progress.detail());
            WbChrome.drawProgressTrack(graphics,x,barY,w,11,(int)(w*progress.progress()),color);
            GuiText.drawCenteredTextWithShadow(graphics,font,Component.literal((int)(progress.progress()*100D)+"%"),width/2,barY+1,WbTheme.TEXT);
            WbText.drawClipped(graphics,font,Lang.text("worldbinder.storage.elapsed",StorageProgress.formatMillis(progress.elapsedMillis())),x,barY+18,w/2-4,WbTheme.TEXT_MUTED);
            WbText.drawClipped(graphics,font,Lang.text("worldbinder.storage.remaining",failed?Lang.string("worldbinder.storage.eta.stopped"):progress.etaText()),x+w/2,barY+18,w/2,WbTheme.TEXT_MUTED);
            if(!l.compact()) {
                Path target=progress.target();
                if(target!=null)WbText.drawClipped(graphics,font,Lang.text("worldbinder.storage.target",target.toAbsolutePath()),x,top+300,w,WbTheme.TEXT_DIM);
                WbText.drawWrapped(graphics,font,Lang.text(failed?"worldbinder.storage.failed_help":done?"worldbinder.storage.complete_help":"worldbinder.storage.background_help"),x,top+319,w,WbTheme.TEXT_MUTED,2);
            }
            super.extractRenderState(graphics,mx,my,delta);
        } finally {width=realWidth;height=realHeight;graphics.pose().popMatrix();}
        WbTooltips.showHovered(this,graphics,font,mx,my,mouseX,mouseY);
        if(detailTooltip!=null)WbTooltips.draw(graphics,font,detailTooltip,mouseX,mouseY,realWidth,realHeight);
    }

    private void statistic(GuiGraphicsExtractor graphics,int x,int y,int w,int h,String label,int count,int color) {
        WbChrome.drawCardSurface(graphics,x,y,w,h,color,false,false);
        WbText.drawClipped(graphics,font,Lang.text(label),x+8,y+6,w-16,WbTheme.TEXT_MUTED);
        WbText.drawClipped(graphics,font,Component.literal(Integer.toString(count)),x+8,y+h-13,w-16,WbTheme.TEXT);
    }

    private void phase(GuiGraphicsExtractor graphics,int x,int y,int w,int index,int current,String key,int color) {
        graphics.fill(x,y,x+w,y+20,index==current?WbTheme.CARD_SELECTED:WbTheme.CARD);
        graphics.fill(x,y+18,x+w,y+20,index<=current?color:WbTheme.DIVIDER);
        WbText.drawClipped(graphics,font,Lang.text(key),x+6,y+5,w-12,index<=current?WbTheme.TEXT:WbTheme.TEXT_DIM);
    }

    private static int phase(StorageStage stage) {
        return switch(stage) {
            case IDLE,SNAPSHOT,REVIEW -> 0;
            case METADATA -> 1;
            case VANILLA_WORLD,MAP_DATA,PLAYER_DATA,RESOURCE_PACK -> 2;
            default -> 3;
        };
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick) {return super.mouseClicked(WbLayout.virtualMouseEvent(event,width,height),doubleClick);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {return super.mouseReleased(WbLayout.virtualMouseEvent(event,width,height));}
}

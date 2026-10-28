package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.export.*;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.*;
import net.worldbinder.selection.*;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** F7/F8 and F10 share exactly the same inclusive coordinates. Exports use committed archive block records. */
public final class WorldBinderRegionScreen extends Screen {
    private final Screen parent;private final String[] values=new String[6];private final EditBox[] fields=new EditBox[6];
    private int sourceIndex=-1;private List<Path> archives=List.of();private boolean busy;
    private String status="";private long known=-1;private CaptureBounds checkedBounds;
    private String fixedDimension;
    public WorldBinderRegionScreen(Screen parent,String dimension){this(parent);fixedDimension=dimension;}
    public WorldBinderRegionScreen(Screen parent){
        super(Lang.text("worldbinder.area.title"));this.parent=parent;
        var selection=WorldBinderClient.selections().getSelection();CaptureBounds bounds;
        if(selection.complete())bounds=CaptureBounds.of(selection);else{
            var player=net.minecraft.client.Minecraft.getInstance().player;var p=player==null?net.minecraft.core.BlockPos.ZERO:player.blockPosition();
            bounds=new CaptureBounds(p.getX()-8,p.getY()-1,p.getZ()-8,p.getX()+8,p.getY()+8,p.getZ()+8);
        }
        int[] array=bounds.array();for(int i=0;i<6;i++)values[i]=Integer.toString(array[i]);
        checkedBounds=bounds;
        archives=WorldBinderClient.scenes().scenes().stream().toList();
        if(!WorldBinderClient.capture().isCapturing()&&!archives.isEmpty())sourceIndex=0;
    }
    private void rebuild(){clearWidgets();init();}
    private String sourceLabel(){return (sourceIndex<0?Lang.string("worldbinder.area.active_source"):archives.get(sourceIndex).getFileName().toString())+(fixedDimension==null?"":" · "+fixedDimension);}
    @Override protected void init(){
        int w=Math.min(440,width-36),x=(width-w)/2,y=38,col=(w-8)/3,half=(w-8)/2;
        var source=WbButton.create(x,y,w,20,Lang.text("worldbinder.area.source",sourceLabel()),Lang.text("worldbinder.area.source_help"),b->{sourceIndex++;if(sourceIndex>=archives.size())sourceIndex=-1;known=-1;rebuild();});source.active=!busy;addRenderableWidget(source);
        String[] labels={"X1","Y1","Z1","X2","Y2","Z2"};
        for(int i=0;i<6;i++){
            int index=i;fields[i]=new EditBox(font,x+(i%3)*(col+4),y+38+(i/3)*34,col,20,Component.literal(labels[i]));fields[i].setMaxLength(12);fields[i].setValue(values[i]);fields[i].setResponder(v->{values[index]=v;known=-1;});fields[i].active=!busy;addRenderableWidget(fields[i]);
        }
        var apply=WbButton.create(x,y+100,half,20,Lang.text("worldbinder.area.apply"),null,b->{try{previewCoverage(apply());}catch(RuntimeException exception){status=exception.getMessage();}});apply.active=!busy;addRenderableWidget(apply);
        var capture=WbButton.create(x+half+8,y+100,half,20,Lang.text("worldbinder.area.capture"),null,b->{try{apply();WorldBinderClient.capture().captureWorldArchive("selection");ClientCompat.setScreen(minecraft,null);}catch(RuntimeException exception){status=exception.getMessage();}});capture.active=!WorldBinderClient.capture().isCapturing()&&!busy;addRenderableWidget(capture);
        var world=WbButton.create(x,height-54,half,20,Lang.text("worldbinder.area.world"),Lang.text("worldbinder.area.real_blocks"),b->export(false));world.active=!busy;addRenderableWidget(world);
        var schematic=WbButton.create(x+half+8,height-54,half,20,Lang.text("worldbinder.area.schematic"),Lang.text("worldbinder.area.real_blocks"),b->export(true));schematic.active=!busy;addRenderableWidget(schematic);
        addRenderableWidget(WbButton.create(x,height-28,w,20,Lang.text("worldbinder.gui.close"),null,b->onClose()));
    }
    private CaptureBounds apply(){
        try{
            int[] v=new int[6];for(int i=0;i<6;i++)v[i]=Integer.parseInt(values[i].trim());
            checkedBounds=new CaptureBounds(Math.min(v[0],v[3]),Math.min(v[1],v[4]),Math.min(v[2],v[5]),Math.max(v[0],v[3]),Math.max(v[1],v[4]),Math.max(v[2],v[5]));
            WorldBinderClient.selections().setSelection(checkedBounds.selection());return checkedBounds;
        }catch(RuntimeException exception){throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.area.invalid"), exception);}
    }
    private CompletableFuture<WorldScene> source(){
        CompletableFuture<WorldScene> loaded;
        if(sourceIndex<0)loaded=WorldBinderClient.capture().prepareAreaExport();else{Path file=archives.get(sourceIndex);
            loaded=CompletableFuture.supplyAsync(()->{try{return WorldBinderClient.scenes().read(file);}catch(java.io.IOException e){throw new CompletionException(e);}});}
        return loaded.thenApply(scene->{if(fixedDimension==null)return scene;var scope=ProjectDimensions.find(scene,fixedDimension);if(scope==null)throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.dimensions.not_recorded",fixedDimension));return ProjectDimensions.dimensionOnly(scope);});
    }
    private void previewCoverage(CaptureBounds bounds){
        if(busy)return;busy=true;status=Lang.stored("worldbinder.area.preparing");rebuild();
        source().thenApplyAsync(scene->RecordedCoverage.knownVolume(scene,bounds)).whenComplete((count,error)->minecraft.execute(()->{busy=false;if(error!=null)status=Lang.stored("worldbinder.area.failed",rootMessage(error));else{known=count;status=Lang.stored("worldbinder.area.coverage",known,bounds.volume());}rebuild();}));
    }
    private void export(boolean schematic){
        final CaptureBounds bounds;try{bounds=apply();}catch(RuntimeException exception){status=exception.getMessage();return;}
            if(busy)return;busy=true;status=Lang.stored("worldbinder.area.preparing");rebuild();
            source().thenApplyAsync(scene->{var copy=AreaExport.crop(scene,bounds);return new Preview(copy,RecordedCoverage.knownVolume(copy,bounds));}).whenComplete((preview,error)->minecraft.execute(()->{
                busy=false;if(error!=null){status=Lang.stored("worldbinder.area.failed",rootMessage(error));rebuild();return;}
                var scene=preview.scene();long coverage=preview.coverage();
                ClientCompat.setScreen(minecraft,new WorldBinderExportScreen(this,scene,Lang.string("worldbinder.area.coverage",coverage,bounds.volume()),()->{
                    if(schematic){writeSchematic(scene,bounds);return;}
                    Path target=WorldBinderPaths.newWorldFolder(scene.name,false);StorageFlow.progress().start(target);ClientCompat.setScreen(minecraft,new WorldBinderStorageProgressScreen(this));
                    StorageFlow.submit(WorldBinderClient.scenes(),scene,target,false,path->{status=Lang.stored("worldbinder.area.saved",path);},failure->{status=Lang.stored("worldbinder.area.failed",rootMessage(failure));});
                }));
            }));
    }
    private void writeSchematic(WorldScene scene,CaptureBounds bounds){
        busy=true;status=Lang.stored("worldbinder.area.preparing");ClientCompat.setScreen(minecraft,this);
        CompletableFuture.supplyAsync(()->{try{String file=FileNames.cleanBaseName(scene.name)+"-"+UUID.randomUUID().toString().substring(0,8)+".schem";
            return SpongeSchematicExporter.write(scene,bounds,WorldBinderPaths.RECOVERY_ROOT.resolve("area_exports").resolve(file));
        }catch(java.io.IOException error){throw new CompletionException(error);}}).whenComplete((result,error)->minecraft.execute(()->{busy=false;status=error==null?Lang.stored("worldbinder.area.saved",result.file()):Lang.stored("worldbinder.area.failed",rootMessage(error));rebuild();}));
    }
    private static String rootMessage(Throwable error){while(error.getCause()!=null)error=error.getCause();return String.valueOf(error.getMessage());}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);int w=Math.min(440,width-36),x=(width-w)/2,col=(w-8)/3;WbChrome.drawPanel(graphics,x-10,8,w+20,height-16);GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        String[] labels={"X1","Y1","Z1","X2","Y2","Z2"};for(int i=0;i<6;i++)graphics.text(font,labels[i],x+(i%3)*(col+4),64+(i/3)*34,WbTheme.TEXT_SOFT,false);
        String line=status.isEmpty()?Lang.string("worldbinder.area.real_blocks"):Lang.resolve(status);
        WbText.drawClipped(graphics,font,Component.literal(line),x,164,w,WbTheme.TEXT_SOFT);
        if(checkedBounds!=null)WbText.drawClipped(graphics,font,Lang.text("worldbinder.area.size",checkedBounds.width(),checkedBounds.height(),checkedBounds.length(),checkedBounds.volume()),x,176,w,WbTheme.ACCENT);
        super.extractRenderState(graphics,mouseX,mouseY,partial);WbTooltips.showHovered(this,graphics,font,mouseX,mouseY,mouseX,mouseY);
    }
    @Override public void onClose(){if(!busy)ClientCompat.setScreen(minecraft,parent);}
    private record Preview(WorldScene scene,long coverage){}
}

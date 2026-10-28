package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.*;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import java.util.*;

/** Ambiguous proxy transitions require a user decision, never a guessed backend name. */
public final class WorldBinderNetworkScreen extends WbScaledScreen {
    private final Screen parent;
    private String name="";
    private int page;
    private boolean remember;
    public WorldBinderNetworkScreen(Screen parent){super(Lang.text("worldbinder.network.title"));this.parent=parent;var capture=WorldBinderClient.capture();if(capture.isCapturing()&&!capture.routingPending())capture.worldBoundary("manual");}
    @Override protected void initContent(){
        int w=Math.min(540,width-28),x=(width-w)/2,half=(w-8)/2;
        var capture=WorldBinderClient.capture();var profile=capture.networkProfile();
        var input=new EditBox(font,x,58,w-134,20,Lang.text("worldbinder.network.backend_name"));input.setMaxLength(64);input.setValue(name);input.setHint(Lang.text("worldbinder.network.backend_name"));input.setResponder(v->name=v);addRenderableWidget(input);
        addRenderableWidget(WbButton.create(x+w-126,58,126,20,Lang.text(capture.routingFailed()?"worldbinder.network.retry_handoff":"worldbinder.network.new"),Lang.text("worldbinder.network.isolated"),b->{if(capture.routingFailed()){capture.retryRouting();rebuild();return;}String actual=name.isBlank()?Lang.string("worldbinder.network.unnamed"):name.trim();if(capture.assignBackend(null,actual,remember)){if(!capture.isCapturing())capture.startRoamingCapture(actual);onClose();}}));
        var backends=new ArrayList<>(profile.backends.values());backends.sort(Comparator.comparing(b->Objects.toString(b.name,b.id)));page=Math.max(0,Math.min(page,Math.max(0,(backends.size()-1)/4)));
        for(int i=page*4;i<Math.min(backends.size(),page*4+4);i++){
            var backend=backends.get(i);String label=Objects.toString(backend.name,backend.id);
            addRenderableWidget(WbButton.create(x,84+(i-page*4)*22,w,20,label,Lang.text("worldbinder.network.choose_help",backend.id),b->{
                if(capture.assignBackend(backend.id,backend.name,remember)){
                    if(!capture.isCapturing()){if(backend.projectPath!=null)capture.resumeAssignedProject(java.nio.file.Path.of(backend.projectPath));else capture.startRoamingCapture(backend.name);}
                    onClose();
                }
            }));
        }
        var previous=addRenderableWidget(WbButton.create(x,178,30,18,"‹",null,b->{page--;rebuild();}));previous.active=page>0;
        var next=addRenderableWidget(WbButton.create(x+36,178,30,18,"›",null,b->{page++;rebuild();}));next.active=(page+1)*4<backends.size();
        String auto=profile.autoCapture==null?Lang.string("worldbinder.network.inherit"):Lang.string(profile.autoCapture?"worldbinder.export.enabled":"worldbinder.export.disabled");
        addRenderableWidget(WbButton.create(x+half+8,178,half,20,Lang.text("worldbinder.network.auto_join",auto),Lang.text("worldbinder.capture_profile.help"),b->{profile.autoCapture=profile.autoCapture==null?false:profile.autoCapture?null:true;WorldBinder.config().save();rebuild();}));
        addRenderableWidget(WbButton.create(x,204,half,20,Lang.text("worldbinder.network.trust_dimensions",Lang.string(profile.trustDimensionChanges?"worldbinder.export.enabled":"worldbinder.export.disabled")),Lang.text("worldbinder.network.trust_help"),b->{profile.trustDimensionChanges=!profile.trustDimensionChanges;WorldBinder.config().save();rebuild();}));
        addRenderableWidget(WbButton.create(x+half+8,204,half,20,Lang.text("worldbinder.network.remember_join",Lang.string(remember?"worldbinder.export.enabled":"worldbinder.export.disabled")),Lang.text("worldbinder.network.remember_help"),b->{remember=!remember;rebuild();}));
        addRenderableWidget(WbButton.create(x,height-28,w,20,Lang.text(capture.routingPending()?"worldbinder.network.keep_paused":"worldbinder.gui.close"),null,b->onClose()));
    }
    @Override protected void renderContent(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        var capture=WorldBinderClient.capture();
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getY()>=58&&b.getY()<=170){b.active=!capture.isSaving()&&(!capture.isCapturing()||capture.routingReady()||b.getY()==58&&capture.routingFailed());if(b.getY()==58)b.setMessage(Lang.text(capture.routingFailed()?"worldbinder.network.retry_handoff":"worldbinder.network.new"));}
        int w=Math.min(540,width-28),x=(width-w)/2;
        WbChrome.drawPanel(graphics,x-8,8,w+16,height-16);GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        WbText.drawWrapped(graphics,font,capture.routingFailed()?capture.routingFailure():Lang.string("worldbinder.network.help"),x,32,w,capture.routingFailed()?WbTheme.ERROR:WbTheme.TEXT_SOFT,2);
    }
    @Override public void onClose(){ClientCompat.setScreen(minecraft,parent);}
}

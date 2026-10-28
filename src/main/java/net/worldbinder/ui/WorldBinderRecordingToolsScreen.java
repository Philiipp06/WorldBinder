package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.Lang;

/** Explicit recording automation, bounded overlay and the shared selection tools. */
public final class WorldBinderRecordingToolsScreen extends WbScaledScreen {
    private final Screen parent;
    public WorldBinderRecordingToolsScreen(Screen parent){super(Lang.text("worldbinder.recording_tools.title"));this.parent=parent;}
    @Override protected void initContent(){
        int x=(width-360)/2,y=48;var capture=WorldBinderClient.capture();
        var toggle=WbButton.create(x,y,176,20,Lang.text("worldbinder.scanner.toggle",Lang.string(capture.scannerEnabled()?"worldbinder.export.enabled":"worldbinder.export.disabled")),Lang.text("worldbinder.scanner.help"),b->{capture.toggleScanner();rebuild();});toggle.active=capture.isCapturing();addRenderableWidget(toggle);
        var resume=WbButton.create(x+184,y,176,20,Lang.text("worldbinder.scanner.resume"),Lang.text("worldbinder.scanner.help"),b->{capture.resumeScanner();onClose();});resume.active=capture.isCapturing();addRenderableWidget(resume);
        y+=26;
        var rescan=WbButton.create(x,y,176,20,Lang.text("worldbinder.scanner.rescan"),Lang.text("worldbinder.scanner.rescan_help"),b->{capture.rescanContainers();onClose();});rescan.active=capture.isCapturing();addRenderableWidget(rescan);
        addRenderableWidget(WbButton.create(x+184,y,176,20,Lang.text("worldbinder.scanner.skip",Lang.string(WorldBinder.config().scannerSkipVerified?"worldbinder.export.enabled":"worldbinder.export.disabled")),null,b->{WorldBinder.config().scannerSkipVerified=!WorldBinder.config().scannerSkipVerified;WorldBinder.config().save();rebuild();}));
        y+=60;
        addRenderableWidget(WbButton.create(x,y,176,20,Lang.text("worldbinder.overlay.toggle",Lang.string(WorldBinder.config().worldChunkOverlay?"worldbinder.export.enabled":"worldbinder.export.disabled")),Lang.text("worldbinder.overlay.legend"),b->{WorldBinder.config().worldChunkOverlay=!WorldBinder.config().worldChunkOverlay;WorldBinder.config().save();rebuild();}));
        addRenderableWidget(WbButton.create(x+184,y,176,20,Lang.text("worldbinder.overlay.radius",WorldBinder.config().worldChunkOverlayRadius),null,b->{WorldBinder.config().worldChunkOverlayRadius=WorldBinder.config().worldChunkOverlayRadius%8+1;WorldBinder.config().save();rebuild();}));
        addRenderableWidget(WbButton.create(x,y+58,176,20,Lang.text("worldbinder.area.title"),Lang.text("worldbinder.area.real_blocks"),b->ClientCompat.setScreen(minecraft,new WorldBinderRegionScreen(this))));
        addRenderableWidget(WbButton.create(x+184,y+58,176,20,Lang.text("worldbinder.capture_profile.title"),Lang.text("worldbinder.capture_profile.help"),b->ClientCompat.setScreen(minecraft,new WorldBinderCaptureSettingsScreen(this))));
        addRenderableWidget(WbButton.create(x,y+84,176,20,Lang.text("worldbinder.network.title"),Lang.text("worldbinder.network.help"),b->ClientCompat.setScreen(minecraft,new WorldBinderNetworkScreen(this))));
        var branch=WbButton.create(x+184,y+84,176,20,Lang.text("worldbinder.replay.branch"),Lang.text("worldbinder.replay.branch_help"),b->{capture.branchReplay();onClose();});branch.active=capture.exportPreview()!=null&&capture.exportPreview().replayIdentity!=null;addRenderableWidget(branch);
        addRenderableWidget(WbButton.create(x,height-28,360,20,Lang.text("worldbinder.gui.close"),null,b->onClose()));
    }
    @Override protected void renderContent(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);int x=(width-380)/2;
        WbChrome.drawPanel(graphics,x,8,380,height-16);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.scanner.help"),x+10,34,360,WbTheme.TEXT_SOFT);
        WbText.drawWrapped(graphics,font,WorldBinderClient.capture().scannerStatus(),x+10,100,360,WbTheme.ACCENT,2);
        WbText.drawWrapped(graphics,font,Lang.string("worldbinder.overlay.legend"),x+10,154,360,WbTheme.TEXT_SOFT,2);
    }
    @Override public void onClose(){ClientCompat.setScreen(minecraft,parent);}
}

package net.worldbinder.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.mixin.ScreenAccessor;
import net.worldbinder.scene.ContainerMerging;
import net.worldbinder.util.Lang;
import java.util.*;

/** Inline controls: no screen switch, menu close, inventory click or item movement. */
public final class ContainerMergeControls {
    private final Screen screen;
    private final List<Button> buttons=new ArrayList<>();
    private final Button toggle;
    private boolean open,projectDefault;
    private final int x,panelWidth;
    public ContainerMergeControls(Screen screen) {
        this.screen=screen;panelWidth=Math.min(250,screen.width-8);x=Math.max(4,screen.width-panelWidth-4);
        var access=(ScreenAccessor)screen;
        toggle=access.worldbinder$addRenderableWidget(WbButton.create(Math.max(4,screen.width-132),4,128,18,Lang.text("worldbinder.containers.menu"),Lang.text("worldbinder.containers.help"),b->{open=!open;WorldBinderClient.capture().onManualContainerInput();refresh();}));
        int y=58;
        for(var choice:ContainerMerging.Strategy.values()) {
            buttons.add(access.worldbinder$addRenderableWidget(WbButton.create(x+5,y,panelWidth-10,19,Lang.text("worldbinder.containers.strategy."+choice.name().toLowerCase(Locale.ROOT)),Lang.text("worldbinder.containers.help"),b->{WorldBinderClient.capture().mergeContainer(choice,projectDefault);refresh();})));
            y+=22;
        }
        buttons.add(access.worldbinder$addRenderableWidget(WbButton.create(x+5,y,panelWidth-10,19,defaultLabel(),defaultLabel(),b->{projectDefault=!projectDefault;b.setMessage(defaultLabel());WbTooltips.register(b,defaultLabel());})));
        refresh();
    }
    private net.minecraft.network.chat.Component defaultLabel(){return Lang.text("worldbinder.containers.project_default",Lang.string(projectDefault?"worldbinder.export.enabled":"worldbinder.export.disabled"));}
    private void refresh(){
        var capture=WorldBinderClient.capture();boolean visible=capture!=null&&capture.isCapturing()&&capture.containerTotalSlots()>0;
        toggle.visible=visible;
        for(int i=0;i<buttons.size();i++){var button=buttons.get(i);button.visible=visible&&open;button.active=capture!=null&&(i==buttons.size()-1||capture.containerKnownSlots()>0);}
    }
    public boolean click(MouseButtonEvent event,boolean doubleClick){
        refresh();
        if(toggle.visible&&toggle.isMouseOver(event.x(),event.y()))return toggle.mouseClicked(event,doubleClick);
        for(var button:buttons)if(button.visible&&button.isMouseOver(event.x(),event.y())){if(button.active)button.mouseClicked(event,doubleClick);return true;}
        return open&&toggle.visible&&event.x()>=x&&event.x()<x+panelWidth&&event.y()>=26&&event.y()<152;
    }
    public void render(GuiGraphicsExtractor graphics,net.minecraft.client.gui.Font font,int mouseX,int mouseY){
        refresh();if(!open||!toggle.visible)return;
        graphics.nextStratum();
        WbChrome.drawPanel(graphics,x,26,panelWidth,126);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.containers.received",WorldBinderClient.capture().containerKnownSlots(),WorldBinderClient.capture().containerTotalSlots()),x+6,34,panelWidth-12,WbTheme.ACCENT);
        WbText.drawClipped(graphics,font,Lang.text("worldbinder.containers.empty_known"),x+6,46,panelWidth-12,WbTheme.TEXT_MUTED);
        // The native screen already renders its widgets; this surface sits behind the inline controls.
        for(var button:buttons)if(button.visible)button.extractRenderState(graphics,mouseX,mouseY,0F);
        WbTooltips.showHovered(screen,graphics,font,mouseX,mouseY,mouseX,mouseY);
    }
}

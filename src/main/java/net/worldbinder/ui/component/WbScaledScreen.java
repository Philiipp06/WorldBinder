package net.worldbinder.ui.component;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Common virtual coordinates for compact, keyboard-accessible tool screens. */
public abstract class WbScaledScreen extends Screen {
    protected WbScaledScreen(Component title){super(title);}
    protected abstract void initContent();
    protected abstract void renderContent(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial);
    protected final void rebuild(){clearWidgets();init();}
    @Override protected final void init(){
        int w=width,h=height;var scale=WbLayout.uiScale(w,h);width=scale.virtualWidth();height=scale.virtualHeight();
        try{initContent();}finally{width=w;height=h;}
    }
    @Override public final void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        int w=width,h=height;var scale=WbLayout.uiScale(w,h);int mx=scale.toVirtualX(mouseX),my=scale.toVirtualY(mouseY);
        graphics.fill(0,0,w,h,WbTheme.BACKDROP);graphics.pose().pushMatrix();graphics.pose().translate(scale.offsetX(),scale.offsetY());graphics.pose().scale(scale.scale(),scale.scale());
        width=scale.virtualWidth();height=scale.virtualHeight();
        try{renderContent(graphics,mx,my,partial);super.extractRenderState(graphics,mx,my,partial);}finally{width=w;height=h;graphics.pose().popMatrix();}
        WbTooltips.showHovered(this,graphics,font,mx,my,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){return super.mouseClicked(WbLayout.virtualMouseEvent(event,width,height),doubleClick);}
    @Override public boolean mouseReleased(MouseButtonEvent event){return super.mouseReleased(WbLayout.virtualMouseEvent(event,width,height));}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){var scale=WbLayout.uiScale(width,height);return super.mouseDragged(WbLayout.virtualMouseEvent(event,width,height),scale.toVirtualDelta(dx),scale.toVirtualDelta(dy));}
}

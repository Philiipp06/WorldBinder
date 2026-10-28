package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import net.worldbinder.validation.*;
import java.util.*;

/** Read the complete report in-game, including chunk coordinates and long messages on small displays. */
public final class WorldBinderReportScreen extends Screen {
    private final Screen parent;
    private final ExportValidationReport report;
    private List<net.minecraft.util.FormattedCharSequence> lines;
    private int page;
    public WorldBinderReportScreen(Screen parent, ExportValidationReport report) {
        super(Lang.text("worldbinder.report.title"));this.parent=parent;this.report=report;
    }
    private int pageSize(){return Math.max(1,(height-92)/12);}
    @Override protected void init(){
        int x=20,w=width-40;
        List<String> text=new ArrayList<>();
        text.add(ExportValidator.shortLine(report));
        text.add(Lang.string("worldbinder.report.completeness",Lang.string(report.completeness.equals("COMPLETE_FOR_RECORDED_SCOPE")?"worldbinder.report.complete":"worldbinder.report.incomplete")));
        text.add(Lang.string("worldbinder.report.scope"));
        text.add("");text.add(Lang.string("worldbinder.report.errors"));
        text.addAll(report.errors.isEmpty()?List.of(Lang.string("worldbinder.report.none")):report.errors.stream().filter(Objects::nonNull).map(Finding::render).toList());
        text.add("");text.add(Lang.string("worldbinder.report.notes"));text.addAll(report.warnings.stream().filter(Objects::nonNull).map(Finding::render).toList());
        text.add("");text.addAll(report.passed.stream().filter(Objects::nonNull).map(Finding::render).toList());
        lines=new ArrayList<>();for(String line:text)lines.addAll(font.split(Component.literal(line.isEmpty()?" ":line),w));
        int pages=Math.max(1,(lines.size()+pageSize()-1)/pageSize());page=Math.min(page,pages-1);
        addRenderableWidget(WbButton.create(x,height-32,60,20,Component.literal("‹"),null,b->{page=Math.max(0,page-1);clearWidgets();init();}));
        addRenderableWidget(WbButton.create(x+66,height-32,60,20,Component.literal("›"),null,b->{page=Math.min(pages-1,page+1);clearWidgets();init();}));
        addRenderableWidget(WbButton.create(width-140,height-32,120,20,Lang.text("worldbinder.report.back"),null,b->onClose()));
    }
    @Override public void onClose(){net.worldbinder.compat.ClientCompat.setScreen(minecraft,parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float delta){
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);WbChrome.drawPanel(graphics,10,10,width-20,height-20);
        GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,18,WbTheme.TEXT);
        for(int row=0;row<pageSize();row++){int index=page*pageSize()+row;if(index>=lines.size())break;graphics.text(font,lines.get(index),20,43+row*12,WbTheme.TEXT_SOFT,true);}
        super.extractRenderState(graphics,mx,my,delta);
    }
}

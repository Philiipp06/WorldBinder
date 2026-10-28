package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.export.common.ExportCompatibility;
import net.worldbinder.scene.*;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;
import java.util.*;

/** Review a frozen recording before export; every replacement uses the same policy as the writer. */
public final class WorldBinderExportScreen extends Screen {
    static final java.util.concurrent.ExecutorService ANALYSIS=java.util.concurrent.Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"WorldBinder export analysis");thread.setDaemon(true);return thread;});
    private final Screen parent;
    private final WorldScene original;
    private final WorldScene draft;
    private final Runnable accept;
    private final Runnable cancel;
    private boolean resolved;
    private final String changes;
    private List<ExportCompatibility.Impact> impacts=List.of();
    private record Analysis(List<ExportCompatibility.Impact> impacts,net.worldbinder.export.common.ExportFiltering.Counts counts){}
    private java.util.concurrent.CompletableFuture<Analysis> analysis;
    private String signature="";
    private boolean analysisFailed;
    private net.worldbinder.export.common.ExportFiltering.Counts counts;
    private int page;
    private String selected;
    private String replacement = "minecraft:stone";
    private String error = "";
    private EditBox seed, block;
    private FooterLayout footer;
    private record FooterLayout(int navigationY, int resetX, int resetY, int resetWidth,
                                int inputY, int inputWidth, int applyX, int applyY, int applyWidth,
                                int statusWidth) {}

    public WorldBinderExportScreen(Screen parent, WorldScene scene, String changes, Runnable accept) {
        this(parent, scene, changes, accept, () -> {});
    }

    public WorldBinderExportScreen(Screen parent, WorldScene scene, String changes, Runnable accept, Runnable cancel) {
        super(Lang.text("worldbinder.export.review.title"));
        this.parent = parent; this.original = scene; this.accept = accept; this.changes = changes;
        this.cancel = cancel;
        draft=reviewCopy(scene);
    }
    private static WorldScene reviewCopy(WorldScene scene){
        var result=new WorldScene();result.exportSettings=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(scene.exportSettings),ExportSettings.class);
        result.targetMinecraftVersion=scene.targetMinecraftVersion;result.minecraftVersion=scene.minecraftVersion;result.originY=scene.originY;
        result.dimension=scene.dimension;result.originX=scene.originX;result.originZ=scene.originZ;result.dimensions=new java.util.LinkedHashMap<>(scene.dimensions);
        result.blocks=scene.blocks;result.entities=scene.entities;result.chunkSnapshots=scene.chunkSnapshots;result.containers=scene.containers;result.player=scene.player;return result;
    }

    private int left() { return (width - panelWidth()) / 2; }
    private int panelWidth() { return Math.min(780, width - 20); }
    private int rows() { return Math.max(1, (footer.navigationY() - 112) / 22); }

    private FooterLayout footerLayout() {
        int x = left() + 10, w = panelWidth() - 20, gap = 6;
        // Action labels grow with the selected language instead of using fixed English-sized widths.
        int applyWidth = Math.min(w, Math.max(134, font.width(Lang.text("worldbinder.export.review.apply")) + 18));
        int resetWidth = Math.min(w, Math.max(100, font.width(Lang.text("worldbinder.export.review.reset")) + 18));
        boolean stackInput = w - applyWidth - gap < 100;
        boolean stackReset = w - resetWidth - gap < 72;
        int applyY = height - 61;
        int inputY = stackInput ? applyY - 24 : applyY;
        int resetY = inputY - 23;
        int navigationY = stackReset ? resetY - 23 : resetY;
        return new FooterLayout(navigationY, stackReset ? x : x + w - resetWidth, resetY,
                stackReset ? w : resetWidth, inputY, stackInput ? w : w - applyWidth - gap,
                stackInput ? x : x + w - applyWidth, applyY, stackInput ? w : applyWidth,
                Math.max(0, stackReset ? w - 74 : w - resetWidth - gap - 74));
    }

    @Override protected void init() {
        String next=draft.targetMinecraftVersion+WorldBinder.GSON.toJson(draft.exportSettings);
        if(!signature.equals(next)){
            if(analysis!=null)analysis.cancel(false);analysisFailed=false;signature=next;var frozen=reviewCopy(draft);
            analysis=java.util.concurrent.CompletableFuture.supplyAsync(()->new Analysis(ExportCompatibility.analyze(frozen),net.worldbinder.export.common.ExportFiltering.count(frozen,frozen.exportSettings.filters)),ANALYSIS);
        }
        int x = left() + 10, w = panelWidth() - 20, gap = 6, half = (w - gap) / 2;
        footer = footerLayout();
        addRenderableWidget(WbButton.create(x, 36, half, 20, Lang.text("worldbinder.export.review.target", draft.targetMinecraftVersion),
                Lang.text("worldbinder.tooltip.target_version"), b -> net.worldbinder.compat.ClientCompat.setScreen(minecraft,
                        new WorldBinderVersionPickerScreen(this, draft.targetMinecraftVersion, version -> { draft.targetMinecraftVersion = version; page = 0; }))));
        addRenderableWidget(WbButton.create(x + half + gap, 36, half, 20, playerLabel(), Lang.text("worldbinder.export.review.player_help"), b -> {
            draft.exportSettings.includePlayer = !draft.exportSettings.includePlayer; rebuild();
        }));
        addRenderableWidget(WbButton.create(x, 61, half, 20, generationLabel(), Lang.text("worldbinder.export.review.generation_help"), b -> {
            var values = ExportSettings.Generation.values(); draft.exportSettings.generation = values[(draft.exportSettings.generation.ordinal() + 1) % values.length];
            b.setMessage(generationLabel());
        }));
        seed = new EditBox(font, x + half + gap, 61, half, 20, Lang.text("worldbinder.export.review.seed"));
        seed.setMaxLength(128); seed.setValue(draft.exportSettings.seed); seed.setHint(Lang.text("worldbinder.export.review.seed"));
        seed.setResponder(value -> draft.exportSettings.seed = value); addRenderableWidget(seed);
        WbTooltips.register(seed, Lang.text("worldbinder.export.review.seed_help"));
        // Filters and extra files share the 134 px action column so narrow windows keep the counts text readable.
        addRenderableWidget(WbButton.create(x+w-110,87,110,18,Lang.text("worldbinder.filters.title"),Lang.text("worldbinder.filters.original_kept"),b->net.worldbinder.compat.ClientCompat.setScreen(minecraft,new WorldBinderExportFiltersScreen(this,draft,this::rebuild))));
        addRenderableWidget(WbButton.iconOnly(WbButton.create(x+w-134,87,18,18,Lang.text("worldbinder.export.extras.title"),Lang.text("worldbinder.export.extras.tooltip"),b->net.worldbinder.compat.ClientCompat.setScreen(minecraft,new WorldBinderExportExtrasScreen(this,draft,this::rebuild))),WbIcon.FOLDER));
        int totalPages = Math.max(1, (impacts.size() + rows() - 1) / rows()); page = Math.min(page, totalPages - 1);
        for (int row = 0; row < rows(); row++) {
            int index = page * rows() + row; if (index >= impacts.size()) break;
            var impact = impacts.get(index);
            String result = switch (impact.kind()) {
                case "height" -> Lang.string("worldbinder.export.impact.height");
                case "data" -> Lang.string("worldbinder.export.impact.data");
                default -> Lang.resolve(impact.replacement());
            };
            String label = impact.kind().equals("unknown") ? Lang.string("worldbinder.export.review.unknown")
                    : impact.count() + " × " + Lang.resolve(impact.source()) + " → " + result;
            var button = WbButton.create(x, 112 + row * 22, w, 20, Component.literal(label),
                    Component.literal(label), b -> { selected = impact.source(); error = ""; rebuild(); });
            button.active = impact.kind().equals("replacement") || impact.kind().equals("properties")||impact.kind().equals("palette");
            addRenderableWidget(button);
        }
        int bottom = footer.navigationY();
        addRenderableWidget(WbButton.create(x, bottom, 30, 18, Component.literal("‹"), Lang.text("worldbinder.export.review.previous"), b -> { page = Math.max(0,page - 1); rebuild(); }));
        addRenderableWidget(WbButton.create(x + 36, bottom, 30, 18, Component.literal("›"), Lang.text("worldbinder.export.review.next"), b -> { page = Math.min(totalPages-1,page+1); rebuild(); }));
        addRenderableWidget(WbButton.create(footer.resetX(), footer.resetY(), footer.resetWidth(), 18,
                Lang.text("worldbinder.export.review.reset"), Lang.text("worldbinder.export.review.reset"), b -> {
            draft.exportSettings.replacements.clear(); selected = null; rebuild();
        }));
        block = new EditBox(font, x, footer.inputY(), footer.inputWidth(), 20, Lang.text("worldbinder.export.review.replacement"));
        block.setMaxLength(512); block.setValue(replacement); block.setResponder(value -> replacement = value);
        addRenderableWidget(block); WbTooltips.register(block, Component.literal(selected == null ? Lang.string("worldbinder.export.review.select") : selected));
        var apply = WbButton.create(footer.applyX(),footer.applyY(),footer.applyWidth(),20,Lang.text("worldbinder.export.review.apply"),Lang.text("worldbinder.export.review.select"), b -> applyReplacement());
        apply.active = selected != null; addRenderableWidget(apply);
        addRenderableWidget(WbButton.create(x,height-27,half,20,Lang.text("worldbinder.gui.cancel"),null,b -> onClose()));
        var exportButton=WbButton.create(x+half+gap,height-27,half,20,Lang.text("worldbinder.export.review.export"),null,b -> {
            if (resolved) return;
            resolved = true;
            original.exportSettings = draft.exportSettings; original.targetMinecraftVersion = draft.targetMinecraftVersion;
            original.targetGenerationProfile = net.worldbinder.version.TargetMinecraftVersion.profileLabel(draft.targetMinecraftVersion);
            net.worldbinder.compat.ClientCompat.setScreen(minecraft,parent); accept.run();
        });exportButton.active=analysis==null&&!analysisFailed;addRenderableWidget(exportButton);
    }

    private Component playerLabel() { return Lang.text("worldbinder.export.review.player", Lang.string(draft.exportSettings.includePlayer ? "worldbinder.export.enabled" : "worldbinder.export.disabled")); }
    private Component generationLabel() { return Lang.text("worldbinder.export.review.generation", Lang.string("worldbinder.export.generation." + draft.exportSettings.generation.name().toLowerCase(Locale.ROOT))); }
    private void rebuild() { clearWidgets(); init(); }
    private void applyReplacement() {
        try {
            ExportCompatibility.setReplacement(draft, selected, replacement);
            error = ""; rebuild();
        } catch (BlockStateStrings.ValidationException exception) { error = exception.storedMessage();
        } catch (Exception exception) { error = Lang.string("worldbinder.export.review.invalid_replacement"); }
    }
    @Override public void onClose() {
        if (resolved) return;
        resolved = true;
        if(analysis!=null)analysis.cancel(false);
        cancel.run();
        net.worldbinder.compat.ClientCompat.setScreen(minecraft,parent);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if(analysis!=null&&analysis.isDone()){
            try{var result=analysis.join();impacts=result.impacts();counts=result.counts();}catch(RuntimeException exception){analysisFailed=true;error=Lang.stored("worldbinder.filters.failed");}
            analysis=null;rebuild();
        }
        graphics.fill(0,0,width,height,WbTheme.BACKDROP);
        WbChrome.drawPanel(graphics,left(),8,panelWidth(),height-16);
        GuiText.drawCenteredTextWithShadow(graphics,font,title,width/2,15,WbTheme.TEXT);
        int x=left()+10,w=panelWidth()-20;
        WbText.drawClipped(graphics,font,Component.literal(changes),x,87,w-140,WbTheme.ACCENT);
        WbText.drawClipped(graphics,font,counts==null?Lang.text("worldbinder.filters.counting"):Lang.text("worldbinder.filters.summary",counts.blocks(),counts.blockEntities(),counts.entities()),x,99,w-140,WbTheme.TEXT_MUTED);
        if (impacts.isEmpty()) WbText.drawClipped(graphics,font,Lang.text("worldbinder.export.review.no_known_changes"),x,116,w,WbTheme.TEXT_SOFT);
        String status = error.isEmpty() ? (selected == null ? Lang.string("worldbinder.export.review.select") : selected) : error;
        WbText.drawClipped(graphics,font,Component.literal(status),x+74,footer.navigationY()+4,footer.statusWidth(),WbTheme.TEXT_SOFT);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        WbTooltips.showHovered(this,graphics,font,mouseX,mouseY,mouseX,mouseY);
    }
}

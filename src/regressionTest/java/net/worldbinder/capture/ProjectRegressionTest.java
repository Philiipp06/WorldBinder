package net.worldbinder.capture;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.platform.*;
import net.worldbinder.scene.*;
import net.worldbinder.validation.*;
import net.worldbinder.storage.BlockRecordChunkCache;
import net.worldbinder.export.common.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Standalone file/data regression tests, deliberately distinguished from actual game acceptance. */
public final class ProjectRegressionTest {
    private static int assertions;
    private static Path root;
    private static SceneLibrary library;
    public static void main(String[] args) throws Exception {
        root = Path.of(".").toAbsolutePath().normalize().resolve("run-" + UUID.randomUUID()); Files.createDirectories(root);
        Platform.install(new ClientPlatform() {
            public Path gameDirectory() { return root; } public Path configDirectory() { return root.resolve("config"); }
            public boolean modLoaded(String id) { return false; } public KeyMapping registerKey(KeyMapping key) { return key; }
            public void endTick(Consumer<Minecraft> action) {} public void hud(BiConsumer<GuiGraphicsExtractor,DeltaTracker> action) {}
            public void joined(Runnable action) {} public void disconnected(Runnable action) {}
        });
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); WorldBinder.init();
        WorldBinder.config().includeServerResourcePack = false; WorldBinder.config().zipWorldExport = false;
        library = new SceneLibrary();
        assertions += ConfigUpgradeRegression.run(root);
        assertions += BetaPolicyRegression.run();
        assertions += MultiDimensionRegression.run(root);
        assertions += LegacyMappingRegression.run(root);
        assertions += LegacyProgressRegression.run(root);
        recoveryOwnership();
        selectionBoundsAndCoverage();
        scannerQueue();overlayStates();filtersAndSchematics();languageFindings();localizedExportDetails();readbackExclusions();
        mapsAndLegacy(); chunkReplacement(); multiRangeUpdate(); arrayCompatibility(); compatibilityPreview(); strictReplacements(); markerAngles(); generationOptions(); packProvenance(); cacheFailure(); missingCaches(); exportRoundTrips(); playerOptIn(); serverLists(); containerStates(); gameRuleCatalog(); changedLighting(); changedPlayerFiles(); failedSave(); corruptExport(); storageProgress();
        Files.writeString(root.resolve("RESULT.txt"), "PASS " + assertions + " assertions; file/data regression only. No in-game acceptance.\n");
        System.out.println("WORLDBINDER_REGRESSION_PASS " + assertions + " assertions; evidence: " + root);
    }

    private static void recoveryOwnership() throws Exception {
        var first=fixture();var second=fixture();second.projectId=first.projectId;second.worldIdentity="another-world";
        var third=fixture();third.projectId=first.projectId;third.dimension="minecraft:the_nether";
        var folders=new ArrayList<Path>();
        for(var scene:List.of(first,second,third)){
            var folder=RecoveryProjects.folder(net.worldbinder.io.WorldBinderPaths.RECOVERY_ROOT,scene);
            library.saveRecoverySnapshot(scene,folder);folders.add(folder);
        }
        check(new HashSet<>(folders).size()==3,"Equal names and even IDs cannot collide across worlds/dimensions");
        for(int i=0;i<folders.size();i++)check(RecoveryProjects.sameOwner(library.read(folders.get(i)),List.of(first,second,third).get(i)),"Recovery retains persistent owner after reopen");
        byte[] prior=Files.readAllBytes(folders.getFirst().resolve("worldbinder/worldbinder_archive.json"));
        boolean rejected=false;try{library.saveRecoverySnapshot(second,folders.getFirst());}catch(java.io.IOException expected){rejected=true;}
        check(rejected&&Arrays.equals(prior,Files.readAllBytes(folders.getFirst().resolve("worldbinder/worldbinder_archive.json"))),"Foreign recovery write rejected without damaging last complete archive");
        var broken=ProjectData.metadataCopy(first);var invalid=new CapturedMap();invalid.colors=new byte[2];broken.maps.put(42,invalid);
        rejected=false;try{library.saveRecoverySnapshot(broken,folders.getFirst());}catch(java.io.IOException expected){rejected=true;}
        check(rejected&&Arrays.equals(prior,Files.readAllBytes(folders.getFirst().resolve("worldbinder/worldbinder_archive.json"))),"Failed recovery verification preserves previous complete generation");
        var newer=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(first),WorldScene.class);newer.blocks.set(0,new BlockRecord(1,64,1,"minecraft:gold_block",false,null));
        Path manifest=folders.getFirst().resolve("worldbinder/worldbinder_manifest.json"),blocked=manifest.resolveSibling("worldbinder_manifest.json.tmp");byte[] previousManifest=Files.readAllBytes(manifest);
        Files.createDirectory(blocked);
        try{rejected=false;try{library.saveRecoverySnapshot(newer,folders.getFirst());}catch(java.io.IOException expected){rejected=true;}
            check(rejected&&Arrays.equals(prior,Files.readAllBytes(folders.getFirst().resolve("worldbinder/worldbinder_archive.json")))&&Arrays.equals(previousManifest,Files.readAllBytes(manifest)),"An unwritable recovery manifest cannot replace the previous complete archive or metadata");
        }finally{Files.delete(blocked);}
        var legacy=net.worldbinder.io.WorldBinderPaths.RECOVERY_ROOT.resolve("_recovery_old-name");
        library.saveRecoverySnapshot(first,legacy);check(library.read(legacy).projectId.equals(first.projectId),"Legacy named recovery folders remain readable");
        library.cleanupRecovery(first);
        check(!Files.exists(folders.getFirst())&&!Files.exists(legacy)&&Files.isRegularFile(folders.get(1).resolve("worldbinder/worldbinder_archive.json"))&&Files.isRegularFile(folders.get(2).resolve("worldbinder/worldbinder_archive.json")),"Cleanup removes only this project/world/dimension, including its old alias");
    }

    private static void selectionBoundsAndCoverage() {
        var bounds=new net.worldbinder.selection.CaptureBounds(-30000000,-64,-30000000,30000000,319,30000000);
        var cursor=new SelectionWork(bounds);
        check(bounds.volume()>1_000_000_000L&&cursor.nextChunk==0&&cursor.tile==null,"Huge selection creates only a constant-memory cursor");
        var small=new net.worldbinder.selection.CaptureBounds(-1,63,0,1,65,1);
        long total=0;Set<String> positions=new HashSet<>();
        cursor=new SelectionWork(small);
        for(long i=0;i<small.chunkCount();i++){
            var tile=cursor.nextTile();cursor.start(tile,null,null);
            while(cursor.block<tile.volume()){var pos=cursor.position();check(small.contains(pos.getX(),pos.getY(),pos.getZ()),"Every incremental position is inside all three bounds");positions.add(pos.toShortString());cursor.block++;total++;}
        }
        check(total==18&&positions.size()==18,"Chunk-tiled cursor visits each selected position once, including negative coordinates");
        var scene=fixture();scene.chunkSnapshots.clear();scene.blocks.clear();
        var snapshot=new ChunkSnapshot(0,0);snapshot.captureMinY=64;snapshot.captureMaxY=64;snapshot.captureBox=new int[]{1,64,1,2,64,2};snapshot.markDone();
        scene.chunkSnapshots.put("0,0",snapshot);
        var region=new net.worldbinder.selection.CaptureBounds(0,63,0,31,65,15);
        check(net.worldbinder.selection.RecordedCoverage.knownVolume(scene,region)==4,"Unreceived chunks and unscanned Y/X/Z remain unknown, even when a neighbouring tile is empty");
        check(net.worldbinder.selection.RecordedCoverage.known(scene,1,64,1)&&!net.worldbinder.selection.RecordedCoverage.known(scene,1,65,1),"Confirmed air is known only inside the exact captured volume");
        var previous=List.of(new BlockRecord(1,64,1,"minecraft:stone",false,null),new BlockRecord(3,64,1,"minecraft:diamond_block",false,null),new BlockRecord(1,63,1,"minecraft:dirt",false,null));
        var merged=ProjectData.mergeChunk(scene,previous,List.of(),snapshot);
        check(merged.size()==2&&merged.stream().noneMatch(b->b.x==1&&b.y==64),"Selected air removes inside blocks while preserving neighbouring columns and Y levels");
        scene.selectionBounds=region;var copy=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(scene),WorldScene.class);
        check(copy.selectionBounds.equals(region)&&net.worldbinder.selection.RecordedCoverage.knownVolume(copy,region)==4,"Selection scope and confirmed coverage survive archive serialization");
    }
    private static void scannerQueue(){
        var queue=new ContainerScanQueue<String>();
        check(queue.offer("double-chest","A")&&!queue.offer("double-chest","same other half")&&queue.offer("barrel","B"),"Scanner canonical targets are unique, including double chest halves");
        check(queue.tick(0,60,2)==ContainerScanQueue.Action.OPEN&&queue.current().equals("A"),"Scanner opens one target");
        check(queue.opened(7,1,60)&&queue.confirmed()==0,"An open menu is not a successful capture");
        check(queue.tick(40,60,2)==ContainerScanQueue.Action.NONE&&queue.current().equals("A"),"Delayed full server response prevents parallel opening");
        check(!queue.received(8)&&queue.confirmed()==0,"Unrelated menu contents cannot confirm this target");
        check(queue.received(7)&&queue.confirmed()==1&&queue.tick(42,60,2)==ContainerScanQueue.Action.CLOSE,"Only associated full contents lead to closing");
        queue.closed(7,43);check(queue.tick(50,60,2)==ContainerScanQueue.Action.NONE&&queue.tick(53,60,2)==ContainerScanQueue.Action.OPEN&&queue.current().equals("B"),"Next target opens only after owned close and cooldown");
        queue.opened(8,54,60);check(queue.tick(114,60,2)==ContainerScanQueue.Action.CLOSE&&queue.confirmed()==1,"Missing full content times out without claiming checked empty");
        queue.closed(8,114);check(queue.tick(124,60,2)==ContainerScanQueue.Action.OPEN,"Bounded retry uses the same target");
        check(queue.tick(184,60,2)==ContainerScanQueue.Action.NONE&&queue.phase()==ContainerScanQueue.Phase.SUSPENDED&&queue.failed()==1,"Unattributed open timeout halts the queue after its retry limit");
        queue.resume();queue.offer("manual","C");queue.tick(200,60,2);queue.opened(9,201,60);queue.suspend();
        check(!queue.received(9)&&queue.tick(300,60,2)==ContainerScanQueue.Action.NONE,"Manual/pause/disconnect cancellation rejects late success and never auto-closes a manual menu");
        queue.rescan();check(queue.confirmed()==0&&queue.offer("double-chest","A"),"Explicit rescan allows previously checked empty or filled containers again");
        int accepted=0;for(int i=0;i<100;i++)if(queue.offer("bounded-"+i,"X"))accepted++;
        check(accepted==63,"Scanner discovery has a bounded pending queue");
    }
    private static void overlayStates(){
        var committed=snapshot(63,65);var scanning=new ChunkSnapshot(0,0);scanning.markScanning();
        check(net.worldbinder.render.WorldBinderWorldOverlay.state(null,null,false,false)==null,"Exploration alone never creates a secured world frame");
        check(net.worldbinder.render.WorldBinderWorldOverlay.state(committed,committed,false,false)==net.worldbinder.render.WorldBinderWorldOverlay.State.SECURED,"Committed recorded scope is green");
        check(net.worldbinder.render.WorldBinderWorldOverlay.state(scanning,committed,true,false)==net.worldbinder.render.WorldBinderWorldOverlay.State.PROCESSING,"Updating a secured chunk is red while retaining the old secured revision");
        check(net.worldbinder.render.WorldBinderWorldOverlay.state(scanning,committed,true,true)==net.worldbinder.render.WorldBinderWorldOverlay.State.FAILED,"Processing errors take yellow priority");
        var hot=SceneCaptureService.detachedMetadataSnapshot(committed,committed,0,0);hot.markError("test metadata failure");
        check(hot!=committed&&committed.isDone()&&hot.exportError,"A live block-entity metadata failure cannot invalidate the committed snapshot");
    }

    private static void languageFindings()throws Exception{
        var legacy=WorldBinder.GSON.fromJson("{\"errors\":[\"An old error with minecraft:stone\"],\"warnings\":[\"Old warning\"]}",ExportValidationReport.class);
        check(legacy.errors.getFirst().render("de_de").equals("An old error with minecraft:stone"),"Legacy report text remains readable without pretending to translate captured text");
        var nested=Finding.of("worldbinder.finding.value_value","region/r.0.0.mca",Finding.of("worldbinder.error.missing_chunk_nbt"));
        var report=new ExportValidationReport();report.errors.add(nested);String json=WorldBinder.GSON.toJson(report);var loaded=WorldBinder.GSON.fromJson(json,ExportValidationReport.class);
        check(json.contains("\"key\"")&&loaded.errors.getFirst().render("en_us").contains("Missing chunk NBT")&&loaded.errors.getFirst().render("de_de").contains("Chunk-NBT fehlt"),"Nested persisted findings render in either language after reload");
        String stored=net.worldbinder.util.Lang.stored("worldbinder.area.coverage",12,24);
        check(!net.worldbinder.util.Lang.resolve(stored,"de_de").equals(net.worldbinder.util.Lang.resolve(stored,"en_us"))&&!net.worldbinder.util.Lang.resolve(stored,"de_de").contains("worldbinder."),"Progress keys/arguments survive storage and resolve in the chosen language");
        var scene=fixture();scene.storageNotes.add(stored);var file=root.resolve("localized-archive.json");library.save(scene,file);
        check(net.worldbinder.util.Lang.resolve(library.read(file).storageNotes.getLast(),"en_us").contains("12"),"Deferred archive notes survive saving and reopening");
        check(net.worldbinder.util.Lang.in("de_de","worldbinder.archive.quality_summary","PASS",90,"gut","").contains("90%"),"Escaped percent placeholders format correctly");
        check(!net.worldbinder.util.Lang.in("de_de","worldbinder.test.missing_key").contains("worldbinder."),"Missing keys produce a localized fallback instead of exposing an internal key");
        check(loaded.errors.getFirst().render("fr_fr").contains("NBT de chunk manquant")&&net.worldbinder.util.Lang.resolve(stored,"fr_fr").contains("12 / 24")&&!net.worldbinder.util.Lang.resolve(stored,"fr_fr").equals(net.worldbinder.util.Lang.resolve(stored,"en_us")),"French findings and stored progress resolve from the French catalog");
        check(net.worldbinder.util.Lang.bundle("fr_ca").equals("fr_fr")&&net.worldbinder.util.Lang.bundle("de_at").equals("de_de")&&net.worldbinder.util.Lang.bundle("es_es").equals("en_us")&&net.worldbinder.util.Lang.bundle(null).equals("en_us"),"Regional French/German variants share their catalog; other languages fall back to English");
        var folder=library.saveWorldFolder(scene,root.resolve("bilingual-world"));String readme=Files.readString(folder.resolve("README.md"));
        check(readme.contains("Deutsch")&&readme.contains("English"),"Generated world README provides both supported languages");
        String text=Files.readString(folder.resolve("worldbinder/export_validation.txt"));check(text.contains("Prüf")&&text.contains("EXPORT FILE READ-BACK")&&!text.contains("worldbinder.finding.")&&!text.contains("\u001eworldbinder:"),"Generated reports contain rendered bilingual findings, never storage tokens or translation keys");
    }

    private static void localizedExportDetails() throws Exception {
        String image = net.worldbinder.render.MapExportInfo.image2d("Captured Name", new int[]{-2, 3, 4, 5}, 2, 15, 4, false);
        check(image.contains("Welt: Captured Name") && image.contains("World: Captured Name")
                && image.contains("x -32, z 48") && image.contains("4 Pixel pro Block") && image.contains("4 px per block"), "2D map metadata uses both catalogs without altering captured names, bounds or detail");
        String tiles = net.worldbinder.render.MapExportInfo.image2d("Server Name", new int[4], 1, 2, 1, true);
        check(tiles.contains("tiles/r.<regionX>.<regionZ>.png") && tiles.contains("1 Pixel = 1 Block") && tiles.contains("1 px = 1 block"), "Tiled map descriptions are bilingual and retain the technical file pattern");
        String render = net.worldbinder.render.MapExportInfo.image3d("World", 1.5, -2.5, 64, 45, 30, 1.5, 640, 360, 2, 8);
        check(render.contains("Höhenskalierung: 150%") && render.contains("height scale: 150%")
                && render.contains("map_3d_sky.png") && render.contains("640 × 360"), "3D image metadata preserves values and renders escaped percentages once");
        String model = net.worldbinder.render.MapExportInfo.model("World", 12, 8, true, -32, 48, 2, 1, 24, 12, true);
        check(model.contains("8 mit Blockhöhenbereichen") && model.contains("8 with block spans")
                && model.contains("2 × 2 Blockzellen") && model.contains("map_3d.obj"), "3D model branches use translated units, spans and output descriptions");
        String simple = net.worldbinder.render.MapExportInfo.model("World", 12, 0, false, 0, 0, 1, 1, 4, 2, false);
        check(simple.contains("nur Höhenkarte") && simple.contains("height map only") && !simple.contains("map_3d.obj"), "Height-map-only GLB metadata reports the correct files in both languages");
        check(net.worldbinder.render.MapExportInfo.objHeader().contains("Eckpunktfarben")
                && net.worldbinder.render.MapExportInfo.objHeader().contains("vertex colors"), "OBJ human comments use the catalog while OBJ syntax remains stable");
        for(String text:List.of(image, tiles, render, model, simple, ExportLegalNotice.text(), ExportLegalNotice.markdown(), ExportLegalNotice.shortLine()))
            check(!text.contains("worldbinder.generated.") && !text.contains("\u001eworldbinder:") && text.contains("Mojang"), "Generated metadata renders language keys and includes its bilingual notice");
        var target = net.worldbinder.version.TargetMinecraftVersion.resolve("1.13.2");
        check(!target.profile().label("de_de").equals(target.profile().label("en_us")), "Generation profile labels can be generated in both languages independent of the client");
        var cause = new FindingStateException(Finding.of("worldbinder.error.received_chunks"));
        var nested = Finding.of("worldbinder.finding.value_value", "r.0.0.mca", Finding.detail(cause));
        var retained = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(nested), Finding.class);
        check(retained.render("de_de").contains("empfangenen Chunks") && retained.render("en_us").contains("received by the client"), "State failures retain structured translated context after JSON persistence");
        var io = new FindingUncheckedIOException(Finding.of("worldbinder.error.persist_validation"), new java.io.IOException("filesystem detail"));
        check(Finding.detail(io) instanceof Finding && io.getCause().getMessage().equals("filesystem detail"), "Unchecked IO failures retain the finding and original OS cause without changing exception semantics");
        var source = fixture(); source.targetMinecraftVersion = "1.13.2";
        var folder = root.resolve("localized-profile-readme"); Files.createDirectories(folder);
        ExportReadmeWriter.write(source, folder, 1);
        String readme = Files.readString(folder.resolve("README.md"));
        check(readme.contains(target.profile().label("de_de")) && readme.contains(target.profile().label("en_us")), "Each generated README section uses its own translated generation profile");
    }

    static void rewriteRegion(Path folder,boolean entities,java.util.function.Consumer<CompoundTag> mutation)throws Exception{
        Path directory=folder.resolve(entities?"entities":"region");var nbt=RegionReadback.read(directory,new ArrayList<>()).get(0L);mutation.accept(nbt);
        var info=new net.minecraft.world.level.chunk.storage.RegionStorageInfo("worldbinder-qa",net.minecraft.world.level.Level.OVERWORLD,entities?"entities":"chunk");
        try(var file=new net.minecraft.world.level.chunk.storage.RegionFile(info,directory.resolve("r.0.0.mca"),directory,false);var out=file.getChunkDataOutputStream(new net.minecraft.world.level.ChunkPos(0,0))){NbtIo.write(nbt,out);}
    }
    private static void readbackExclusions()throws Exception{
        for(String version:List.of("1.13.2","1.20.4","26.3")){
            var scene=fixture();scene.targetMinecraftVersion=version;var excluded=entity("minecraft:item",1.5,64,1.5);scene.entities.add(excluded);scene.entities.add(entity("minecraft:armor_stand",1.25,64,1.75));scene.exportSettings.filters.droppedItems=true;scene.exportSettings.filters.blockIds.add("minecraft:chest");
            var folder=library.saveWorldFolder(scene,root.resolve("filter-corruption-"+version));
            check(ExportValidator.validate(scene,folder).filesValid,"Baseline filtered world has exact block entity/entity contents: "+version);
            Path blocks=folder.resolve("region/r.0.0.mca");byte[] original=Files.readAllBytes(blocks);boolean old=version.equals("1.13.2");
            rewriteRegion(folder,false,root->{var chunk=root.getCompound("Level").orElse(root);var be=new CompoundTag();be.putString("id","minecraft:chest");be.putInt("x",2);be.putInt("y",64);be.putInt("z",2);chunk.getList(old?"TileEntities":"block_entities").orElseThrow().add(be);});
            check(ExportValidator.validate(scene,folder).errors.stream().anyMatch(e->e.render().contains("Unexpected")),"Read-back rejects a surviving filtered block entity: "+version);Files.write(blocks,original);
            Path entityFile=folder.resolve(old?"region/r.0.0.mca":"entities/r.0.0.mca");byte[] entities=Files.readAllBytes(entityFile);
            rewriteRegion(folder,!old,root->{var chunk=root.getCompound("Level").orElse(root);chunk.getList("Entities").orElseThrow().getCompound(0).orElseThrow().putString("id","minecraft:pig");});
            check(!ExportValidator.validate(scene,folder).filesValid,"Readable entity with the original UUID but wrong type is detected: "+version);Files.write(entityFile,entities);
            rewriteRegion(folder,!old,root->{var chunk=root.getCompound("Level").orElse(root);chunk.getList("Entities").orElseThrow().add(WorldExportEngine.normalizedEntityNbt(scene,excluded));});
            check(ExportValidator.validate(scene,folder).errors.stream().anyMatch(e->e.render().contains("Entity count differs")),"Read-back rejects an extra excluded entity: "+version);Files.write(entityFile,entities);
            check(ExportValidator.validate(scene,folder).filesValid,"Restored original files pass strict content validation: "+version);
        }
    }
    private static EntityRecord entity(String type,double x,double y,double z){var value=new EntityRecord();value.uuid=UUID.randomUUID().toString();value.type=type;value.x=x;value.y=y;value.z=z;value.fullNbt="{}";return value;}
    private static void filtersAndSchematics() throws Exception {
        var scene=fixture();scene.entities.add(entity("minecraft:item",1.25,64,1.25));scene.entities.add(entity("minecraft:experience_orb",1.5,64,1.5));scene.entities.add(entity("minecraft:zombie",1,64,1));scene.entities.add(entity("minecraft:armor_stand",1.75,64,1.75));
        scene.exportSettings.filters.droppedItems=true;scene.exportSettings.filters.experienceOrbs=true;scene.exportSettings.filters.entityIds.add("minecraft:zombie");scene.exportSettings.filters.blockIds.add("minecraft:chest");
        String original=WorldBinder.GSON.toJson(scene);var filtered=ExportFiltering.apply(scene);
        check(filtered.filterCounts.blocks()==1&&filtered.filterCounts.blockEntities()==1&&filtered.filterCounts.entities()==3,"Filter preview counts blocks, their block entities and selected entity categories/types");
        check(filtered.blocks.size()==2&&filtered.entities.size()==1&&!filtered.containers.containsKey(ContainerRecord.blockKey(2,64,2)),"Filtered blocks lose associated container data only in the export copy");
        check(ExportLightPolicy.invalidatedChunks(filtered).containsAll(Set.of(0L,ProjectData.chunkKey(1,0)))&&WorldBinder.GSON.toJson(scene).equals(original),"Filtered opacity changes request neighbour relighting without mutating source blocks or light");
        Path folder=library.saveWorldFolder(scene,root.resolve("filtered-world"));check(ExportValidator.validate(scene,folder).filesValid,"Filtered world passes actual file-content read-back");
        var reopened=library.read(folder);check(reopened.blocks.size()==3&&reopened.entities.size()==4&&reopened.exportSettings.filters.blockIds.contains("minecraft:chest"),"Archive keeps all original contents and project filter choices after restart");
        reopened.exportSettings.filters=new ExportFilterSettings();
        Path restored=library.saveWorldFolder(reopened,root.resolve("unfiltered-again"));
        check(ExportValidator.validate(reopened,restored).filesValid,"Re-export after clearing filters restores originally excluded contents");
        boolean rejected=false;try{ExportFiltering.parseIds(scene,"minecraft:stnoe",true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Unknown filter registry ID is rejected");
        var source=fixture();source.blocks.add(new BlockRecord(1,66,1,"minecraft:gold_block",false,null));source.entities.add(entity("minecraft:armor_stand",1.25,64.5,1.75));source.entities.add(entity("minecraft:zombie",2,67,2));source.entities.add(entity("minecraft:cow",10,64,10));
        var bounds=new net.worldbinder.selection.CaptureBounds(1,64,1,3,66,3);String before=WorldBinder.GSON.toJson(source);
        var crop=net.worldbinder.export.AreaExport.crop(source,bounds);check(crop.blocks.size()==4&&crop.entities.size()==1&&crop.originX==1&&crop.originY==64&&crop.blocks.stream().anyMatch(b->b.state.equals("minecraft:stone")&&b.y==0),"Area export clips blocks/entities in all dimensions and keeps hidden full block data");
        check(net.worldbinder.selection.RecordedCoverage.knownVolume(crop,bounds)==27&&crop.chunkSnapshots.values().stream().allMatch(s->s.relightRequired),"Crop preserves exact known air coverage and persists boundary relighting");
        Path schematic=root.resolve("received-area.schem");var result=net.worldbinder.export.SpongeSchematicExporter.write(source,bounds,schematic);
        var nbt=NbtIo.readCompressed(schematic,NbtAccounter.unlimitedHeap()).getCompound("Schematic").orElseThrow();var blocks=nbt.getCompound("Blocks").orElseThrow();
        check(nbt.getInt("Version").orElse(-1)==3&&nbt.getShort("Width").orElse((short)0)==3&&result.blocks()==27,"Sponge v3 dimensions and format version read back");
        var palette=blocks.getCompound("Palette").orElseThrow();var grid=blocks.getByteArray("Data").orElseThrow();
        check(grid.length==27&&grid[0]==palette.getInt("minecraft:stone").orElseThrow()&&grid[9]==0&&grid[18]==palette.getInt("minecraft:gold_block").orElseThrow(),"Schematic X/Z/Y indices preserve bottom blocks, received air and blocks above them");
        var blockEntities=blocks.getList("BlockEntities").orElseThrow();check(blockEntities.size()==2&&blockEntities.getCompound(0).orElseThrow().getIntArray("Pos").orElseThrow()[1]==0,"Schematic block entities have relative positions and received inventories");
        check(nbt.getList("Entities").orElseThrow().size()==1&&WorldBinder.GSON.toJson(source).equals(before),"Schematic contains only in-bounds entities and never mutates the original capture");
        byte[] saved=Files.readAllBytes(schematic);source.chunkSnapshots.clear();rejected=false;
        try{net.worldbinder.export.SpongeSchematicExporter.write(source,bounds,schematic);}catch(java.io.IOException expected){rejected=true;}
        check(rejected&&Arrays.equals(saved,Files.readAllBytes(schematic)),"Unknown selection cannot silently become air or replace a complete schematic");
        Path missing=root.resolve("unknown-area.schem");rejected=false;try{net.worldbinder.export.SpongeSchematicExporter.write(source,bounds,missing);}catch(java.io.IOException expected){rejected=true;}
        check(rejected&&!Files.exists(missing),"A new schematic with unreceived positions is rejected before creating an output file");
    }

    private static void mapsAndLegacy() throws Exception {
        CapturedMap map = new CapturedMap(); map.patch(0,0,2,1,new byte[]{0,12});
        map = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(map),CapturedMap.class); map.patch(2,0,1,1,new byte[]{9});
        check(map.receivedPixels()==3 && map.colors[0]==0 && map.colors[1]==12,"Map patch coverage distinguishes received zero from unknown");
        Path legacy = root.resolve("legacy.worldbinder.json");
        Files.writeString(legacy,"{\"formatVersion\":2,\"name\":\"legacy\",\"dimension\":\"minecraft:overworld\",\"mapIds\":[7],\"blocks\":[{\"x\":0,\"y\":64,\"z\":0,\"state\":\"minecraft:stone\"}]}");
        WorldScene scene = library.read(legacy);
        check(scene.projectId.equals(library.read(legacy).projectId),"Legacy archive without createdAt gets a stable identity on repeated reads");
        String oldIdentity=UUID.nameUUIDFromBytes((legacy.toAbsolutePath().normalize()+":"+com.google.gson.JsonParser.parseString(Files.readString(legacy))).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        check(scene.projectId.equals(oldIdentity),"Streaming modern archives preserves the pre-existing legacy recovery identity algorithm");
        check(scene.formatVersion==4 && scene.worldIdentity==null && scene.maps.isEmpty()&&!scene.captureSettings.multiDimension,"Old archive loads with unknown new data and its original dimension boundary");
        var folder = library.saveWorldFolder(scene,root.resolve("legacy-export")); var report=ExportValidator.validate(scene,folder);
        check(report.filesValid && report.missingMapImages==1 && !report.completeness.equals("COMPLETE_FOR_RECORDED_SCOPE"),"Valid files remain distinct from complete recording");
    }

    private static void chunkReplacement() throws Exception {
        WorldScene before=fixture();
        ChunkSnapshot update = snapshot(64,64); update.verifiedEmpty=true;
        var removed=ProjectData.mergeChunk(before,before.blocks,List.of(),update);
        check(removed.isEmpty(),"Removed blocks are replaced by air inside updated range");
        before.blocks.add(new BlockRecord(9,10,9,"minecraft:stone",false,null));
        removed=ProjectData.mergeChunk(before,before.blocks,List.of(),update);
        check(removed.size()==1 && removed.getFirst().y==10,"Unknown outside updated Y range retained");
        var retained=ProjectData.mergeChunk(before,before.blocks,List.of(new BlockRecord(2,64,2,"minecraft:chest[facing=south,type=single,waterlogged=false]",true,null)),update);
        check(retained.stream().anyMatch(b->b.x==2&&b.blockEntityNbt!=null&&b.blockEntityNbt.contains("Items")),"A property change cannot erase unavailable block entity data of the same block type");
        var partial=CaptureSceneSnapshotFactory.copyForStorage(before,true,null,Map.of(0L,List.of(new BlockRecord(1,64,1,"minecraft:dirt",false,null))),Map.of(),Map.of(),k->false);
        check(partial.blockCount()==before.blockCount(),"Interrupted staged scan cannot replace committed blocks");
        before.chunkSnapshots.put("0,0",update);
        var complete=CaptureSceneSnapshotFactory.copyForStorage(before,true,null,Map.of(),Map.of(0L,List.of()),Map.of(),k->true);
        check(complete.blockCount()==1 && complete.maps.get(7).receivedPixels()==16384 && complete.player.enderChest.slotCount==27,"Committed empty update keeps auxiliary data");
        Path saved=library.saveWorldFolder(complete,root.resolve("empty-update"));
        check(ExportValidator.validate(complete,saved).filesValid,"Updated removal exports and reads back");
        var empty = fixture(); empty.blocks.clear(); empty.chunkSnapshots.get("0,0").verifiedEmpty=true;
        saved=library.saveWorldFolder(empty,root.resolve("entirely-empty"));
        check(ExportValidator.validate(empty,saved).chunks==1,"Entire verified empty chunk is written, not dropped");
    }

    private static void cacheFailure() throws Exception {
        var cache=new BlockRecordChunkCache(root.resolve("cache")); cache.writeChunk(0L,List.of()); cache.flushWrites();
        check(cache.chunkKeys().contains(0L) && cache.readChunk(0L).isEmpty(),"Durable cache represents verified empty chunk");
        Files.write(root.resolve("cache/c.0.0.wbcache"),new byte[]{1,2,3});
        boolean failed=false;try{cache.readChunk(0L);}catch(java.io.IOException expected){failed=true;}
        check(failed,"Damaged committed cache cannot silently produce a successful save");
        cache.stopWriter();
        var barrier=new BlockRecordChunkCache(root.resolve("cache-snapshot-barrier"));
        barrier.writeChunk(0L,List.of(new BlockRecord(0,0,0,"minecraft:stone",false,null)));
        var frozen=barrier.snapshotAsync();
        barrier.writeChunk(0L,List.of(new BlockRecord(0,0,0,"minecraft:dirt",false,null)));
        check(frozen.join().get(0L).getFirst().state.equals("minecraft:stone")&&barrier.readChunk(0L).getFirst().state.equals("minecraft:dirt"),
                "Asynchronous recovery keeps its exact committed generation while later captures continue");barrier.stopWriter();
    }

    private static void strictReplacements() {
        WorldScene scene=fixture();String source="minecraft:copper_bulb[lit=true]";
        scene.exportSettings.replacements.put(source,"minecraft:dirt");
        for(String input:List.of("minecraft:stnoe","unknown:nope","minecraft:stone[wrong=true]","minecraft:chest[facing=sideways]",
                "minecraft:chest[facing=north,facing=south]","minecraft:chest[facing=north,]","minecraft:chest[facing]","minecraft:chest[facing=north","minecraft:stone[ ]","")) {
            boolean rejected=false;try{ExportCompatibility.setReplacement(scene,source,input);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected&&scene.exportSettings.replacements.get(source).equals("minecraft:dirt"),"Invalid replacement rejected without mutation: "+input);
        }
        ExportCompatibility.setReplacement(scene,source,"minecraft:air");
        check(scene.exportSettings.replacements.get(source).equals("minecraft:air"),"Explicit valid air replacement remains supported");
        ExportCompatibility.setReplacement(scene,source,"minecraft:chest[facing=west]");
        check(scene.exportSettings.replacements.get(source).contains("facing=west"),"Valid block properties accepted");
    }

    private static void missingCaches() throws Exception {
        WorldScene scene=fixture();Path previous=library.saveWorldFolder(scene,root.resolve("cache-previous"));
        Path archive=previous.resolve("worldbinder/worldbinder_archive.json");byte[] saved=Files.readAllBytes(archive);
        for(String damage:List.of("missing","zero","truncated","header")) {
            Path folder=root.resolve("cache-"+damage);var cache=new BlockRecordChunkCache(folder);
            cache.writeChunk(0L,List.of(scene.blocks.getFirst()));cache.flushWrites();cache.stopWriter();Path payload=folder.resolve("c.0.0.wbcache");
            if(damage.equals("missing"))Files.delete(payload);
            else if(damage.equals("zero"))Files.write(payload,new byte[0]);
            else if(damage.equals("header"))Files.write(payload,new byte[12]);
            else {byte[] bytes=Files.readAllBytes(payload);Files.write(payload,Arrays.copyOf(bytes,bytes.length-1));}
            cache=new BlockRecordChunkCache(folder);boolean failed=false;
            try{var copy=CaptureSceneSnapshotFactory.copyForStorage(scene,true,cache,Map.of(),Map.of(),Map.of(),key->true);library.saveWorldFolder(copy,previous);}
            catch(IllegalStateException expected){failed=true;}
            check(failed&&Arrays.equals(saved,Files.readAllBytes(archive)),"Expected "+damage+" cache aborts save after restart and preserves previous archive");
            cache.stopWriter();
        }
    }

    private static void markerAngles() {
        check(net.worldbinder.render.MarkerAngles.interpolate(359,1,.5f)==0,"Marker crosses 360 by two degrees, not a full turn");
        check(net.worldbinder.render.MarkerAngles.interpolate(1,359,.5f)==0,"Reverse marker wrap stays on the short arc");
        check(net.worldbinder.render.MarkerAngles.interpolate(170,-170,.5f)==-180,"Signed-angle crossing remains stable");
        check(net.worldbinder.render.MarkerAngles.interpolate(359,1,1000)==1,"Unbounded partial tick cannot spin the marker");
        boolean correct=true;
        for(double camera:new double[]{0,45,90,180,359})for(double pitch:new double[]{25,50,90})for(float yaw:new float[]{0,90,180,270,359,1}) {
            var view=new net.worldbinder.render.MapRenderer3D.Camera(0,0,0,camera,pitch,1,1);
            double[] from=net.worldbinder.render.MapRenderer3D.project(view,100,100,0,0,0);
            double[] to=net.worldbinder.render.MapRenderer3D.project(view,100,100,-Math.sin(Math.toRadians(yaw)),0,Math.cos(Math.toRadians(yaw)));
            double heading=Math.toRadians(net.worldbinder.render.MarkerAngles.projected(yaw,camera,pitch));
            double dx=to[0]-from[0],dy=to[1]-from[1];
            correct&=(-Math.sin(heading)*dx+Math.cos(heading)*dy)/Math.hypot(dx,dy)>.99999;
        }
        check(correct,"All marker headings match independently projected world look vectors over rotated/tilted cameras");
        var snapshot=snapshot(0,255);snapshot.replacementRanges.add(new int[]{0,255});var copy=snapshot.copy();
        snapshot.colors[0]=123;snapshot.sections.get(4).blockLight[0]=7;snapshot.replacementRanges.getFirst()[0]=12;
        check(copy.colors[0]!=123&&copy.sections.get(4).blockLight[0]!=7&&copy.replacementRanges.getFirst()[0]==0,
                "Worker snapshot keeps independent preview, light and replacement coverage during subsequent updates");
    }

    private static void multiRangeUpdate() throws Exception {
        WorldScene scene=fixture();scene.blocks.add(new BlockRecord(1,10,1,"minecraft:dirt",false,null));scene.blocks.add(new BlockRecord(1,100,1,"minecraft:stone",false,null));
        scene.chunkSnapshots.get("0,0").sample(1,1,100,"minecraft:stone",0xff123456);
        var store=new CaptureBlockStore();store.resume(scene,"multi-range");
        var first=snapshot(64,64);store.commit(0L,scene,first);scene.chunkSnapshots.put("0,0",first);
        check(first.heights[17]==100&&first.colors[17]==0xff123456,"Map surface outside updated Y range retained exactly");
        check(scene.containers.isEmpty(),"Removed containers cannot inject stale inventory into a later replacement");
        var second=snapshot(10,10);store.stage(0L,new BlockRecord(1,10,1,"minecraft:cobblestone",false,null));store.commit(0L,scene,second);scene.chunkSnapshots.put("0,0",second);
        WorldScene copy=CaptureSceneSnapshotFactory.copyForStorage(scene,true,store.cache(),Map.of(),store.hotChunks(),Map.of(),k->true);
        check(copy.blocks.size()==2&&copy.blocks.stream().noneMatch(b->b.y==64),"Consecutive updates with different Y ranges never resurrect removed blocks");
        check(copy.blocks.stream().anyMatch(b->b.y==10&&b.state.equals("minecraft:cobblestone")),"Second range update retained");
        check(scene.blockCount()+store.recordCount()-store.replacedBaselineCount()==2,"Live block progress counts updated chunks without duplication");
    }

    private static void arrayCompatibility() {
        byte[] bytes=new byte[2048];Arrays.fill(bytes,(byte)15);
        String compact=WorldBinder.GSON.toJson(bytes);
        check(compact.length()<100&&Arrays.equals(bytes,WorldBinder.GSON.fromJson(compact,byte[].class)),"Lossless compact light arrays");
        check(Arrays.equals(new byte[]{0,-1,127},WorldBinder.GSON.fromJson("[0,-1,127]",byte[].class)),"Earlier numeric byte arrays still readable");
        check(Arrays.equals(new boolean[]{false,true},WorldBinder.GSON.fromJson("[false,true]",boolean[].class)),"Earlier boolean coverage arrays still readable");
        check(WorldIdentity.dimensionMatches("ResourceKey[minecraft:dimension / minecraft:overworld]","minecraft:overworld")
                &&!WorldIdentity.dimensionMatches("minecraft:the_nether","minecraft:overworld"),"Legacy dimension syntax recognized; different dimension rejected");
    }

    private static void compatibilityPreview() {
        WorldScene scene=fixture();scene.targetMinecraftVersion="1.16.5";
        scene.blocks.add(new BlockRecord(5,65,5,"minecraft:cherry_planks",false,null));
        check(ExportCompatibility.analyze(scene).stream().anyMatch(i->i.source().equals("minecraft:cherry_planks")&&i.count()==1),"Preview lists affected content and exact block count");
        scene.exportSettings.replacements.put("minecraft:cherry_planks","minecraft:diamond_block");
        check(ExportCompatibility.state(scene,"minecraft:cherry_planks").equals("minecraft:diamond_block"),"Selected replacement drives exporter policy");
        check(ExportCompatibility.automatic("example:cherry_copper",2566).equals("example:cherry_copper"),"Unknown mod content is not guessed from name fragments");
        check(ExportCompatibility.automatic("minecraft:bamboo_sapling",3105).equals("minecraft:bamboo_sapling"),"Existing bamboo saplings are not mistaken for new bamboo blocks");
        var item=ItemDataConverter.item(AuxiliaryDataWriter.parse("{id:\"minecraft:filled_map\",count:1,components:{\"minecraft:map_id\":7}}"),2586);
        check(item.getCompound("tag").orElseThrow().getInt("map").orElse(-1)==7,"Map IDs survive item downgrade");
    }

    private static void generationOptions() throws Exception {
        for(var mode:ExportSettings.Generation.values()){
            WorldScene scene=fixture();scene.targetMinecraftVersion="1.20.4";scene.exportSettings.generation=mode;scene.exportSettings.seed="123456789";
            var folder=library.saveWorldFolder(scene,root.resolve("generation-"+mode));
            var settings=NbtIo.readCompressed(folder.resolve("level.dat"),NbtAccounter.unlimitedHeap()).getCompound("Data").orElseThrow().getCompound("WorldGenSettings").orElseThrow();
            check(settings.getLong("seed").orElse(0L)==123456789L,"Custom seed persists "+mode);
            var generator=settings.getCompound("dimensions").orElseThrow().getCompound("minecraft:overworld").orElseThrow().getCompound("generator").orElseThrow();
            check(generator.getString("type").orElse("").equals(mode==ExportSettings.Generation.NORMAL?"minecraft:noise":"minecraft:flat"),"Generator policy "+mode);
            check(ExportValidator.validate(scene,folder).filesValid,"Recorded chunks preserved for generator "+mode);
        }
    }

    private static void packProvenance() throws Exception {
        Path pack=root.resolve("source-pack.zip");
        try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(pack))){zip.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));zip.write("{\"pack\":{\"pack_format\":75,\"description\":\"QA pack\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
        WorldScene scene=fixture();scene.resourcePackSource=pack.toString();scene.resourcePackExpected=true;
        WorldBinder.config().includeServerResourcePack=true;
        Path folder=library.saveWorldFolder(scene,root.resolve("pack-export"));
        WorldScene reopened=library.read(folder);
        check(reopened.resourcePackSource!=null&&reopened.resourcePackSha256!=null,"Bundled pack has persistent provenance");
        Path again=library.reexport(folder);
        check(Arrays.equals(Files.readAllBytes(pack),Files.readAllBytes(again.resolve("resourcepacks/resources.zip"))),"Offline re-export retains original pack bytes");
        Path json=root.resolve("portable-pack.json");library.save(reopened,json);WorldScene portable=library.read(json);
        check(portable.resourcePackSource!=null&&Files.exists(Path.of(portable.resourcePackSource)),"Standalone archive retains a content-addressed pack sidecar");
        WorldBinder.config().includeServerResourcePack=false;
        Path disabled=library.saveWorldFolder(reopened,root.resolve("pack-disabled"));
        check(!Files.exists(disabled.resolve("resourcepacks/resources.zip"))&&library.read(disabled).resourcePackSource!=null,"Disabling the active pack retains its archived bytes");
        check(library.read(folder.resolve("worldbinder/worldbinder_archive.json")).resourcePackSource!=null,"Direct nested archive path resolves its portable pack");
    }

    private static void exportRoundTrips() throws Exception {
        for(String version:List.of("26.3","26.2","26.1.2","26.1.1","26.1","1.20.4","1.16.5","1.13.2")) {
            WorldScene scene=fixture(); scene.targetMinecraftVersion=version;
            Path folder=library.saveWorldFolder(scene,root.resolve("export-"+version));
            WorldScene reloaded=library.read(folder);
            check(reloaded.containers.size()==2 && reloaded.maps.get(7).receivedPixels()==16384,"Auxiliary archive roundtrip "+version);
            var report=ExportValidator.validate(reloaded,folder);
            check(report.filesValid,"Written regions, blocks, containers, original biomes/light readable "+version+" "+report.errors);
            var again=library.reexport(folder);
            check(ExportValidator.validate(library.read(again),again).filesValid,"Offline re-export "+version);
        }
    }

    private static void playerOptIn() throws Exception {
        WorldScene scene=fixture();scene.exportSettings.includePlayer=true;
        for(String version:List.of("26.3","1.20.4","1.16.5")) {
            scene.targetMinecraftVersion=version; Path folder=library.saveWorldFolder(scene,root.resolve("player-"+version));
            check(ExportValidator.validate(scene,folder).filesValid,"Own player, XP, health, food, pose and ender chest "+version);
            var player=NbtIo.readCompressed(folder.resolve("playerdata/"+scene.player.uuid+".dat"),NbtAccounter.unlimitedHeap());
            check(player.getInt("XpLevel").orElse(0)==12 && player.getList("EnderItems").orElseThrow().isEmpty(),"Known empty ender chest and XP retained "+version);
        }
        scene.exportSettings.includePlayer=false;Path folder=library.saveWorldFolder(scene,root.resolve("prepared-player"));
        check(!Files.exists(folder.resolve("playerdata/"+scene.player.uuid+".dat")),"Own player omitted without explicit opt-in");
    }

    /** 1.7.0: opt-in player list (CSV) and dimension tree (TXT) next to world exports. */
    private static void serverLists() throws Exception {
        WorldScene scene=fixture();
        var alice=new SeenPlayer();alice.uuid="00000000-0000-0000-0000-00000000000a";alice.name="Alice";alice.inTabList=true;alice.gameMode="survival";alice.latencyMs=42;alice.firstSeen=1_700_000_000_000L;alice.lastSeen=1_700_000_100_000L;
        var tricky=new SeenPlayer();tricky.uuid="00000000-0000-0000-0000-00000000000b";tricky.name="=HYPERLINK(1)";tricky.displayName="Bob, \"the builder\"";tricky.seenInWorld=true;tricky.hasPosition=true;tricky.x=1;tricky.y=64;tricky.z=-3;tricky.dimension="minecraft:overworld";tricky.sightings=3;
        scene.seenPlayers.put(alice.uuid,alice);scene.seenPlayers.put(tricky.uuid,tricky);
        String csv=net.worldbinder.export.common.ServerListsWriter.playersCsv(scene);
        check(csv.startsWith("﻿uuid,name,display_name,self,"),"Player CSV starts with a UTF-8 BOM and the header");
        check(csv.indexOf("Alice")<csv.indexOf("HYPERLINK")||csv.indexOf("Alice")>0,"Player CSV rows are written");
        check(csv.contains(",\"'=HYPERLINK(1)\",")||csv.contains(",'=HYPERLINK(1),"),"Formula-like names are neutralized against CSV injection");
        check(csv.contains("\"Bob, \"\"the builder\"\"\""),"Commas and quotes are quoted RFC 4180 style");
        check(csv.contains(",42,")&&csv.contains(",1,64,-3,3"),"Ping and last position are exported");
        scene.exportSettings.includePlayerList=false;Path without=library.saveWorldFolder(scene,root.resolve("lists-off"));
        check(!Files.exists(without.resolve("worldbinder/players.csv")),"Player list is only written after explicit opt-in");
        check(Files.isRegularFile(without.resolve("worldbinder/dimensions.txt")),"Dimension tree is written by default");
        scene.exportSettings.includePlayerList=true;Path folder=library.saveWorldFolder(scene,root.resolve("lists-on"));
        check(Files.readString(folder.resolve("worldbinder/players.csv")).contains("Alice"),"Opted-in player list is written next to the export");
        String tree=Files.readString(folder.resolve("worldbinder/dimensions.txt"));
        check(tree.contains("minecraft:overworld")&&tree.contains("dimensions/minecraft/overworld"),"Dimension tree names the dimension and its 26.x folder");
        check(ExportValidator.validate(scene,folder).filesValid,"Extra list files keep the export valid");
        check(library.read(folder).seenPlayers.size()==2,"Recorded players persist in the archive");
        scene.exportSettings.includeDimensionTree=false;Path noTree=library.saveWorldFolder(scene,root.resolve("lists-no-tree"));
        check(!Files.exists(noTree.resolve("worldbinder/dimensions.txt")),"Dimension tree can be switched off per export");
        check(net.worldbinder.export.common.ServerListsWriter.exportFolder("minecraft:the_nether",3700).equals("DIM-1")
                &&net.worldbinder.export.common.ServerListsWriter.exportFolder("minecraft:the_nether",5023).equals("dimensions/minecraft/the_nether"),"Dimension folders follow the target layout");
    }

    /** 1.7.0: world highlight states for unopened, partly known and verified containers. */
    private static void containerStates() {
        WorldScene scene=fixture();
        String key=ContainerRecord.blockKey(1,64,1),pair=ContainerRecord.blockKey(2,64,1);
        check(net.worldbinder.render.ContainerHighlight.state(scene,"minecraft:stone",key,null)==null,"Non-containers are never highlighted");
        check(net.worldbinder.render.ContainerHighlight.state(scene,"minecraft:chest",key,null)==net.worldbinder.render.ContainerHighlight.State.UNOPENED,"A chest without received contents is unopened");
        var record=new ContainerRecord();record.type="minecraft:chest";record.slotCount=27;record.knownSlots=new boolean[27];record.knownSlots[0]=true;scene.containers.put(key,record);
        check(net.worldbinder.render.ContainerHighlight.state(scene,"minecraft:chest",key,null)==net.worldbinder.render.ContainerHighlight.State.PARTIAL,"Some received slots mark a container as partly known");
        java.util.Arrays.fill(record.knownSlots,true);
        check(net.worldbinder.render.ContainerHighlight.state(scene,"minecraft:chest",key,null)==net.worldbinder.render.ContainerHighlight.State.VERIFIED,"Every received slot verifies a container");
        check(net.worldbinder.render.ContainerHighlight.state(scene,"minecraft:chest",key,pair)==net.worldbinder.render.ContainerHighlight.State.PARTIAL,"A double chest needs both halves for verification");
    }

    /** 1.7.0: version-aware game-rule catalog and editable settings. */
    private static void gameRuleCatalog() {
        var daylight=net.worldbinder.config.GameRuleCatalog.rule("doDaylightCycle");
        check(net.worldbinder.config.GameRuleCatalog.defaultValue("26.3",daylight).equals("true"),"Exact 26.3 default from the bundled jar facts");
        var raids=net.worldbinder.config.GameRuleCatalog.rule("disableRaids");
        check(net.worldbinder.config.GameRuleCatalog.exportKey("26.3",raids).equals("minecraft:raids")&&net.worldbinder.config.GameRuleCatalog.exportValue("26.3",raids,"true").equals("false"),"Inverted rules convert for 26.x");
        check(net.worldbinder.config.GameRuleSettings.supports("26.3","pvp")&&!net.worldbinder.config.GameRuleSettings.supports("1.21.8","pvp"),"26.x-only rules stay hidden for older targets");
        check(net.worldbinder.config.GameRuleSettings.supports("1.20.5","spawnChunkRadius")&&!net.worldbinder.config.GameRuleSettings.supports("26.1","spawnChunkRadius"),"Rules removed in 26.x stay available for their own versions only");
        check(net.worldbinder.config.GameRuleSettings.rules("26.3").length==59,"Every 26.3 game rule is editable");
        var settings=new net.worldbinder.config.GameRuleSettings();settings.load("maxEntityCramming=5000;pvp=false;custom_rule=1");
        check(settings.intValue("maxEntityCramming")==100&&!settings.value("pvp"),"Integer rules are clamped and booleans parsed");
        String saved=settings.serializeAndMarkClean();
        check(saved.contains("custom_rule=1")&&saved.contains("pvp=false"),"Unknown rules survive and new rules are stored");
        settings.reset("26.3","pvp");check(settings.isDefault("26.3","pvp"),"Resetting restores the target default");
    }

    private static void failedSave() throws Exception {
        WorldScene scene=fixture();Path folder=library.saveWorldFolder(scene,root.resolve("transaction"));
        byte[] original=Files.readAllBytes(folder.resolve("worldbinder/worldbinder_archive.json"));
        scene.blocks.getFirst().state="minecraft:definitely_not_a_real_block";
        boolean failed=false;try{library.saveWorldFolder(scene,folder);}catch(java.io.IOException expected){failed=true;}
        check(failed && Arrays.equals(original,Files.readAllBytes(folder.resolve("worldbinder/worldbinder_archive.json"))),"Failed export preserves previous project revision byte-for-byte");
        Path archive=root.resolve("atomic.json");scene=fixture();library.save(scene,archive);original=Files.readAllBytes(archive);scene.formatVersion=999;
        failed=false;try{library.save(scene,archive);}catch(java.io.IOException expected){failed=true;}
        check(failed && Arrays.equals(original,Files.readAllBytes(archive)),"Failed archive verification preserves previous archive");
    }

    private static void storageProgress() throws Exception {
        var progress=new net.worldbinder.storage.StorageProgress();var scene=fixture();Path target=root.resolve("progress");
        progress.start(target,scene,true);long job=progress.jobId();progress.update(net.worldbinder.storage.StorageStage.METADATA,"QA",.4);
        progress.update(net.worldbinder.storage.StorageStage.VANILLA_WORLD,"QA",.2);
        check(progress.progress()==.4&&progress.context().blocks()==scene.blockCount()&&progress.context().disconnected(),"Progress is monotonic and retains explicit disconnect/project context");
        progress.reviewReady();check(!progress.isRunning()&&!progress.isTerminal(),"A prepared review is not a running or completed export");
        progress.dismissReview();check(!progress.hasActivity(),"Dismissing review cannot trigger an automatic storage popup");
        progress.start(target,scene,false);check(progress.jobId()>job&&progress.progress()==0,"New jobs have independent IDs even when a tick misses the snapshot stage");
        progress.update(net.worldbinder.storage.StorageStage.VANILLA_WORLD,"QA",.6);progress.fail("QA failure");long elapsed=progress.elapsedMillis();Thread.sleep(25);
        check(progress.elapsedMillis()==elapsed&&progress.progress()==.6,"Failure freezes elapsed time without pretending 100 percent succeeded");
        progress.start(target);progress.finish(target);elapsed=progress.elapsedMillis();Thread.sleep(25);check(progress.elapsedMillis()==elapsed,"Completed duration remains stable while viewing the result");
        var compact=library.saveWorldFolder(scene,root.resolve("compact-archive"));var archived=compact.resolve("worldbinder/worldbinder_archive.json");
        check(!Files.readString(archived).contains("\n  \"blocks\"")&&library.read(compact).blocks.size()==scene.blocks.size(),"Compact archive keeps the existing schema and survives full deserialize");
        check(ExportValidator.validate(scene,compact).filesValid,"Streaming read-back validates every written layout and auxiliary payload");
        for(String type:List.of("region","entities","poi"))try(var files=Files.list(compact.resolve(type))){
            for(Path file:files.filter(Files::isRegularFile).toList())check(Files.mismatch(file,compact.resolve("dimensions/minecraft/overworld").resolve(type).resolve(file.getFileName()))==-1,"Mirrored "+type+" layouts reuse complete byte-identical region files, not regenerated payloads");
        }
    }

    private static void corruptExport() throws Exception {
        WorldScene scene=fixture(); Path folder=library.saveWorldFolder(scene,root.resolve("corruption"));
        Path region=folder.resolve("region/r.0.0.mca"); byte[] bytes=Files.readAllBytes(region);bytes[0]=0x7f;Files.write(region,bytes);
        var report=ExportValidator.validate(scene,folder);
        check(!report.filesValid && report.missingChunks==1 && report.errors.stream().anyMatch(e->e.render().contains("0,0")),"Read-back finds corrupt offsets and identifies affected chunk");
    }

    private static void changedLighting() throws Exception {
        for(String target:List.of("1.13.2","1.20.4","26.3")) {
            boolean custom=target.equals("26.3");WorldScene scene=fixture();scene.targetMinecraftVersion=target;
            String source=custom?"minecraft:glowstone":"minecraft:copper_bulb[lit=true,powered=false]";
            scene.blocks.add(new BlockRecord(15,64,1,source,false,null));
            if(custom)ExportCompatibility.setReplacement(scene,source,"minecraft:stone");
            for(int x:List.of(1,3)){var extra=snapshot(0,255);extra.chunkX=x;scene.chunkSnapshots.put(x+",0",extra);}
            Path folder=library.saveWorldFolder(scene,root.resolve("relight-"+custom));
            var report=ExportValidator.validate(scene,folder);var regions=net.worldbinder.validation.RegionReadback.read(folder.resolve("region"),new ArrayList<>());
            check(report.filesValid&&report.relightChunks==2,"Converted light requires read-back-verified recalculation for changed and neighbouring chunk: "+custom);
            for(int x:List.of(0,1)){
                var root=regions.get(ProjectData.chunkKey(x,0));var chunk=root.getCompound("Level").orElse(root);
                check(!chunk.getBoolean("isLightOn").orElse(true)&&chunk.getList(target.equals("1.13.2")?"Sections":"sections").orElseThrow().stream()
                        .allMatch(t->t instanceof CompoundTag section&&(target.equals("1.13.2")
                                ?Arrays.equals(new byte[2048],section.getByteArray("BlockLight").orElse(null))&&Arrays.equals(new byte[2048],section.getByteArray("SkyLight").orElse(null))
                                :!section.contains("BlockLight")&&!section.contains("SkyLight"))),"No stale light in affected chunk "+x);
            }
            var distant=regions.get(ProjectData.chunkKey(3,0));check(distant.getCompound("Level").orElse(distant).getBoolean("isLightOn").orElse(false),"Unchanged distant chunk retains trusted light");
            check(scene.chunkSnapshots.get("0,0").sections.get(4).hasBlockLight(),"Original archive light remains available for another target");
        }
    }

    private static void changedPlayerFiles() throws Exception {
        for(String version:List.of("26.3","1.20.4")) {
            WorldScene scene=fixture();scene.targetMinecraftVersion=version;scene.exportSettings.includePlayer=true;
            scene.player.advancements.put("minecraft:story/root","{\"criteria\":{\"crafting_table\":\"2026-10-23 12:00:00 +0000\"},\"done\":true}");
            Path folder=library.saveWorldFolder(scene,root.resolve("player-integrity-"+version));
            Path playerFile=folder.resolve("playerdata/"+scene.player.uuid+".dat");byte[] original=Files.readAllBytes(playerFile);
            CompoundTag nbt=NbtIo.readCompressed(playerFile,NbtAccounter.unlimitedHeap());
            CompoundTag broken=nbt.copy();broken.put("Inventory",new ListTag());NbtIo.writeCompressed(broken,playerFile);
            check(!ExportValidator.validate(scene,folder).filesValid,"Readable but missing inventory detected: "+version);Files.write(playerFile,original);
            broken=nbt.copy();if(version.equals("26.3"))broken.remove("equipment");else broken.getList("Inventory").orElseThrow().removeIf(t->t instanceof CompoundTag item&&item.getByte("Slot").orElse((byte)0)==102);
            NbtIo.writeCompressed(broken,playerFile);check(!ExportValidator.validate(scene,folder).filesValid,"Missing equipment detected: "+version);Files.write(playerFile,original);
            broken=nbt.copy();broken.getList("Inventory").orElseThrow().getCompound(0).orElseThrow().putString("id","minecraft:dirt");NbtIo.writeCompressed(broken,playerFile);
            check(!ExportValidator.validate(scene,folder).filesValid,"Wrong item in a readable inventory detected: "+version);Files.write(playerFile,original);
            String prefix=version.equals("26.3")?"players/":"";
            Path stats=folder.resolve(prefix+"stats/"+scene.player.uuid+".json");byte[] statsBytes=Files.readAllBytes(stats);
            var json=com.google.gson.JsonParser.parseString(Files.readString(stats)).getAsJsonObject();json.getAsJsonObject("stats").add("minecraft:custom",new com.google.gson.JsonObject());Files.writeString(stats,WorldBinder.GSON.toJson(json));
            check(ExportValidator.validate(scene,folder).errors.stream().anyMatch(e->e.render().contains("statistics")),"Missing statistic values detected: "+version);Files.write(stats,statsBytes);
            Path advances=folder.resolve(prefix+"advancements/"+scene.player.uuid+".json");byte[] advancementBytes=Files.readAllBytes(advances);
            json=com.google.gson.JsonParser.parseString(Files.readString(advances)).getAsJsonObject();json.remove("minecraft:story/root");Files.writeString(advances,WorldBinder.GSON.toJson(json));
            check(ExportValidator.validate(scene,folder).errors.stream().anyMatch(e->e.render().contains("advancements")),"Missing advancement detected: "+version);Files.write(advances,advancementBytes);
            json=com.google.gson.JsonParser.parseString(Files.readString(advances)).getAsJsonObject();json.getAsJsonObject("minecraft:story/root").remove("criteria");Files.writeString(advances,WorldBinder.GSON.toJson(json));
            check(!ExportValidator.validate(scene,folder).filesValid,"Missing advancement criteria detected: "+version);Files.write(advances,advancementBytes);
            Path primary=version.equals("26.3")?folder.resolve("players/data/"+scene.player.uuid+".dat"):folder.resolve("level.dat");byte[] primaryBytes=Files.readAllBytes(primary);
            broken=NbtIo.readCompressed(primary,NbtAccounter.unlimitedHeap());CompoundTag actual=version.equals("26.3")?broken:broken.getCompound("Data").orElseThrow().getCompound("Player").orElseThrow();actual.put("Inventory",new ListTag());NbtIo.writeCompressed(broken,primary);
            check(!ExportValidator.validate(scene,folder).filesValid,"Primary game player copy checked in addition to playerdata fallback: "+version);Files.write(primary,primaryBytes);
            check(ExportValidator.validate(scene,folder).filesValid,"Restored original player data fully validates: "+version);
        }
    }

    public static WorldScene fixture() {
        WorldScene scene=new WorldScene();scene.name="WorldBinder QA";scene.dimension="minecraft:overworld";scene.worldIdentity="qa-world";
        scene.sizeX=16;scene.sizeY=256;scene.sizeZ=16;scene.targetMinecraftVersion="26.3";scene.minecraftVersion="26.3";
        scene.blocks.add(new BlockRecord(1,64,1,"minecraft:stone",false,null));
        scene.blocks.add(new BlockRecord(2,64,2,"minecraft:chest[facing=north,type=single,waterlogged=false]",true,"{id:\"minecraft:chest\",Items:[]}"));
        scene.blocks.add(new BlockRecord(3,64,3,"minecraft:barrel[facing=up,open=false]",true,"{id:\"minecraft:barrel\",Items:[]}"));
        scene.chunkSnapshots.put("0,0",snapshot(0,255));
        ContainerRecord chest=new ContainerRecord();chest.type="minecraft:chest";chest.slotCount=27;chest.observedAt=1;
        chest.items.add("{Slot:5b,id:\"minecraft:diamond\",count:3,components:{\"minecraft:custom_name\":\"Kept item\",\"minecraft:map_id\":7}}");
        ContainerRecord barrel=new ContainerRecord();barrel.type="minecraft:barrel";barrel.slotCount=27;barrel.observedAt=2;
        scene.containers.put(ContainerRecord.blockKey(2,64,2),chest);scene.containers.put(ContainerRecord.blockKey(3,64,3),barrel);
        CapturedMap map=new CapturedMap();map.id=7;byte[] pixels=new byte[16384];for(int i=0;i<pixels.length;i++)pixels[i]=(byte)(i%128);
        map.patch(0,0,128,128,pixels);map.nbt="{dimension:\"minecraft:overworld\",xCenter:128,zCenter:256,scale:1b,trackingPosition:1b,unlimitedTracking:0b,locked:0b,colors:[B;]}";
        scene.maps.put(7,map);scene.mapIds.add(7);
        CapturedPlayer player=new CapturedPlayer();player.uuid="12345678-1234-1234-1234-123456789abc";
        player.nbt="{Inventory:[{Slot:0b,id:\"minecraft:diamond\",count:8}],equipment:{chest:{id:\"minecraft:iron_chestplate\",count:1}},XpP:0.5f,XpLevel:12,XpTotal:250,Health:17.0f,foodLevel:18,Pos:[1.5d,65.0d,1.5d],Rotation:[35.0f,12.0f],playerGameType:0}";
        player.enderChest=new ContainerRecord();player.enderChest.type="minecraft:ender_chest";player.enderChest.slotCount=27;
        player.statsReceived=true;player.stats.put("minecraft:custom",Map.of("minecraft:jump",12));player.advancementsReceived=true;
        scene.player=player;return scene;
    }
    private static ChunkSnapshot snapshot(int min,int max) {
        var snapshot=new ChunkSnapshot(0,0);snapshot.captureMinY=min;snapshot.captureMaxY=max;snapshot.fullChunkColumns=true;snapshot.hasBiomeData=true;snapshot.lightEstimated=false;snapshot.markDone();
        for(int y=min>>4;y<=max>>4;y++) {
            var section=new ChunkSectionData();section.biomes=new String[64];section.blockLight=new byte[2048];section.skyLight=new byte[2048];
            for(int i=0;i<64;i++)section.biomes[i]=i%2==0?"minecraft:forest":"minecraft:taiga";
            for(int i=0;i<2048;i++){section.blockLight[i]=(byte)(i%256);section.skyLight[i]=(byte)(255-i%256);}snapshot.sections.put(y,section);
        }return snapshot;
    }
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
}

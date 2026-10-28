package net.worldbinder.qa;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.world.block.BlockState;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local QA helper, never part of WorldBinder. Imports via actual FAWE and compares the actual Paper world. */
public final class WorldBinderImportProbe extends JavaPlugin {
    private final Map<BlockVector3,BlockState> expected=new LinkedHashMap<>();
    private final Map<BlockVector3,String> inventories=new LinkedHashMap<>();
    private final List<String[]> entities=new ArrayList<>();
    private World world;private int checks,width,height,length;
    private static final BlockVector3 OFFSET=BlockVector3.at(48,100,48);
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::importActual,80);}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void importActual(){
        try{
            world=Bukkit.getWorlds().getFirst();var parser=new ParserContext();parser.setWorld(BukkitAdapter.adapt(world));parser.setActor(BukkitAdapter.adapt(Bukkit.getConsoleSender()));
            for(String line:Files.readAllLines(getDataFolder().toPath().resolve("expected.tsv"))){var fields=line.split("\t");switch(fields[0]){
                case "SIZE"->{width=Integer.parseInt(fields[1]);height=Integer.parseInt(fields[2]);length=Integer.parseInt(fields[3]);}
                case "B"->expected.put(BlockVector3.at(Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Integer.parseInt(fields[3])),WorldEdit.getInstance().getBlockFactory().parseFromInput(fields[4],parser).toImmutableState());
                case "C"->inventories.put(BlockVector3.at(Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Integer.parseInt(fields[3])),new String(Base64.getDecoder().decode(fields[4]),StandardCharsets.UTF_8));
                case "E"->entities.add(fields);
            }}
            var file=getDataFolder().toPath().resolve("input.schem").toFile();var format=ClipboardFormats.findByFile(file);check(format!=null,"FAWE recognizes the actual Sponge v3 file");
            Clipboard clipboard;try(var input=Files.newInputStream(file.toPath());var reader=format.getReader(input)){clipboard=reader.read();}
            check(clipboard.getDimensions().equals(BlockVector3.at(width,height,length)),"FAWE dimensions match source selection");
            for(var entry:expected.entrySet())check(clipboard.getBlock(entry.getKey()).equals(entry.getValue()),"FAWE imported source state at "+entry.getKey());
            check(clipboard.getEntities().size()==entities.size(),"FAWE imported all selected entities");
            try(var session=WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))){
                var operation=new ClipboardHolder(clipboard).createPaste(session).to(OFFSET).copyEntities(true).copyBiomes(true).ignoreAirBlocks(false).build();Operations.complete(operation);
            }
            Bukkit.getScheduler().runTaskLater(this,this::verifyActual,40);
        }catch(Throwable failure){finish(failure);}
    }
    private void verifyActual(){
        try{
            for(var entry:expected.entrySet()){
                var p=entry.getKey().add(OFFSET);check(BukkitAdapter.adapt(world.getBlockAt(p.x(),p.y(),p.z()).getBlockData()).equals(entry.getValue()),"FAWE native Paper paste state at "+p);
            }
            Object level=world.getClass().getMethod("getHandle").invoke(world);var posType=Class.forName("net.minecraft.core.BlockPos");var lookupType=Class.forName("net.minecraft.core.HolderLookup$Provider");Object lookup=level.getClass().getMethod("registryAccess").invoke(level);
            var parser=Class.forName("net.minecraft.nbt.TagParser").getMethod("parseCompoundFully",String.class);var tagType=Class.forName("net.minecraft.nbt.CompoundTag");var get=tagType.getMethod("get",String.class);
            for(var entry:inventories.entrySet()){
                var p=entry.getKey().add(OFFSET);Object pos=posType.getConstructor(int.class,int.class,int.class).newInstance(p.x(),p.y(),p.z());Object be=level.getClass().getMethod("getBlockEntity",posType).invoke(level,pos);check(be!=null,"FAWE pasted block entity at "+p);
                Object actual=be.getClass().getMethod("saveWithFullMetadata",lookupType).invoke(be,lookup),wanted=parser.invoke(null,entry.getValue());
                check(Objects.equals(get.invoke(actual,"Items"),get.invoke(wanted,"Items")),"FAWE Paper inventory slots/counts/full item components at "+p);
            }
            var actual=world.getEntities().stream().filter(entity->{var p=entity.getLocation();return p.getX()>=48&&p.getX()<48+width&&p.getY()>=100&&p.getY()<100+height&&p.getZ()>=48&&p.getZ()<48+length;}).toList();
            check(actual.size()==entities.size(),"FAWE keeps selected entities alive after native world ticks");
            for(var entry:entities){double x=48+Double.parseDouble(entry[2]),y=100+Double.parseDouble(entry[3]),z=48+Double.parseDouble(entry[4]);check(actual.stream().anyMatch(entity->{var p=entity.getLocation();return entity.getType().getKey().toString().equals(entry[1])&&Math.abs(p.getX()-x)<.01&&Math.abs(p.getY()-y)<.01&&Math.abs(p.getZ()-z)<.01;}),"FAWE entity type/position/anchor "+entry[1]);}
            finish(null);
        }catch(Throwable failure){finish(failure);}
    }
    private void finish(Throwable failure){
        try{String result=failure==null?"PASS FAWE 2.16.1 / Paper 26.3 build 222 actual Sponge v3 import and native paste; "+checks+" checks\n":"FAIL after "+checks+" checks: "+failure+"\n";Files.writeString(getDataFolder().toPath().resolve("RESULT.txt"),result);System.out.println(failure==null?"WORLDBINDER_FAWE_IMPORT_PASS "+checks:result);if(failure!=null)failure.printStackTrace();}catch(Exception error){error.printStackTrace();}
        Bukkit.shutdown();
    }
}

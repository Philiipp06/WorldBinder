package net.worldbinder.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Optional integrations are omitted entirely when their upstream classes are absent. */
public final class WorldBinderMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.endsWith("ReplayModSeekMixin")||mixin.endsWith("FlashbackSeekMixin")){
            try(var input=getClass().getClassLoader().getResourceAsStream(target.replace('.','/')+".class")){
                if(input==null)return false;
                var node=new ClassNode();new org.objectweb.asm.ClassReader(input).accept(node,org.objectweb.asm.ClassReader.SKIP_CODE|org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
                Set<String> methods=new HashSet<>();for(var method:node.methods)methods.add(method.name+method.desc);
                return mixin.endsWith("FlashbackSeekMixin")?methods.contains("goToReplayTick(I)V"):methods.containsAll(Set.of("doJump(IZ)V","restartedReplay()V"));
            }catch(java.io.IOException error){return false;}
        }
        return true;
    }
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}

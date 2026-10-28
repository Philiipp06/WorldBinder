package net.worldbinder.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.replaymod.replay.ReplayHandler",remap=false)
public abstract class ReplayModSeekMixin implements net.worldbinder.replay.ReplayPlayback.Guarded {
    @Inject(method="doJump(IZ)V",at=@At("HEAD"),require=1,remap=false)
    private void worldbinder$beforeSeek(int millis,boolean retainCamera,CallbackInfo ci){net.worldbinder.replay.ReplayPlayback.seekTo(millis);}
    @Inject(method="restartedReplay()V",at=@At("HEAD"),require=1,remap=false)
    private void worldbinder$beforeRestart(CallbackInfo ci){net.worldbinder.replay.ReplayPlayback.seek();}
}

package net.worldbinder.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.moulberry.flashback.playback.ReplayServer",remap=false)
public abstract class FlashbackSeekMixin implements net.worldbinder.replay.ReplayPlayback.Guarded {
    @Inject(method="goToReplayTick(I)V",at=@At("HEAD"),require=1,remap=false)
    private void worldbinder$beforeSeek(int tick,CallbackInfo ci){net.worldbinder.replay.ReplayPlayback.seekTo(tick*50L);}
}

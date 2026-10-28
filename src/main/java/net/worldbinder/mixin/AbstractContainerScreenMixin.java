package net.worldbinder.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @org.spongepowered.asm.mixin.Unique private net.worldbinder.ui.component.ContainerMergeControls worldbinder$mergeControls;
    @Inject(method="init",at=@At("RETURN"))
    private void worldbinder$mergeInit(CallbackInfo ci){worldbinder$mergeControls=new net.worldbinder.ui.component.ContainerMergeControls((Screen)(Object)this);}
    @Inject(method="extractRenderState",at=@At("RETURN"))
    private void worldbinder$mergeRender(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial,CallbackInfo ci){
        if(worldbinder$mergeControls!=null)worldbinder$mergeControls.render(graphics,net.minecraft.client.Minecraft.getInstance().font,mouseX,mouseY);
    }
    @Inject(method="keyPressed",at=@At("HEAD"))
    private void worldbinder$manualKey(net.minecraft.client.input.KeyEvent event,CallbackInfoReturnable<Boolean> cir){
        if(WorldBinderClient.capture()!=null)WorldBinderClient.capture().onManualContainerInput();
    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void worldbinder$manualClick(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> cir){
        if(WorldBinderClient.capture()!=null)WorldBinderClient.capture().onManualContainerInput();
        if(worldbinder$mergeControls!=null&&worldbinder$mergeControls.click(event,doubleClick))cir.setReturnValue(true);
    }
    @Inject(method = "removed", at = @At("HEAD"))
    private void worldbinder$containerScreenRemoved(CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onContainerScreenClosed((Screen) (Object) this);
        }
    }
}

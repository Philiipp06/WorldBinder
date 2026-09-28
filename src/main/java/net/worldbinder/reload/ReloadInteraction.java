package net.worldbinder.reload;

import net.minecraft.client.gui.screens.Overlay;

public final class ReloadInteraction {
    private ReloadInteraction() {
    }

    public static Overlay forInput(Overlay overlay) {
        return overlay instanceof ReloadOverlayState state && state.worldbinder$allowsInteraction()
                ? null : overlay;
    }
}

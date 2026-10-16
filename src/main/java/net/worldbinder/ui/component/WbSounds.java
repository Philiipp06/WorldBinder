package net.worldbinder.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;

/** Central, configurable interface sounds. Every WorldBinder menu and HUD feedback routes through here. */
public final class WbSounds {
    private static long lastTickMillis;
    private static long lastHoverMillis;

    private WbSounds() {
    }

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.32F);
    }

    public static void click(SoundManager manager) {
        play(manager, SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.32F);
    }

    public static void toggle(boolean enabled) {
        play(SoundEvents.UI_BUTTON_CLICK.value(), enabled ? 1.28F : 0.82F, 0.30F);
    }

    public static void tab() {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.65F, 0.22F);
    }

    public static void dropdown(boolean open) {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), open ? 1.9F : 1.35F, 0.20F);
    }

    public static void sliderTick() {
        long now = System.currentTimeMillis();
        if (now - lastTickMillis < 45L) {
            return;
        }
        lastTickMillis = now;
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.10F);
    }

    public static void hover() {
        if (!WorldBinder.config().uiHoverSounds) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHoverMillis < 60L) {
            return;
        }
        lastHoverMillis = now;
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.05F);
    }

    public static void open() {
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.55F, 0.28F);
    }

    public static void success() {
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.62F, 0.30F);
    }

    public static void info() {
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F, 0.22F);
    }

    public static void warning() {
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 0.95F, 0.24F);
    }

    public static void error() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.72F, 0.38F);
    }

    public static void movementToggle(boolean enabled) {
        play(SoundEvents.NOTE_BLOCK_PLING.value(), enabled ? 1.78F : 0.94F, 0.26F);
    }

    public static void mapPing() {
        play(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 1.7F, 0.18F);
    }

    public static void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }

    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        play(client.getSoundManager(), sound, pitch, volume);
    }

    private static void play(SoundManager manager, SoundEvent sound, float pitch, float volume) {
        WorldBinderConfig config = WorldBinder.config();
        if (manager == null || sound == null || !config.uiSounds) {
            return;
        }
        float master = Math.max(0, Math.min(200, config.uiSoundVolumePercent)) / 100.0F;
        if (master <= 0.0F) {
            return;
        }
        manager.play(SimpleSoundInstance.forUI(sound, pitch, Math.min(1.0F, volume * master)));
    }
}

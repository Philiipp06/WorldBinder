package net.worldbinder.platform;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.nio.file.Path;
import java.util.function.*;

public interface ClientPlatform {
    Path gameDirectory();
    Path configDirectory();
    boolean modLoaded(String id);
    KeyMapping registerKey(KeyMapping key);
    void endTick(Consumer<Minecraft> tick);
    void hud(BiConsumer<GuiGraphicsExtractor, DeltaTracker> renderer);
    void joined(Runnable action);
    void disconnected(Runnable action);
    default void clientCommand(String name, Runnable action) { clientCommand(name, action, java.util.Map.of()); }
    default void clientCommand(String name, Runnable action, java.util.Map<String,Runnable> subcommands) {}
}

package net.worldbinder.platform;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

@Mod(value = "worldbinder", dist = Dist.CLIENT)
public final class NeoForgeEntrypoint implements ClientPlatform {
    private final List<KeyMapping> keys = new ArrayList<>();
    private BiConsumer<GuiGraphicsExtractor, DeltaTracker> renderer;

    public NeoForgeEntrypoint(IEventBus modBus) {
        Platform.install(this);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            net.worldbinder.client.WorldBinderClient.initialize();
            keys.forEach(event::register);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelow(VanillaGuiLayers.CHAT,
                Identifier.fromNamespaceAndPath("worldbinder", "hud"), (graphics, delta) -> {
                    if (renderer != null) renderer.accept(graphics, delta);
                }));
    }
    public Path gameDirectory() { return FMLPaths.GAMEDIR.get(); }
    public Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
    public boolean modLoaded(String id) { return ModList.get().isLoaded(id); }
    public KeyMapping registerKey(KeyMapping key) { keys.add(key); return key; }
    public void endTick(Consumer<Minecraft> tick) { NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick.accept(Minecraft.getInstance())); }
    public void hud(BiConsumer<GuiGraphicsExtractor, DeltaTracker> renderer) { this.renderer = renderer; }
    public void joined(Runnable action) { NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> action.run()); }
    public void disconnected(Runnable action) { NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> action.run()); }
    public void clientCommand(String name, Runnable action, Map<String,Runnable> subcommands) {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
            var root=com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal(name)
                    .executes(context -> { action.run(); return 1; });
            subcommands.forEach((literal,command)->root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                    .<net.minecraft.commands.CommandSourceStack>literal(literal).executes(context->{command.run();return 1;})));
            event.getDispatcher().register(root);
        });
    }
}

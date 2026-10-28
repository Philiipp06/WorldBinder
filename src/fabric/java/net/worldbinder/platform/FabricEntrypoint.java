package net.worldbinder.platform;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.nio.file.Path;
import java.util.function.*;

public final class FabricEntrypoint implements ClientModInitializer, ClientPlatform {
    @Override public void onInitializeClient() {
        Platform.install(this);
        net.worldbinder.client.WorldBinderClient.initialize();
    }
    public Path gameDirectory() { return FabricLoader.getInstance().getGameDir(); }
    public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    public boolean modLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public KeyMapping registerKey(KeyMapping key) { return KeyMappingHelper.registerKeyMapping(key); }
    public void endTick(Consumer<Minecraft> tick) { ClientTickEvents.END_CLIENT_TICK.register(tick::accept); }
    public void hud(BiConsumer<GuiGraphicsExtractor, DeltaTracker> renderer) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("worldbinder", "hud"), renderer::accept);
    }
    public void joined(Runnable action) { ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> action.run()); }
    public void disconnected(Runnable action) { ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> action.run()); }
    public void clientCommand(String name, Runnable action, java.util.Map<String,Runnable> subcommands) {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> {
                var root=com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal(name)
                        .executes(context -> { action.run(); return 1; });
                subcommands.forEach((literal,command)->root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal(literal)
                        .executes(context->{command.run();return 1;})));
                dispatcher.register(root);
        });
    }
}

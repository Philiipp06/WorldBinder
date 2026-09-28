package net.worldbinder.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClientCompat {
    private static final Method GUI_SCREEN = method(Gui.class, "screen");
    private static final Method GUI_SET_SCREEN = method(Gui.class, "setScreen", Screen.class);
    private static final Method MC_SET_SCREEN = method(Minecraft.class, "setScreen", Screen.class);
    private static final Field MC_SCREEN = field(Minecraft.class, "screen");
    private static final Field GUI_HUD = field(Gui.class, "hud");
    private static final Method HUD_IS_HIDDEN = GUI_HUD == null ? null : method(GUI_HUD.getType(), "isHidden");
    private static final Method HUD_GET_CHAT = GUI_HUD == null ? null : method(GUI_HUD.getType(), "getChat");
    private static final Field OPTIONS_HIDE_GUI = field(Options.class, "hideGui");
    private static final Method GUI_GET_CHAT = method(Gui.class, "getChat");

    private ClientCompat() {
    }

    public static Screen screen(Minecraft client) {
        try {
            if (GUI_SCREEN != null) return (Screen) GUI_SCREEN.invoke(client.gui);
            if (MC_SCREEN != null) return (Screen) MC_SCREEN.get(client);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read Minecraft screen", exception);
        }
        throw new IllegalStateException("No supported Minecraft screen accessor");
    }

    public static void setScreen(Minecraft client, Screen screen) {
        try {
            if (GUI_SET_SCREEN != null) {
                GUI_SET_SCREEN.invoke(client.gui, screen);
                return;
            }
            if (MC_SET_SCREEN != null) {
                MC_SET_SCREEN.invoke(client, screen);
                return;
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot open Minecraft screen", exception);
        }
        throw new IllegalStateException("No supported Minecraft screen setter");
    }

    public static boolean hudHidden(Minecraft client) {
        try {
            if (GUI_HUD != null && HUD_IS_HIDDEN != null) {
                return (boolean) HUD_IS_HIDDEN.invoke(GUI_HUD.get(client.gui));
            }
            return OPTIONS_HIDE_GUI != null && OPTIONS_HIDE_GUI.getBoolean(client.options);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read HUD visibility", exception);
        }
    }

    public static boolean multiplayer(Minecraft client) {
        return !client.isLocalServer()
                || client.getSingleplayerServer() != null && client.getSingleplayerServer().isPublished();
    }

    public static void clientSystemMessage(Minecraft client, Component message) {
        chat(client).addClientSystemMessage(message);
    }

    public static ChatComponent chat(Minecraft client) {
        try {
            if (GUI_GET_CHAT != null) {
                return (ChatComponent) GUI_GET_CHAT.invoke(client.gui);
            }
            if (GUI_HUD != null && HUD_GET_CHAT != null) {
                return (ChatComponent) HUD_GET_CHAT.invoke(GUI_HUD.get(client.gui));
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot access Minecraft chat", exception);
        }
        throw new IllegalStateException("No supported Minecraft chat accessor");
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }
}

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

    // These integer constants changed in 26.3. Reading the runtime fields avoids javac inlining
    // the build version's values into a jar used on another supported Minecraft release.
    private static final class MouseButtons {
        static final int LEFT=code("MOUSE_BUTTON_LEFT"),MIDDLE=code("MOUSE_BUTTON_MIDDLE"),RIGHT=code("MOUSE_BUTTON_RIGHT");
        private static int code(String name){try{return com.mojang.blaze3d.platform.InputConstants.class.getField(name).getInt(null);}catch(ReflectiveOperationException error){throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.mouse_constant",name),error);}}
    }
    public static int mouseLeft(){return MouseButtons.LEFT;}
    public static int mouseMiddle(){return MouseButtons.MIDDLE;}
    public static int mouseRight(){return MouseButtons.RIGHT;}
    private static final java.util.Map<String,Integer> KEY_CODES=new java.util.concurrent.ConcurrentHashMap<>();
    public static int key(String name){return KEY_CODES.computeIfAbsent(name,value->{try{return com.mojang.blaze3d.platform.InputConstants.class.getField(value).getInt(null);}catch(ReflectiveOperationException error){throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.key_constant",value),error);}});}

    public static Screen screen(Minecraft client) {
        try {
            if (GUI_SCREEN != null) return (Screen) GUI_SCREEN.invoke(client.gui);
            if (MC_SCREEN != null) return (Screen) MC_SCREEN.get(client);
        } catch (ReflectiveOperationException exception) {
            throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.read_screen"), exception);
        }
        throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.screen_accessor"));
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
            throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.open_screen"), exception);
        }
        throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.screen_setter"));
    }

    public static boolean hudHidden(Minecraft client) {
        try {
            if (GUI_HUD != null && HUD_IS_HIDDEN != null) {
                return (boolean) HUD_IS_HIDDEN.invoke(GUI_HUD.get(client.gui));
            }
            return OPTIONS_HIDE_GUI != null && OPTIONS_HIDE_GUI.getBoolean(client.options);
        } catch (ReflectiveOperationException exception) {
            throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.hud_visibility"), exception);
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
            throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.access_chat"), exception);
        }
        throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.chat_accessor"));
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

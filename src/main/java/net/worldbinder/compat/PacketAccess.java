package net.worldbinder.compat;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Minecraft 26.3 converted several chunk/light packet classes to records. Keep the same jar usable on 26.1/26.2. */
public final class PacketAccess {
    private record Key(Class<?> type, String name) {}
    private static final ConcurrentHashMap<Key, Method> METHODS = new ConcurrentHashMap<>();
    private PacketAccess() {}
    @SuppressWarnings("unchecked") public static <T> T get(Object target, String recordName, String oldName) {
        Key key = new Key(target.getClass(), recordName);
        Method method = METHODS.computeIfAbsent(key, k -> {
            for (String name : new String[]{recordName,oldName}) {
                try { return k.type().getMethod(name); } catch (NoSuchMethodException ignored) {}
            }
            throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.packet_accessor", k.type().getSimpleName() + "." + recordName));
        });
        try { return (T) method.invoke(target); }
        catch (ReflectiveOperationException exception) { throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.packet_accessor", target.getClass().getSimpleName() + "." + recordName), exception); }
    }
}

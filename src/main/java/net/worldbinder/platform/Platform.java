package net.worldbinder.platform;

public final class Platform {
    private static ClientPlatform client;
    private Platform() {}
    public static void install(ClientPlatform implementation) {
        if (client != null) throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.platform_installed"));
        client = java.util.Objects.requireNonNull(implementation);
    }
    public static ClientPlatform get() {
        if (client == null) throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.platform_missing"));
        return client;
    }
}

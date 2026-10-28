package net.worldbinder.test;

import net.worldbinder.WorldBinder;
import java.nio.file.*;

final class NativeArtifactChecks {
    static void neoForge()throws Exception{
        String jar=System.getProperty("worldbinder.compatibilityNeoJar");if(jar==null)return;
        Path artifact=Path.of(jar);String source=WorldBinder.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if(!source.contains(artifact.getFileName().toString()))throw new AssertionError("NeoForge QA must load the frozen production jar: "+source);
        WorldBinder.LOGGER.info("WORLDBINDER_RELEASE_ARTIFACT {} SOURCE {} SHA-256 {}",artifact,source,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))));
    }
}

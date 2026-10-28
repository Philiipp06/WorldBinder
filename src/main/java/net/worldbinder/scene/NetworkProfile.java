package net.worldbinder.scene;

import java.util.*;

/** User-provided backend identities. Brand, seed and dimension keys are deliberately not backend IDs. */
public final class NetworkProfile {
    public String name;
    public String address;
    public static final class Backend {
        public String id=UUID.randomUUID().toString();
        public String name;
        public String projectPath;
        public String projectId;
        public List<String> dimensions=new ArrayList<>();
    }
    public Map<String,Backend> backends=new LinkedHashMap<>();
    public Boolean autoCapture;
    public CaptureSettings captureSettings;
    public String joinBackend;
    /** Explicit user association for this endpoint; disabled unless deliberately selected. */
    public boolean trustDimensionChanges;
}

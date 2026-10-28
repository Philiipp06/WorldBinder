package net.worldbinder.scene;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ExportFilterSettings {
    public boolean droppedItems,experienceOrbs,armorStands,hostileMobs;
    public Set<String> entityIds=new LinkedHashSet<>();
    public Set<String> blockIds=new LinkedHashSet<>();
}

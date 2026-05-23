package net.worldbinder.selection;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.worldbinder.util.Chat;

public final class SelectionManager {
    private BlockPos first;
    private BlockPos second;

    public void setFirstFromCrosshair() {
        BlockPos pos = getPlayerFeetPosition();
        if (pos == null) {
            Chat.warnKey("worldbinder.chat.no_player_position");
            return;
        }
        first = pos.immutable();
        Chat.infoKey("worldbinder.chat.position_1_set", format(pos));
    }

    public void setSecondFromCrosshair() {
        BlockPos pos = getPlayerFeetPosition();
        if (pos == null) {
            Chat.warnKey("worldbinder.chat.no_player_position");
            return;
        }
        second = pos.immutable();
        Chat.infoKey("worldbinder.chat.position_2_set", format(pos));
    }

    public Selection getSelection() {
        return new Selection(first, second);
    }

    public BlockPos first() {
        return first;
    }

    public BlockPos second() {
        return second;
    }

    public boolean hasCompleteSelection() {
        return first != null && second != null;
    }

    public String describe() {
        if (!hasCompleteSelection()) {
            return net.worldbinder.util.Lang.string("worldbinder.selection.no_complete");
        }
        Selection selection = getSelection();
        return net.worldbinder.util.Lang.string("worldbinder.selection.blocks", format(selection.min()), format(selection.max()), selection.volumeLong());
    }

    public String sizeLine() {
        if (!hasCompleteSelection()) {
            return "0 × 0 × 0";
        }
        Selection selection = getSelection();
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        return (max.getX() - min.getX() + 1) + " × " + (max.getY() - min.getY() + 1) + " × " + (max.getZ() - min.getZ() + 1);
    }

    private BlockPos getPlayerFeetPosition() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return null;
        }
        return client.player.blockPosition();
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}

package com.core.attribute.tacz;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Editor access control.
 *
 * <p>Editing is restricted to the single-player / LAN host, and to operators on a
 * dedicated server. Permission plugins are deliberately <b>not</b> consulted: on hybrid
 * (Bukkit + Forge) servers a LuckPerms wildcard or a default-group grant would otherwise
 * silently let ordinary players open the editor and rewrite item data.</p>
 */
public final class AttributePermissions {

    /**
     * Vanilla command permission level required on a dedicated server.
     * {@code 2} is the usual "admin / gamemaster" tier; raise it to {@code 3} or {@code 4}
     * to require full operators.
     */
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private AttributePermissions() {
    }

    /** {@code true} only for the single-player host or an operator at the required level. */
    public static boolean canEdit(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        // Single-player and the LAN host always may edit, whatever their op status.
        if (server.isSingleplayerOwner(player.getGameProfile())) {
            return true;
        }
        // Otherwise operators only.
        return player.hasPermissions(REQUIRED_PERMISSION_LEVEL);
    }
}

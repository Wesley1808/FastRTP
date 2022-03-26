package me.wesley1808.fastrtp.util;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

public final class PermissionManager {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");

    public static boolean hasPermission(CommandSourceStack src, String perm, int level) {
        return LOADED ? Permissions.check(src, perm, level) : src.hasPermission(level);
    }

    public static boolean hasPermission(Player src, String perm) {
        return LOADED ? Permissions.check(src, perm, 2) : src.hasPermissions(2);
    }
}

package me.wesley1808.fastrtp.util;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

public final class RtpPerms {
    public static final Identifier BYPASS_COOLDOWN = id("bypass.cooldown");
    public static final Identifier COMMAND_RELOAD = id("command.reload");
    public static final Identifier COMMAND_RTP = id("command.root");
    public static final Identifier COMMAND_RTP_BACK = id("command.back");
    public static final Identifier COMMAND_RTP_ADVANCED = id("command.advanced");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("fast-rtp", path);
    }

    public static Predicate<CommandSourceStack> require(Identifier permission, PermissionLevel level) {
        return PermissionPredicates.require(permission, level);
    }

    public static Predicate<CommandSourceStack> require(Identifier permission, boolean defaultValue) {
        return PermissionPredicates.require(permission, defaultValue);
    }

    public static boolean check(PermissionContextOwner owner, Identifier permission, PermissionLevel level) {
        return owner.checkPermission(permission, level);
    }
}

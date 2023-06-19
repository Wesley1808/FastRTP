package me.wesley1808.fastrtp.util;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

public final class Permission {
    private static final String BASE = "fast-rtp.";
    public static final String BYPASS_COOLDOWN = BASE + "bypass.cooldown";
    public static final String COMMAND_RELOAD = BASE + "command.reload";
    public static final String COMMAND_RTP = BASE + "command.root";
    public static final String COMMAND_RTP_ADVANCED = BASE + "command.advanced";

    public static Predicate<CommandSourceStack> require(String perm, int level) {
        return Permissions.require(perm, level);
    }

    public static boolean check(CommandSourceStack src, String perm, int level) {
        return Permissions.check(src, perm, level);
    }

    public static boolean check(Player src, String perm, int level) {
        return Permissions.check(src, perm, level);
    }
}

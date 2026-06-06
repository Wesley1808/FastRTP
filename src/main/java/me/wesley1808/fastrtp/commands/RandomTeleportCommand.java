package me.wesley1808.fastrtp.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.fastrtp.config.ConfigHandler;
import me.wesley1808.fastrtp.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class RandomTeleportCommand {
    private static final Object2ObjectOpenHashMap<UUID, Pair<ServerLevel, Vec3>> RTP_COORDS = new Object2ObjectOpenHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("rtp")
                .requires(src -> !Config.instance().requirePermission || RtpPerms.check(src, RtpPerms.COMMAND_RTP, PermissionLevel.GAMEMASTERS))
                .executes(ctx -> execute(ctx.getSource(), Bypass.NONE));

        root.then(Commands.literal("reload")
                .requires(RtpPerms.require(RtpPerms.COMMAND_RELOAD, PermissionLevel.GAMEMASTERS))
                .executes(ctx -> reloadConfig(ctx.getSource()))
        );

        var advancedRtpRoot = Commands.argument("player", EntityArgument.player())
                .requires(RtpPerms.require(RtpPerms.COMMAND_RTP_ADVANCED, PermissionLevel.GAMEMASTERS))
                .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), Bypass.NONE))
                .then(buildAdvancedRtpNode(Bypass.NONE));

        for (Bypass bypass : Bypass.values()) {
            if (bypass != Bypass.NONE) {
                advancedRtpRoot.then(Commands.literal("bypass_%s".formatted(bypass.toString().toLowerCase(Locale.ROOT)))
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), bypass))
                        .then(buildAdvancedRtpNode(bypass))
                );
            }
        }

        root.then(advancedRtpRoot);

        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("fastrtp").redirect(node));

        if (Config.instance().rtpBackEnabled) {
            dispatcher.register(Commands.literal("rtpback")
                    .requires(RtpPerms.require(RtpPerms.COMMAND_RTP_BACK, true))
                    .executes(ctx -> executeBack(ctx.getSource().getPlayerOrException()))
            );
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> buildAdvancedRtpNode(Bypass bypass) {
        return Commands.argument("world", DimensionArgument.dimension())
                .executes(ctx -> execute(
                        ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"),
                        DimensionArgument.getDimension(ctx, "world"),
                        bypass
                ))

                .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                        .executes(ctx -> execute(
                                ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player"),
                                DimensionArgument.getDimension(ctx, "world"),
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                bypass
                        ))

                        .then(Commands.argument("minRadius", IntegerArgumentType.integer(0))
                                .executes(ctx -> execute(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        DimensionArgument.getDimension(ctx, "world"),
                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                        IntegerArgumentType.getInteger(ctx, "minRadius"),
                                        bypass
                                ))
                        )
                );
    }

    private static int execute(CommandSourceStack source, Bypass bypass) throws CommandSyntaxException {
        return execute(source, source.getPlayerOrException(), bypass);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, Bypass bypass) {
        return execute(source, player, Util.getLevel(player), bypass);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, Bypass bypass) {
        return execute(source, player, level, Util.getRadius(level), bypass);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius, Bypass bypass) {
        return execute(source, player, level, radius, Config.instance().minRadius, bypass);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius, int minRadius, Bypass bypass) {
        if (minRadius > radius) {
            source.sendFailure(Component.literal("The minimum radius cannot be larger than the maximum radius!"));
            return 0;
        }

        if (PositionLocator.isLocating(player) || !Scheduler.canSchedule(player.getUUID())) {
            // If the player is already locating a random position, don't start a new one.
            return 0;
        }

        Config.Messages messages = Config.instance().messages;
        if (!bypass.bypassesCooldown() && CooldownManager.hasCooldown(player.getUUID())) {
            String seconds = String.valueOf(CooldownManager.getCooldownInSeconds(player.getUUID()));
            player.sendSystemMessage(Util.format(messages.rtpOnCooldown.replace("${seconds}", seconds)), false);
            return 0;
        }

        String error = bypass.bypassesChecks() ? null : Util.mayTeleport(player);
        if (error != null) {
            player.sendSystemMessage(Util.format(messages.preventedRtp.replace("${reason}", error)));
            return 0;
        }

        player.sendSystemMessage(Util.format(messages.rtpStartSearch), true);
        CooldownManager.addCooldown(player);

        long startTime = System.currentTimeMillis();
        PositionLocator locator = new PositionLocator(level, player.getUUID(), radius, minRadius);

        locator.findPosition((pos) -> {
            if (pos == null) {
                player.sendSystemMessage(Util.format(messages.rtpLocNotFound));
                CooldownManager.removeCooldown(player.getUUID());
            } else {
                long elapsedTime = System.currentTimeMillis() - startTime;
                player.sendSystemMessage(Util.format(messages.rtpLocFound.replace("${seconds}", String.format("%.1f", elapsedTime / 1000F))), true);

                if (bypass.bypassesChecks() || !Config.instance().useStrictTeleportCheck) {
                    teleportPlayer(player, level, pos);
                } else {
                    Scheduler.scheduleTeleport(player,
                            () -> teleportPlayer(player, level, pos),
                            () -> {
                                CooldownManager.removeCooldown(player.getUUID());
                                player.sendSystemMessage(Util.format(messages.tpCancelled), false);
                            }
                    );
                }
            }
        });

        return 1;
    }

    private static void teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (player.isAlive()) {
            player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot(), true);
            player.connection.resetPosition();
            RTP_COORDS.put(player.getUUID(), new ObjectObjectImmutablePair<>(level, pos));

            player.sendSystemMessage(Util.format(Config.instance().messages.rtpTeleportPlayer
                    .replace("${x}", String.format("%.0f", pos.x))
                    .replace("${y}", String.format("%.0f", pos.y))
                    .replace("${z}", String.format("%.0f", pos.z))
                    .replace("${world}", level.dimension().identifier().getPath())
            ));
        }
    }

    private static int executeBack(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!Scheduler.canSchedule(uuid)) {
            return 0;
        }

        Config.Messages messages = Config.instance().messages;
        Pair<ServerLevel, Vec3> pair = RTP_COORDS.get(uuid);
        if (pair == null) {
            player.sendSystemMessage(Util.format(messages.rtpBackLocNotFound));
            return 0;
        }

        String error = Util.mayTeleport(player);
        if (error != null) {
            player.sendSystemMessage(Util.format(messages.preventedRtpBack.replace("${reason}", error)));
            return 0;
        }

        Vec3 pos = pair.right();
        ServerLevel level = pair.left();

        level.getChunkSource().addTicketWithRadius(RegistryUtil.PRE_TELEPORT, ChunkPos.containing(BlockPos.containing(pos)), 1);
        Scheduler.scheduleTeleport(player, () -> {
            player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot(), true);
            player.connection.resetPosition();
            player.sendSystemMessage(Util.format(messages.rtpBackSuccess));
        }, () -> {
            player.sendSystemMessage(Util.format(messages.tpCancelled), false);
        });

        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        ConfigHandler.load();
        source.sendSuccess(() -> Component.literal("Config reloaded!").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    public static void clearLastTeleports() {
        RTP_COORDS.clear();
    }

    private enum Bypass {
        ALL,
        NONE,
        CHECKS,
        COOLDOWN;

        private boolean bypassesChecks() {
            return this == ALL || this == CHECKS;
        }

        private boolean bypassesCooldown() {
            return this == ALL || this == COOLDOWN;
        }
    }
}
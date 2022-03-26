package me.wesley1808.fastrtp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.fastrtp.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.DimensionArgument.dimension;
import static net.minecraft.commands.arguments.DimensionArgument.getDimension;
import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.arguments.EntityArgument.player;

public final class RandomTeleportCommand {
    private static final TicketType<Integer> PRE_TELEPORT = TicketType.create("pre_teleport", Integer::compareTo, 70);
    private static final Object2ObjectOpenHashMap<UUID, Pair<ServerLevel, Vec3>> RTP_COORDS = new Object2ObjectOpenHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("rtp")
                .requires(src -> !Config.instance().requirePermission || PermissionManager.hasPermission(src, "fast-rtp.command.root", 2))
                .executes(ctx -> execute(ctx.getSource()))

                .then(argument("player", player())
                        .requires(src -> PermissionManager.hasPermission(src, "fast-rtp.command.advanced", 2))
                        .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player")))

                        .then(argument("world", dimension())
                                .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player"), getDimension(ctx, "world")))

                                .then(argument("radius", integer(0))
                                        .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player"), getDimension(ctx, "world"), getInteger(ctx, "radius")))

                                        .then(argument("minRadius", integer(0))
                                                .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player"), getDimension(ctx, "world"), getInteger(ctx, "radius"), getInteger(ctx, "minRadius")))
                                        )
                                )
                        )
                )
        );

        dispatcher.register(literal("rtpback").executes(ctx -> executeBack(ctx.getSource().getPlayerOrException())));
    }

    private static int execute(CommandSourceStack source) throws CommandSyntaxException {
        return execute(source, source.getPlayerOrException());
    }

    private static int execute(CommandSourceStack source, ServerPlayer player) {
        return execute(source, player, Util.getLevel(player));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level) {
        return execute(source, player, level, Util.getRadius(level));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius) {
        return execute(source, player, level, radius, Config.instance().minRadius);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius, int minRadius) {
        if (minRadius > radius) {
            source.sendFailure(new TextComponent("Minimum radius cannot be larger than radius!"));
            return 0;
        }

        if (CooldownManager.hasCooldown(player.getUUID())) {
            String seconds = String.valueOf(CooldownManager.getCooldownInSeconds(player.getUUID()));
            player.displayClientMessage(Util.format(Config.instance().messageOnCooldown.replace("#SECONDS", seconds)), false);
            return 0;
        }

        player.displayClientMessage(Util.format(Config.instance().messageRtpSearching), true);
        CooldownManager.addCooldown(player);

        PositionLocator locator = new PositionLocator(level, radius, minRadius);
        locator.findPosition((pos) -> {
            if (pos != null) {
                player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
                player.connection.resetPosition();
                RTP_COORDS.put(player.getUUID(), new ObjectObjectImmutablePair<>(level, pos));

                player.displayClientMessage(Util.format(Config.instance().messageRtpSuccess
                        .replace("#X", String.format("%.0f", pos.x))
                        .replace("#Y", String.format("%.0f", pos.y))
                        .replace("#Z", String.format("%.0f", pos.z))
                        .replace("#WORLD", level.dimension().location().getPath())
                ), false);
            } else {
                player.displayClientMessage(Util.format(Config.instance().messageRtpFail), false);
                CooldownManager.removeCooldown(player.getUUID());
            }
        });

        return 1;
    }

    private static int executeBack(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!Scheduler.canSchedule(uuid)) {
            player.displayClientMessage(new TextComponent("Please wait before using this command again!").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        Pair<ServerLevel, Vec3> pair = RTP_COORDS.get(uuid);
        if (pair == null) {
            player.displayClientMessage(Util.format(Config.instance().messageRtpBackFail), false);
            return 0;
        }

        Vec3 pos = pair.right();
        ServerLevel level = pair.left();

        level.getChunkSource().addRegionTicket(PRE_TELEPORT, new ChunkPos(new BlockPos(pos)), 1, player.getId());
        Scheduler.scheduleTeleport(player, () -> {
            player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            player.connection.resetPosition();
            player.displayClientMessage(Util.format(Config.instance().messageRtpBackSuccess), false);
            RTP_COORDS.remove(uuid);
        });

        return 1;
    }
}
package me.wesley1808.fastrtp.util;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.wesley1808.fastrtp.FastRTP;
import me.wesley1808.fastrtp.config.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    private static final ScheduledThreadPoolExecutor SCHEDULER = new ScheduledThreadPoolExecutor(0);
    private static final ObjectOpenHashSet<UUID> ACTIVE = new ObjectOpenHashSet<>();

    public static boolean canSchedule(UUID uuid) {
        return !ACTIVE.contains(uuid);
    }

    public static void schedule(long millis, Runnable runnable) {
        SCHEDULER.schedule(runnable, millis, TimeUnit.MILLISECONDS);
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
    }

    public static void scheduleTeleport(ServerPlayer player, Runnable runnable) {
        ACTIVE.add(player.getUUID());
        teleportLoop(3, player.getUUID(), player.position(), runnable);
    }

    private static void teleportLoop(int seconds, UUID uuid, Vec3 oldPos, Runnable runnable) {
        ServerPlayer player = FastRTP.server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            ACTIVE.remove(uuid);
            return;
        }

        if (!player.position().closerThan(oldPos, 2)) {
            ACTIVE.remove(uuid);
            player.displayClientMessage(Util.format(Config.instance().messageTpCancelled), false);
            return;
        }

        player.displayClientMessage(Util.format(Config.instance().messageTpSecondsLeft
                .replace("#SECONDS", String.valueOf(seconds))
                .replace("seconds", seconds == 1 ? "second" : "seconds")
        ), false);

        if (seconds == 1) {
            schedule(1000, () -> {
                runnable.run();
                ACTIVE.remove(uuid);
            });
        } else {
            schedule(1000, () -> teleportLoop(seconds - 1, uuid, oldPos, runnable));
        }
    }
}
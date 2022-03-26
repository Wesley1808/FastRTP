package me.wesley1808.fastrtp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import me.wesley1808.fastrtp.commands.RandomTeleportCommand;
import me.wesley1808.fastrtp.config.ConfigHandler;
import me.wesley1808.fastrtp.util.PositionLocator;
import me.wesley1808.fastrtp.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

public class FastRTP implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static MinecraftServer server;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(this::onRegisterCommands);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(this::onReload);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    private void onTick(MinecraftServer server) {
        PositionLocator.update();
    }

    private void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, boolean dedicated) {
        RandomTeleportCommand.register(dispatcher);
    }

    private void onReload(MinecraftServer server, ResourceManager manager, boolean success) {
        ConfigHandler.load();
    }

    private void onServerStarted(MinecraftServer server) {
        FastRTP.server = server;
        ConfigHandler.load();
    }

    private void onServerStopped(MinecraftServer server) {
        Scheduler.shutdown();
    }
}

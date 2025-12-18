package me.wesley1808.fastrtp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import me.wesley1808.fastrtp.commands.RandomTeleportCommand;
import me.wesley1808.fastrtp.config.ConfigHandler;
import me.wesley1808.fastrtp.util.CooldownManager;
import me.wesley1808.fastrtp.util.PositionLocator;
import me.wesley1808.fastrtp.util.RegistryUtil;
import me.wesley1808.fastrtp.util.Scheduler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public class FastRTP implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        RegistryUtil.register();

        CommandRegistrationCallback.EVENT.register(this::onRegisterCommands);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopped);
        }

        ConfigHandler.load();
        ConfigHandler.save();
    }

    private void onTick(MinecraftServer server) {
        PositionLocator.update();
    }

    private void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        RandomTeleportCommand.register(dispatcher);
    }

    private void onServerStopped(MinecraftServer server) {
        if (server.isDedicatedServer()) {
            Scheduler.shutdown();
        } else {
            CooldownManager.clearCooldowns();
            RandomTeleportCommand.clearLastTeleports();
        }
    }

    private void onClientStopped(Minecraft client) {
        Scheduler.shutdown();
    }
}

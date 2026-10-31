package me.wesley1808.fastrtp.util;

import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.parsers.NodeParser;
import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.teleportapi.api.TeleportManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Util {
    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .quickText()
            .requireSafe()
            .build();

    public static Component format(String string) {
        return PARSER.parseComponent(string, ParserContext.of());
    }

    public static ServerLevel getLevel(ServerPlayer player) {
        ServerLevel currentLevel = player.level();

        ServerLevel redirect = parseLevel(currentLevel.getServer(), Config.instance().dimensionRedirects.get(currentLevel.dimension().identifier().toString()));
        if (redirect != null) {
            return redirect;
        }

        if (Config.instance().useCurrentWorld) {
            return currentLevel;
        }

        ServerLevel defaultLevel = parseLevel(currentLevel.getServer(), Config.instance().defaultDimension);
        return defaultLevel != null ? defaultLevel : currentLevel;
    }

    @Nullable
    public static ServerLevel parseLevel(MinecraftServer server, @Nullable String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return null;
        }

        Identifier location = Identifier.tryParse(dimension);
        if (location == null) {
            return null;
        }

        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    public static int getRadius(ServerLevel level) {
        final int borderRadius = (int) (level.getWorldBorder().getSize() / 2) - 16;
        final int radius = Config.instance().radius;
        if (radius < 0) {
            return borderRadius;
        }

        return Math.min(borderRadius, radius);
    }

    @Nullable
    public static String mayTeleport(ServerPlayer player, boolean useStrictCheck) {
        if (TeleportManager.isTeleporting(player)) {
            return "You are already teleporting!";
        }

        if (useStrictCheck && player.gameMode.isSurvival()) {
            if (player.hasEffect(MobEffects.LEVITATION)) {
                return "You cannot teleport while levitating!";
            }

            if (player.hasEffect(MobEffects.DARKNESS)) {
                return "You cannot teleport while affected by darkness!";
            }

            return isBeingHunted(player, 24);
        }
        return null;
    }

    private static String isBeingHunted(ServerPlayer player, int huntRange) {
        List<Monster> monsters = player.level().getEntities(
                EntityTypeTest.forClass(Monster.class),
                player.getBoundingBox().inflate(64),
                EntitySelector.NO_SPECTATORS
        );

        int huntRangeSqr = huntRange * huntRange;
        for (Monster monster : monsters) {
            boolean isTargetedBy = monster.getTarget() == player || (
                    monster.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) &&
                    monster.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player
            );

            if (!isTargetedBy && monster instanceof Warden warden) {
                isTargetedBy = warden.getAngerManagement().getActiveEntity().orElse(null) == player;
            }

            if (isTargetedBy && canHunt(monster, player, huntRangeSqr)) {
                return "You cannot teleport while there are monsters hunting you!";
            }
        }

        return null;
    }

    private static boolean canHunt(Monster monster, ServerPlayer player, int huntRangeSqr) {
        if (monster instanceof Warden) {
            return true;
        }

        return player.distanceToSqr(monster) < huntRangeSqr && monster.getSensing().hasLineOfSight(player);
    }
}

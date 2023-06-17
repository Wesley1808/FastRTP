package me.wesley1808.fastrtp.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TeleportUtils {
    @Nullable
    public static String mayTeleport(ServerPlayer player) {
        if (player.gameMode.isSurvival()) {
            if (player.hasEffect(MobEffects.LEVITATION)) {
                return "Levitation Effect";
            }

            if (player.level().dimension().location().getNamespace().equals("minecraft")) {
                if (player.hasEffect(MobEffects.DARKNESS)) {
                    return "Darkness Effect";
                }

                List<Monster> monsters = player.level().getEntities(EntityTypeTest.forClass(Monster.class), player.getBoundingBox().inflate(64D), EntitySelector.NO_SPECTATORS);
                for (Monster monster : monsters) {
                    boolean isTargetedBy = monster.getTarget() == player || (monster.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && monster.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player);
                    if (!isTargetedBy && monster instanceof Warden warden) {
                        isTargetedBy = warden.getAngerManagement().getActiveEntity().orElse(null) == player;
                    }

                    if (isTargetedBy) {
                        if (monster instanceof Warden) {
                            return "Hunted by warden";
                        }

                        float distance = player.distanceTo(monster);
                        if (distance < 24 && monster.getSensing().hasLineOfSight(player)) {
                            return String.format("Hunted by %s (%.0f blocks away)", BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()).getPath(), distance);
                        }
                    }
                }
            }
        }

        return null;
    }
}

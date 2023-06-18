package me.wesley1808.fastrtp.util;

import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.fastrtp.mixins.ServerChunkCacheAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

public final class Util {

    @Nullable
    public static LevelChunk getChunkIfLoaded(ServerLevel level, int chunkX, int chunkZ) {
        final ChunkHolder holder = getChunkHolder(level.getChunkSource(), chunkX, chunkZ);
        return holder != null ? holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left().orElse(null) : null;
    }

    @Nullable
    private static ChunkHolder getChunkHolder(ServerChunkCache chunkCache, int chunkX, int chunkZ) {
        ServerChunkCacheAccessor accessor = (ServerChunkCacheAccessor) chunkCache;
        return accessor.getHolder(ChunkPos.asLong(chunkX, chunkZ));
    }

    // Outdated message formatting method.
    // Will be used until there's a better solution that consistently works in snapshots.
    public static MutableComponent format(String string) {
        var builder = new StringBuilder(string);
        for (int index = builder.indexOf("&"); index >= 0; index = builder.indexOf("&", index + 1)) {
            if (matches(builder.charAt(index + 1))) {
                builder.setCharAt(index, '§');
            }
        }
        return Component.literal(builder.toString());
    }

    private static boolean matches(Character c) {
        return "b0931825467adcfelmnor".contains(c.toString());
    }

    public static ServerLevel getLevel(ServerPlayer player) {
        ResourceLocation location;
        if (Config.instance().useCurrentWorld || (location = ResourceLocation.tryParse(Config.instance().defaultDimension)) == null) {
            return player.serverLevel();
        }

        return player.server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    public static int getRadius(ServerLevel level) {
        final int borderRadius = (int) (level.getWorldBorder().getSize() / 2) - 16;
        final int radius = Config.instance().radius;
        if (radius < 0) {
            return borderRadius;
        }

        return Math.min(borderRadius, radius);
    }
}

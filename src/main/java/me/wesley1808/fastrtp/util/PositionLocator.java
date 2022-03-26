package me.wesley1808.fastrtp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

public final class PositionLocator {
    private static final TicketType<ChunkPos> LOCATE = TicketType.create("locate", Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final Random RANDOM = new Random();
    private final ServerLevel level;
    private final int radius;
    private final int minRadius;
    private final int maxY;
    private long stopTime;

    public PositionLocator(ServerLevel level, int radius, int minRadius) {
        this.level = level;
        this.maxY = level.getLogicalHeight();
        this.radius = radius >> 4;
        this.minRadius = minRadius >> 4;
    }

    public CompletableFuture<Vec3> findPosition() {
        this.stopTime = System.currentTimeMillis() + 10000;
        return CompletableFuture.supplyAsync(this::newPosition);
    }

    private Vec3 newPosition() {
        if (System.currentTimeMillis() > this.stopTime) {
            return null;
        }

        ChunkPos chunkPos = RANDOM.nextBoolean()
                ? new ChunkPos(this.nextRandomValueWithMinimum(), this.nextRandomValue())
                : new ChunkPos(this.nextRandomValue(), this.nextRandomValueWithMinimum());

        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        BlockPos pos = new BlockPos(x, 128, z);
        if (!this.isValid(pos)) {
            return this.newPosition();
        }

        LevelChunk chunk = this.getChunk(chunkPos);
        if (chunk == null) {
            return null;
        }

        return this.findSafePositionIn(chunk, pos);
    }

    private Vec3 findSafePositionIn(ChunkAccess chunk, BlockPos pos) {
        final double centerX = pos.getX();
        final double centerZ = pos.getZ();
        double x = centerX;
        double z = centerZ;
        int y = this.getY(chunk, x, z);
        int attempts = 30;

        while (--attempts > 0 && !this.isSafe(chunk, x, y, z)) {
            x = Mth.nextDouble(RANDOM, centerX - 5, centerX + 5);
            z = Mth.nextDouble(RANDOM, centerZ - 5, centerZ + 5);
            y = this.getY(chunk, x, z);
        }

        if (attempts > 0) {
            return new Vec3(Mth.floor(x) + 0.5D, y, Mth.floor(z) + 0.5D);
        } else {
            return this.newPosition();
        }
    }

    /**
     * Checks if the position is safe to teleport to.
     *
     * @param chunk: The chunk to check in
     * @return Boolean: if the position is safe.
     */
    private boolean isSafe(ChunkAccess chunk, double x, int y, double z) {
        BlockPos pos = new BlockPos(x, y - 1, z);
        Material material = chunk.getBlockState(pos).getMaterial();

        return pos.getY() <= this.maxY
                && !material.isLiquid() && material != Material.FIRE && material != Material.BAMBOO && material != Material.CACTUS
                && this.level.noCollision(EntityType.PLAYER.getAABB(Mth.floor(x) + 0.5D, y, Mth.floor(z) + 0.5D));
    }

    /**
     * Quick checks to skip a lot of expensive chunk loading.
     */
    private boolean isValid(BlockPos pos) {
        if (this.level.getWorldBorder().isWithinBounds(pos)) {
            return this.isBiomeValid(this.level.getBiome(pos));
        }
        return false;
    }

    private boolean isBiomeValid(Holder<Biome> biome) {
        ResourceKey<Biome> key = this.level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getResourceKey(biome.value()).orElse(null);
        return key != null
                && !biome.is(BiomeTags.IS_BEACH)
                && !biome.is(BiomeTags.IS_OCEAN)
                && !biome.is(BiomeTags.IS_DEEP_OCEAN)
                && !biome.is(BiomeTags.IS_RIVER)
                && key != Biomes.THE_END
                && key != Biomes.SMALL_END_ISLANDS
                && key != Biomes.THE_VOID;
    }

    private LevelChunk getChunk(ChunkPos pos) {
        LevelChunk chunk = Util.getChunkIfLoaded(this.level, pos.x, pos.z);
        if (chunk != null) {
            return chunk;
        }

        this.level.getChunkSource().addRegionTicket(LOCATE, pos, 1, pos);
        while (chunk == null && System.currentTimeMillis() <= this.stopTime) {
            LockSupport.parkNanos("Waiting for chunk", 250000000L); // 250ms
            chunk = Util.getChunkIfLoaded(this.level, pos.x, pos.z);
        }

        return chunk;
    }

    private int nextRandomValue() {
        return Mth.nextInt(RANDOM, -this.radius, this.radius);
    }

    private int nextRandomValueWithMinimum() {
        return RANDOM.nextBoolean() ? Mth.nextInt(RANDOM, this.minRadius, this.radius) : Mth.nextInt(RANDOM, -this.radius, -this.minRadius);
    }

    private int getY(ChunkAccess chunk, double x, double z) {
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z) + 1;
    }
}
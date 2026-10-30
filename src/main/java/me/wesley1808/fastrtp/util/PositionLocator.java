package me.wesley1808.fastrtp.util;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.wesley1808.fastrtp.FastRTP;
import me.wesley1808.fastrtp.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PositionLocator {
    private static final ObjectOpenHashSet<UUID> LOCATORS = new ObjectOpenHashSet<>();
    private static final RandomSource RANDOM = RandomSource.createThreadLocalInstance();
    private static final int MAX_SAFETY_CHECK_RADIUS = 4;
    private final ServerLevel level;
    private final UUID uuid;
    private final int minRadius;
    private final int radius;
    private final int centerX;
    private final int centerZ;
    private Consumer<Vec3> callback;
    private long startTime;
    private long stopTime;
    public int fastCheckFails;
    public int chunkCheckFails;

    public static void clear() {
        LOCATORS.clear();
    }

    public static boolean isLocating(ServerPlayer player) {
        return LOCATORS.contains(player.getUUID());
    }

    public PositionLocator(ServerLevel level, UUID uuid, int radius, int minRadius) {
        this.level = level;
        this.uuid = uuid;
        this.radius = radius >> 4;
        this.minRadius = minRadius >> 4;

        WorldBorder border = this.level.getWorldBorder();
        this.centerX = (int) border.getCenterX() >> 4;
        this.centerZ = (int) border.getCenterZ() >> 4;
    }

    public void findPosition(Consumer<Vec3> callback) {
        LOCATORS.add(this.uuid);
        this.callback = callback;
        this.startTime = System.currentTimeMillis();
        this.stopTime = this.startTime + 10000;
        this.newPosition();
    }

    private void onFinished(@Nullable Vec3 pos) {
        LOCATORS.remove(this.uuid);
        this.callback.accept(pos);
    }

    private void onComplete(Vec3 pos) {
        if (Config.instance().debugLogging) {
            FastRTP.LOGGER.info("[FastRTP] Completed RTP request in {}ms. ({} skipped chunks, {} unsafe chunks)",
                    System.currentTimeMillis() - this.startTime,
                    this.fastCheckFails,
                    this.chunkCheckFails
            );
        }
        this.onFinished(pos);
    }

    private void onFailure(String reason) {
        if (Config.instance().debugLogging) {
            FastRTP.LOGGER.info("[FastRTP] RTP failed: {}. ({} skipped chunks, {} unsafe chunks)",
                    reason,
                    this.fastCheckFails,
                    this.chunkCheckFails
            );
        }
        this.onFinished(null);
    }

    private void newPosition() {
        if (this.chunkCheckFails >= 256) {
            this.onFailure("too many unsafe chunks");
            return;
        }

        if (System.currentTimeMillis() > this.stopTime) {
            this.onFailure("took too long");
            return;
        }

        for (int attempts = 0; attempts < 10000; ++attempts) {
            ChunkPos pos = RANDOM.nextBoolean()
                    ? new ChunkPos(this.nextRandomValueWithMinimum(this.centerX), this.nextRandomValue(this.centerZ))
                    : new ChunkPos(this.nextRandomValue(this.centerX), this.nextRandomValueWithMinimum(this.centerZ));

            // Quick checks to skip a lot of unnecessary chunk loading.
            if (this.isValid(pos)) {
                this.queueChunk(pos);
                return;
            }

            this.fastCheckFails++;
        }

        this.onFailure("too many skipped chunks");
    }

    private void queueChunk(ChunkPos pos) {
        CompletableFuture<?> chunkLoadFuture = this.level.getChunkSource().addTicketAndLoadWithRadius(RegistryUtil.LOCATE, pos, 0);
        chunkLoadFuture.thenAcceptAsync(_ -> {
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) {
                this.onFailure("chunk load at [%d, %d] failed".formatted(pos.x(), pos.z()));
                return;
            }

            this.onChunkLoaded(chunk);
        }, this.level.getServer());
    }

    private void onChunkLoaded(LevelChunk chunk) {
        Vec3 pos = this.findSafePositionInChunk(chunk);
        if (pos != null) {
            this.onComplete(pos);
            return;
        }

        this.chunkCheckFails++;
        this.newPosition();
    }

    private Vec3 findSafePositionInChunk(LevelChunk chunk) {
        int centerX = chunk.getPos().getMiddleBlockX();
        int centerZ = chunk.getPos().getMiddleBlockZ();
        int negativeDiff = 8 - Mth.clamp(Config.instance().safetyCheckRadius, 1, MAX_SAFETY_CHECK_RADIUS);
        int positiveDiff = negativeDiff - 1;

        for (int x = centerX - negativeDiff; x <= centerX + positiveDiff; x++) {
            for (int z = centerZ - negativeDiff; z <= centerZ + positiveDiff; z++) {
                int y = this.getY(chunk, x, z);
                if (this.isSafe(chunk, x, y, z)) {
                    return new Vec3(x + 0.5D, y + 1, z + 0.5D);
                }
            }
        }

        return null;
    }

    private boolean isSafe(LevelChunk chunk, int centerX, int y, int centerZ) {
        // Early return if the landing position isn't safe.
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(centerX, y, centerZ);
        if (!this.isSafeBelowPlayer(chunk.getBlockState(mutable)) ||
            !this.isSafeSurroundingPlayer(chunk.getBlockState(mutable.move(Direction.UP))) ||
            !this.level.noCollision(EntityTypes.PLAYER.getSpawnAABB(centerX + 0.5D, y + 1, centerZ + 0.5D))
        ) {
            return false;
        }

        int radius = Math.min(Config.instance().safetyCheckRadius, MAX_SAFETY_CHECK_RADIUS);
        if (radius > 0) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (x != centerX || z != centerZ) {
                        BlockState state = chunk.getBlockState(mutable.set(x, y, z));
                        if (!this.isSafeSurroundingBelowPlayer(state) || !this.isSafeSurroundingPlayer(chunk.getBlockState(mutable.move(Direction.UP)))) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean isSafeBelowPlayer(BlockState state) {
        Block block = state.getBlock();
        return this.isSafeSurroundingBelowPlayer(state) && block != Blocks.BAMBOO;
    }

    private boolean isSafeSurroundingBelowPlayer(BlockState state) {
        Block block = state.getBlock();
        return (state.is(BlockTags.ENTITIES_CAN_TELEPORT_TO) || block == Blocks.SNOW) &&
               block != Blocks.CACTUS &&
               block != Blocks.MAGMA_BLOCK;
    }

    private boolean isSafeSurroundingPlayer(BlockState state) {
        Block block = state.getBlock();
        return !state.is(BlockTags.FIRE) &&
               !state.is(BlockTags.CAMPFIRES) &&
               block != Blocks.LAVA &&
               block != Blocks.POWDER_SNOW &&
               block != Blocks.MAGMA_BLOCK &&
               block != Blocks.CACTUS &&
               block != Blocks.SWEET_BERRY_BUSH;
    }

    private int getY(LevelChunk chunk, int x, int z) {
        if (!this.level.dimensionType().hasCeiling()) {
            return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }

        int bottomY = chunk.getMinY();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, this.level.getLogicalHeight(), z);

        boolean isAir = false;
        boolean isAirBelow = false;
        while (mutable.getY() >= bottomY && isAirBelow || !isAir) {
            isAir = isAirBelow;
            isAirBelow = chunk.getBlockState(mutable.move(Direction.DOWN)).isAir();
        }

        return mutable.getY();
    }

    private boolean isValid(ChunkPos pos) {
        if (this.level.getWorldBorder().isWithinBounds(pos)) {
            return this.isBiomeValid(this.level.getBiome(pos.getMiddleBlockPosition(this.level.getLogicalHeight())));
        }
        return false;
    }

    private boolean isBiomeValid(Holder<Biome> biome) {
        for (TagKey<Biome> biomeTag : Config.instance().blackListedBiomeTags) {
            if (biome.is(biomeTag)) {
                return false;
            }
        }

        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        return key.isEmpty() || !Config.instance().blackListedBiomes.contains(key.get());
    }

    private int nextRandomValue(int center) {
        return Mth.nextInt(RANDOM, center - this.radius, center + this.radius);
    }

    private int nextRandomValueWithMinimum(int center) {
        return RANDOM.nextBoolean()
                ? Mth.nextInt(RANDOM, center + this.minRadius, center + this.radius)
                : Mth.nextInt(RANDOM, center - this.radius, center - this.minRadius);
    }
}
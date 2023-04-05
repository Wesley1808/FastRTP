package me.wesley1808.fastrtp.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.wesley1808.fastrtp.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PositionLocator {
    private static final Predicate<BlockState> BELOW_PLAYER_PREDICATE = (state) -> (state.getMaterial().blocksMotion() || state.is(Blocks.SNOW)) && !state.is(Blocks.BAMBOO) && !state.is(Blocks.CACTUS) && !state.is(Blocks.MAGMA_BLOCK);
    private static final Predicate<BlockState> SURROUNDING_BLOCK_PREDICATE = (state) -> !state.is(BlockTags.FIRE) && !state.is(Blocks.LAVA) && !state.is(Blocks.POWDER_SNOW) && !state.is(Blocks.MAGMA_BLOCK);
    private static final Object2ObjectOpenHashMap<UUID, PositionLocator> LOCATORS = new Object2ObjectOpenHashMap<>();
    private static final ObjectOpenHashSet<UUID> PENDING_REMOVAL = new ObjectOpenHashSet<>();
    private static final TicketType<ChunkPos> LOCATE = TicketType.create("locate", Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final RandomSource RANDOM = RandomSource.create();
    private final ServerLevel level;
    private final UUID uuid;
    private final int minRadius;
    private final int radius;
    private final int centerX;
    private final int centerZ;
    private Consumer<Vec3> callback;
    private ChunkPos queuedPos;
    private long stopTime;
    private int attempts;
    private int x;
    private int z;

    public static boolean isLocating(ServerPlayer player) {
        return LOCATORS.containsKey(player.getUUID());
    }

    public static void update() {
        for (PositionLocator locator : LOCATORS.values()) {
            locator.tick();
        }

        for (UUID uuid : PENDING_REMOVAL) {
            LOCATORS.remove(uuid);
        }

        PENDING_REMOVAL.clear();
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

    private void tick() {
        if (System.currentTimeMillis() <= this.stopTime) {
            LevelChunk chunk = Util.getChunkIfLoaded(this.level, this.queuedPos.x, this.queuedPos.z);
            if (chunk != null) {
                this.onChunkLoaded(chunk);
            }
        } else {
            this.onChunkLoaded(null);
        }
    }

    public void findPosition(Consumer<Vec3> callback) {
        this.callback = callback;
        this.stopTime = System.currentTimeMillis() + 10000;
        this.newPosition();
    }

    private void newPosition() {
        if (++this.attempts > 256 || System.currentTimeMillis() > this.stopTime) {
            this.callback.accept(null);
            return;
        }

        ChunkPos pos = RANDOM.nextBoolean()
                ? new ChunkPos(this.nextRandomValueWithMinimum(this.centerX), this.nextRandomValue(this.centerZ))
                : new ChunkPos(this.nextRandomValue(this.centerX), this.nextRandomValueWithMinimum(this.centerZ));

        this.x = pos.getMiddleBlockX();
        this.z = pos.getMiddleBlockZ();

        // Quick checks to skip a lot of unnecessary chunk loading.
        if (this.isValid(pos)) {
            this.queueChunk(pos);
        } else {
            this.newPosition();
        }
    }

    private void queueChunk(ChunkPos pos) {
        this.level.getChunkSource().addRegionTicket(LOCATE, pos, 0, pos);
        this.queuedPos = pos;
        PENDING_REMOVAL.remove(this.uuid);
        LOCATORS.put(this.uuid, this);
    }

    private void onChunkLoaded(LevelChunk chunk) {
        PENDING_REMOVAL.add(this.uuid);

        if (chunk == null) {
            this.callback.accept(null);
            return;
        }

        Vec3 pos = this.findSafePositionInChunk(chunk, this.x, this.z);
        if (pos != null) {
            this.callback.accept(pos);
            return;
        }

        this.newPosition();
    }

    private Vec3 findSafePositionInChunk(LevelChunk chunk, final int centerX, final int centerZ) {
        for (int x = centerX - 6; x <= centerX + 5; x++) {
            for (int z = centerZ - 6; z <= centerZ + 5; z++) {
                int y = this.getY(chunk, x, z);
                if (this.isSafe(chunk, x, y, z)) {
                    return new Vec3(Mth.floor(x) + 0.5D, y + 1, Mth.floor(z) + 0.5D);
                }
            }
        }

        return null;
    }

    private boolean isSafe(LevelChunk chunk, double centerX, int y, double centerZ) {
        // Early return if the landing position isn't safe.
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(centerX, y, centerZ);
        if (!BELOW_PLAYER_PREDICATE.test(chunk.getBlockState(mutable)) || !this.level.noCollision(EntityType.PLAYER.getAABB(Mth.floor(centerX) + 0.5D, y + 1, Mth.floor(centerZ) + 0.5D))) {
            return false;
        }

        int radius = Math.min(Config.instance().safetyCheckRadius, 2);
        if (radius > 0) {
            for (double x = centerX - radius; x <= centerX + radius; x++) {
                for (double z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (x != centerX || z != centerZ) {
                        BlockState state = chunk.getBlockState(mutable.set(x, y, z));
                        if (state.isAir() || !SURROUNDING_BLOCK_PREDICATE.test(state) || !SURROUNDING_BLOCK_PREDICATE.test(chunk.getBlockState(mutable.move(Direction.UP)))) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private int getY(LevelChunk chunk, double x, double z) {
        if (!this.level.dimensionType().hasCeiling()) {
            return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        }

        final double bottomY = chunk.getMinBuildHeight();
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, this.level.getLogicalHeight(), z);

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
            return this.isBiomeValid(this.level.getBiome(new BlockPos(this.x, 128, this.z)));
        }
        return false;
    }

    private boolean isBiomeValid(Holder<Biome> biome) {
        ResourceKey<Biome> key = this.level.registryAccess().registryOrThrow(Registries.BIOME).getResourceKey(biome.value()).orElse(null);
        return key != null
                && !biome.is(BiomeTags.IS_BEACH)
                && !biome.is(BiomeTags.IS_OCEAN)
                && !biome.is(BiomeTags.IS_DEEP_OCEAN)
                && !biome.is(BiomeTags.IS_RIVER)
                && key != Biomes.THE_END
                && key != Biomes.SMALL_END_ISLANDS
                && key != Biomes.THE_VOID;
    }

    private int nextRandomValue(int center) {
        return Mth.nextInt(RANDOM, center - this.radius, center + this.radius);
    }

    private int nextRandomValueWithMinimum(int center) {
        return RANDOM.nextBoolean() ? Mth.nextInt(RANDOM, center + this.minRadius, center + this.radius) : Mth.nextInt(RANDOM, center - this.radius, center - this.minRadius);
    }
}
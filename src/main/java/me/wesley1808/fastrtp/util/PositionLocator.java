package me.wesley1808.fastrtp.util;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Random;
import java.util.function.Consumer;

public final class PositionLocator {
    private static final ObjectOpenHashSet<PositionLocator> LOCATORS = new ObjectOpenHashSet<>();
    private static final TicketType<ChunkPos> LOCATE = TicketType.create("locate", Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final Random RANDOM = new Random();
    private final ServerLevel level;
    private final int minRadius;
    private final int radius;
    private final int maxY;
    private final int centerX;
    private final int centerZ;
    private Consumer<Vec3> callback;
    private ChunkPos queuedPos;
    private long stopTime;
    private int attempts;
    private int x;
    private int z;

    public static void update() {
        for (PositionLocator locator : LOCATORS) {
            locator.tick();
        }
    }

    public PositionLocator(ServerLevel level, int radius, int minRadius) {
        this.level = level;
        this.maxY = level.getLogicalHeight();
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

        ChunkPos chunkPos = RANDOM.nextBoolean()
                ? new ChunkPos(this.nextRandomValueWithMinimum(this.centerX), this.nextRandomValue(this.centerZ))
                : new ChunkPos(this.nextRandomValue(this.centerX), this.nextRandomValueWithMinimum(this.centerZ));

        this.x = chunkPos.getMiddleBlockX();
        this.z = chunkPos.getMiddleBlockZ();

        if (this.isValid(new BlockPos(this.x, 128, this.z))) {
            this.queueChunk(chunkPos);
        } else {
            this.newPosition();
        }
    }

    private void queueChunk(ChunkPos pos) {
        this.level.getChunkSource().addRegionTicket(LOCATE, pos, 0, pos);
        this.queuedPos = pos;
        LOCATORS.add(this);
    }

    private void onChunkLoaded(LevelChunk chunk) {
        LOCATORS.remove(this);

        if (chunk == null) {
            this.callback.accept(null);
            return;
        }

        this.findSafePositionInChunk(chunk, this.x, this.z);
    }

    private void findSafePositionInChunk(LevelChunk chunk, final int centerX, final int centerZ) {
        for (int x = centerX - 6; x <= centerX + 5; x++) {
            for (int z = centerZ - 6; z <= centerZ + 5; z++) {
                int y = this.getY(chunk, x, z);
                if (this.isSafe(chunk, x, y, z)) {
                    this.callback.accept(new Vec3(Mth.floor(x) + 0.5D, y, Mth.floor(z) + 0.5D));
                    return;
                }
            }
        }

        this.newPosition();
    }

    private boolean isSafe(ChunkAccess chunk, double x, int y, double z) {
        BlockPos pos = new BlockPos(x, y - 1, z);
        Material material = chunk.getBlockState(pos).getMaterial();

        return pos.getY() <= this.maxY
                && !material.isLiquid() && material != Material.FIRE && material != Material.BAMBOO && material != Material.CACTUS
                && this.level.noCollision(EntityType.PLAYER.getAABB(Mth.floor(x) + 0.5D, y, Mth.floor(z) + 0.5D));
    }

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

    private int nextRandomValue(int center) {
        return Mth.nextInt(RANDOM, center - this.radius, center + this.radius);
    }

    private int nextRandomValueWithMinimum(int center) {
        return RANDOM.nextBoolean() ? Mth.nextInt(RANDOM, center + this.minRadius, center + this.radius) : Mth.nextInt(RANDOM, center - this.radius, center - this.minRadius);
    }

    private int getY(ChunkAccess chunk, double x, double z) {
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z) + 1;
    }
}
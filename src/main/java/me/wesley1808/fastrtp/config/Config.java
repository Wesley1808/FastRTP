package me.wesley1808.fastrtp.config;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;
import java.util.Set;

public class Config {
    protected static Config instance = new Config();
    public boolean requirePermission = false;
    public boolean useCurrentWorld = false;
    public boolean useStrictTeleportCheck = false;
    public String defaultDimension = "minecraft:overworld";
    public int radius = -1;
    public int minRadius = 0;
    public int safetyCheckRadius = 1;
    public int cooldown = 30;

    public ReferenceOpenHashSet<ResourceKey<Biome>> blackListedBiomes = new ReferenceOpenHashSet<>(Set.of(
            Biomes.THE_END,
            Biomes.SMALL_END_ISLANDS,
            Biomes.THE_VOID
    ));

    public ReferenceArrayList<TagKey<Biome>> blackListedBiomeTags = new ReferenceArrayList<>(List.of(
            BiomeTags.IS_BEACH,
            BiomeTags.IS_OCEAN,
            BiomeTags.IS_DEEP_OCEAN,
            BiomeTags.IS_RIVER
    ));

    public String messageRtpFail = "&c[✖] Could not find a safe location!";
    public String messageRtpSuccess = "&3Teleported to &a${x} ${y} ${z} &3in &a${world}";
    public String messageRtpSearching = "&eSearching for a safe location...";
    public String messageRtpFound = "&eFound a safe location in ${seconds} seconds";
    public String messageOnCooldown = "&c[✖] &6Please wait &e${seconds} &6seconds before using the RTP again!";
    public String messageRtpBackSuccess = "&3Teleported back to your last random teleport!";
    public String messageRtpBackFail = "&c[✖] You don't have any recent random teleports.";
    public String messageTpCancelled = "&c[✖] Teleportation was cancelled.";
    public String messageTpSecondsLeft = "&eTeleporting in ${seconds} seconds...";

    public static Config instance() {
        return instance;
    }
}

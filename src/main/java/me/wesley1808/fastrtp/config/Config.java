package me.wesley1808.fastrtp.config;

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

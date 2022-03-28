package me.wesley1808.fastrtp.config;

public class Config {
    protected static Config instance = new Config();
    public boolean requirePermission = false;
    public boolean useCurrentWorld = false;
    public String defaultDimension = "minecraft:overworld";
    public int radius = -1;
    public int minRadius = 0;
    public int safetyCheckRadius = 1;
    public int cooldown = 60;

    public String messageRtpFail = "&cCould not find a safe location!";
    public String messageRtpSuccess = "&3Teleported to &a#X #Y #Z &3in &a#WORLD";
    public String messageRtpSearching = "&eSearching for a safe location...";
    public String messageOnCooldown = "&cYou are on cooldown for #SECONDS seconds.";
    public String messageRtpBackSuccess = "&3Teleported back to your last random teleport!";
    public String messageRtpBackFail = "&cYou don't have any recent random teleports.";
    public String messageTpCancelled = "&cTeleportation was cancelled.";
    public String messageTpSecondsLeft = "&eTeleporting in #SECONDS seconds...";

    public static Config instance() {
        return instance;
    }
}

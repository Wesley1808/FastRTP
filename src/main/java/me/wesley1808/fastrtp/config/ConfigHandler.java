package me.wesley1808.fastrtp.config;

import me.wesley1808.fastrtp.FastRTP;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ConfigHandler {
    private static final File DIR = FabricLoader.getInstance().getConfigDir().toFile();
    private static final File CONFIG = new File(DIR, "fast-rtp.json");

    public static void save() {
        if (!DIR.exists()) {
            DIR.mkdirs();
        }

        try (var writer = new FileWriter(CONFIG, StandardCharsets.UTF_8)) {
            writer.write(Json.INSTANCE.toJson(Config.instance));
        } catch (IOException e) {
            FastRTP.LOGGER.error("Failed to save config!", e);
        }
    }

    public static void load() {
        if (CONFIG.exists()) {
            try (var reader = new FileReader(CONFIG, StandardCharsets.UTF_8)) {
                Config.instance = Json.INSTANCE.fromJson(reader, Config.class);
            } catch (IOException e) {
                FastRTP.LOGGER.error("Failed to load config!", e);
            }
        } else {
            save();
        }
    }
}

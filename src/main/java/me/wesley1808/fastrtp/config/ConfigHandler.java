package me.wesley1808.fastrtp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.wesley1808.fastrtp.FastRTP;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ConfigHandler {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final File DIR = FabricLoader.getInstance().getConfigDir().toFile();
    private static final File CONFIG = new File(DIR, "fast-rtp.json");

    public static void save() {
        if (!DIR.exists()) {
            DIR.mkdirs();
        }

        try (var writer = new FileWriter(CONFIG, StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(Config.instance));
        } catch (IOException e) {
            FastRTP.LOGGER.error("Failed to save config!", e);
        }
    }

    public static void load() {
        if (CONFIG.exists()) {
            try (var reader = new FileReader(CONFIG, StandardCharsets.UTF_8)) {
                Config.instance = GSON.fromJson(reader, Config.class);
            } catch (IOException e) {
                FastRTP.LOGGER.error("Failed to load config!", e);
            }
        }
    }
}

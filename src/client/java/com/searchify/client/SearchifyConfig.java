package com.searchify.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class SearchifyConfig {

    public enum DisplayMode {
        ANIMATION, TEXTURE, COLOR, OUTLINE, PULSE, GHOST
    }

    public static boolean isEnabled = true;
    public static boolean autoLock = false;
    public static String savedSearchQuery = "";
    public static boolean searchInsideContainers = true;
    public static String searchKeybind = "key.keyboard.g";

    public static DisplayMode displayMode = DisplayMode.ANIMATION;
    public static int animationSpeed = 30;
    public static int highlightColor = 0x00FF00;
    public static int ghostAlpha = 70;
    public static int pulseScale = 125;

    public static boolean enableChests = true;
    public static boolean enableBarrels = true;
    public static boolean enableEnderChests = true;
    public static boolean enableTrappedChests = true;
    public static boolean enableCopperChests = true;
    public static boolean enableShulkerBoxes = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("searchify.json").toFile();
    private static final Logger LOGGER = LoggerFactory.getLogger("Searchify");

    private static class ConfigData {
        boolean isEnabled = true;
        boolean autoLock = false;
        String savedSearchQuery = "";
        boolean searchInsideContainers = true;
        String searchKeybind = "key.keyboard.g";

        DisplayMode displayMode = DisplayMode.ANIMATION;
        int animationSpeed = 30;
        int ghostAlpha = 70;
        int pulseScale = 125;
        String highlightColor = "#00FF00";

        boolean enableChests = true;
        boolean enableBarrels = true;
        boolean enableEnderChests = true;
        boolean enableTrappedChests = true;
        boolean enableCopperChests = true;
        boolean enableShulkerBoxes = true;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    isEnabled = data.isEnabled;
                    autoLock = data.autoLock;
                    searchInsideContainers = data.searchInsideContainers;
                    if (data.savedSearchQuery != null) savedSearchQuery = data.savedSearchQuery;
                    if (data.searchKeybind != null) searchKeybind = data.searchKeybind;

                    if (data.displayMode != null) displayMode = data.displayMode;
                    animationSpeed = Math.max(1, Math.min(100, data.animationSpeed));
                    ghostAlpha = Math.max(0, Math.min(100, data.ghostAlpha));
                    pulseScale = Math.max(100, Math.min(200, data.pulseScale));

                    enableChests = data.enableChests;
                    enableBarrels = data.enableBarrels;
                    enableEnderChests = data.enableEnderChests;
                    enableTrappedChests = data.enableTrappedChests;
                    enableCopperChests = data.enableCopperChests;
                    enableShulkerBoxes = data.enableShulkerBoxes;

                    try {
                        if (data.highlightColor != null) {
                            highlightColor = Integer.parseInt(data.highlightColor.replace("#", ""), 16);
                        }
                    } catch (NumberFormatException e) {
                        highlightColor = 0x00FF00;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to read Searchify config!", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        ConfigData data = new ConfigData();
        data.isEnabled = isEnabled;
        data.autoLock = autoLock;
        data.searchInsideContainers = searchInsideContainers;
        data.savedSearchQuery = savedSearchQuery;
        data.searchKeybind = searchKeybind;

        data.displayMode = displayMode;
        data.animationSpeed = animationSpeed;
        data.ghostAlpha = ghostAlpha;
        data.pulseScale = pulseScale;
        data.highlightColor = String.format("#%06X", (0xFFFFFF & highlightColor));

        data.enableChests = enableChests;
        data.enableBarrels = enableBarrels;
        data.enableEnderChests = enableEnderChests;
        data.enableTrappedChests = enableTrappedChests;
        data.enableCopperChests = enableCopperChests;
        data.enableShulkerBoxes = enableShulkerBoxes;

        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save Searchify config!", e);
        }
    }
}
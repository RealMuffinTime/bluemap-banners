/**
 * Taken from https://github.com/AV306/chathook/blob/main/src/main/java/me/av306/chathook/config/ConfigManager.java
 * But that should work out, shouldn't it? :)
 *  - RealMuffinTime
 */

package dev.nincodedo.bluemapbanners.manager;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Hashtable;

public class ConfigManager {
    private final Hashtable<Config, String> config = new Hashtable<>();
    private final String configFilePath;
    private static ConfigManager configManager;

    public ConfigManager() {
        this.configFilePath = FabricLoader.getInstance().getConfigDir().resolve("bluemap-banners/bluemap-banners.properties").toString();

        this.initialConfigFile();
        this.readConfigFile();
        this.updateConfigFile(false);
        configManager = this;
    }

    public static ConfigManager getInstance() {
        return configManager;
    }

    private void readConfigFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(this.configFilePath))) {
            // Iterate over each line in the file
            for (String line : reader.lines().toArray(String[]::new)) {
                if (line.startsWith("#") || line.isEmpty()) continue;

                // Split it by the equal sign (.properties format)
                String[] entry = line.split("=");
                try {
                    // Try adding the entry into the hashmap
                    this.config.put(Config.fromKey(entry[0].trim()), entry[1].trim());
                }
                catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                    BlueMapBanners.LOGGER.error("Invalid config line: {}", line);
                }
            }
        }
        catch (IOException ioe) {
            BlueMapBanners.LOGGER.error("IOException while reading config file: {}", ioe.getMessage());
        }
    }

    public void updateConfigFile(boolean force) {
        try {
            // read the current file and save found configs
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader fileIn = new BufferedReader(new FileReader(this.configFilePath));
            Hashtable<Config, String> configsFound = new Hashtable<>();
            while ((line = fileIn.readLine()) != null) {
                String key = line.split("=")[0].trim();
                if (line.startsWith("#") || line.isEmpty()) {
                    stringBuilder.append(line).append("\n");
                } else {
                    try {
                        Config config = Config.fromKey(key);
                        configsFound.put(config, "");
                        if (force) {
                            stringBuilder.append(config.getKey()).append("=").append(this.config.get(config)).append("\n");
                        } else {
                            stringBuilder.append(line).append("\n");
                        }
                    } catch (IllegalArgumentException e) {
                        BlueMapBanners.LOGGER.error("Invalid config line: {}", line);
                        stringBuilder.append("#").append(line).append("\n");
                    }
                }
            }
            fileIn.close();

            // add missing config lines
            for (Config config : Config.values()) {
                if (!configsFound.containsKey(config)) {
                    stringBuilder.append(config.getKey()).append("=").append(config.getDefaultValue()).append("\n");
                }
            }

            // write the file with new configs
            BufferedWriter fileOut = new BufferedWriter(new FileWriter(this.configFilePath));
            fileOut.write(stringBuilder.toString());
            fileOut.close();

        } catch (IOException ioe) {
            BlueMapBanners.LOGGER.error("IOException while writing config file: {}", ioe.getMessage());
        }
    }

    public void initialConfigFile() {
        // Create the config folder if not exist
        try {
            Files.createDirectories(FabricLoader.getInstance().getConfigDir());
        } catch (IOException e) {
            BlueMapBanners.LOGGER.error("IOException while creating config directory: {}", e.getMessage());
        }

        // Create a standard configuration if the file does not exist
        File file = new File(this.configFilePath);
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.configFilePath))) {
                StringBuilder string = new StringBuilder("""
                        # +-------------------------------------------------------+
                        # | BlueMap Banners main config file                      |
                        # |   Modify this file to change BlueMap Banners settings |
                        # +-------------------------------------------------------+""");

                string.append("\n\n\n");

                for (Config config : Config.values()) {
                    string.append(config.getKey()).append("=").append(config.getDefaultValue()).append("\n");
                }

                writer.write(string.toString(), 0, string.length());
            } catch (IOException ioe) {
                BlueMapBanners.LOGGER.error("IOException while writing initial config file: {}", ioe.getMessage());
            }
        }
    }

    public String getConfig(Config config) {
        if (this.config.get(config) == null)
            return config.getDefaultValue();
        return this.config.get(config);
    }

    public Boolean getBoolConfig(Config config) {
        return Boolean.parseBoolean(getConfig(config));
    }

    public int getIntConfig(Config config) {
        try {
            return Integer.parseInt(getConfig(config));
        } catch (NumberFormatException e) {
            BlueMapBanners.LOGGER.warn("Invalid config value for {}: {}", config.getKey(), getConfig(config));
            return Integer.parseInt(config.getDefaultValue());
        }
    }

    public void setConfig(Config config, String value) {
        this.config.put(config, value);
        updateConfigFile(true);
    }

    public void setConfig(Config config, boolean value) {
        this.config.put(config, String.valueOf(value));
        updateConfigFile(true);
    }

    public void setConfig(Config config, int value) {
        this.config.put(config, String.valueOf(value));
        updateConfigFile(true);
    }
}
/**
 * Taken from https://github.com/AV306/chathook/blob/main/src/main/java/me/av306/chathook/config/ConfigManager.java
 * But that should work out, shouldn't it? :)
 *  - RealMuffinTime
 */

package dev.nincodedo.bluemapbanners;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Hashtable;

public class ConfigManager
{
    private final Hashtable<String, String> config = new Hashtable<>();
    private final Hashtable<String, String> defaultConfig = new Hashtable<>();
    private final String configFilePath;
    private static ConfigManager configManager;

    public ConfigManager()
    {
        this.configFilePath = FabricLoader.getInstance().getConfigDir().resolve( "bluemap-banners/bluemap-banners.properties" ).toString();

        // Initialize the hashtable with default values
        defaultConfig.put("notifyPlayerOnBannerPlace", "true");
        defaultConfig.put("notifyPlayerOnMarkerAdd", "true");
        defaultConfig.put("notifyPlayerOnMarkerRemove", "true");
        defaultConfig.put("notifyGlobalOnMarkerRemove", "false");
        defaultConfig.put("markerAddInstantOnBannerPlace", "false");
        defaultConfig.put("markerMaxViewDistance", "10000000");
        defaultConfig.put("bluemapUrl", "https://your-url-to-bluemap.com/");

        this.initialConfigFile();
        this.readConfigFile();
        this.updateConfigFile();
        configManager = this;
    }

    public static ConfigManager getInstance() {
        return configManager;
    }

    private void readConfigFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(this.configFilePath))) {
            // Iterate over each line in the file
            for (String line : reader.lines().toArray(String[]::new)) {
                if ( line.startsWith( "#" ) || line.isEmpty() ) continue;

                // Split it by the equals sign (.properties format)
                String[] entry = line.split( "=" );
                try {
                    // Try adding the entry into the hashmap
                    this.config.put( entry[0].trim(), entry[1].trim() );
                }
                catch (IndexOutOfBoundsException oobe) {
                    BlueMapBanners.LOGGER.error( "Invalid config line: {}", line );
                }
            }
        }
        catch (IOException ioe) {
            BlueMapBanners.LOGGER.error( "IOException while reading config file: {}", ioe.getMessage() );
        }
    }

    public void updateConfigFile() {
        try {
            // read the current file and save found configs
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader fileIn = new BufferedReader(new FileReader(this.configFilePath));
            Hashtable<String, String> configsFound = new Hashtable<>();
            while ((line = fileIn.readLine()) != null) {
                String key = line.split("=")[0].trim();
                if (line.startsWith( "#" ) || line.isEmpty()) {
                    stringBuilder.append(line).append("\n");
                } else if (this.defaultConfig.containsKey(key)) {
                    configsFound.put(key, "");
                    stringBuilder.append(line).append("\n");
                } else {
                    stringBuilder.append("#").append(line).append("\n");
                }
            }
            fileIn.close();

            // add missing config lines
            for (String key : this.defaultConfig.keySet()) {
                if (!configsFound.containsKey(key)) {
                    stringBuilder.append(key).append("=").append(this.defaultConfig.get(key)).append("\n");
                }
            }

            // write the file with new configs
            BufferedWriter fileOut = new BufferedWriter(new FileWriter( this.configFilePath ) );
            fileOut.write(stringBuilder.toString());
            fileOut.close();

        } catch (IOException ioe) {
            BlueMapBanners.LOGGER.error( "IOException while writing config file: {}", ioe.getMessage() );
        }
    }

    public void initialConfigFile() {
        // Create the config folder if not exist
        try {
            Files.createDirectories(FabricLoader.getInstance().getConfigDir());
        } catch (IOException e) {
            BlueMapBanners.LOGGER.error( "IOException while creating config directory: {}", e.getMessage() );
        }
        // Create standard configuration if file does not exist
        File file = new File(this.configFilePath);
        if (!file.exists()) {
            try ( BufferedWriter writer = new BufferedWriter( new FileWriter( this.configFilePath ) ) )
            {
                StringBuilder string = new StringBuilder("""
                        # +-------------------------------------------------------+
                        # | BlueMap Banners main config file                      |
                        # |   Modify this file to change BlueMap Banners settings |
                        # +-------------------------------------------------------+""");

                string.append("\n\n\n");

                for (String key : defaultConfig.keySet()) {
                    string.append(key).append("=").append(defaultConfig.get(key)).append("\n");
                }

                writer.write(string.toString(), 0, string.length() );
            } catch (IOException ioe) {
                BlueMapBanners.LOGGER.error( "IOException while writing initial config file: {}", ioe.getMessage() );
            }
        }
    }

    public String getConfig(String name) {
        if (this.config.get(name) == null) {
            if (this.defaultConfig.get(name) == null) {
                throw new IllegalArgumentException("Config name " + name + " not found.");
            }
            return this.defaultConfig.get(name);
        } else {
            return this.config.get(name);
        }
    }

    public Boolean getBoolConfig(String name) {
        return Boolean.parseBoolean(getConfig(name));
    }

    public int getIntConfig(String name) {
        return Integer.parseInt(getConfig(name));
    }
}
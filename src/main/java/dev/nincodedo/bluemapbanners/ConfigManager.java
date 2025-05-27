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
    private final String configFilePath;
    private static ConfigManager configManager;

    public ConfigManager()
    {
        this.configFilePath = FabricLoader.getInstance().getConfigDir().resolve( "bluemap-banners/bluemap-banners.properties" ).toString();
        this.initialConfigFile();
        this.readConfigFile();
        configManager = this;
    }

    public static ConfigManager getInstance() {
        return configManager;
    }

    private void readConfigFile()
    {
        try ( BufferedReader reader = new BufferedReader( new FileReader( this.configFilePath ) ) )
        {
            // Iterate over each line in the file
            for ( String line : reader.lines().toArray( String[]::new ) )
            {
                if ( line.startsWith( "#" ) || line.isEmpty() ) continue;

                // Split it by the equals sign (.properties format)
                String[] entry = line.split( "=" );
                try
                {
                    // Try adding the entry into the hashmap
                    this.config.put( entry[0].trim(), entry[1].trim() );
                }
                catch ( IndexOutOfBoundsException oobe )
                {
                    BlueMapBanners.LOGGER.error( "Invalid config line: {}", line );
                }

            }
        }
        catch ( IOException ioe )
        {
            BlueMapBanners.LOGGER.error( "IOException while reading config file: {}", ioe.getMessage() );
        }
    }

    public void saveConfigFile(String name, String value)
    {
        try {
            BufferedReader fileIn = new BufferedReader(new FileReader(this.configFilePath));

            String line;
            StringBuilder inputBuilder = new StringBuilder();
            while ((line = fileIn.readLine()) != null) {
                inputBuilder.append(line).append("\n");
            }
            fileIn.close();

            // replace line in string
            String outputStr = inputBuilder.toString().replace(name + "=" + config.get(name), name + "=" + value);

            // write the new string with the replaced line
            BufferedWriter fileOut = new BufferedWriter(new FileWriter( this.configFilePath ) );
            fileOut.write(outputStr);
            fileOut.close();

        } catch (IOException ioe) {
            BlueMapBanners.LOGGER.error( "IOException while writing config file: {}", ioe.getMessage() );
        }
    }

    public void initialConfigFile() {
        // Create config folder if not exist
        try {
            Files.createDirectories(FabricLoader.getInstance().getConfigDir());
        } catch (IOException e) {
            BlueMapBanners.LOGGER.error( "IOException while creating config directory: {}", e.getMessage() );
        }
        // Create standard configuration if file does not exist
        File f = new File(this.configFilePath);
        if (!f.exists()) {
            try ( BufferedWriter writer = new BufferedWriter( new FileWriter( this.configFilePath ) ) )
            {
                String string = """ 
                            # +-------------------------------------------------------+
                            # | BlueMap Banners main config file                      |
                            # |   Modify this file to change BlueMap Banners settings |
                            # +-------------------------------------------------------+

                            notify_player_on_banner_place=true
                            notify_player_on_marker_add=true
                            notify_player_on_marker_remove=true
                            notify_global_on_marker_remove=false
                            maxDistance=10000000
                            bluemap_url=https://your-url-to-bluemap.com/
                            """;

                writer.write( string, 0, string.length() );
            } catch (IOException ioe) {
                BlueMapBanners.LOGGER.error( "IOException while writing initial config file: {}", ioe.getMessage() );
            }
        }
    }

    public String getConfig(String name )
    {
        return this.config.get( name );
    }

    public  Boolean getBoolConfig(String name) {
        return Boolean.parseBoolean(this.config.get(name));
    }

    public void setConfig(String name, String value )
    {
        saveConfigFile(name, value);
        this.config.put(name, value);
    }

    public void setConfig(String name, boolean value )
    {
        saveConfigFile(name, String.valueOf(value));
        this.config.put(name, String.valueOf(value));
    }
}
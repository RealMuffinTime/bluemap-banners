package dev.nincodedo.bluemapbanners.storage;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.manager.Config;
import dev.nincodedo.bluemapbanners.manager.ConfigManager;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

public class JdbcStorage implements Storage {

    Connection connection;

    public JdbcStorage() {
        ConfigManager configManager = ConfigManager.getInstance();

        // Try directly loading the specified class
        boolean directLoad = true;
        try {
            Class.forName(configManager.getConfig(Config.STORAGE_JDBC_ENTRY));
            BlueMapBanners.LOGGER.debug("Class directly found");
        } catch (ClassNotFoundException e) {
            directLoad = false;
        }

        // Use URLClassLoader to load the specified JAR, if not already found in ClassLoader
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        if (!directLoad) {
            try {
                if (!Files.exists(Path.of(configManager.getConfig(Config.STORAGE_JDBC_JAR)))) {
                    throw new MalformedURLException(configManager.getConfig(Config.STORAGE_JDBC_JAR));
                }

                URL jarUrl = Path.of(configManager.getConfig(Config.STORAGE_JDBC_JAR)).toUri().toURL();

                loader = new URLClassLoader(new URL[]{jarUrl}, ClassLoader.getSystemClassLoader());
            }  catch (MalformedURLException exception) {
                BlueMapBanners.LOGGER.error("Invalid JDBC URL specified in config: {}", exception.getMessage());
            }
        }

        // Initialize JDBC driver
        try {
            if (!directLoad) {
                Class<?> clazz = Class.forName(configManager.getConfig(Config.STORAGE_JDBC_ENTRY), true, loader);

                Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();

                DriverManager.registerDriver(new DriverShim(driver));
            } else {
                Class<?> clazz = Class.forName(configManager.getConfig(Config.STORAGE_JDBC_ENTRY));

                Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();

                DriverManager.registerDriver(driver);
            }
        }catch (ClassNotFoundException exception) {
            BlueMapBanners.LOGGER.error("JDBC JAR not found: {}", exception.getMessage());
        } catch (NoSuchMethodException exception) {
            BlueMapBanners.LOGGER.error("JDBC JAR does not contain a public constructor: {}", exception.getMessage());
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to initialize JDBC driver: {}", exception.getMessage());
        }

        // Connect to database
        try {
            Properties properties = new Properties();
            properties.setProperty("user", configManager.getConfig(Config.STORAGE_JDBC_USER));
            properties.setProperty("password", configManager.getConfig(Config.STORAGE_JDBC_PASSWORD));

            connection = DriverManager.getConnection(configManager.getConfig(Config.STORAGE_JDBC_URL), properties);
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to connect to JDBC database: {}", exception.getMessage());
        }

        try {
            assert connection != null;
            DatabaseMetaData meta = connection.getMetaData();

            try (ResultSet result = meta.getTables(null, null, "bluemap_banners", null)) {
                boolean tableExists = false;
                while (result.next()) {
                    if (result.getString("TABLE_NAME").equalsIgnoreCase("bluemap_banners")) {
                        tableExists = true;
                        break;
                    }
                }

                if (!tableExists) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("""
                            CREATE TABLE bluemap_banners (
                                version CHAR(20)
                            )
                        """);
                    }

                    try (var insertStatement = connection.prepareStatement("INSERT INTO bluemap_banners (version) VALUES (?)")) {
                        insertStatement.setString(1, BlueMapBanners.VERSION);
                        insertStatement.executeUpdate();
                    }
                }
            }

            try (ResultSet result = meta.getTables(null, null, "bluemap_banners_players", null)) {
                boolean tableExists = false;
                while (result.next()) {
                    if (result.getString("TABLE_NAME").equalsIgnoreCase("bluemap_banners_players")) {
                        tableExists = true;
                        break;
                    }
                }
                if (!tableExists) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("""
                            CREATE TABLE bluemap_banners_players (
                                uuid CHAR(36) PRIMARY KEY,
                                max_markers INT NOT NULL DEFAULT -1
                            )
                        """);
                    }
                }
            }

            try (ResultSet result = meta.getTables(null, null, "bluemap_banners_markers", null)) {
                boolean tableExists = false;
                while (result.next()) {
                    if (result.getString("TABLE_NAME").equalsIgnoreCase("bluemap_banners_markers")) {
                        tableExists = true;
                        break;
                    }
                }
                if (!tableExists) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("""
                            CREATE TABLE bluemap_banners_markers (
                                world VARCHAR(255) NOT NULL,
                                pos VARCHAR(255) NOT NULL,
                                offset_x DOUBLE NOT NULL,
                                offset_y DOUBLE NOT NULL,
                                offset_z DOUBLE NOT NULL,
                                player_uuid CHAR(36) NOT NULL,
                                near_icon VARCHAR(255),
                                far_icon VARCHAR(255),
                                marker_text VARCHAR(255),
                                added_at VARCHAR(255),
                                PRIMARY KEY (world, pos),
                                FOREIGN KEY (player_uuid) REFERENCES bluemap_banners_players(uuid)
                            )
                        """);
                    }
                }
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to create tables: {}", exception.getMessage());
        }
    }

    @Override
    public void addMarker(String world, String pos, Vec3 offset, UUID player, String nearIcon, String farIcon, String text) {
        try {
            // Ensure player exists in players table to satisfy foreign key constraint
            String playerQuery = connection.getMetaData().getURL().startsWith("jdbc:sqlite") ?
                    "INSERT OR IGNORE INTO bluemap_banners_players (uuid) VALUES (?)" :
                    "INSERT IGNORE INTO bluemap_banners_players (uuid) VALUES (?)";
            try (var playerStatement = connection.prepareStatement(playerQuery)) {
                playerStatement.setString(1, player.toString());
                playerStatement.executeUpdate();
            }

            String query = connection.getMetaData().getURL().startsWith("jdbc:sqlite") ?
                    """
                    INSERT OR REPLACE INTO bluemap_banners_markers (world, pos, offset_x, offset_y, offset_z, player_uuid, near_icon, far_icon, marker_text, added_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """ :
                    """
                    REPLACE INTO bluemap_banners_markers (world, pos, offset_x, offset_y, offset_z, player_uuid, near_icon, far_icon, marker_text, added_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (var statement = connection.prepareStatement(query)) {
                statement.setString(1, world);
                statement.setString(2, pos);
                statement.setDouble(3, offset.x());
                statement.setDouble(4, offset.y());
                statement.setDouble(5, offset.z());
                statement.setString(6, player.toString());
                statement.setString(7, nearIcon);
                statement.setString(8, farIcon);
                statement.setString(9, text);
                statement.setString(10, java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to add marker: {}", exception.getMessage());
        }
    }

    @Override
    public boolean existsMarker(String world, String pos) {
        try (var statement = connection.prepareStatement("SELECT 1 FROM bluemap_banners_markers WHERE world = ? AND pos = ?")) {
            statement.setString(1, world);
            statement.setString(2, pos);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to check marker existence: {}", exception.getMessage());
        }
        return false;
    }

    @Override
    public void removeMarker(String world, String pos) {
        try (var statement = connection.prepareStatement("DELETE FROM bluemap_banners_markers WHERE world = ? AND pos = ?")) {
            statement.setString(1, world);
            statement.setString(2, pos);
            statement.executeUpdate();
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to remove marker: {}", exception.getMessage());
        }
    }

    @Override
    public void addWorld(String world) {
        // No-op for JDBC as worlds are just strings in the markers table
    }

    @Override
    public boolean existsWorld(String world) {
        try (var statement = connection.prepareStatement("SELECT 1 FROM bluemap_banners_markers WHERE world = ? LIMIT 1")) {
            statement.setString(1, world);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to check world existence: {}", exception.getMessage());
        }
        return false;
    }

    @Override
    public void removeWorld(String world) {
        try (var statement = connection.prepareStatement("DELETE FROM bluemap_banners_markers WHERE world = ?")) {
            statement.setString(1, world);
            statement.executeUpdate();
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to remove world: {}", exception.getMessage());
        }
    }

    @Override
    public void setMaxMarkerByPlayer(UUID player, int maxMarkers) {
        try {
            String query = connection.getMetaData().getURL().startsWith("jdbc:sqlite") ?
                    """
                    INSERT OR REPLACE INTO bluemap_banners_players (uuid, max_markers)
                    VALUES (?, ?)
                    """ :
                    """
                    REPLACE INTO bluemap_banners_players (uuid, max_markers)
                    VALUES (?, ?)
                    """;
            try (var statement = connection.prepareStatement(query)) {
                statement.setString(1, player.toString());
                statement.setInt(2, maxMarkers);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to set max markers for player: {}", exception.getMessage());
        }
    }

    @Override
    public int getMaxMarkerByPlayer(UUID player) {
        try (var statement = connection.prepareStatement("SELECT max_markers FROM bluemap_banners_players WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("max_markers");
                }
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to get max markers for player: {}", exception.getMessage());
        }
        return -1;
    }

    @Override
    public int getMarkerCountByPlayer(UUID player) {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM bluemap_banners_markers WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to get marker count for player: {}", exception.getMessage());
        }
        return 0;
    }

    @Override
    public int getMarkerCountByPlayerByWorld(UUID player, String world) {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM bluemap_banners_markers WHERE player_uuid = ? AND world = ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, world);
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to get marker count for player in world: {}", exception.getMessage());
        }
        return 0;
    }

    @Override
    public MarkerSet getGeneratedMarkerSet(String world) {
        MarkerSet set = new MarkerSet("BlueMap Banners");

        try (var statement = connection.prepareStatement("SELECT * FROM bluemap_banners_markers WHERE world = ?")) {
            statement.setString(1, world);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String pos = resultSet.getString("pos");
                    double offsetX = resultSet.getDouble("offset_x");
                    double offsetY = resultSet.getDouble("offset_y");
                    double offsetZ = resultSet.getDouble("offset_z");
                    String farIcon = resultSet.getString("far_icon");
                    String text = resultSet.getString("marker_text");

                    Double[] posArray = Arrays.stream(pos.split(", ")).map(Double::parseDouble).toArray(Double[]::new);
                    Vector3d position = new Vector3d(posArray[0], posArray[1], posArray[2]);
                    position = position.add(
                            0.5 + offsetX,
                            0.5 + (offsetX == 0.0 && offsetY == 0.0 && offsetZ == 0.0 ? -0.5 : 0.5),
                            0.5 + offsetZ
                    );

                    POIMarker marker = new POIMarker(text, position);
                    marker.setDetail(text);

                    BlueMapAPI.getInstance().ifPresent(api -> {
                        for (var map : api.getMaps()) {
                            marker.setIcon(
                                    map.getAssetStorage().getAssetUrl(farIcon),
                                    12,
                                    offsetX == 0.0 && offsetY == 0.0 && offsetZ == 0.0 ? 32 : 0
                            );
                            break;
                        }
                    });

                    marker.setMaxDistance(ConfigManager.getInstance().getIntConfig(Config.MARKER_MAX_VIEW_DISTANCE));

                    set.put(pos, marker);
                }
            }
        } catch (SQLException exception) {
            BlueMapBanners.LOGGER.error("Failed to get marker set for world: {}", exception.getMessage());
        }

        return set;
    }
}

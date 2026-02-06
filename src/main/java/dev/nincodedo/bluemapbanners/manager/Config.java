package dev.nincodedo.bluemapbanners.manager;

public enum Config {
    NOTIFY_PLAYER_ON_BANNER_PLACE("notifyPlayerOnBannerPlace", "true"),
    NOTIFY_PLAYER_ON_MARKER_ADD("notifyPlayerOnMarkerAdd", "true"),
    NOTIFY_PLAYER_ON_MARKER_REMOVE("notifyPlayerOnMarkerRemove", "true"),
    NOTIFY_GLOBAL_ON_MARKER_REMOVE("notifyGlobalOnMarkerRemove", "false"),
    MARKER_ADD_INSTANT_ON_BANNER_PLACE("markerAddInstantOnBannerPlace", "false"),
    MARKER_ADD_WITH_ORIGINAL_NAME("markerAddWithOriginalName", "false"),
    MARKER_MAX_VIEW_DISTANCE("markerMaxViewDistance", "10000000"),
    BLUEMAP_URL("bluemapUrl", "https://your-url-to-bluemap.com/"),
    SEND_METRICS("sendMetrics", "true"),
    STORAGE_TYPE("storageType", "JSON"),
    STORAGE_JDBC_URL("storageJdbcUrl", "jdbc:mariadb://localhost:3306/bluemap-banners"),
    STORAGE_JDBC_USER("storageJdbcUser", "root"),
    STORAGE_JDBC_PASSWORD("storageJdbcPassword", "root"),
    STORAGE_JDBC_JAR("storageJdbcJar", "path/to/your/mariadb-java-client-3.0.7.jar"),
    STORAGE_JDBC_ENTRY("storageJdbcEntry", "org.mariadb.jdbc.Driver");

    private final String key;
    private final String defaultValue;

    Config(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return this.key;
    }

    public String getDefaultValue() {
        return this.defaultValue;
    }

    public static Config fromKey(String key) {
        for (Config config : Config.values()) {
            if (config.key.equalsIgnoreCase(key)) {
                return config;
            }
        }
        throw new IllegalArgumentException("Unknown key: " + key);
    }
}

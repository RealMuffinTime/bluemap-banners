package dev.nincodedo.bluemapbanners.storage;

import com.flowpowered.math.vector.Vector3d;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.manager.Config;
import dev.nincodedo.bluemapbanners.manager.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JsonStorage implements Storage {

    private static class Data {
        public String version;
        public Map<UUID, Integer> players = new HashMap<>();
        public Map<String, Map<String, Map<String, Object>>> worlds = new HashMap<>();
    }

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private Data data;

    private final File file = new File(FabricLoader.getInstance().getConfigDir().resolve("bluemap-banners/bluemap-banners-data.json").toUri());

    public JsonStorage() {
        try {
            data = gson.fromJson(new FileReader(file), Data.class);
        } catch (FileNotFoundException exception) {
            data = new Data();
            data.version = BlueMapBanners.VERSION;
        }
    }

    @Override
    public void addMarker(String world, String pos, Vec3 offset, UUID player, String nearIcon, String farIcon, String text) {

        if (!existsWorld(world)) {
            addWorld(world);
        }

        Map<String, Object> banner = new HashMap<>();
        banner.put("offset", new Double[]{offset.x(), offset.y(), offset.z()});
        banner.put("player", player.toString());
        banner.put("nearIcon", nearIcon);
        banner.put("farIcon", farIcon);
        banner.put("text", text);
        banner.put("when", ZonedDateTime.now(ZoneId.of("UTC")).toString());

        data.worlds.get(world).put(pos, banner);
        save();
    }

    @Override
    public boolean existsMarker(String world, String pos) {
        if (!data.worlds.containsKey(world)) {
            return false;
        }
        return data.worlds.get(world).containsKey(pos);
    }

    @Override
    public void removeMarker(String world, String pos) {
        if (!data.worlds.containsKey(world)) {
            return;
        }
        data.worlds.get(world).remove(pos);
        save();
    }

    @Override
    public void addWorld(String world) {
        data.worlds.put(world, new HashMap<>());
    }

    @Override
    public boolean existsWorld(String world) {
        return data.worlds.containsKey(world);
    }

    @Override
    public void removeWorld(String world) {
        data.worlds.remove(world);
    }

    @Override
    public void setMaxMarkerByPlayer(UUID player, int maxMarkers) {
        data.players.put(player, maxMarkers);
        save();
    }

    @Override
    public int getMaxMarkerByPlayer(UUID player) {
        if (data.players.containsKey(player)) {
            return data.players.get(player);
        }
        return -1;
    }

    @Override
    public int getMarkerCountByPlayer(UUID player) {
        int count = 0;
        for (String world : data.worlds.keySet()) {
            count += getMarkerCountByPlayerByWorld(player, world);
        }
        return count;
    }

    @Override
    public int getMarkerCountByPlayerByWorld(UUID player, String world) {
        int count = 0;
        for (String pos : data.worlds.get(world).keySet()) {
            if (data.worlds.get(world).get(pos).get("player").equals(player.toString())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public MarkerSet getGeneratedMarkerSet(String world) {
        if (!data.worlds.containsKey(world)) {
            addWorld(world);
        }

        MarkerSet set = new MarkerSet("BlueMap Banners");

        for (String pos : data.worlds.get(world).keySet()) {
            Map<String, Object> banner = data.worlds.get(world).get(pos);

            Double[] posArray = Arrays.stream(pos.split(", ")).map(Double::parseDouble).toArray(Double[]::new);
            Vector3d position = new Vector3d(posArray[0], posArray[1], posArray[2]);
            List<Double> offset = (List<Double>) banner.get("offset");
            position = position.add(
                    0.5 + offset.getFirst(),
                    0.5 + (offset.getFirst() == 0.0 && offset.get(1) == 0.0 && offset.getLast() == 0.0 ? -0.5 : 0.5),
                    0.5 + offset.getLast()
            );

            POIMarker marker = new POIMarker(banner.get("text").toString(), position);
            marker.setDetail(banner.get("text").toString());

            marker.setIcon(
                    BlueMapAPI.getInstance().get().getMaps().iterator().next().getAssetStorage().getAssetUrl(banner.get("farIcon").toString()),
                    12,
                    offset.getFirst() == 0.0 && offset.get(1) == 0.0 && offset.getLast() == 0.0 ? 32 : 0
            );

            marker.setMaxDistance(ConfigManager.getInstance().getIntConfig(Config.MARKER_MAX_VIEW_DISTANCE));

            set.put(pos, marker);
        }

        return set;
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (Exception exception) {
            BlueMapBanners.LOGGER.error("Failed to JSON data to file!", exception);
        }
    }
}

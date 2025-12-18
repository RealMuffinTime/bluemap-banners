package dev.nincodedo.bluemapbanners;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MarkerManager {

    private final String bannerMarkerSetId = "bluemap-banners";
    private static MarkerManager markerManager;

    public MarkerManager() {
        markerManager = this;
    }

    public static MarkerManager getInstance() {
        return markerManager;
    }

    public void loadMarkerSets() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            for (BlueMapWorld world : api.getWorlds()) {
                // Not the nicest with the names
                String fileName = FabricLoader.getInstance().getConfigDir().resolve(String.format("bluemap-banners/maps/%s.json", worldToString(world))).toString();
                try (FileReader reader = new FileReader(fileName)) {
                    MarkerSet markerSet = MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
                    world.getMaps().forEach(map -> map.getMarkerSets().put(bannerMarkerSetId, markerSet));
                } catch (FileNotFoundException ex) {
                    MarkerSet markerSet = MarkerSet.builder().label("BlueMap Banners").defaultHidden(false).toggleable(true).build();
                    world.getMaps().forEach(map -> map.getMarkerSets().put(bannerMarkerSetId, markerSet));
                } catch (IOException ex) {
                    BlueMapBanners.LOGGER.error(ex.getMessage(), ex);
                }
            }
        });
    }

    public void saveMarkerSet(BlueMapWorld world) {
        String fileName = FabricLoader.getInstance().getConfigDir().resolve(String.format("bluemap-banners/maps/%s.json", worldToString(world))).toString();
        BlueMapMap map = world.getMaps().iterator().next();
        map.getMarkerSets().forEach((id, markerSet) -> {
            if (id != null && id.equals(bannerMarkerSetId)) {
                try (FileWriter writer = new FileWriter(fileName)) {
                    MarkerGson.INSTANCE.toJson(markerSet, writer);
                } catch (IOException ex) {
                    BlueMapBanners.LOGGER.error(ex.getMessage(), ex);
                }
            }
        });
    }

    public boolean doesMarkerExist(BannerBlockEntity bannerBlockEntity) {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        BlueMapWorld world = api.getWorld(bannerBlockEntity.getLevel()).orElseThrow();
        for (BlueMapMap map : world.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(bannerMarkerSetId);
            if (set.get(bannerBlockEntity.getBlockPos().toShortString()) != null) {
                return true;
            }
        }
        return false;
    }

    public void removeMarker(BannerBlockEntity bannerBlockEntity) {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        BlueMapWorld world = api.getWorld(bannerBlockEntity.getLevel()).orElseThrow();
        for (BlueMapMap map : world.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(bannerMarkerSetId);
            set.remove(bannerBlockEntity.getBlockPos().toShortString());
        }
        saveMarkerSet(world);
    }

    public void addMarker(BannerBlockEntity bannerBlockEntity, String blockName) {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        BlueMapWorld world = api.getWorld(bannerBlockEntity.getLevel()).orElseThrow();
        for (BlueMapMap map : world.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(bannerMarkerSetId);
            Vec3 pos = bannerBlockEntity.getBlockPos().getCenter();
            var iconAddress = map.getAssetStorage().getAssetUrl(bannerBlockEntity.getBaseColor().name().toLowerCase() + ".png");
            int markerMaxViewDistance = ConfigManager.getInstance().getIntConfig("markerMaxViewDistance");
            POIMarker bannerMarker = POIMarker.builder().label(blockName).position(pos.x(), pos.y(), pos.z()).icon(iconAddress, 12, 32).maxDistance(markerMaxViewDistance).build();
            set.put(bannerBlockEntity.getBlockPos().toShortString(), bannerMarker);
        }
        saveMarkerSet(world);
    }

    private String worldToString(BlueMapWorld world) {
        return world.getId().replace("#minecraft:", "_").replace("_overworld", "");
    }
}

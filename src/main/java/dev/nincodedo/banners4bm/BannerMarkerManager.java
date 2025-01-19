package dev.nincodedo.banners4bm;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BannerMarkerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("BannerMarkerManager");

    private final String bannerMarkerSetId = "banners4bm";

    public void loadMarkerSets() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            for (BlueMapWorld world : api.getWorlds()) {
                // Not the nicest with the names
                String fileName = FabricLoader.getInstance().getConfigDir().resolve(String.format("banners4bm/maps/%s.json", world.getId().replace("#minecraft:", "_").replace("_overworld", ""))).toString();
                File markerSetsFile = new File(fileName);
                if (markerSetsFile.exists()) {
                    try (FileReader reader = new FileReader(fileName)) {
                        world.getMaps().forEach(map -> map.getMarkerSets().put(bannerMarkerSetId, MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class)));
                    } catch (IOException ex) {
                        LOGGER.error(ex.getMessage());
                    }
                } else {
                    world.getMaps().forEach(map -> map.getMarkerSets().put(bannerMarkerSetId, MarkerSet.builder().label("Map Banners").defaultHidden(false).toggleable(true).build()));
                }
            }
        });
    }

    public void saveMarkerSet(World mcWorld) {
        String fileName = FabricLoader.getInstance().getConfigDir().resolve(String.format("banners4bm/maps/%s.json", worldToString(mcWorld))).toString();
        BlueMapAPI.getInstance().flatMap(api -> api.getWorld(mcWorld)).ifPresent(world -> {
            BlueMapMap map = world.getMaps().iterator().next();
            map.getMarkerSets().forEach((id, markerSet) -> {
                if (id != null && id.equals(bannerMarkerSetId)) {
                    try (FileWriter writer = new FileWriter(fileName)) {
                        MarkerGson.INSTANCE.toJson(markerSet, writer);
                    } catch (IOException ex) {
                        LOGGER.error(ex.getMessage());
                    }
                }
            });
        });
    }

    public void removeMarker(BannerBlockEntity bannerBlockEntity) {
        toggleMarker(bannerBlockEntity, null);
    }

    public void toggleMarker(BannerBlockEntity bannerBlockEntity, BlockState blockState) {
        BlueMapAPI.getInstance().flatMap(blueMapAPI -> blueMapAPI.getWorld(bannerBlockEntity.getWorld())).ifPresent(blueMapWorld -> {
            blueMapWorld.getMaps().forEach(blueMapMap -> {
                var existingBannerMarkerSet = blueMapMap.getMarkerSets().get(bannerMarkerSetId);
                if (existingBannerMarkerSet == null) {
                    return;
                }
                var markerId = bannerBlockEntity.getPos().toShortString();
                var existingMarker = existingBannerMarkerSet.getMarkers().get(markerId);
                if (existingMarker != null) {
                    existingBannerMarkerSet.remove(markerId);
                } else if (blockState != null) {
                    String name;
                    if (bannerBlockEntity.getCustomName() != null) {
                        name = bannerBlockEntity.getCustomName().getString();
                    } else {
                        var blockTranslationKey = blockState.getBlock().getTranslationKey();
                        name = Text.translatable(blockTranslationKey).getString();
                    }
                    addMarker(bannerBlockEntity, name, existingBannerMarkerSet, blueMapMap);
                }
            });
        });
        saveMarkerSet(bannerBlockEntity.getWorld());
    }

    private void addMarker(BannerBlockEntity bannerBlockEntity, String blockName, MarkerSet markerSet, BlueMapMap map) {
        Vec3d pos = bannerBlockEntity.getPos().toCenterPos();
        var iconAddress = map.getAssetStorage().getAssetUrl(bannerBlockEntity.getColorForState().name().toLowerCase() + ".png");
        POIMarker bannerMarker = POIMarker.builder().label(blockName).position(pos.getX(), pos.getY(), pos.getZ()).icon(iconAddress, 0, 0).build();
        markerSet.put(bannerBlockEntity.getPos().toShortString(), bannerMarker);
    }

    // Pretty sure there is a better way, but I don't know it... Maybe you know?
    private String worldToString(World world) {
        String string = world.getRegistryKey().toString().replace("ResourceKey[minecraft:dimension / minecraft:", "").replace("]", "");
        if (string.equals("overworld")) {
            string = "";
        } else {
            string = "_" + string;
        }
        return  world.toString().replace("ServerLevel[", "").replace("]", "") + string;
    }
}

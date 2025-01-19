package dev.nincodedo.banners4bm;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BannerMarkerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("BannerMarkerManager");

    private final String markerJsonFileName = FabricLoader.getInstance().getConfigDir().resolve( "banners4bm/maps/overworld.json" ).toString();
    private final String markerSetLabel = "Map Banners";
    private final String bannerMarkerSetId = "overworldmapbanners";

    public void loadMarkers(ServerWorld overworld) {
        MarkerSet bannerMarkerSet = getMarkerSet();
        var optionalApi = BlueMapAPI.getInstance();
        if (bannerMarkerSet == null || optionalApi.isEmpty()) {
            return;
        }
        var api = optionalApi.get();

        api.getWorld(overworld).ifPresent(blueMapWorld -> blueMapWorld.getMaps().forEach(blueMapMap -> blueMapMap.getMarkerSets().put(bannerMarkerSetId, bannerMarkerSet)));
    }

    private MarkerSet getMarkerSet() {
        File markerFile = new File(markerJsonFileName);
        if (markerFile.exists()) {
            try (FileReader reader = new FileReader(markerJsonFileName)) {
                return MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
            } catch (IOException ex) {
                // handle io-exception
                ex.printStackTrace();
            }
        } else {
            return MarkerSet.builder().label(markerSetLabel).defaultHidden(false).toggleable(true).build();
        }
        return null;
    }

    public void saveMarkers() {
        BlueMapAPI.getInstance().ifPresent(blueMapAPI -> blueMapAPI.getMaps().forEach(blueMapMap -> blueMapMap.getMarkerSets().forEach((id, markerSet) -> {
            if (id != null && id.equals(bannerMarkerSetId)) {
                try (FileWriter writer = new FileWriter(markerJsonFileName)) {
                    MarkerGson.INSTANCE.toJson(markerSet, writer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        })));
    }

    public void removeMarker(BlockEntity blockEntity) {
        toggleMarker(null, blockEntity);
    }

    public void toggleMarker(BlockState blockState, BlockEntity blockEntity) {
        if (!(blockEntity instanceof BannerBlockEntity bannerBlockEntity)) {
            return;
        }
        BlueMapAPI.getInstance().flatMap(blueMapAPI -> blueMapAPI.getWorld(blockEntity.getWorld())).ifPresent(blueMapWorld -> {
            blueMapWorld.getMaps().forEach(blueMapMap -> {
                var existingBannerMarkerSet = blueMapMap.getMarkerSets().get(bannerMarkerSetId);
                if (existingBannerMarkerSet == null) {
                    return;
                }
                var markerId = blockEntity.getPos().toShortString();
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
                    addMarker(name, bannerBlockEntity, existingBannerMarkerSet, blueMapMap);
                }
            });
        });
    }

    private void addMarker(String blockName, BannerBlockEntity bannerBlockEntity, MarkerSet existingBannerMarkerSet, BlueMapMap blueMapMap) {
        var blockPos = bannerBlockEntity.getPos();
        var x = blockPos.toCenterPos().getX();
        var y = blockPos.toCenterPos().getY();
        var z = blockPos.toCenterPos().getZ();
        var iconAddress = blueMapMap.getAssetStorage().getAssetUrl(bannerBlockEntity.getColorForState().name().toLowerCase() + ".png");
        POIMarker bannerMarker = POIMarker.builder().label(blockName).position(x, y, z).icon(iconAddress, 0, 0).build();
        existingBannerMarkerSet.put(blockPos.toShortString(), bannerMarker);
    }
}

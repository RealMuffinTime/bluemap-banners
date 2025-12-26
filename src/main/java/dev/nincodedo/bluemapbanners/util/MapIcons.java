package dev.nincodedo.bluemapbanners.util;

import de.bluecolored.bluemap.api.BlueMapAPI;
import java.io.IOException;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import net.minecraft.world.item.DyeColor;

public class MapIcons {
    public void loadMapIcons(BlueMapAPI blueMapAPI) {
        blueMapAPI.getMaps().forEach(blueMapMap -> {
            var assetStorage = blueMapMap.getAssetStorage();
            for (var dyeColor : DyeColor.values()) {
                var iconName = dyeColor.name().toLowerCase() + ".png";
                try {
                    if (!assetStorage.assetExists(iconName)) {
                        try (var outStream = assetStorage.writeAsset(iconName);
                             var stream = BlueMapBanners.class.getResourceAsStream("/assets/bluemap-banners/icons/banners/" + iconName)) {
                            if (stream != null) {
                                BlueMapBanners.LOGGER.trace("Writing icon {} to map {}", iconName, blueMapMap);
                                outStream.write(stream.readAllBytes());
                            }
                        }
                    }
                } catch (IOException e) {
                    BlueMapBanners.LOGGER.error("Failed to create an icon for {}", iconName, e);
                }
            }
        });
    }
}

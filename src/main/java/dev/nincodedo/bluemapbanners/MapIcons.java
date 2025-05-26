package dev.nincodedo.bluemapbanners;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.minecraft.util.DyeColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

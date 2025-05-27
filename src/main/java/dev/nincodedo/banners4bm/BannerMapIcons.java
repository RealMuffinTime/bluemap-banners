package dev.nincodedo.banners4bm;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.minecraft.util.DyeColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BannerMapIcons {

    private static final Logger LOGGER = LoggerFactory.getLogger("BannerMapIcons");

    public void loadMapIcons(BlueMapAPI blueMapAPI) {
        blueMapAPI.getMaps().forEach(blueMapMap -> {
            var assetStorage = blueMapMap.getAssetStorage();
            for (var dyeColor : DyeColor.values()) {
                var iconName = dyeColor.name().toLowerCase() + ".png";
                try {
                    if (!assetStorage.assetExists(iconName)) {
                        try (var outStream = assetStorage.writeAsset(iconName);
                             var stream = Banners4BM.class.getResourceAsStream("/assets/banners4bm/icons/banners/" + iconName)) {
                            if (stream != null) {
                                LOGGER.trace("Writing icon {} to map {}", iconName, blueMapMap);
                                outStream.write(stream.readAllBytes());
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to create an icon for {}", iconName, e);
                }
            }
        });
    }
}

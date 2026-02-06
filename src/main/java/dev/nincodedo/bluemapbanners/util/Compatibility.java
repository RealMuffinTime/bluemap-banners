package dev.nincodedo.bluemapbanners.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.manager.MarkerManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Compatibility {

    private static final UUID DEFAULT_PLAYER_UUID = UUID.fromString("e195d3d7-e6dc-456e-8393-e2f90816a1af");

    /**
     * Apply compatibility fixes for old versions of BlueMap Banners
     */
    public static void init(MinecraftServer server) {
        importOldMarkers(server);
    }

    /**
     * Import old format marker data from config folder maps/* into the new storage format.
     * After importing a file, it is moved to maps-imported/*.
     */
    public static void importOldMarkers(MinecraftServer server) {
        MarkerManager markerManager = MarkerManager.getInstance();
        String mapsConfigFolder = FabricLoader.getInstance().getConfigDir().resolve("bluemap-banners/maps").toString();

        if (mapsConfigFolder.isBlank()) return;

        Path folder = Paths.get(mapsConfigFolder);
        if (!Files.isDirectory(folder)) return;

        Path importedFolder = folder.resolveSibling("maps-imported");
        try {
            Files.createDirectories(importedFolder);
        } catch (IOException e) {
            BlueMapBanners.LOGGER.error("Failed to create maps-imported directory: {}", e.getMessage());
            return;
        }

        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String worldId = path.getFileName().toString().replace(".json", "").replace("world_", "");
                        try (Reader reader = Files.newBufferedReader(path)) {
                            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                            if (root.has("markers")) {
                                JsonObject markers = root.getAsJsonObject("markers");
                                for (Map.Entry<String, JsonElement> entry : markers.entrySet()) {
                                    Integer[] position = Arrays.stream(entry.getKey().split(", ")).map(Integer::valueOf).toArray(Integer[]::new);

                                    ResourceKey<Level> resourceKey = null;
                                    switch (worldId) {
                                        case "world" -> resourceKey = Level.OVERWORLD;
                                        case "the_nether" -> resourceKey = Level.NETHER;
                                        case "the_end" -> resourceKey = Level.END;
                                    }

                                    if (resourceKey != null) {
                                        ResourceKey<Level> finalResourceKey = resourceKey;
                                        ServerLevel serverLevel = StreamSupport.stream(server.getAllLevels().spliterator(), false).filter(level -> level.dimension() == finalResourceKey).findFirst().orElse(null);

                                        BlockPos blockPos = new BlockPos(position[0], position[1], position[2]);

                                        assert serverLevel != null;
                                        BlockState blockState = serverLevel.getBlockState(blockPos);

                                        ChunkAccess chunk = serverLevel.getChunk(position[0] >> 4, position[2] >> 4);

                                        BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) chunk.getBlockEntity(blockPos);

                                        if (bannerBlockEntity != null) {
                                            GameProfile gameProfile = new GameProfile(DEFAULT_PLAYER_UUID, "It's MuffinTime.");

                                            Player player = new Player(serverLevel, gameProfile) {
                                                @Override
                                                public @Nullable GameType gameMode() {
                                                    return null;
                                                }
                                            };

                                            markerManager.addMarker(blockState, bannerBlockEntity, player);
                                        }
                                    }
                                }
                            }
                            // Move file to imported folder
                            Files.move(path, importedFolder.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                            BlueMapBanners.LOGGER.info("Imported and moved old marker file format: {}", path.getFileName());
                        } catch (IOException e) {
                            BlueMapBanners.LOGGER.error("Failed to import old marker file {} format: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            BlueMapBanners.LOGGER.error("Failed to list maps directory: {}", e.getMessage());
        }
    }
}

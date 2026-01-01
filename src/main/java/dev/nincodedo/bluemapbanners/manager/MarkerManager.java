package dev.nincodedo.bluemapbanners.manager;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.storage.JdbcStorage;
import dev.nincodedo.bluemapbanners.storage.JsonStorage;
import dev.nincodedo.bluemapbanners.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class MarkerManager {

    private final String bannerMarkerSetId = "bluemap-banners";
    private static MarkerManager markerManager;
    private Storage storage;

    public MarkerManager() {
        markerManager = this;
    }

    public static MarkerManager getInstance() {
        return markerManager;
    }

    public void loadMarkerSets() {
        switch (ConfigManager.getInstance().getConfig(Config.STORAGE_TYPE)) {
            case "JDBC" -> storage = new JdbcStorage();
            case "JSON" -> storage = new JsonStorage();
            default -> {
                storage = new JsonStorage();
                BlueMapBanners.LOGGER.error("Invalid storage type specified: {}", ConfigManager.getInstance().getConfig(Config.STORAGE_TYPE));
                BlueMapBanners.LOGGER.warn("Using default storage type: JSON");
            }
        };

        BlueMapAPI.getInstance().ifPresent(api -> {
            for (BlueMapWorld world : api.getWorlds()) {
                world.getMaps().forEach(map -> map.getMarkerSets().put(bannerMarkerSetId, storage.getGeneratedMarkerSet(world.getId())));
            }
        });
    }

    public boolean doesMarkerExist(BannerBlockEntity bannerBlockEntity) {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        BlueMapWorld world = api.getWorld(bannerBlockEntity.getLevel()).orElseThrow();
        return storage.existsMarker(world.getId(), bannerBlockEntity.getBlockPos().toShortString());
    }

    public void removeMarker(BannerBlockEntity bannerBlockEntity) {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        BlueMapWorld world = api.getWorld(bannerBlockEntity.getLevel()).orElseThrow();
        for (BlueMapMap map : world.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(bannerMarkerSetId);
            set.remove(bannerBlockEntity.getBlockPos().toShortString());
        }
        storage.removeMarker(world.getId(), bannerBlockEntity.getBlockPos().toShortString());
    }

    public String getMarkerName(BlockState blockState, BannerBlockEntity bannerBlockEntity) {
        String name;
        if (bannerBlockEntity.getCustomName() != null) {
            name = bannerBlockEntity.getCustomName().getString();
        } else {
            if (bannerBlockEntity.components().keySet().contains(DataComponents.ITEM_NAME))
                name = Objects.requireNonNull(bannerBlockEntity.components().get(DataComponents.ITEM_NAME)).getString();
            else {
                String blockTranslationKey = blockState.getBlock().getDescriptionId();
                name = Component.translatable(blockTranslationKey).getString();
            }
        }
        return name;
    }

    public Vec3 getMarkerOffset(BlockState blockState) {
        Vec3 offset = Vec3.ZERO;
        if (blockState.getBlock() instanceof WallBannerBlock) {
            Direction facing = blockState.getValue(WallBannerBlock.FACING);
            offset = switch (facing) {
                case Direction.NORTH -> offset.add(0, 0, 0.5);
                case Direction.EAST -> offset.add(-0.5, 0, 0);
                case Direction.SOUTH -> offset.add(0, 0, -0.5);
                case Direction.WEST -> offset.add(0.5, 0, 0);
                default -> offset;
            };
        }
        return offset;
    }

    public void addMarker(BlockState blockState, BannerBlockEntity bannerBlockEntity, Player player) {
        BlueMapAPI api = BlueMapAPI.getInstance().orElseThrow();
        BlueMapWorld world = api.getWorld(bannerBlockEntity.getLevel()).orElseThrow();

        Vec3 offset = getMarkerOffset(blockState);
        String text = getMarkerName(blockState, bannerBlockEntity);
        Vec3 pos = bannerBlockEntity.getBlockPos().getCenter();
        int markerMaxViewDistance = ConfigManager.getInstance().getIntConfig(Config.MARKER_MAX_VIEW_DISTANCE);

        for (BlueMapMap map : world.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(bannerMarkerSetId);
            var iconAddress = map.getAssetStorage().getAssetUrl(bannerBlockEntity.getBaseColor().name().toLowerCase() + ".png");

            pos = pos.add(
                    offset.x,
                    (Objects.equals(offset, Vec3.ZERO) ? -0.5 : 0.5),
                    offset.z
            );

            POIMarker bannerMarker = POIMarker.builder()
                    .label(text)
                    .detail(text)
                    .position(pos.x(), pos.y(), pos.z())
                    .icon(iconAddress, 12, Objects.equals(offset, Vec3.ZERO) ? 32 : 0)
                    .maxDistance(markerMaxViewDistance).build();
            set.put(bannerBlockEntity.getBlockPos().toShortString(), bannerMarker);
        }

        storage.addMarker(world.getId(), bannerBlockEntity.getBlockPos().toShortString(), offset, player.getUUID(), "", bannerBlockEntity.getBaseColor().name().toLowerCase() + ".png", text);
    }
}

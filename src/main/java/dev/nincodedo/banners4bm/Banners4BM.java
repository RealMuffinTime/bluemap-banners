package dev.nincodedo.banners4bm;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Banners4BM implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Banners4BM");
    BannerMarkerManager bannerMarkerManager;
    BannerMapIcons bannerMapIcons;
    ConfigManager configManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Starting Banners4BM");

        String mapsConfigFolder = FabricLoader.getInstance().getConfigDir().resolve( "banners4bm/maps" ).toString();
        new File(mapsConfigFolder).mkdirs();

        bannerMarkerManager = new BannerMarkerManager();
        bannerMapIcons = new BannerMapIcons();
        configManager = new ConfigManager();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isRemote()) {
                return;
            }
            BlueMapAPI.onEnable(blueMapAPI -> {
                LOGGER.info("Integrating Banners4BM into BlueMap");
                bannerMarkerManager.loadMarkerSets();
                bannerMapIcons.loadMapIcons(blueMapAPI);
            });
        });
        BlueMapAPI.onDisable(blueMapAPI -> {
            LOGGER.info("Stopping Banners4BM");
        });
        UseBlockCallback.EVENT.register(this::useBlock);
    }

    private ActionResult useBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (player.isSpectator() || player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (player.getMainHandStack().isOf(Items.FILLED_MAP) || player.getOffHandStack().isOf(Items.FILLED_MAP)) {
            var blockState = world.getBlockState(hitResult.getBlockPos());
            var blockEntity = world.getBlockEntity(hitResult.getBlockPos());
            if (blockState != null && blockState.isIn(BlockTags.BANNERS)) {
                LOGGER.trace("Toggling marker at {}", hitResult.getBlockPos());
                bannerMarkerManager.toggleMarker((BannerBlockEntity) blockEntity, blockState);
            }
        }
        return ActionResult.PASS;
    }
}

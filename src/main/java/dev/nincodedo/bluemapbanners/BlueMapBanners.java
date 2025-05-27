package dev.nincodedo.bluemapbanners;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.NoSuchElementException;

public class BlueMapBanners implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("BlueMap Banners");
    MarkerManager markerManager;
    MapIcons bannerMapIcons;
    ConfigManager configManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Starting BlueMap Banners");

        String mapsConfigFolder = FabricLoader.getInstance().getConfigDir().resolve( "bluemap-banners/maps" ).toString();
        new File(mapsConfigFolder).mkdirs();

        markerManager = new MarkerManager();
        bannerMapIcons = new MapIcons();
        configManager = new ConfigManager();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isRemote()) {
                return;
            }
            BlueMapAPI.onEnable(blueMapAPI -> {
                LOGGER.info("Integrating BlueMap Banners into BlueMap");
                markerManager.loadMarkerSets();
                bannerMapIcons.loadMapIcons(blueMapAPI);
            });
        });
        BlueMapAPI.onDisable(blueMapAPI -> {
            LOGGER.info("Stopping BlueMap Banners");
        });
        UseBlockCallback.EVENT.register(this::useBlock);
        PlayerBlockBreakEvents.BEFORE.register(this::breakBlock);
    }

    private ActionResult useBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (player.isSpectator() || player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (player.getMainHandStack().isOf(Items.MAP) || player.getOffHandStack().isOf(Items.MAP) || player.getMainHandStack().isOf(Items.FILLED_MAP) || player.getOffHandStack().isOf(Items.FILLED_MAP)) {
            BlockState blockState = world.getBlockState(hitResult.getBlockPos());
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(hitResult.getBlockPos());
            if (blockState != null && blockState.isIn(BlockTags.BANNERS) && bannerBlockEntity != null) {
                try {
                    if (!markerManager.doesMarkerExist(bannerBlockEntity)) {
                        String name;
                        if (bannerBlockEntity.getCustomName() != null) {
                            name = bannerBlockEntity.getCustomName().getString();
                        } else {
                            String blockTranslationKey = blockState.getBlock().getTranslationKey();
                            name = Text.translatable(blockTranslationKey).getString();
                        }
                        markerManager.addMarker(bannerBlockEntity, name);
                        if (ConfigManager.getInstance().getBoolConfig("notify_player_on_marker_add"))
                            player.sendMessage(Text.literal("[BlueMap Banners] You added a marker to the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemap_url")))).withUnderline(true))).append(Text.of(".")), false);
                    } else {
                        markerManager.removeMarker(bannerBlockEntity);
                        if (ConfigManager.getInstance().getBoolConfig("notify_player_on_marker_remove"))
                            player.sendMessage(Text.literal("[BlueMap Banners] You removed a marker from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemap_url")))).withUnderline(true))).append(Text.of(".")), false);
                    }
                } catch (NoSuchElementException ignored) {}
            }
        }
        return ActionResult.PASS;
    }

    private boolean breakBlock(World world, PlayerEntity playerEntity, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity) {
        if (blockState.isIn(BlockTags.BANNERS)) {
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(blockPos);
            try {
                if (bannerBlockEntity != null && markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notify_player_on_marker_remove")) {
                        playerEntity.sendMessage(Text.literal("[BlueMap Banners] You removed a marker from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemap_url")))).withUnderline(true))).append(Text.of(".")), false);
                    }
                }
            } catch (NoSuchElementException ignored) {}
        }
        return true;
    }
}

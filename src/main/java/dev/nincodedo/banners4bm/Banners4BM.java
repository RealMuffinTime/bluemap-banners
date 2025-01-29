package dev.nincodedo.banners4bm;

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
import java.util.NoSuchElementException;

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
                    if (!bannerMarkerManager.doesMarkerExist(bannerBlockEntity)) {
                        String name;
                        if (bannerBlockEntity.getCustomName() != null) {
                            name = bannerBlockEntity.getCustomName().getString();
                        } else {
                            String blockTranslationKey = blockState.getBlock().getTranslationKey();
                            name = Text.translatable(blockTranslationKey).getString();
                        }
                        bannerMarkerManager.addMarker(bannerBlockEntity, name);
                        if (ConfigManager.getInstance().getBoolConfig("notify_player_on_marker_add"))
                            player.sendMessage(Text.literal("[Banner4BM] You added a marker to the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ConfigManager.getInstance().getConfig("bluemap_url"))).withUnderline(true))).append(Text.of(".")), false);
                    } else {
                        bannerMarkerManager.removeMarker(bannerBlockEntity);
                        if (ConfigManager.getInstance().getBoolConfig("notify_player_on_marker_remove"))
                            player.sendMessage(Text.literal("[Banner4BM] You removed a marker from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ConfigManager.getInstance().getConfig("bluemap_url"))).withUnderline(true))).append(Text.of(".")), false);
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
                if (bannerBlockEntity != null && bannerMarkerManager.doesMarkerExist(bannerBlockEntity)) {
                    bannerMarkerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notify_player_on_marker_remove")) {
                        playerEntity.sendMessage(Text.literal("[Banner4BM] You removed a marker from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ConfigManager.getInstance().getConfig("bluemap_url"))).withUnderline(true))).append(Text.of(".")), false);
                    }
                }
            } catch (NoSuchElementException ignored) {}
        }
        return true;
    }
}

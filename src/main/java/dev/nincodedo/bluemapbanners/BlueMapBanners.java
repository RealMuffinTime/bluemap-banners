package dev.nincodedo.bluemapbanners;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class BlueMapBanners implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("BlueMap Banners");
    MarkerManager markerManager;
    MapIcons bannerMapIcons;
    ConfigManager configManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Starting BlueMap Banners");

        String mapsConfigFolder = FabricLoader.getInstance().getConfigDir().resolve("bluemap-banners/maps").toString();
        new File(mapsConfigFolder).mkdirs();

        Compatibility.init(mapsConfigFolder);
        markerManager = new MarkerManager();
        bannerMapIcons = new MapIcons();
        configManager = new ConfigManager();

        CommandRegistrationCallback.EVENT.register(this::registerCommands);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isPublished()) {
                return;
            }
            BlueMapAPI.onEnable(blueMapAPI -> {
                LOGGER.info("Integrating BlueMap Banners into BlueMap");
                markerManager.loadMarkerSets();
                bannerMapIcons.loadMapIcons(blueMapAPI);
            });
        });
        BlueMapAPI.onDisable(blueMapAPI -> LOGGER.info("Stopping BlueMap Banners"));
        UseBlockCallback.EVENT.register(this::useBlock);
        PlayerBlockBreakEvents.BEFORE.register(this::breakBlock);
    }

    private InteractionResult useBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isSpectator() ||
                (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) ||
                ConfigManager.getInstance().getBoolConfig("markerAddInstantOnBannerPlace")) {
            return InteractionResult.PASS;
        }
        if (player.getMainHandItem().is(Items.MAP) || player.getOffhandItem().is(Items.MAP) || player.getMainHandItem().is(Items.FILLED_MAP) || player.getOffhandItem().is(Items.FILLED_MAP)) {
            BlockState blockState = world.getBlockState(hitResult.getBlockPos());
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(hitResult.getBlockPos());
            if (blockState != null && blockState.is(BlockTags.BANNERS) && bannerBlockEntity != null) {
                try {
                    if (!markerManager.doesMarkerExist(bannerBlockEntity)) {
                        addMarkerWithName(blockState, bannerBlockEntity, markerManager);
                        if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerAdd"))
                            player.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerAdd", getWebText()), false);
                    } else {
                        markerManager.removeMarker(bannerBlockEntity);
                        if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerRemove"))
                            player.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerRemove", getWebText()), false);
                    }
                } catch (NoSuchElementException ignored) {}
            }
        }
        return InteractionResult.PASS;
    }

    public static void addMarkerWithName(BlockState blockState, BannerBlockEntity bannerBlockEntity, MarkerManager markerManager) {
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
        markerManager.addMarker(bannerBlockEntity, name);
    }

    public static Component getWebText() {
        URI uri;
        String bluemapUrl = ConfigManager.getInstance().getConfig("bluemapUrl");
        String invalidUrl = "https://invalid-url-in-config.com/";
        try {
            // Minecraft only works with an http or https scheme
            uri = new URI(bluemapUrl);
            if (!Objects.equals(uri.getScheme(), "http") && !Objects.equals(uri.getScheme(), "https")) {
                throw new URISyntaxException(bluemapUrl, "Invalid URI Scheme");
            }
        } catch (URISyntaxException exception) {
            LOGGER.error("Could not validate bluemapUrl:", exception);
            return Component.translatable("bluemapbanners.webMap").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(invalidUrl))).withHoverEvent(new HoverEvent.ShowText(Component.literal(invalidUrl))).withUnderlined(true));
        }
        return Component.translatable("bluemapbanners.webMap").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(uri)).withHoverEvent(new HoverEvent.ShowText(Component.literal(bluemapUrl))).withUnderlined(true));
    }

    private boolean breakBlock(Level world, Player playerEntity, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity) {
        if (blockState.is(BlockTags.BANNERS)) {
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(blockPos);
            try {
                if (bannerBlockEntity != null && markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerRemove"))
                        playerEntity.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerRemove", BlueMapBanners.getWebText()), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
        return true;
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        final LiteralCommandNode<CommandSourceStack> baseCommand = dispatcher
                .register(literal("bluemapbanners")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "bluemapbanners.commands.status",
                                    configManager.getConfig("notifyPlayerOnBannerPlace"),
                                    configManager.getConfig("notifyPlayerOnMarkerAdd"),
                                    configManager.getConfig("notifyPlayerOnMarkerRemove"),
                                    configManager.getConfig("notifyGlobalOnMarkerRemove"),
                                    configManager.getConfig("markerAddInstantOnBannerPlace"),
                                    configManager.getConfig("markerAddWithOriginalName"),
                                    configManager.getConfig("markerMaxViewDistance"),
                                    configManager.getConfig("bluemapUrl")
                            ), false);
                            return 1;
                        })

                        .then(literal("notifyPlayerOnBannerPlace").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnBannerPlace.status",
                                                    configManager.getConfig("notifyPlayerOnBannerPlace")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyPlayerOnBannerPlace")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.true"), false);
                                                configManager.setConfig("notifyPlayerOnBannerPlace", true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyPlayerOnBannerPlace")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.false"), false);
                                                configManager.setConfig("notifyPlayerOnBannerPlace", false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("notifyPlayerOnMarkerAdd").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnMarkerAdd.status",
                                                    configManager.getConfig("notifyPlayerOnMarkerAdd")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyPlayerOnMarkerAdd")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.true"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerAdd", true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyPlayerOnMarkerAdd")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.false"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerAdd", false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("notifyPlayerOnMarkerRemove").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnMarkerRemove.status",
                                                    configManager.getConfig("notifyPlayerOnMarkerRemove")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyPlayerOnMarkerRemove")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.true"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerRemove", true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyPlayerOnMarkerRemove")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.false"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerRemove", false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("notifyGlobalOnMarkerRemove").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyGlobalOnMarkerRemove.status",
                                                    configManager.getConfig("notifyGlobalOnMarkerRemove")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyGlobalOnMarkerRemove")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.true"), false);
                                                configManager.setConfig("notifyGlobalOnMarkerRemove", true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyGlobalOnMarkerRemove")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.false"), false);
                                                configManager.setConfig("notifyGlobalOnMarkerRemove", false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("markerAddInstantOnBannerPlace").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerAddInstantOnBannerPlace.status",
                                                    configManager.getConfig("markerAddInstantOnBannerPlace")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("markerAddInstantOnBannerPlace")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.true"), false);
                                                configManager.setConfig("markerAddInstantOnBannerPlace", true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("markerAddInstantOnBannerPlace")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.false"), false);
                                                configManager.setConfig("markerAddInstantOnBannerPlace", false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )
                        
                        .then(literal("markerAddWithOriginalName").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerAddWithOriginalName.status",
                                                    configManager.getConfig("markerAddWithOriginalName")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("markerAddWithOriginalName")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.true"), false);
                                                configManager.setConfig("markerAddWithOriginalName", true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("markerAddWithOriginalName")) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.false"), false);
                                                configManager.setConfig("markerAddWithOriginalName", false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("markerMaxViewDistance").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerMaxViewDistance.status",
                                                    configManager.getConfig("markerMaxViewDistance")
                                            ), false);
                                            return 1;
                                        })

                                        .then(argument("markerMaxViewDistance", greedyString()) .executes(context -> {
                                            configManager.setConfig("markerMaxViewDistance", Integer.parseInt(StringArgumentType.getString(context, "markerMaxViewDistance")));
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerMaxViewDistance.update",
                                                    configManager.getConfig("markerMaxViewDistance")
                                            ), false);

                                            return 1;
                                        }))
                        )

                        .then(literal("bluemapUrl").executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.bluemapUrl.status",
                                                    configManager.getConfig("bluemapUrl")
                                            ), false);
                                            return 1;
                                        })

                                        .then(argument("bluemapUrl", greedyString()) .executes(context -> {
                                            configManager.setConfig("bluemapUrl", StringArgumentType.getString(context, "bluemapUrl"));
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.bluemapUrl.update",
                                                    configManager.getConfig("bluemapUrl")
                                            ), false);

                                            return 1;
                                        }))
                        )
                );

        dispatcher.register(literal("bb")
                .redirect(baseCommand)
                .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "bluemapbanners.commands.status",
                            configManager.getConfig("notifyPlayerOnBannerPlace"),
                            configManager.getConfig("notifyPlayerOnMarkerAdd"),
                            configManager.getConfig("notifyPlayerOnMarkerRemove"),
                            configManager.getConfig("notifyGlobalOnMarkerRemove"),
                            configManager.getConfig("markerAddInstantOnBannerPlace"),
                            configManager.getConfig("markerAddWithOriginalName"),
                            configManager.getConfig("markerMaxViewDistance"),
                            configManager.getConfig("bluemapUrl")
                    ), false);
                    return 1;
                })
        );
    }
}

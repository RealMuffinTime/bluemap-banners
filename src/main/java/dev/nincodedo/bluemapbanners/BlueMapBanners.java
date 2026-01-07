package dev.nincodedo.bluemapbanners;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.bluecolored.bluemap.api.BlueMapAPI;
import dev.nincodedo.bluemapbanners.manager.Config;
import dev.nincodedo.bluemapbanners.manager.ConfigManager;
import dev.nincodedo.bluemapbanners.manager.MarkerManager;
import dev.nincodedo.bluemapbanners.util.Compatibility;
import dev.nincodedo.bluemapbanners.util.MapIcons;
import dev.nincodedo.bluemapbanners.util.Metrics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.permissions.Permissions;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class BlueMapBanners implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("BlueMap Banners");
    public static String VERSION = FabricLoader.getInstance().getModContainer("bluemap-banners").orElseThrow().getMetadata().getVersion().getFriendlyString();
    MarkerManager markerManager;
    MapIcons bannerMapIcons;
    ConfigManager configManager;
    Timer daemonTimer;

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onInitialize() {
        LOGGER.info("Loading BlueMap Banners v{}", VERSION);

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

        daemonTimer = new Timer("BlueMap-Banners-Plugin-DaemonTimer", true);
        TimerTask metricsTask = new TimerTask() {
            @Override
            public void run() {
                if (configManager.getBoolConfig(Config.SEND_METRICS)) {
                    Metrics.sendReport("fabric", VERSION, SharedConstants.getCurrentVersion().name());
                }
            }
        };
        daemonTimer.scheduleAtFixedRate(metricsTask, TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(30));
    }

    @SuppressWarnings("SameReturnValue")
    private InteractionResult useBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isSpectator() ||
                (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) ||
                ConfigManager.getInstance().getBoolConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE)) {
            return InteractionResult.PASS;
        }
        if (player.getMainHandItem().is(Items.MAP) || player.getOffhandItem().is(Items.MAP) || player.getMainHandItem().is(Items.FILLED_MAP) || player.getOffhandItem().is(Items.FILLED_MAP)) {
            BlockState blockState = world.getBlockState(hitResult.getBlockPos());
            BannerBlockEntity bannerBlockEntity = null;
            try {
                bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(hitResult.getBlockPos());
            } catch (ClassCastException ignored) {}
            if (blockState.is(BlockTags.BANNERS) && bannerBlockEntity != null) {
                try {
                    if (!markerManager.doesMarkerExist(bannerBlockEntity)) {
                        markerManager.addMarker(blockState, bannerBlockEntity, player);
                        if (ConfigManager.getInstance().getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD))
                            player.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerAdd", getWebText()), false);
                    } else {
                        markerManager.removeMarker(bannerBlockEntity);
                        if (ConfigManager.getInstance().getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE))
                            player.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerRemove", getWebText()), false);
                    }
                } catch (NoSuchElementException ignored) {}
            }
        }
        return InteractionResult.PASS;
    }

    public static Component getWebText() {
        URI uri;
        String bluemapUrl = ConfigManager.getInstance().getConfig(Config.BLUEMAP_URL);
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
                    if (ConfigManager.getInstance().getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE))
                        playerEntity.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerRemove", BlueMapBanners.getWebText()), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
        return true;
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        final LiteralCommandNode<CommandSourceStack> baseCommand = dispatcher
                .register(literal("bluemapbanners")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "bluemapbanners.commands.status",
                                    configManager.getConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE),
                                    configManager.getConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD),
                                    configManager.getConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE),
                                    configManager.getConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE),
                                    configManager.getConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE),
                                    configManager.getConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME),
                                    configManager.getConfig(Config.MARKER_MAX_VIEW_DISTANCE),
                                    configManager.getConfig(Config.BLUEMAP_URL),
                                    configManager.getConfig(Config.SEND_METRICS)
                            ), false);
                            return 1;
                        })

                        .then(literal(Config.NOTIFY_PLAYER_ON_BANNER_PLACE.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnBannerPlace.status",
                                                    configManager.getConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.true"), false);
                                                configManager.setConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.false"), false);
                                                configManager.setConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal(Config.NOTIFY_PLAYER_ON_MARKER_ADD.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnMarkerAdd.status",
                                                    configManager.getConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.true"), false);
                                                configManager.setConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.false"), false);
                                                configManager.setConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnMarkerRemove.status",
                                                    configManager.getConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.true"), false);
                                                configManager.setConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.false"), false);
                                                configManager.setConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.notifyGlobalOnMarkerRemove.status",
                                                    configManager.getConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.true"), false);
                                                configManager.setConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.false"), false);
                                                configManager.setConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerAddInstantOnBannerPlace.status",
                                                    configManager.getConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.true"), false);
                                                configManager.setConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.false"), false);
                                                configManager.setConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )
                        
                        .then(literal(Config.MARKER_ADD_WITH_ORIGINAL_NAME.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerAddWithOriginalName.status",
                                                    configManager.getConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.true"), false);
                                                configManager.setConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.false"), false);
                                                configManager.setConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.markerAddWithOriginalName.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal(Config.MARKER_MAX_VIEW_DISTANCE.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerMaxViewDistance.status",
                                                    configManager.getConfig(Config.MARKER_MAX_VIEW_DISTANCE)
                                            ), false);
                                            return 1;
                                        })

                                        .then(argument(Config.MARKER_MAX_VIEW_DISTANCE.getKey(), greedyString()) .executes(context -> {
                                            configManager.setConfig(Config.MARKER_MAX_VIEW_DISTANCE, Integer.parseInt(StringArgumentType.getString(context, Config.MARKER_MAX_VIEW_DISTANCE.getKey())));
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.markerMaxViewDistance.update",
                                                    configManager.getConfig(Config.MARKER_MAX_VIEW_DISTANCE)
                                            ), false);

                                            return 1;
                                        }))
                        )

                        .then(literal(Config.BLUEMAP_URL.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.bluemapUrl.status",
                                                    configManager.getConfig(Config.BLUEMAP_URL)
                                            ), false);
                                            return 1;
                                        })

                                        .then(argument(Config.BLUEMAP_URL.getKey(), greedyString()) .executes(context -> {
                                            configManager.setConfig(Config.BLUEMAP_URL, StringArgumentType.getString(context, Config.BLUEMAP_URL.getKey()));
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.bluemapUrl.update",
                                                    configManager.getConfig(Config.BLUEMAP_URL)
                                            ), false);

                                            return 1;
                                        }))
                        )

                        .then(literal(Config.SEND_METRICS.getKey()).executes(context -> {
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "bluemapbanners.commands.sendMetrics.status",
                                                    configManager.getConfig(Config.SEND_METRICS)
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig(Config.SEND_METRICS)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.sendMetrics.true"), false);
                                                configManager.setConfig(Config.SEND_METRICS, true);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.sendMetrics.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig(Config.SEND_METRICS)) {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.sendMetrics.false"), false);
                                                configManager.setConfig(Config.SEND_METRICS, false);
                                            } else {
                                                context.getSource().sendSuccess(() -> Component.translatable(
                                                        "bluemapbanners.commands.sendMetrics.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )
                );

        dispatcher.register(literal("bb")
                .redirect(baseCommand)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "bluemapbanners.commands.status",
                            configManager.getConfig(Config.NOTIFY_PLAYER_ON_BANNER_PLACE),
                            configManager.getConfig(Config.NOTIFY_PLAYER_ON_MARKER_ADD),
                            configManager.getConfig(Config.NOTIFY_PLAYER_ON_MARKER_REMOVE),
                            configManager.getConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE),
                            configManager.getConfig(Config.MARKER_ADD_INSTANT_ON_BANNER_PLACE),
                            configManager.getConfig(Config.MARKER_ADD_WITH_ORIGINAL_NAME),
                            configManager.getConfig(Config.MARKER_MAX_VIEW_DISTANCE),
                            configManager.getConfig(Config.BLUEMAP_URL),
                            configManager.getConfig(Config.SEND_METRICS)
                    ), false);
                    return 1;
                })
        );
    }
}

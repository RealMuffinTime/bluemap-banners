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
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

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
            if (!server.isRemote()) {
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

    private ActionResult useBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (player.isSpectator() ||
                (player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()) ||
                ConfigManager.getInstance().getBoolConfig("markerAddInstantOnBannerPlace")) {
            return ActionResult.PASS;
        }
        if (player.getMainHandStack().isOf(Items.MAP) || player.getOffHandStack().isOf(Items.MAP) || player.getMainHandStack().isOf(Items.FILLED_MAP) || player.getOffHandStack().isOf(Items.FILLED_MAP)) {
            BlockState blockState = world.getBlockState(hitResult.getBlockPos());
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(hitResult.getBlockPos());
            if (blockState != null && blockState.isIn(BlockTags.BANNERS) && bannerBlockEntity != null) {
                try {
                    if (!markerManager.doesMarkerExist(bannerBlockEntity)) {
                        addMarkerWithName(blockState, bannerBlockEntity, markerManager);
                        if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerAdd"))
                            player.sendMessage(Text.literal("[BlueMap Banners] You added a marker to the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemapUrl")))).withUnderline(true))).append(Text.of(".")), false);
                    } else {
                        markerManager.removeMarker(bannerBlockEntity);
                        if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerRemove"))
                            player.sendMessage(Text.literal("[BlueMap Banners] You removed a marker from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemapUrl")))).withUnderline(true))).append(Text.of(".")), false);
                    }
                } catch (NoSuchElementException ignored) {}
            }
        }
        return ActionResult.PASS;
    }

    public static void addMarkerWithName(BlockState blockState, BannerBlockEntity bannerBlockEntity, MarkerManager markerManager) {
        String name;
        if (bannerBlockEntity.getCustomName() != null) {
            name = bannerBlockEntity.getCustomName().getString();
        } else {
            if (bannerBlockEntity.getComponents().getTypes().contains(DataComponentTypes.ITEM_NAME))
                name = Objects.requireNonNull(bannerBlockEntity.getComponents().get(DataComponentTypes.ITEM_NAME)).getString();
            else {
                String blockTranslationKey = blockState.getBlock().getTranslationKey();
                name = Text.translatable(blockTranslationKey).getString();
            }
        }
        markerManager.addMarker(bannerBlockEntity, name);
    }

    private boolean breakBlock(World world, PlayerEntity playerEntity, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity) {
        if (blockState.isIn(BlockTags.BANNERS)) {
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(blockPos);
            try {
                if (bannerBlockEntity != null && markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerRemove")) {
                        playerEntity.sendMessage(Text.literal("[BlueMap Banners] You removed a marker from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemapUrl")))).withUnderline(true))).append(Text.of(".")), false);
                    }
                }
            } catch (NoSuchElementException ignored) {}
        }
        return true;
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        final LiteralCommandNode<ServerCommandSource> baseCommand = dispatcher
                .register(literal("bluemapbanners")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            context.getSource().sendFeedback(() -> Text.translatable(
                                    "bluemapbanners.commands.status",
                                    configManager.getConfig("notifyPlayerOnBannerPlace"),
                                    configManager.getConfig("notifyPlayerOnMarkerAdd"),
                                    configManager.getConfig("notifyPlayerOnMarkerRemove"),
                                    configManager.getConfig("notifyGlobalOnMarkerRemove"),
                                    configManager.getConfig("markerAddInstantOnBannerPlace"),
                                    configManager.getConfig("markerMaxViewDistance"),
                                    configManager.getConfig("bluemapUrl")
                            ), false);
                            return 1;
                        })

                        .then(literal("notifyPlayerOnBannerPlace").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnBannerPlace.status",
                                                    configManager.getConfig("notifyPlayerOnBannerPlace")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyPlayerOnBannerPlace")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.true"), false);
                                                configManager.setConfig("notifyPlayerOnBannerPlace", true);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyPlayerOnBannerPlace")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.false"), false);
                                                configManager.setConfig("notifyPlayerOnBannerPlace", false);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnBannerPlace.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("notifyPlayerOnMarkerAdd").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnMarkerAdd.status",
                                                    configManager.getConfig("notifyPlayerOnMarkerAdd")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyPlayerOnMarkerAdd")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.true"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerAdd", true);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyPlayerOnMarkerAdd")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.false"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerAdd", false);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerAdd.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("notifyPlayerOnMarkerRemove").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.notifyPlayerOnMarkerRemove.status",
                                                    configManager.getConfig("notifyPlayerOnMarkerRemove")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyPlayerOnMarkerRemove")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.true"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerRemove", true);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyPlayerOnMarkerRemove")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.false"), false);
                                                configManager.setConfig("notifyPlayerOnMarkerRemove", false);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyPlayerOnMarkerRemove.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("notifyGlobalOnMarkerRemove").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.notifyGlobalOnMarkerRemove.status",
                                                    configManager.getConfig("notifyGlobalOnMarkerRemove")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("notifyGlobalOnMarkerRemove")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.true"), false);
                                                configManager.setConfig("notifyGlobalOnMarkerRemove", true);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("notifyGlobalOnMarkerRemove")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.false"), false);
                                                configManager.setConfig("notifyGlobalOnMarkerRemove", false);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.notifyGlobalOnMarkerRemove.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("markerAddInstantOnBannerPlace").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.markerAddInstantOnBannerPlace.status",
                                                    configManager.getConfig("markerAddInstantOnBannerPlace")
                                            ), false);
                                            return 1;
                                        })
                                        .then(literal("true").executes(context -> {
                                            if (!configManager.getBoolConfig("markerAddInstantOnBannerPlace")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.true"), false);
                                                configManager.setConfig("markerAddInstantOnBannerPlace", true);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.already_true"), false);
                                            }
                                            return 1;
                                        }))

                                        .then(literal("false").executes(context -> {
                                            if (configManager.getBoolConfig("markerAddInstantOnBannerPlace")) {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.false"), false);
                                                configManager.setConfig("markerAddInstantOnBannerPlace", false);
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.translatable(
                                                        "bluemapbanners.commands.markerAddInstantOnBannerPlace.already_false"), false);
                                            }
                                            return 1;
                                        }))
                        )

                        .then(literal("markerMaxViewDistance").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.markerMaxViewDistance.status",
                                                    configManager.getConfig("markerMaxViewDistance")
                                            ), false);
                                            return 1;
                                        })

                                        .then(argument("markerMaxViewDistance", greedyString()) .executes(context -> {
                                            configManager.setConfig("markerMaxViewDistance", Integer.parseInt(StringArgumentType.getString(context, "markerMaxViewDistance")));
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.markerMaxViewDistance.update",
                                                    configManager.getConfig("markerMaxViewDistance")
                                            ), false);

                                            return 1;
                                        }))
                        )

                        .then(literal("bluemapUrl").executes(context -> {
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.bluemapUrl.status",
                                                    configManager.getConfig("bluemapUrl")
                                            ), false);
                                            return 1;
                                        })

                                        .then(argument("bluemapUrl", greedyString()) .executes(context -> {
                                            configManager.setConfig("bluemapUrl", StringArgumentType.getString(context, "bluemapUrl"));
                                            context.getSource().sendFeedback(() -> Text.translatable(
                                                    "bluemapbanners.commands.bluemapUrl.update",
                                                    configManager.getConfig("bluemapUrl")
                                            ), false);

                                            return 1;
                                        }))
                        )
                );

        dispatcher.register(literal("bb")
                .redirect(baseCommand)
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    context.getSource().sendFeedback(() -> Text.translatable(
                            "bluemapbanners.commands.status",
                            configManager.getConfig("enabled"),
                            configManager.getConfig("notifyPlayerOnBannerPlace"),
                            configManager.getConfig("notifyPlayerOnMarkerAdd"),
                            configManager.getConfig("notifyPlayerOnMarkerRemove"),
                            configManager.getConfig("notifyGlobalOnMarkerRemove"),
                            configManager.getConfig("markerAddInstantOnBannerPlace"),
                            configManager.getConfig("markerMaxViewDistance"),
                            configManager.getConfig("bluemapUrl")
                    ), false);
                    return 1;
                })
        );
    }
}

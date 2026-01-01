package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.manager.ConfigManager;
import dev.nincodedo.bluemapbanners.manager.MarkerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"))
    public void placeInject(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = context.getClickedPos();
        Level world = context.getLevel();
        Player player = context.getPlayer();
        BlockState state = world.getBlockState(pos);
        if (state.is(BlockTags.BANNERS)) {
            MarkerManager markerManager = MarkerManager.getInstance();
            ConfigManager configManager = ConfigManager.getInstance();
            if (configManager.getBoolConfig("markerAddInstantOnBannerPlace")) {
                BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(pos);
                if (bannerBlockEntity != null && !markerManager.doesMarkerExist(bannerBlockEntity)) {
                    if (bannerBlockEntity.getCustomName() != null || configManager.getBoolConfig("markerAddWithOriginalName")) {
                        markerManager.addMarker(state, bannerBlockEntity, player);
                        if (configManager.getBoolConfig("notifyPlayerOnMarkerAdd") && player != null)
                            player.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnMarkerAdd", BlueMapBanners.getWebText()), false);
                    }
                }
            } else {
                if (configManager.getBoolConfig("notifyPlayerOnBannerPlace") && player != null)
                    player.displayClientMessage(Component.translatable("bluemapbanners.notifyPlayerOnBannerPlace", BlueMapBanners.getWebText()), false);
            }
        }
    }
}

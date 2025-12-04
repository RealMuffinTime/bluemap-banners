package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.ConfigManager;
import dev.nincodedo.bluemapbanners.MarkerManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;emitGameEvent(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/event/GameEvent$Emitter;)V"))
    public void onPlaceInject(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockState state = world.getBlockState(pos);
        if (state.isIn(BlockTags.BANNERS)) {
            MarkerManager markerManager = MarkerManager.getInstance();
            ConfigManager configManager = ConfigManager.getInstance();
            if (configManager.getBoolConfig("markerAddInstantOnBannerPlace")) {
                BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(pos);
                if (bannerBlockEntity != null && !markerManager.doesMarkerExist(bannerBlockEntity)) {
                    if (bannerBlockEntity.getCustomName() != null || configManager.getBoolConfig("markerAddWithOriginalName")) {
                        BlueMapBanners.addMarkerWithName(state, bannerBlockEntity, markerManager);
                        if (configManager.getBoolConfig("notifyPlayerOnMarkerAdd") && player != null)
                            player.sendMessage(Text.translatable("bluemapbanners.notifyPlayerOnMarkerAdd", BlueMapBanners.getWebText()), false);
                    }
                }
            } else {
                if (configManager.getBoolConfig("notifyPlayerOnBannerPlace") && player != null)
                    player.sendMessage(Text.translatable("bluemapbanners.notifyPlayerOnBannerPlace", BlueMapBanners.getWebText()), false);
            }
        }
    }
}

package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.manager.ConfigManager;
import dev.nincodedo.bluemapbanners.manager.MarkerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {
    @Inject(method = "onExplosionHit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private void destroyBlockInject(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, Explosion explosion, BiConsumer<ItemStack, BlockPos> biConsumer, CallbackInfo ci) {
        if (blockState.is(BlockTags.BANNERS)) {
            MarkerManager markerManager = MarkerManager.getInstance();
            BannerBlockEntity bannerBlockEntity = new BannerBlockEntity(blockPos, blockState);
            bannerBlockEntity.setLevel(serverLevel);
            try {
                if (markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notifyGlobalOnMarkerRemove"))
                        Objects.requireNonNull(serverLevel.getServer()).getPlayerList().broadcastSystemMessage(Component.translatable("bluemapbanners.notifyGlobalOnMarkerRemove", BlueMapBanners.getWebText()), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
    }
}


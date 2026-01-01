package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.manager.Config;
import dev.nincodedo.bluemapbanners.manager.MarkerManager;
import dev.nincodedo.bluemapbanners.manager.ConfigManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.NoSuchElementException;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "destroyBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"))
    private void destroyBlockInject(BlockPos blockPos, boolean bl, Entity entity, int i, CallbackInfoReturnable<Boolean> cir) {
        Level thisLevel = (Level)(Object)this;
        BlockState blockState = thisLevel.getBlockState(blockPos);
        if (blockState.is(BlockTags.BANNERS)) {
            MarkerManager markerManager = MarkerManager.getInstance();
            BannerBlockEntity bannerBlockEntity = new BannerBlockEntity(blockPos, blockState);
            bannerBlockEntity.setLevel(thisLevel);
            try {
                if (markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig(Config.NOTIFY_GLOBAL_ON_MARKER_REMOVE))
                        Objects.requireNonNull(thisLevel.getServer()).getPlayerList().broadcastSystemMessage(Component.translatable("bluemapbanners.notifyGlobalOnMarkerRemove", BlueMapBanners.getWebText()), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
    }
}


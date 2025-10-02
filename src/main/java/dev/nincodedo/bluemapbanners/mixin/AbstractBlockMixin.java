package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.BlueMapBanners;
import dev.nincodedo.bluemapbanners.MarkerManager;
import dev.nincodedo.bluemapbanners.ConfigManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.NoSuchElementException;
import java.util.Objects;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {
    @Inject(at = @At("HEAD"), method = "onStateReplaced")
    private void onBreakInject(BlockState state, ServerWorld world, BlockPos pos, boolean moved, CallbackInfo ci) {
        if (state.isIn(BlockTags.BANNERS)) {
            MarkerManager markerManager = MarkerManager.getInstance();
            BannerBlockEntity bannerBlockEntity = new BannerBlockEntity(pos, state);
            bannerBlockEntity.setWorld(world);
            try {
                if (markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notifyGlobalOnMarkerRemove"))
                        Objects.requireNonNull(world.getServer()).getPlayerManager().broadcast(Text.translatable("bluemapbanners.notifyGlobalOnMarkerRemove", BlueMapBanners.getWebText()), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
    }
}


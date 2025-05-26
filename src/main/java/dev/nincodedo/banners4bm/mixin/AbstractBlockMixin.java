package dev.nincodedo.banners4bm.mixin;

import dev.nincodedo.banners4bm.BannerMarkerManager;
import dev.nincodedo.banners4bm.ConfigManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
            BannerMarkerManager bannerMarkerManager = BannerMarkerManager.getInstance();
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(pos);
            try {
                if (bannerBlockEntity != null && bannerMarkerManager.doesMarkerExist(bannerBlockEntity)) {
                    bannerMarkerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notify_global_on_marker_remove"))
                                Objects.requireNonNull(world.getServer()).getPlayerManager().broadcast(Text.literal("[Banner4BM] A marker has been removed from the web map :'("), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
    }
}


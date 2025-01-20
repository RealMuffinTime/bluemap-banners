package dev.nincodedo.banners4bm.mixin;

import dev.nincodedo.banners4bm.BannerMarkerManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {
    @Inject(at = @At("HEAD"), method = "onStateReplaced")
    private void onBreakInject(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (state.isIn(BlockTags.BANNERS) && !newState.isIn(BlockTags.BANNERS)) {
            BannerMarkerManager.getInstance().removeMarker((BannerBlockEntity) world.getBlockEntity(pos));
        }
    }
}


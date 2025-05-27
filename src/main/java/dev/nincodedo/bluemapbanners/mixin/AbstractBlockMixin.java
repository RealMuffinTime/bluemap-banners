package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.MarkerManager;
import dev.nincodedo.bluemapbanners.ConfigManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.Objects;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {
    @Inject(at = @At("HEAD"), method = "onStateReplaced")
    private void onBreakInject(BlockState state, ServerWorld world, BlockPos pos, boolean moved, CallbackInfo ci) {
        if (state.isIn(BlockTags.BANNERS)) {
            MarkerManager markerManager = MarkerManager.getInstance();
            BannerBlockEntity bannerBlockEntity = (BannerBlockEntity) world.getBlockEntity(pos);
            try {
                if (bannerBlockEntity != null && markerManager.doesMarkerExist(bannerBlockEntity)) {
                    markerManager.removeMarker(bannerBlockEntity);
                    if (ConfigManager.getInstance().getBoolConfig("notifyPlayerOnMarkerRemove"))
                        Objects.requireNonNull(world.getServer()).getPlayerManager().broadcast(Text.literal("[BlueMap Banners] A marker has been removed from the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemapUrl")))).withUnderline(true))).append(Text.of(".")), false);
                }
            } catch (NoSuchElementException ignored) {}
        }
    }
}


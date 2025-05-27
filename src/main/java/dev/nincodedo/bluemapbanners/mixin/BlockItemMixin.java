package dev.nincodedo.bluemapbanners.mixin;

import dev.nincodedo.bluemapbanners.ConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(at = @At("HEAD"), method = "postPlacement")
    public void onPlaceInject(BlockPos pos, World world, PlayerEntity player, ItemStack stack, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.isIn(BlockTags.BANNERS)) {
            if (ConfigManager.getInstance().getBoolConfig("notify_player_on_banner_place")) {
                player.sendMessage(Text.literal("[BlueMap Banners] Use a map item on the banner to add a marker on the ").append(Text.literal("web map").setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(ConfigManager.getInstance().getConfig("bluemap_url")))).withUnderline(true))).append(Text.of(".")), false);
            }
        }
    }
}

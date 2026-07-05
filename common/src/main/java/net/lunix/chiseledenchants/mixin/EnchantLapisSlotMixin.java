package net.lunix.chiseledenchants.mixin;

import net.lunix.chiseledenchants.ModdedTableHolder;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Make the enchanting-table lapis slot require lapis BLOCKS on the modded (Chiseled Enchanter) table and reject
 * gems there, while leaving REGULAR tables exactly on vanilla (gems only). Vanilla's mayPlace allows only gems,
 * so we override it: capture the owning menu in the slot's constructor, and in mayPlace branch on whether that
 * menu is the modded table. Target is the lapis slot's anonymous class {@code EnchantmentMenu$3} (its mayPlace
 * checks Items.LAPIS_LAZULI — verified on 26.1/26.2). If the capture ever fails, we fall back to also allowing
 * blocks (gems still work) so nothing breaks.
 */
@Mixin(targets = "net.minecraft.world.inventory.EnchantmentMenu$3")
public abstract class EnchantLapisSlotMixin {

    @Unique private EnchantmentMenu chiseledEnchants$menu;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void chiseledEnchants_captureMenu(EnchantmentMenu menu, Container container, int slot, int x, int y,
                                              CallbackInfo ci) {
        this.chiseledEnchants$menu = menu;
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void chiseledEnchants_lapisRule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        boolean modded = chiseledEnchants$menu instanceof ModdedTableHolder h && h.chiseledEnchants$isModdedTable();
        if (modded) {
            cir.setReturnValue(stack.is(Items.LAPIS_BLOCK));   // modded table: ONLY lapis blocks (gems rejected)
        } else if (stack.is(Items.LAPIS_BLOCK)) {
            cir.setReturnValue(true);                          // regular table: also allow blocks; gems -> vanilla
        }
        // regular table + gems (or anything else): fall through to vanilla mayPlace
    }
}

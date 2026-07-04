package net.lunix.chiseledenchants.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widen the enchanting-table lapis slot to ALSO accept lapis blocks (vanilla only allows gems). Additive —
 * gems still work, so normal tables are unaffected; the modded "Chiseled Enchanter" then requires blocks in
 * its consumption logic. Target is the lapis slot's anonymous class {@code EnchantmentMenu$3} (its mayPlace
 * checks Items.LAPIS_LAZULI — verified on 26.1/26.2; the $3 index is coupled to that MC source layout).
 */
@Mixin(targets = "net.minecraft.world.inventory.EnchantmentMenu$3")
public abstract class EnchantLapisSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void chiseledEnchants_allowLapisBlock(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(Items.LAPIS_BLOCK)) cir.setReturnValue(true);
    }
}

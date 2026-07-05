package net.lunix.chiseledenchants.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Let players SHIFT-CLICK lapis blocks into the enchant lapis slot. Vanilla {@code EnchantmentMenu.quickMoveStack}
 * only routes a shift-clicked item to the lapis slot when it's LAPIS_LAZULI (gems), so a block never goes there.
 * We handle the block case at HEAD (move it into slot 1) and cancel; everything else falls through to vanilla.
 * Pairs with {@link EnchantLapisSlotMixin} (which lets a block sit in the slot at all).
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantQuickMoveMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void chiseledEnchants_shiftLapisBlock(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index < 2) return;   // slots 0 = item, 1 = lapis, 2+ = inventory; only handle inventory -> menu
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot from = self.getSlot(index);
        if (!from.hasItem() || !from.getItem().is(Items.LAPIS_BLOCK)) return;

        Slot lapisSlot = self.getSlot(1);
        ItemStack moving = from.getItem();
        if (!lapisSlot.mayPlace(moving)) return;
        ItemStack inSlot = lapisSlot.getItem();
        int cap = Math.min(lapisSlot.getMaxStackSize(), moving.getMaxStackSize());

        if (inSlot.isEmpty()) {
            int n = Math.min(moving.getCount(), cap);
            lapisSlot.setByPlayer(moving.copyWithCount(n));
            moving.shrink(n);
        } else if (ItemStack.isSameItemSameComponents(inSlot, moving)) {
            int n = Math.min(moving.getCount(), cap - inSlot.getCount());
            if (n <= 0) { cir.setReturnValue(ItemStack.EMPTY); return; }   // slot full
            inSlot.grow(n);
            moving.shrink(n);
            lapisSlot.setChanged();
        } else {
            return;   // slot holds something else — let vanilla run (it just won't route the block)
        }
        if (moving.isEmpty()) from.setByPlayer(ItemStack.EMPTY);
        else from.setChanged();
        cir.setReturnValue(ItemStack.EMPTY);   // handled; stop the shift-click loop
    }
}

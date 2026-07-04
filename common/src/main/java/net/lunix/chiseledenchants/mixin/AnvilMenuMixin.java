package net.lunix.chiseledenchants.mixin;

import net.lunix.chiseledenchants.GuideBook;
import net.lunix.chiseledenchants.ModConfig;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guide-book trigger: rename a book &amp; quill to the configured keyword in an anvil and the result becomes
 * the chiseledEnchants guide (a server-built written book — vanilla clients render it fine). We ride the TAIL
 * of the vanilla result computation and only override the output when the rename matches, so normal anvil
 * use is untouched. itemName + cost are declared on AnvilMenu (safe to @Shadow); the slots live on the
 * ItemCombinerMenu superclass, so we reach them via cast rather than shadowing inherited members.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Shadow private String itemName;
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void chiseledEnchants_guide(CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        ItemStack left = self.getSlot(0).getItem();                 // slot 0 = first input (the book)
        ItemStack guide = GuideBook.forRename(left, itemName);
        if (guide.isEmpty()) return;                                // not a guide rename — leave vanilla result alone
        int resultIdx = ((ItemCombinerMenu) (Object) this).getResultSlot();
        self.getSlot(resultIdx).set(guide);
        cost.set(Math.max(1, ModConfig.get().guideAnvilCost));
    }
}

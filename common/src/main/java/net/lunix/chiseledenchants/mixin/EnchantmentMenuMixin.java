package net.lunix.chiseledenchants.mixin;

import net.lunix.chiseledenchants.ChiseledEnchanting;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Core hook for chiseledEnchants (DESIGN.md §6). After vanilla successfully enchants the item,
 * force in the guaranteed enchants from nearby chiseled bookshelves and roll book consumption.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

    @Shadow @Final private Container enchantSlots;
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final public int[] costs;

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void chiseledEnchants_afterEnchant(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            ChiseledEnchanting.applyGuarantees(enchantSlots, access, id, costs);
        }
    }
}

package net.lunix.chiseledenchants.mixin;

import net.lunix.chiseledenchants.ChiseledEnchanting;
import net.lunix.chiseledenchants.ModConfig;
import net.lunix.chiseledenchants.ModdedTableHolder;
import net.lunix.chiseledenchants.TableNotice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modded-table takeover (DESIGN.md §1/§5/§6/§7). On a table surrounded by chiseled bookshelves the mod
 * drives the whole enchant — targeted enchants only, its own XP/lapis cost, no vanilla roll — and shows
 * its own (or blanked-on-error) slots. Regular/vanilla tables are untouched. The enchantment seed is
 * shared between the displayed slots and the click so the previewed cost/level is exactly what's charged.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin implements ModdedTableHolder {

    @Shadow @Final private Container enchantSlots;
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private DataSlot enchantmentSeed;
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;

    @Shadow public abstract int getEnchantmentSeed();

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void chiseledEnchants_takeover(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        Boolean handled = ChiseledEnchanting.moddedEnchant(enchantSlots, access, id, getEnchantmentSeed(), player);
        if (handled != null) {
            cir.setReturnValue(handled);                       // modded table: suppress vanilla
            if (Boolean.TRUE.equals(handled)) {
                enchantmentSeed.set(player.getEnchantmentSeed());   // fresh rolls for the next item
                // broadcastChanges() lives on AbstractContainerMenu (superclass), so reach it via cast
                ((AbstractContainerMenu) (Object) this).broadcastChanges();   // sync consumed lapis / XP / result item
            }
        }
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void chiseledEnchants_slots(Container container, CallbackInfo ci) {
        if (container == enchantSlots) {
            ChiseledEnchanting.moddedSlots(enchantSlots, access, getEnchantmentSeed(), costs, enchantClue, levelClue);
        }
    }

    /** Actionbar notice when a player opens the modded table (server-side 3-arg ctor only). */
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void chiseledEnchants_openNotice(int id, Inventory inv, ContainerLevelAccess openAccess, CallbackInfo ci) {
        ModConfig cfg = ModConfig.get();
        if (cfg.tableOpenNotice == null || cfg.tableOpenNotice.isBlank()) return;
        if (!(inv.player instanceof ServerPlayer sp)) return;
        openAccess.execute((level, pos) -> {
            if (!level.isClientSide() && ChiseledEnchanting.isModdedTable(level, pos)) {
                Component text = Component.literal(cfg.tableOpenNotice.trim()).withStyle(ChatFormatting.AQUA);
                TableNotice.showBoss(sp, text);   // boss bar at the top; stays until they leave the table
            }
        });
    }

    /** Clear the open-notice boss bar when the player closes the table (in case it's still showing). */
    @Inject(method = "removed", at = @At("HEAD"))
    private void chiseledEnchants_closeNotice(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) {
            TableNotice.removeFor(sp);
        }
    }

    /** Is this menu the modded (Chiseled Enchanter) table? Used by the lapis slot to accept only blocks there. */
    @Override
    public boolean chiseledEnchants$isModdedTable() {
        boolean[] modded = {false};
        access.execute((level, pos) -> modded[0] = ChiseledEnchanting.isModdedTable(level, pos));
        return modded[0];
    }
}

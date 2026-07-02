package net.lunix.chiseledenchants.mixin;

import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Core hook for chiseledEnchants. Currently a no-op placeholder that proves the mixin
 * pipeline (json → both manifests → JAVA_25) applies cleanly; the injection handlers land
 * here during implementation.
 *
 * TODO — implement against verified 26.1.2 {@link EnchantmentMenu} signatures (decompile to
 * confirm method names before writing @Inject targets):
 *   1. §2 scan  — @Inject TAIL of slotsChanged(Container): walk the 24 chiseled-bookshelf
 *      positions (air-gap checked) via the menu's ContainerLevelAccess, tally single-enchant
 *      books, cache per-enchant guarantee chances (chancePerMaxBook × bookLevel/maxLevel).
 *   2. §6 apply — hook the apply path (clickMenuButton → EnchantmentHelper.enchantItem result):
 *      force in successful guaranteed enchants (compatible + non-conflicting, treasure/curse
 *      gated by config), then roll consumption on the shelves (lowest-level book first).
 *   3. §7.1 lapis — widen the lapis slot mayPlace to also accept lapis blocks (protection
 *      mode) and consume blocks instead of gems when present.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    // Injection handlers land here during implementation.
}

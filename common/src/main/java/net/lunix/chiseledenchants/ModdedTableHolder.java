package net.lunix.chiseledenchants;

/**
 * Duck interface merged onto {@code EnchantmentMenu} by its mixin so the lapis-slot's inner-class mixin can ask
 * whether this menu is the modded (Chiseled Enchanter) table — used to accept ONLY lapis blocks there while
 * leaving regular tables on vanilla (gems).
 */
public interface ModdedTableHolder {
    boolean chiseledEnchants$isModdedTable();
}

package net.lunix.chiseledenchants.paper;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans chiseled bookshelves around an enchanting table for single-enchant books, grouped by enchant.
 * Paper equivalent of the mod's {@code scan()}. (First cut: a box scan — the mod's exact vanilla-power +
 * air-gap geometry is a later refinement.)
 */
public final class ShelfScanner {

    private ShelfScanner() {}

    /** A stored book found in a chiseled shelf: its shelf block, slot, enchant, and level. */
    public record Book(Block shelf, int slot, Enchantment enchant, int level) {}

    /** Group every single-enchant book in the surrounding chiseled shelves by its enchant. */
    public static Map<Enchantment, List<Book>> scan(Block table) {
        Map<Enchantment, List<Book>> byEnchant = new HashMap<>();
        int radius = Math.max(2, PluginConfig.scanRadius);
        int layers = Math.max(1, PluginConfig.scanLayers);
        for (int dy = 0; dy < layers; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;   // table column + air-gap ring
                    Block b = table.getRelative(dx, dy, dz);
                    if (!(b.getState() instanceof ChiseledBookshelf shelf)) continue;
                    ItemStack[] contents = shelf.getInventory().getContents();
                    for (int slot = 0; slot < contents.length; slot++) {
                        ItemStack item = contents[slot];
                        if (item == null || item.getType() != Material.ENCHANTED_BOOK) continue;
                        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) continue;
                        Map<Enchantment, Integer> stored = meta.getStoredEnchants();
                        if (stored.size() != 1) continue;                    // single-enchant only
                        Map.Entry<Enchantment, Integer> e = stored.entrySet().iterator().next();
                        byEnchant.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                                .add(new Book(b, slot, e.getKey(), e.getValue()));
                    }
                }
            }
        }
        return byEnchant;
    }

    /** Census of shelves in range: {@code [chiseled, regular]} — drives modded/mixed detection. */
    public static int[] shelfCensus(Block table) {
        int radius = Math.max(2, PluginConfig.scanRadius);
        int layers = Math.max(1, PluginConfig.scanLayers);
        int chiseled = 0, regular = 0;
        for (int dy = 0; dy < layers; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
                    Material t = table.getRelative(dx, dy, dz).getType();
                    if (t == Material.CHISELED_BOOKSHELF) chiseled++;
                    else if (t == Material.BOOKSHELF) regular++;
                }
            }
        }
        return new int[]{chiseled, regular};
    }
}

package net.lunix.chiseledenchants.paper;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans chiseled bookshelves around an enchanting table for single-enchant books, grouped by enchant.
 * Paper equivalent of the mod's {@code scan()}. Only max-level books ultimately count toward a guarantee
 * (checked when resolving), matching the mod's anti-launder rule.
 *
 * <p>TODO: port the mod's exact geometry (vanilla enchanting-power positions + air-gap ring) and the
 * max-level filtering / conflict resolution. This stub returns the raw books grouped by enchant.
 */
public final class ShelfScanner {

    private ShelfScanner() {}

    /** A stored book found in a chiseled shelf: its shelf, slot, enchant, and level. */
    public record Book(Block shelf, int slot, Enchantment enchant, int level) {}

    /** Scan the shelves around {@code table} and group single-enchant books by enchant. */
    public static Map<Enchantment, List<Book>> scan(Block table) {
        Map<Enchantment, List<Book>> byEnchant = new HashMap<>();
        int radius = Math.max(2, PluginConfig.scanRadius);
        int layers = Math.max(1, PluginConfig.scanLayers);
        Location base = table.getLocation();

        for (int dy = 0; dy < layers; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;   // table column + air-gap ring
                    Block b = base.clone().add(dx, dy, dz).getBlock();
                    if (!(b.getState() instanceof ChiseledBookshelf shelf)) continue;
                    // TODO: air-gap check (the distance-1 block toward the table must transmit power).
                    ItemStack[] contents = shelf.getInventory().getContents();
                    for (int slot = 0; slot < contents.length; slot++) {
                        ItemStack book = contents[slot];
                        if (book == null) continue;
                        // TODO: read STORED_ENCHANTMENTS (enchanted books hold exactly one) and add a Book.
                    }
                }
            }
        }
        return byEnchant;
    }

    /** Placeholder to keep the record referenced; real callers build these during the scan above. */
    static List<Book> emptyBooks() {
        return new ArrayList<>();
    }
}

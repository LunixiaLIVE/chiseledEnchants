package net.lunix.chiseledenchants.paper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * The in-game guide, a signed written book (renders on a vanilla client). Paper equivalent of the mod's
 * GuideBook. TODO: build the full page set from {@link PluginConfig} so the numbers match the live config,
 * mirroring the mod's pages (max-books rule, the single option, costs, status bar, commands).
 */
public final class GuideBook {

    private GuideBook() {}

    public static ItemStack create() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("chiseledEnchants Guide");
        meta.setAuthor("chiseledEnchants");
        meta.addPage(
                "chiseledEnchants\n\nTargeted enchanting — you choose the enchants, no random rolls.\n\n"
                        + "Stock chiseled bookshelves around the table with single-enchant books.");
        meta.addPage(
                "Only MAX-level books count (Sharpness V, Mending I).\n\n"
                        + booksLine() + " max-level books of one enchant = it at max, guaranteed.");
        book.setItemMeta(meta);
        return book;
    }

    private static String booksLine() {
        return Integer.toString(Math.max(1, PluginConfig.booksForFullChance));
    }
}

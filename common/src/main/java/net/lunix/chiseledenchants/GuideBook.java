package net.lunix.chiseledenchants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/**
 * The in-game guide, delivered as a signed {@code written_book}. The pages are a server-set data component,
 * so a fully-formatted guide renders on a VANILLA client with no client mod. Triggered by renaming a book
 * &amp; quill to the configured keyword in an anvil (see {@link net.lunix.chiseledenchants.mixin.AnvilMenuMixin}).
 */
public final class GuideBook {

    private GuideBook() {}

    /**
     * If the anvil rename matches the configured keyword and the left item is a BOOK &amp; QUILL, returns the
     * guide; else EMPTY. Requiring a writable_book keeps it immersive and means the finished guide can't be
     * re-edited or accidentally blanked by another mod (the result is a signed, read-only written book).
     */
    public static ItemStack forRename(ItemStack left, String renameText) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.guideEnabled || renameText == null) return ItemStack.EMPTY;
        String want = cfg.guideKeyword == null ? "" : cfg.guideKeyword.trim();
        if (want.isEmpty() || !renameText.trim().equalsIgnoreCase(want)) return ItemStack.EMPTY;
        if (!left.is(Items.WRITABLE_BOOK)) return ItemStack.EMPTY;   // book & quill only
        return create();
    }

    /** Build the guide as a signed written book (page content is server-authoritative → vanilla-client safe). */
    public static ItemStack create() {
        // Pull the live figures from the in-memory config so the book reflects THIS server's settings
        // (an admin edits the config, runs /cench reload, and the next book made reads the new numbers).
        ModConfig cfg = ModConfig.get();
        int chanceDenom = ChiseledEnchanting.chanceDenom(cfg);
        int levelDenom = ChiseledEnchanting.levelDenom(cfg);
        int guarantee = Math.max(chanceDenom, levelDenom);
        int maxCost = cfg.costOfMaxEnchant;
        int lapisLow = Math.max(0, cfg.lapisLow);
        int lapisHigh = Math.max(0, cfg.lapisHigh);
        double perBlock = Math.max(0.0, cfg.protectionPerBlock);
        String perBlockStr = perBlock == Math.floor(perBlock) ? Integer.toString((int) perBlock) : Double.toString(perBlock);
        double maxProtPct = Math.max(0.0, Math.min(100.0, cfg.maxProtectionPercent));
        String maxProtStr = maxProtPct == Math.floor(maxProtPct) ? Integer.toString((int) maxProtPct) : Double.toString(maxProtPct);
        int eatPct = (int) Math.round(Math.max(0.0, Math.min(1.0, cfg.bookConsumeChance)) * 100);
        String tableName = (cfg.specialTableName == null || cfg.specialTableName.isBlank())
                ? "Chiseled Enchanter" : cfg.specialTableName.trim();

        // Every page is kept short so nothing overflows a written-book page (~14 lines each).
        String tablePage = !cfg.requireSpecialTable
                ? "Any enchanting table works — no special table needed."
                : cfg.craftOnlyTable
                    ? "Craft the \"" + tableName + "\" (it's in your recipe book)."
                    : "Craft the \"" + tableName + "\", or rename any enchanting table to it in an anvil.";
        String protectPage = cfg.bookProtectionEnabled
                ? "Each enchant applied has a " + eatPct + "% chance to eat its book.\n\nSpend spare lapis (+"
                  + perBlockStr + "% each) to protect them, up to " + maxProtStr + "%."
                : "Each enchant applied has a " + eatPct + "% chance to eat its book.\n\nProtection is off — extra "
                  + "lapis does nothing.";
        String conflictNote = cfg.resolveConflicts
                ? "Conflicting enchants? More books wins. An equal tie blanks it — add a book to break it."
                : "Don't stock conflicting enchants — it blanks the options.";
        String blankReasons = cfg.resolveConflicts
                ? "Options blank when:\n\n• mixed chiseled + regular shelves\n\n• tied conflicting enchants\n\n"
                  + "• too little lapis."
                : "Options blank when:\n\n• mixed chiseled + regular shelves\n\n• conflicting enchants stocked\n\n"
                  + "• too little lapis.";

        List<Filterable<Component>> pages = List.of(
                page("chiseledEnchants",
                        "Targeted enchanting — you choose the enchants, no random rolls."),
                page("The table", tablePage),
                page("The shelves",
                        "Ring the table with chiseled bookshelves (usual spots, 1-block gap).\n\nStock them with "
                        + "single-enchant books."),
                page("Guarantees",
                        "Per enchant:\n\nChance to land = books / " + chanceDenom + "\n\nLevel = books averaged "
                        + "over " + levelDenom + " (blank slots = 0)."),
                page("Max it out",
                        guarantee + " max-level books of one enchant = that enchant at max, guaranteed.\n\n"
                        + conflictNote),
                page("The 3 options",
                        "Three tiers. Top = highest levels; the two below = reduced.\n\nEach tier costs its own "
                        + "lapis (" + lapisLow + "-" + lapisHigh + " blocks)."),
                page("Cost",
                        "XP: about " + maxCost + " levels per maxed enchant (per enchant that lands).\n\nLapis "
                        + "BLOCKS, not gems: " + lapisLow + "-" + lapisHigh + " per option."),
                page("Protecting books", protectPage),
                page("Commands",
                        "/cench — shelf summary\n\n/cench preview — held-item preview\n\n/cench table — shelf "
                        + "preview"),
                page("More commands",
                        "/cench find <enchant> — trace the shelves that hold it\n\n/cench about — mod info + links"),
                page("If it's blank", blankReasons)
        );
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("chiseledEnchants Guide"),
                        "chiseledEnchants", 0, pages, true));
        return book;
    }

    private static Filterable<Component> page(String title, String body) {
        return Filterable.passThrough(Component.empty()
                .append(Component.literal(title + "\n\n").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(body)));
    }
}

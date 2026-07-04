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
        List<Filterable<Component>> pages = List.of(
                page("chiseledEnchants",
                        "Targeted enchanting.\n\nStock chiseled bookshelves around an enchanting table with "
                        + "single-enchant books to guarantee exactly the enchants you want."),
                page("Setup",
                        "Place chiseled bookshelves in the usual enchanting spots (with the 1-block air gap), "
                        + "then fill them with single-enchant books.\n\nAll chiseled = our system. Mixed with "
                        + "regular shelves = blanked."),
                page("Guarantees",
                        "Per enchant:\n\n• Chance to land = books ÷ 6\n\n• Level = the books averaged over "
                        + "6 slots (empty slots count as 0)\n\nA full shelf of 6 max-level books = that enchant "
                        + "at max, every time."),
                page("The 3 options",
                        "Top option = highest levels.\n\nMiddle / bottom = reduced levels, cheaper.\n\nEvery "
                        + "stocked, compatible enchant applies together on the item you insert."),
                page("Cost",
                        "XP: about 10 levels per maxed enchant, charged from your first levels.\n\nLapis: 1 per "
                        + "enchant, minimum."),
                page("Protecting books",
                        "The table eats one book per enchant it applies.\n\nAdd extra lapis to reduce that — "
                        + "a full stack = 100% protection, and all of it is consumed. Lapis matters!"),
                page("Commands",
                        "/cench — shelf summary\n\n/cench preview — what applies to your held item\n\n"
                        + "/cench table — what the shelves apply\n\n/cench find <enchant> — trace it"),
                page("If it's blank",
                        "The 3 options blank out when:\n\n• chiseled + regular shelves are mixed, or\n\n• "
                        + "conflicting enchants are stocked (e.g. Sharpness + Smite).\n\nUse /cench table to find "
                        + "the problem.")
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

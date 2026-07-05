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
        int blocksForCap = perBlock > 0 ? (int) Math.ceil(maxProtPct / perBlock) : 0;
        int eatPct = (int) Math.round(Math.max(0.0, Math.min(1.0, cfg.bookConsumeChance)) * 100);
        String xpWhere = cfg.xpFromFirstLevels ? "from your first levels" : "off the top of your levels";
        boolean requireTable = cfg.requireSpecialTable;
        String tableName = (cfg.specialTableName == null || cfg.specialTableName.isBlank())
                ? "Arcane Enchanter" : cfg.specialTableName.trim();
        String acquire = cfg.craftOnlyTable
                ? "Craft the \"" + tableName + "\" (dragon head + netherite + obsidian). "
                : "Craft the \"" + tableName + "\", or anvil-rename an enchanting table to \"" + tableName + "\". ";
        String setup = requireTable
                ? acquire + "Place chiseled bookshelves around it (usual enchanting spots, 1-block air gap) and "
                  + "fill them with single-enchant books.\n\nMixing in regular shelves blanks the options."
                : "Place chiseled bookshelves around an enchanting table (usual spots, 1-block air gap) and fill "
                  + "them with single-enchant books.\n\nMixing in regular shelves blanks the options.";
        String protectPage = cfg.bookProtectionEnabled
                ? "Without spare lapis, each applied enchant has a " + eatPct + "% chance to eat its book.\n\n"
                  + "Each lapis block beyond an option's cost adds " + perBlockStr + "% protection, up to "
                  + maxProtStr + "% (~" + blocksForCap + " extra blocks). Those blocks are consumed too."
                : "Each applied enchant has a " + eatPct + "% chance to eat its book.\n\nBook protection is off "
                  + "here — extra lapis does nothing; the table only takes each option's required blocks.";

        List<Filterable<Component>> pages = List.of(
                page("chiseledEnchants",
                        "Targeted enchanting — no random rolls.\n\nStock chiseled bookshelves around the table "
                        + "with single-enchant books to guarantee exactly the enchants you want, paid in XP and "
                        + "lapis BLOCKS."),
                page("Setup", setup),
                page("Guarantees",
                        "Per enchant:\n\n• Chance to land = books ÷ " + chanceDenom + "\n\n• Level = the books "
                        + "averaged over " + levelDenom + " slots (empty slots count as 0)\n\n" + guarantee
                        + " max-level books of one enchant = that enchant at max, every time."),
                page("The 3 options",
                        "Three power tiers. The top applies the highest levels; the two below apply reduced "
                        + "levels. Each tier has its own lapis cost (" + lapisLow + "–" + lapisHigh + " blocks) "
                        + "that unlocks it.\n\nAll stocked, compatible enchants apply together — you pay XP per "
                        + "enchant that lands."),
                page("Cost",
                        "XP: about " + maxCost + " levels per maxed enchant, charged " + xpWhere + " (per enchant "
                        + "that lands).\n\nLapis BLOCKS, not gems: " + lapisLow + "–" + lapisHigh + " per option. "
                        + "See /cench table for the exact cost."),
                page("Protecting books", protectPage),
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

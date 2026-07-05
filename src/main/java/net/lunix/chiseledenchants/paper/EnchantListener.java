package net.lunix.chiseledenchants.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The enchanting-table takeover — Paper equivalent of the mod's EnchantmentMenu mixin, via events.
 * {@link PrepareItemEnchantEvent}: detect the modded table, drive the status bar, and put the resolved
 * enchant on the top offer. {@link EnchantItemEvent}: apply the resolved max-level set, charge the gem +
 * XP economy, and roll book consumption. {@link InventoryCloseEvent}: drop the status bar.
 *
 * <p>The offer display cost is clamped to the enchant GUI's 1–30 range; the real XP formula is charged on
 * click. NOTE: needs in-game testing for offer clickability when chiseled shelves provide no vanilla power.
 */
public final class EnchantListener implements Listener {

    private final ChiseledEnchantsPaper plugin;
    private final Random rng = new Random();

    public EnchantListener(ChiseledEnchantsPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepare(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        Block table = event.getEnchantBlock();
        if (!Targeting.isModdedTable(table)) return;

        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) offers[i] = null;   // blank; we fill the top slot below

        ItemStack item = event.getItem();
        if (isBook(item) && !PluginConfig.allowBookEnchanting) {
            plugin.statusBar().setProblem(player, "Book enchanting is disabled here");
            return;
        }

        Map<Enchantment, List<ShelfScanner.Book>> byEnchant = ShelfScanner.scan(table);
        String problem = Targeting.statusReason(table, byEnchant);
        if (problem != null) {
            plugin.statusBar().setProblem(player, problem);
            return;
        }
        plugin.statusBar().setReady(player, readyText());

        List<Targeting.Landed> landed = Targeting.resolve(player, table, item, byEnchant);
        if (landed.isEmpty()) return;

        Targeting.Landed top = landed.get(0);
        for (Targeting.Landed l : landed) if (l.level() > top.level()) top = l;
        int displayCost = Math.max(1, Math.min(30, Targeting.xpCost(landed)));
        offers[offers.length - 1] = new EnchantmentOffer(top.ench(), top.level(), displayCost);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Block table = event.getEnchantBlock();
        if (!Targeting.isModdedTable(table)) return;

        event.setCancelled(true);   // take full control of the gem + book economy
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (isBook(item) && !PluginConfig.allowBookEnchanting) return;

        Map<Enchantment, List<ShelfScanner.Book>> byEnchant = ShelfScanner.scan(table);
        if (Targeting.statusReason(table, byEnchant) != null) return;   // blanked

        List<Targeting.Landed> landed = Targeting.resolve(player, table, item, byEnchant);
        if (landed.isEmpty()) return;

        if (!(player.getOpenInventory().getTopInventory() instanceof EnchantingInventory inv)) return;
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        ItemStack lapis = inv.getSecondary();
        int lapisHave = (lapis != null && lapis.getType() == Material.LAPIS_LAZULI) ? lapis.getAmount() : 0;
        int lapisCost = Math.max(0, PluginConfig.lapisCost);
        int xp = Targeting.xpCost(landed);

        if (!creative && lapisHave < lapisCost) {
            player.sendActionBar(Component.text("The " + PluginConfig.specialTableName + " needs " + lapisCost + " lapis."));
            return;
        }
        if (!creative && player.getLevel() < xp) {
            player.sendActionBar(Component.text("Needs " + xp + " XP level" + (xp == 1 ? "" : "s") + "."));
            return;
        }

        // ── book protection from SURPLUS gems (same maths as the mod, gems instead of blocks) ──
        int protGems = 0;
        double protection = 0.0;
        if (PluginConfig.bookProtectionEnabled) {
            double perBlock = Math.max(0.0, PluginConfig.protectionPerBlock);
            double maxProt = Math.max(0.0, Math.min(1.0, PluginConfig.maxProtection));
            int gemsForCap = perBlock > 0.0 ? (int) Math.ceil(maxProt / perBlock) : 0;
            protGems = Math.min(Math.max(0, lapisHave - lapisCost), gemsForCap);
            protection = Math.min(maxProt, protGems * perBlock);
        }
        int lapisSpent = lapisCost + protGems;

        // ── apply the enchants (books become enchanted books; gear gets the enchants) ──
        inv.setItem(applyEnchants(item, landed));

        // ── charge gems + XP (survival only, like vanilla) ──
        if (!creative) {
            if (lapis != null) {
                int left = lapisHave - lapisSpent;
                lapis.setAmount(Math.max(0, left));
                inv.setSecondary(left > 0 ? lapis : null);
            }
            player.giveExpLevels(-xp);
        }

        // ── roll book consumption ──
        double consume = PluginConfig.bookConsumeChance * (1.0 - protection);
        for (Targeting.Landed l : landed) {
            if (rng.nextDouble() >= consume) continue;
            if (!l.books().isEmpty()) consumeBook(l.books().get(0));
        }

        player.updateInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) plugin.statusBar().remove(player);
    }

    /** Gear → unsafe-enchant in place; a plain book → an enchanted book with stored enchants. */
    private static ItemStack applyEnchants(ItemStack item, List<Targeting.Landed> landed) {
        if (isBook(item)) {
            ItemStack result = new ItemStack(Material.ENCHANTED_BOOK, item.getAmount());
            if (result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                for (Targeting.Landed l : landed) meta.addStoredEnchant(l.ench(), l.level(), true);
                result.setItemMeta(meta);
            }
            return result;
        }
        ItemStack result = item.clone();
        for (Targeting.Landed l : landed) result.addUnsafeEnchantment(l.ench(), l.level());
        return result;
    }

    private void consumeBook(ShelfScanner.Book book) {
        if (book.shelf().getState() instanceof ChiseledBookshelf shelf) {
            shelf.getInventory().setItem(book.slot(), null);
            shelf.update();
        }
    }

    private static boolean isBook(ItemStack item) {
        return item != null && item.getType() == Material.BOOK;
    }

    private static String readyText() {
        String n = PluginConfig.specialTableName;
        return (n == null || n.isBlank()) ? "Chiseled Enchanter — ready" : n + " — ready";
    }
}

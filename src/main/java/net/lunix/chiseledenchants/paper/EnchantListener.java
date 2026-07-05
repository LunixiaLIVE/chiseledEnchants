package net.lunix.chiseledenchants.paper;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

/**
 * The enchanting-table takeover — Paper equivalent of the mod's EnchantmentMenu mixin, using events instead
 * of mixins. {@link PrepareItemEnchantEvent} is where offers are computed (replace them with the guaranteed
 * set + drive the status bar); {@link EnchantItemEvent} is the click (apply the resolved enchants + economy).
 *
 * <p>Scaffold: detection and targeting are stubbed. The wiring (events registered, status bar reachable) is
 * real so the next phase is just filling in {@link #isModdedTable} + the shelf-scan/resolve logic.
 */
public final class EnchantListener implements Listener {

    private final ChiseledEnchantsPaper plugin;

    public EnchantListener(ChiseledEnchantsPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepare(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        Block table = event.getEnchantBlock();
        if (!isModdedTable(table)) return;   // vanilla table → untouched

        // TODO: ShelfScanner.scan(table) → resolve max-level guarantees (conflict/tie handling) →
        //       overwrite event.getOffers(); set the status bar red if there's a problem.
        plugin.statusBar().setReady(player, PluginConfig.specialTableName + " — ready");
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Block table = event.getEnchantBlock();
        if (!isModdedTable(table)) return;

        // TODO: event.getEnchantsToAdd().clear(); add the resolved max-level enchants; charge the
        //       lapis/XP economy; roll book consumption (see the mod's moddedEnchant).
    }

    /**
     * TODO: a modded table = a specially-named enchanting table (with the rarity colour when
     * {@code craftOnlyTable}) that also has chiseled bookshelves in range. Mirror the mod's isModdedTable.
     */
    private boolean isModdedTable(Block table) {
        return false;   // stub until detection + shelf scan are implemented
    }
}

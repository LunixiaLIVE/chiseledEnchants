package net.lunix.chiseledenchants.paper;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper port of chiseledEnchants — targeted enchanting driven by chiseled bookshelves around the table.
 * A from-scratch Bukkit/Paper reimplementation of the Fabric/NeoForge mod (same design, different platform):
 * events instead of mixins, {@code config.yml} instead of JSON, Bukkit {@code BossBar} instead of a mod boss bar.
 * Server-side only, like the mod.
 */
public final class ChiseledEnchantsPaper extends JavaPlugin {

    private static ChiseledEnchantsPaper instance;
    private StatusBar statusBar;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();              // writes config.yml on first run
        PluginConfig.load(this);
        this.statusBar = new StatusBar(this);
        ChiseledRecipe.register(this);    // the crafted "Chiseled Enchanter"

        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);

        var cmd = getCommand("chiseledenchants");
        if (cmd != null) {
            ChiseledCommand handler = new ChiseledCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("chiseledEnchants (Paper) enabled — v" + getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        if (statusBar != null) statusBar.clearAll();
        getLogger().info("chiseledEnchants (Paper) disabled.");
    }

    public static ChiseledEnchantsPaper getInstance() {
        return instance;
    }

    public StatusBar statusBar() {
        return statusBar;
    }
}

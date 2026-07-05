package net.lunix.chiseledenchants.paper;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player status boss bar above the enchanting table — the Paper equivalent of the mod's TableNotice.
 * GREEN = ready, RED = a problem (mixed shelves, a conflict tie, or a below-max book). One bar per player,
 * reused in place so a live green ⇄ red change doesn't flicker.
 */
public final class StatusBar {

    private final Plugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public StatusBar(Plugin plugin) {
        this.plugin = plugin;
    }

    /** GREEN "ready" bar. */
    public void setReady(Player player, String text) {
        set(player, text, BarColor.GREEN);
    }

    /** RED problem bar, with the reason. */
    public void setProblem(Player player, String reason) {
        set(player, "⚠ " + reason, BarColor.RED);
    }

    private void set(Player player, String text, BarColor color) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(text, color, BarStyle.SOLID);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        } else {
            bar.setTitle(text);
            bar.setColor(color);
        }
        bar.setProgress(1.0);
        bar.setVisible(true);
    }

    /** Drop a player's bar (call when they close the table). */
    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    /** Drop every bar (plugin disable). */
    public void clearAll() {
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
    }
}

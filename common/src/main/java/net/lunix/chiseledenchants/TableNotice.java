package net.lunix.chiseledenchants;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.inventory.EnchantmentMenu;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Boss-bar notice shown at the TOP of the screen (above an open container GUI) while a player has the modded
 * table open. It's private to that one player (only they are added to the event). Removed when they close the
 * table ({@code EnchantmentMenu.removed} hook); {@link #tick} is a safety net that drops any bar for a player
 * who is no longer in an enchanting menu, in case that hook is ever missed.
 */
public final class TableNotice {

    private TableNotice() {}

    private record Job(ServerBossEvent event, ServerPlayer player) {}

    private static final List<Job> JOBS = new ArrayList<>();

    /** Show a boss-bar notice to the player until they leave the table (replaces any prior one). */
    public static synchronized void showBoss(ServerPlayer player, Component text) {
        removeFor(player);
        ServerBossEvent event = new ServerBossEvent(
                UUID.randomUUID(), text, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        event.setProgress(1.0f);
        event.addPlayer(player);
        JOBS.add(new Job(event, player));
    }

    /** Drop any active notice bar for a player (called when they close the table). */
    public static synchronized void removeFor(ServerPlayer player) {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (j.player() == player) {
                j.event().removeAllPlayers();
                it.remove();
            }
        }
    }

    /** Safety net: drop bars for players who are no longer in an enchanting menu. */
    public static synchronized void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (!(j.player().containerMenu instanceof EnchantmentMenu)) {
                j.event().removeAllPlayers();
                it.remove();
            }
        }
    }
}

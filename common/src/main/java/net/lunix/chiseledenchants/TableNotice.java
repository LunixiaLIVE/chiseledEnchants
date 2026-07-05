package net.lunix.chiseledenchants;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Transient boss-bar notice shown at the TOP of the screen (above an open container GUI) when a player opens
 * the modded table. Boss bars don't self-expire, so we track each with a tick countdown and remove it from
 * {@link #tick} (wired into each loader's server-tick hook alongside {@link ParticleScheduler}).
 */
public final class TableNotice {

    private TableNotice() {}

    private static final class Job {
        final ServerBossEvent event;
        final ServerPlayer player;
        int ticksLeft;
        Job(ServerBossEvent event, ServerPlayer player, int ticksLeft) {
            this.event = event;
            this.player = player;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    /** Show a boss-bar notice to the player for {@code ticks}, replacing any prior one. */
    public static synchronized void showBoss(ServerPlayer player, Component text, int ticks) {
        removeFor(player);
        ServerBossEvent event = new ServerBossEvent(
                UUID.randomUUID(), text, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        event.setProgress(1.0f);
        event.addPlayer(player);
        JOBS.add(new Job(event, player, Math.max(1, ticks)));
    }

    /** Drop any active notice bar for a player. */
    public static synchronized void removeFor(ServerPlayer player) {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (j.player == player) {
                j.event.removeAllPlayers();
                it.remove();
            }
        }
    }

    /** Called each server tick by the loader entrypoints — expires finished notices. */
    public static synchronized void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (--j.ticksLeft <= 0) {
                j.event.removeAllPlayers();
                it.remove();
            }
        }
    }
}

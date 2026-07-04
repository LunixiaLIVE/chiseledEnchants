package net.lunix.chiseledenchants;

import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server-tick driven, pulsing particle trace for {@code /cench find}. A single command emission can't
 * repeat over time, so we queue a job and let the per-loader server-tick hook ({@link #tick}) fire one
 * pulse every {@code duration} ticks. Each pulse traces TRAIL particles from the table out to each shelf.
 */
public final class ParticleScheduler {

    private ParticleScheduler() {}

    /** One traced thread: the target (a shelf) and its color; origin is shared per job (the table). */
    public record Beam(Vec3 target, int color) {}

    private static final class Job {
        final ResourceKey<Level> dim;
        final Vec3 table;
        final List<Beam> beams;
        final int duration;    // ticks per pulse (also the trail's travel time)
        final boolean fromTable;  // true = table→shelf, false = shelf→table
        int repeatsLeft;
        int countdown;         // ticks until the next pulse

        Job(ResourceKey<Level> dim, Vec3 table, List<Beam> beams, int duration, boolean fromTable, int repeats) {
            this.dim = dim;
            this.table = table;
            this.beams = beams;
            this.duration = duration;
            this.fromTable = fromTable;
            this.repeatsLeft = repeats;
            this.countdown = 1;   // fire on the next tick
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    /** Queue a pulsing trace: {@code repeats} pulses, each {@code duration} ticks, table↔shelves per {@code fromTable}. */
    public static synchronized void schedule(ServerLevel level, Vec3 table, List<Beam> beams,
                                             int repeats, int duration, boolean fromTable) {
        if (beams.isEmpty() || repeats <= 0) return;
        JOBS.add(new Job(level.dimension(), table, new ArrayList<>(beams), Math.max(1, duration), fromTable, repeats));
    }

    /** Called once per server tick by each loader's entrypoint. */
    public static synchronized void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (--j.countdown > 0) continue;
            ServerLevel level = server.getLevel(j.dim);
            if (level != null) fire(level, j);
            if (--j.repeatsLeft <= 0) {
                it.remove();
            } else {
                j.countdown = j.duration;   // next pulse when this one finishes → back-to-back pulses
            }
        }
    }

    private static void fire(ServerLevel level, Job j) {
        for (Beam b : j.beams) {
            // TRAIL travels spawn→target over its lifetime, so the flow direction is just which end is which.
            Vec3 spawn = j.fromTable ? j.table : b.target();
            Vec3 trailTarget = j.fromTable ? b.target() : j.table;
            level.sendParticles(new TrailParticleOption(trailTarget, b.color(), j.duration),
                    spawn.x, spawn.y, spawn.z, 40, 0.16, 0.2, 0.16, 0.0);
        }
    }
}

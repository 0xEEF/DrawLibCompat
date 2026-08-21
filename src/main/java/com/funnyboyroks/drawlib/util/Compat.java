package com.funnyboyroks.drawlib.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime feature-detection so that a single compiled DrawLib jar can run
 * as-is across the widest practical range of server software and Minecraft
 * versions, degrading gracefully instead of hard-failing when an API isn't
 * present:
 * <ul>
 *     <li>Vanilla Bukkit / CraftBukkit / Spigot -- no Paper-only API</li>
 *     <li>Paper and Paper forks (Purpur, Pufferfish, etc.)</li>
 *     <li>Folia (regionised multithreading)</li>
 *     <li>Minecraft versions old enough to lack {@code Particle.DustOptions} (pre-1.13)</li>
 *     <li>Minecraft versions where {@code Particle.REDSTONE} was renamed to {@code Particle.DUST} (1.20.5+)</li>
 * </ul>
 * <p>
 * Every check below is resolved once via reflection and cached in a static
 * field. None of these lookups throw if the class/method in question doesn't
 * exist on the running server -- they simply report "not available" so the
 * caller can fall back to an older/simpler code path.
 */
public final class Compat {

    private static final Logger LOG = Logger.getLogger("DrawLib");

    private Compat() {
    }

    // ---------------------------------------------------------------
    // Folia detection
    // ---------------------------------------------------------------

    /** True if this server is running Folia (regionised multithreading). */
    public static final boolean IS_FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private static final Method GET_REGION_SCHEDULER = IS_FOLIA
        ? findMethod(Bukkit.class, "getRegionScheduler")
        : null;

    private static final Method REGION_SCHEDULER_EXECUTE = IS_FOLIA
        ? findRegionSchedulerExecute()
        : null;

    // ---------------------------------------------------------------
    // Paper-only expanded spawnParticle(receivers, source, ..., force)
    // ---------------------------------------------------------------

    private static final Method PAPER_SPAWN_PARTICLE = findPaperSpawnParticle();

    /** True if the Paper-only targeted/force-render spawnParticle overload is available. */
    public static boolean hasPaperParticleApi() {
        return PAPER_SPAWN_PARTICLE != null;
    }

    // ---------------------------------------------------------------
    // Particle.DustOptions (added 1.13)
    // ---------------------------------------------------------------

    /** True if {@code Particle.DustOptions} exists on this server (MC 1.13+). */
    public static final boolean HAS_DUST_OPTIONS = classExists("org.bukkit.Particle$DustOptions");

    // ---------------------------------------------------------------
    // Particle enum constant: DUST (1.20.5+) or REDSTONE (older)
    // ---------------------------------------------------------------

    /**
     * The correct enum constant for a colourable dust particle on this
     * server, or {@code null} if neither name resolves (should not happen
     * on any real Bukkit-family server that has a Particle enum at all).
     */
    public static final Particle DUST_PARTICLE = resolveDustParticle();

    // ---------------------------------------------------------------
    // Detection helpers
    // ---------------------------------------------------------------

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findPaperSpawnParticle() {
        try {
            return org.bukkit.World.class.getMethod(
                "spawnParticle",
                Particle.class, List.class, Player.class,
                double.class, double.class, double.class,
                int.class, double.class, double.class, double.class,
                double.class, Object.class, boolean.class
            );
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findRegionSchedulerExecute() {
        try {
            Class<?> schedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            for (Method m : schedulerClass.getMethods()) {
                // execute(Plugin, Location, Runnable)
                if (m.getName().equals("execute") && m.getParameterCount() == 3) {
                    return m;
                }
            }
        } catch (Throwable ignored) {
            // Not on Folia, or API shape changed -- caller falls back to running inline.
        }
        return null;
    }

    private static Particle resolveDustParticle() {
        try {
            return Particle.valueOf("DUST");
        } catch (Throwable t) {
            try {
                return Particle.valueOf("REDSTONE");
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    // ---------------------------------------------------------------
    // Invocation helpers
    // ---------------------------------------------------------------

    /**
     * Calls Paper's expanded {@code spawnParticle(receivers, source, ..., force)}
     * overload via reflection. Only call this after checking {@link #hasPaperParticleApi()}.
     */
    public static <T> void spawnParticlePaper(
        org.bukkit.World world, Particle particle, List<Player> receivers,
        double x, double y, double z, T data, boolean force
    ) {
        try {
            PAPER_SPAWN_PARTICLE.invoke(
                world, particle, receivers, null,
                x, y, z, 1, 0d, 0d, 0d, 0d, data, force
            );
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.log(Level.WARNING, "DrawLib: failed to call Paper's spawnParticle via reflection", e);
        }
    }

    /**
     * Runs {@code task} on the Folia region thread that owns {@code location}.
     * On non-Folia servers (or if no plugin instance was supplied), the task
     * just runs immediately on the calling thread, which is correct/required
     * behaviour for every other server platform.
     *
     * @param plugin   The owning plugin, used to schedule on Folia. May be {@code null}
     *                 on non-Folia servers; must be non-null on Folia or the task is
     *                 simply run inline (which may violate Folia's thread-ownership
     *                 rules if called from an unrelated thread).
     * @param location The location the task operates on.
     * @param task     The work to run.
     */
    public static void runRegionAware(Plugin plugin, Location location, Runnable task) {
        if (!IS_FOLIA || plugin == null || GET_REGION_SCHEDULER == null || REGION_SCHEDULER_EXECUTE == null) {
            task.run();
            return;
        }
        try {
            Object scheduler = GET_REGION_SCHEDULER.invoke(null);
            REGION_SCHEDULER_EXECUTE.invoke(scheduler, plugin, location, task);
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "DrawLib: failed to schedule on Folia region thread, running inline", t);
            task.run();
        }
    }
}

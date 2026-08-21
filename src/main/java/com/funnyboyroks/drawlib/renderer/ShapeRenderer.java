package com.funnyboyroks.drawlib.renderer;

import com.funnyboyroks.drawlib.util.Compat;
import com.funnyboyroks.drawlib.util.Util;
import com.funnyboyroks.drawlib.util.Vector;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Main class for the library.
 * <p>
 * Intended Usage:
 * <pre>
 *     ShapeRenderer renderer = new ShapeRenderer(myPlugin); // pass your plugin for Folia support
 *
 *     renderer.setColor(Color.RED);
 *     renderer.setStepSize(0.1);
 *     renderer.setReceivers(player);
 *     renderer.drawLine(point1, point2);
 * </pre>
 *
 * <em>Note: The draw methods only show the particles once, it is recommended to draw them every tick.</em>
 *
 * <h2>Compatibility</h2>
 * ShapeRenderer detects, at runtime, what the underlying server actually
 * supports and adapts automatically -- a single compiled jar works across:
 * <ul>
 *     <li><b>Paper / Paper forks</b> (Purpur, Pufferfish, ...): uses Paper's
 *     targeted, force-rendered {@code spawnParticle} overload -- identical
 *     behaviour to the original DrawLib.</li>
 *     <li><b>Vanilla Bukkit / CraftBukkit / Spigot</b>: falls back to
 *     per-player {@link Player#spawnParticle}, or a world-wide particle if
 *     no receivers were set. There is no "force show past render distance"
 *     on these platforms, since that flag is a Paper-only addition.</li>
 *     <li><b>Folia</b>: if a {@link Plugin} instance is supplied via the
 *     constructor, every particle spawn is dispatched onto the correct
 *     region thread automatically. Without a plugin instance, calls run on
 *     whatever thread invoked them, which is only safe on non-Folia
 *     servers.</li>
 *     <li><b>Minecraft 1.13+</b>: uses {@link Particle.DustOptions} for
 *     accurate, arbitrary particle colour (handles the {@code REDSTONE} to
 *     {@code DUST} rename in 1.20.5 automatically).</li>
 *     <li><b>Minecraft 1.9 - 1.12</b>: {@code Particle.DustOptions} doesn't
 *     exist yet, so colour is approximated with the legacy "offsets as
 *     RGB, count = 0" redstone-particle trick.</li>
 * </ul>
 * Minecraft versions older than 1.9 aren't supported, since those predate
 * Bukkit's {@code Particle} enum entirely (a fundamentally different,
 * string/id-based effect API would be required).
 */
public class ShapeRenderer {

    private final Plugin plugin; // Used only to schedule on the correct thread on Folia; may be null

    private Color        color; // Colour of the particles to be drawn
    private List<Player> receivers; // Players that will receive the particles
    private boolean      force; // Force show particles (Paper only; ignored elsewhere)
    private double       step_size = .1; // Step between two particles
    private boolean      optimize; // Optimize particles

    /**
     * Create a renderer with no owning plugin.
     * <p>
     * This is fine on every server platform <em>except</em> Folia -- if you
     * might run on Folia, use {@link #ShapeRenderer(Plugin)} instead so
     * particle calls are dispatched to the correct region thread.
     */
    public ShapeRenderer() {
        this(null);
    }

    /**
     * @param plugin Your plugin instance. Required for correct behaviour on
     *               Folia (so drawing can be scheduled onto the right region
     *               thread); ignored/unused on every other server platform.
     */
    public ShapeRenderer(@Nullable Plugin plugin) {
        this.plugin = plugin;
        this.color = Color.RED;
        this.receivers = null;
        this.force = false;
        this.optimize = true;
    }

    /**
     * @param color The new colour for the particles (not 100% accurate as Minecraft introduces some randomness)
     */
    public void setColor(@NotNull Color color) {
        this.color = color;
    }

    /**
     * @param step_size The new step between particles. Lower values may cause performance issues on client and/or server, especially on low-end devices. Higher value may make the lines hard to see. Must be greater than 0.
     */
    public void setStepSize(double step_size) {
        if (step_size <= 0) {
            throw new IllegalArgumentException("step size must be greater than 0");
        }

        this.step_size = step_size;
    }

    /**
     * @param optimize Optimize plot line
     */
    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * @param receivers The players that will receive the particles -- null for all players in world
     */
    public void setReceivers(@Nullable List<Player> receivers) {
        this.receivers = receivers;
    }

    /**
     * @param receivers The players that will receive the particles
     */
    public void setReceivers(@NotNull Player... receivers) {
        this.receivers = List.of(receivers);
    }

    /**
     * @param force If the particle should be forceshown to the players. Paper (and forks) only --
     *              silently has no effect on vanilla Bukkit/Spigot, since that server software has
     *              no equivalent flag.
     */
    public void setForceShow(boolean force) {
        this.force = force;
    }

    /**
     * Draw a single particle point
     *
     * @param point The location to draw
     */
    public void drawPoint(@NotNull Location point) {
        Compat.runRegionAware(this.plugin, point, () -> this.spawnParticleAt(point));
    }

    /**
     * Does the actual particle spawning, choosing the best API the running
     * server supports. Always invoked on a thread that's safe to touch
     * {@code point}'s world (either the calling thread on non-Folia servers,
     * or the correct region thread on Folia).
     */
    private void spawnParticleAt(@NotNull Location point) {
        World world = point.getWorld();
        if (world == null || Compat.DUST_PARTICLE == null) {
            return;
        }

        if (Compat.HAS_DUST_OPTIONS) {
            Particle.DustOptions data = new Particle.DustOptions(this.color, .5f);

            if (Compat.hasPaperParticleApi()) {
                // Best case: Paper / Paper fork -- exact receivers + force-render support
                Compat.spawnParticlePaper(
                    world, Compat.DUST_PARTICLE, this.receivers,
                    point.getX(), point.getY(), point.getZ(), data, this.force
                );
                return;
            }

            // Vanilla Bukkit/CraftBukkit/Spigot: no receivers/force overload,
            // so target players manually (or broadcast if none were set).
            if (this.receivers == null) {
                world.spawnParticle(Compat.DUST_PARTICLE, point, 1, 0, 0, 0, 0, data);
            } else {
                for (Player p : this.receivers) {
                    p.spawnParticle(Compat.DUST_PARTICLE, point, 1, 0, 0, 0, 0, data);
                }
            }
            return;
        }

        // Pre-1.13 fallback: Particle.DustOptions doesn't exist yet. Fake
        // the colour using the legacy "count = 0, offsets = RGB" redstone
        // dust trick instead.
        this.spawnLegacyColouredParticle(world, point);
    }

    private void spawnLegacyColouredParticle(@NotNull World world, @NotNull Location point) {
        // Red must be > 0 or the client treats the particle as invisible/default-coloured.
        double r = Math.max(this.color.getRed() / 255d, 0.0001d);
        double g = this.color.getGreen() / 255d;
        double b = this.color.getBlue() / 255d;

        if (this.receivers == null) {
            world.spawnParticle(Compat.DUST_PARTICLE, point, 0, r, g, b, 1);
        } else {
            for (Player p : this.receivers) {
                p.spawnParticle(Compat.DUST_PARTICLE, point, 0, r, g, b, 1);
            }
        }
    }

    /**
     * Draw a line between a and b -- Order does not matter.
     *
     * @param a First Point
     * @param b Second Point
     */
    public void drawLine(@NotNull Location a, @NotNull Location b) {
        if (a.getWorld() != b.getWorld()) {
            throw new IllegalArgumentException("Cannot draw lines between differing worlds.");
        }

        if (a.distanceSquared(b) == 0) {
            drawPoint(a);
            return;
        }

        Vector vec = new Vector(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());

        double length = vec.getMag();
        final double lengthOfTerm = 2.0;  // terminal length = 2.0 blocks
        final double lengthOfMinMiddle = 8.0;
        final double lengthOfStartOptimize = lengthOfTerm * 2 + lengthOfMinMiddle;
        // |<--------------------------------- length ------------------------------------>|
        // |  term  |                          middle                             |  term  |
        // ********** * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * **********
        if (this.optimize && length >= lengthOfStartOptimize) {
            final double ppb = 1.0 / step_size; // points per block
            final int term = (int)(lengthOfTerm * ppb); // number of terminal points
            final int middle = (int)(lengthOfMinMiddle * ppb); // number of middle points
            final int max = term * 2 + middle; // max points on line
            for (int i = 0; i <= max; i++) {
                double mag;  // 0.0 ~ 1.0
                if (i >= term + middle) {
                    mag = (length - lengthOfTerm + ((double)(i - (term + middle)) / term) * lengthOfTerm) / length;
                } else if (i < term) {
                    mag = ((double) i / term) * lengthOfTerm / length;
                } else {
                    mag = (lengthOfTerm + ((double)(i - term) / middle) * (length - lengthOfTerm * 2)) / length;
                }
                Location l = vec.clone().mult(mag).toLocation(a.getWorld());
                l.add(a);
                drawPoint(l);
            }
        } else {
            Vector unit = vec.getUnit();

            for (double mag = 0; mag * mag < vec.getMagSq(); mag += step_size) {
                Location l = unit.clone().mult(mag).toLocation(a.getWorld());
                l.add(a);
                drawPoint(l);
            }
        }

    }

    /**
     * Draw a sphere with the provided radius
     *
     * @param centre The centre point of the sphere
     * @param radius The radius of the sphere
     */
    public void drawSphere(@NotNull Location centre, double radius) {
        for (double phi = 0; phi <= Math.PI; phi += step_size) {
            double z = radius * Math.cos(phi);
            for (double theta = 0; theta <= 2 * Math.PI; theta += step_size) {
                double x = radius * Math.sin(phi) * Math.cos(theta);
                double y = radius * Math.sin(phi) * Math.sin(theta);
                drawPoint(centre.clone().add(x, y, z));
            }
        }
    }

    /**
     * Draw a cuboid between two corners -- Order does not matter
     *
     * @param a First Corner
     * @param b Second Corner
     */
    public void drawCuboid(@NotNull Location a, @NotNull Location b) {
        if (!a.getWorld().equals(b.getWorld())) {
            throw new IllegalArgumentException("Cannot draw a cuboid between different worlds.");
        }

        Vector min = new Vector(Util.minLocation(a, b));
        Vector max = new Vector(Util.maxLocation(a, b));

        Location[] vertices = {
            min.toLocation(a.getWorld()),
            new Vector(min.x, min.y, max.z).toLocation(a.getWorld()),
            new Vector(min.x, max.y, min.z).toLocation(a.getWorld()),
            new Vector(min.x, max.y, max.z).toLocation(a.getWorld()),
            new Vector(max.x, min.y, min.z).toLocation(a.getWorld()),
            new Vector(max.x, min.y, max.z).toLocation(a.getWorld()),
            new Vector(max.x, max.y, min.z).toLocation(a.getWorld()),
            max.toLocation(a.getWorld())
        };

        drawLine(vertices[0], vertices[1]);
        drawLine(vertices[0], vertices[2]);
        drawLine(vertices[0], vertices[4]);

        drawLine(vertices[3], vertices[1]);
        drawLine(vertices[3], vertices[2]);
        drawLine(vertices[3], vertices[7]);

        drawLine(vertices[5], vertices[1]);
        drawLine(vertices[5], vertices[4]);
        drawLine(vertices[5], vertices[7]);

        drawLine(vertices[6], vertices[2]);
        drawLine(vertices[6], vertices[4]);
        drawLine(vertices[6], vertices[7]);
    }

    /**
     * Draw a vector from a point -- similar to {@link #drawLine(Location, Location)}
     *
     * @param point The origin point
     * @param vec   The vector to draw
     */
    public void drawVector(@NotNull Location point, @NotNull Vector vec) {

        drawLine(point, point.clone().add(vec.toLocation(point.getWorld())));

    }

    /**
     * Draw a line between any set of points
     *
     * @param points The set of points
     * @param closed If the shape should be closed at the end
     */
    public void drawLine(@NotNull Location[] points, boolean closed) {
        if (points.length == 0) return;

        if (points.length == 1) {
            drawPoint(points[0]);
            return;
        }

        if (closed && points.length > 2) {
            Location[] a = new Location[points.length + 1];
            System.arraycopy(points, 0, a, 0, points.length);
            a[a.length - 1] = a[0];
            points = a;
        }

        for (int i = 0; i < points.length - 1; ++i) {
            drawLine(points[i], points[i + 1]);
        }
    }

    /**
     * Draw a line between any set of points (not closed)
     *
     * @param points The set of points
     */
    public void drawLine(@NotNull Location[] points) {
        drawLine(points, false);
    }

    /**
     * Draw the outline of a block face
     *
     * @param block The location of the block
     * @param face  Block face to draw
     */
    public void drawBlockFace(@NotNull Block block, @NotNull BlockFace face) {

        Location pt1 = block.getLocation();
        Location pt2 = block.getLocation();

        switch (face) {
            case UP:
                pt1.add(0, 1, 0);
                pt2.add(1, 1, 1);
                break;
            case DOWN:
                pt2.add(1, 0, 1);
                break;
            case NORTH:
                pt2.add(1, 1, 0);
                break;
            case SOUTH:
                pt1.add(0, 0, 1);
                pt2.add(1, 1, 1);
                break;
            case EAST:
                pt1.add(1, 0, 0);
                pt2.add(1, 1, 1);
                break;
            case WEST:
                pt2.add(0, 1, 1);
                break;
            default:
                throw new IllegalArgumentException("Invalid BlockFace, use UP, DOWN, NORTH, SOUTH, EAST, or WEST");
        }

        drawCuboid(pt1, pt2);
    }
}

[![](https://jitpack.io/v/0xEEF/DrawLibCompat.svg)](https://jitpack.io/#0xEEF/DrawLibCompat)

# DrawLibCompat

A simple library to draw some simple shapes using particles. Fork of [DrawLib by funnyboy-roks](https://github.com/funnyboy-roks/DrawLib)

![Screenshot](./img/screenshot.png)

## Compatibility

A single compiled jar auto-detects what the running server supports at
runtime and adapts accordingly -- there's nothing to configure.

| Server software | Support |
|---|---|
| Paper / Paper forks (Purpur, Pufferfish, ...) | Full support: targeted, force-rendered particles |
| Vanilla Bukkit / CraftBukkit / Spigot | Supported: per-player targeted particles (no "force show past render distance", since that's a Paper-only flag) |
| Folia | Supported *if* you pass your `Plugin` instance to `new ShapeRenderer(plugin)` -- drawing is then automatically dispatched onto the correct region thread. Without a plugin instance, only safe if you already guarantee correct-thread calls yourself. |

| Minecraft version | Support |
|---|---|
| 1.13 and newer | Full support, accurate arbitrary particle colour via `Particle.DustOptions` (handles the `REDSTONE` → `DUST` rename in 1.20.5 automatically) |
| 1.9 - 1.12 | Supported via a legacy colour approximation (`Particle.DustOptions` doesn't exist yet on these versions) |
| Below 1.9 | Not supported -- these versions predate Bukkit's `Particle` enum entirely |

See `Compat.java` for the reflection-based detection this relies on.

## Maven

Add jitpack to your `repositories`:
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
```

Add DrawLib to your `dependencies`:
```xml
<dependency>
    <groupId>com.github.0xEEF</groupId>
    <artifactId>DrawLibCompat</artifactId>
    <version>test</version>
</dependency>
```

For other tools, see the [JitPack](https://jitpack.io/#0xEEF/DrawLibCompat) page

## Usage

```java
// Pass your plugin instance so drawing works correctly on Folia too.
// (new ShapeRenderer() with no arguments still works everywhere except Folia.)
ShapeRenderer renderer = new ShapeRenderer(myPlugin);

renderer.setColor(Color.RED);
renderer.setStepSize(0.1);
renderer.setReceivers(player);
renderer.drawLine(point1, point2);
```

## Current Shapes

- Point
- Line
- Cuboid
- Sphere

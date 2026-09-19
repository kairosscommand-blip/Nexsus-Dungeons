package com.nexuscraft.nexusdungeons;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists every dungeon's blueprint and live state to dungeons.yml -- a flat list of maps (one
 * map per dungeon), same config-driven-list-of-maps pattern NexusHouses' own TreasureRegistry
 * settled on for its chest state, for the same reason: a dungeon's name is free text and could in
 * principle collide with YAML's own path-segment syntax if it were ever used as a path key, so
 * every dungeon here is a plain map entry instead, never a path segment.
 */
final class DungeonRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Dungeon> dungeonsById = new LinkedHashMap<>();

    DungeonRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dungeons.yml");
    }

    void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        dungeonsById.clear();
        for (Map<?, ?> raw : data.getMapList("dungeons")) {
            try {
                Dungeon dungeon = fromMap(raw);
                dungeonsById.put(dungeon.id, dungeon);
            } catch (Exception e) {
                plugin.getLogger().warning("[NexusDungeons] Skipping a corrupt dungeon entry: " + e.getMessage());
            }
        }
    }

    void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Dungeon dungeon : dungeonsById.values()) {
            list.add(toMap(dungeon));
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("dungeons", list);
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[NexusDungeons] Could not save dungeons.yml: " + e.getMessage());
        }
    }

    void add(Dungeon dungeon) {
        dungeonsById.put(dungeon.id, dungeon);
        save();
    }

    boolean removeByName(String name) {
        Dungeon match = byName(name);
        if (match == null) {
            return false;
        }
        dungeonsById.remove(match.id);
        save();
        return true;
    }

    Dungeon byName(String name) {
        for (Dungeon dungeon : dungeonsById.values()) {
            if (dungeon.name.equalsIgnoreCase(name)) {
                return dungeon;
            }
        }
        return null;
    }

    Dungeon byId(UUID id) {
        return dungeonsById.get(id);
    }

    Collection<Dungeon> all() {
        return dungeonsById.values();
    }

    /** The dungeon whose carved bounding box contains this location, or null if it's outside
     *  every known dungeon -- checked on every block break/place/explode in the world, so this
     *  stays a plain linear scan over however many dungeons exist rather than a spatial index;
     *  a server running enough simultaneous huge dungeons for that to matter is not this
     *  plugin's expected scale. */
    Dungeon dungeonContaining(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (Dungeon dungeon : dungeonsById.values()) {
            if (dungeon.contains(worldName, x, y, z)) {
                return dungeon;
            }
        }
        return null;
    }

    /** The dungeon whose sealed entrance vault this exact block belongs to, or null. Checked on
     *  every right-click so DungeonEntranceListener knows when a click is "at a vault door" at
     *  all -- separate from dungeonContaining() since a not-yet-ready dungeon's entrance still
     *  needs to be found (to tell the player it's still forming) even though its bounding box is
     *  provisional until generation finishes. */
    Dungeon entranceDungeonAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (Dungeon dungeon : dungeonsById.values()) {
            if (!dungeon.entranceOpen && dungeon.isEntranceBlock(worldName, x, y, z)) {
                return dungeon;
            }
        }
        return null;
    }

    private static Map<String, Object> toMap(Dungeon d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id.toString());
        m.put("name", d.name);
        m.put("world", d.world);
        m.put("origin-x", d.originX);
        m.put("origin-y", d.originY);
        m.put("origin-z", d.originZ);
        m.put("cells-x", d.cellsX);
        m.put("cells-z", d.cellsZ);
        m.put("floors", d.floors);
        m.put("corridor-width", d.corridorWidth);
        m.put("wall-thickness", d.wallThickness);
        m.put("floor-height", d.floorHeight);
        m.put("key-required", d.keyRequired);
        m.put("seed", d.seed);
        m.put("ready", d.ready);
        m.put("cleared", d.cleared);
        m.put("entrance-x", d.entranceX);
        m.put("entrance-y", d.entranceY);
        m.put("entrance-z", d.entranceZ);
        m.put("entrance-open", d.entranceOpen);
        m.put("boss-chest-x", d.bossChestX);
        m.put("boss-chest-y", d.bossChestY);
        m.put("boss-chest-z", d.bossChestZ);
        m.put("min-x", d.minX);
        m.put("min-y", d.minY);
        m.put("min-z", d.minZ);
        m.put("max-x", d.maxX);
        m.put("max-y", d.maxY);
        m.put("max-z", d.maxZ);
        return m;
    }

    private static Dungeon fromMap(Map<?, ?> m) {
        UUID id = UUID.fromString(String.valueOf(m.get("id")));
        String name = String.valueOf(m.get("name"));
        String world = String.valueOf(m.get("world"));
        Dungeon d = new Dungeon(id, name, world,
                intOf(m, "origin-x"), intOf(m, "origin-y"), intOf(m, "origin-z"),
                intOf(m, "cells-x"), intOf(m, "cells-z"), intOf(m, "floors"),
                intOf(m, "corridor-width"), intOf(m, "wall-thickness"), intOf(m, "floor-height"),
                boolOf(m, "key-required"), longOf(m, "seed"));
        d.ready = boolOf(m, "ready");
        d.cleared = boolOf(m, "cleared");
        d.entranceX = intOf(m, "entrance-x");
        d.entranceY = intOf(m, "entrance-y");
        d.entranceZ = intOf(m, "entrance-z");
        d.entranceOpen = boolOf(m, "entrance-open");
        d.bossChestX = intOf(m, "boss-chest-x");
        d.bossChestY = intOf(m, "boss-chest-y");
        d.bossChestZ = intOf(m, "boss-chest-z");
        d.minX = intOf(m, "min-x");
        d.minY = intOf(m, "min-y");
        d.minZ = intOf(m, "min-z");
        d.maxX = intOf(m, "max-x");
        d.maxY = intOf(m, "max-y");
        d.maxZ = intOf(m, "max-z");
        return d;
    }

    private static int intOf(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? 0 : Integer.parseInt(String.valueOf(v));
    }

    private static long longOf(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? 0L : Long.parseLong(String.valueOf(v));
    }

    private static boolean boolOf(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }
}

package com.nexuscraft.nexusdungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Carves one {@link Dungeon}'s every floor into real blocks, spread across many ticks so a huge
 * dungeon never freezes the server the way doing this synchronously would. This is deliberately
 * the only class in the plugin that touches the actual world -- everything else either plans
 * (MazeGenerator/MazeLayout) or reacts to players (the listeners).
 *
 * <p><b>Geometry, floor by floor.</b> Each floor is a grid of {@code cellsX * cellsZ} logical
 * cells, {@code pitch = corridorWidth + wallThickness} blocks apart on both axes. Every cell owns
 * and writes exactly one {@code pitch x pitch} column of the world (its own corridor square, the
 * bridge to its east neighbor, the bridge to its south neighbor, and the small corner between the
 * two) -- so the whole floor is carved with no block written twice and no gap left un-owned,
 * except the floor's outer west/north perimeter, which {@link #sealPerimeter} closes in one cheap
 * pass once the cell loop finishes (the east/south perimeter falls out for free: a boundary cell's
 * "linked to a neighbor that doesn't exist" bridge is always closed by {@link MazeLayout}).
 * Every column, open or closed, gets a real solid floor slab and ceiling slab {@code wallThickness}
 * blocks thick -- an "open" column additionally has {@code floorHeight} blocks of real air between
 * them; a "closed" column is solid rock top to bottom. That, plus {@link DungeonProtectionListener}
 * cancelling every break/place/explode inside a dungeon's bounds, is what "you can't break through
 * it" actually means here: every wall is real, and every wall is protected.
 */
final class DungeonBuilder implements Runnable {

    private final JavaPlugin plugin;
    private final DungeonRegistry registry;
    private final DungeonLootTable lootTable;
    private final DungeonKeys keys;
    private final Dungeon dungeon;
    private final Random random;

    // ---- config snapshot, read once at construction ----
    private final int blocksPerTick;
    private final Material[] wallPalette;
    private final Material[] floorAccents;
    private final double floorAccentChance;
    private final int torchInterval;
    private final double extraLoopChance;
    private final double treasureChance;
    private final double cryptChance;
    private final double prisonChance;
    private final double trapChance;
    private final int chestItemsMin;
    private final int chestItemsMax;
    private final EntityType[] denizenMobs;
    private final double denizenCountPer100Cells;
    private final EntityType bossBaseType;
    private final String bossName;
    private final double bossHealthMultiplier;
    private final double bossDamageMultiplier;
    private final int bossRoomExtraHeight;

    // ---- live carve state ----
    private int floorIndex;
    private MazeLayout maze;
    private int[] farthestCell;
    private int floorBaseY;
    private int cellCursor;
    private int nextStartX;
    private int nextStartZ;
    private BukkitTask task;

    DungeonBuilder(JavaPlugin plugin, DungeonRegistry registry, DungeonLootTable lootTable, DungeonKeys keys, Dungeon dungeon) {
        this.plugin = plugin;
        this.registry = registry;
        this.lootTable = lootTable;
        this.keys = keys;
        this.dungeon = dungeon;
        this.random = new Random(dungeon.seed);

        ConfigurationSection config = plugin.getConfig();
        this.blocksPerTick = Math.max(200, config.getInt("generation.blocks-per-tick", 6000));
        this.wallPalette = materialListOrDefault(config.getStringList("palette.walls"),
                new Material[]{Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS,
                        Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.DEEPSLATE_BRICKS});
        this.floorAccents = materialListOrDefault(config.getStringList("palette.floor-accents"),
                new Material[]{Material.GRAVEL, Material.MOSS_BLOCK});
        this.floorAccentChance = config.getDouble("palette.floor-accent-chance", 0.05);
        this.torchInterval = Math.max(2, config.getInt("generation.torch-interval", 6));
        this.extraLoopChance = config.getDouble("generation.extra-loop-chance", 0.04);
        this.treasureChance = config.getDouble("rooms.treasure-chance", 0.35);
        this.cryptChance = config.getDouble("rooms.crypt-chance", 0.15);
        this.prisonChance = config.getDouble("rooms.prison-chance", 0.08);
        this.trapChance = config.getDouble("rooms.trap-chance", 0.20);
        this.chestItemsMin = Math.max(1, config.getInt("rooms.chest-items-min", 2));
        this.chestItemsMax = Math.max(chestItemsMin, config.getInt("rooms.chest-items-max", 5));
        this.denizenMobs = entityListOrDefault(config.getStringList("denizens.mobs"),
                new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
                        EntityType.CAVE_SPIDER, EntityType.HUSK, EntityType.SILVERFISH, EntityType.WITCH});
        this.denizenCountPer100Cells = config.getDouble("denizens.count-per-100-cells", 14.0);
        this.bossBaseType = entityTypeOrDefault(config.getString("boss.base-type", "WITHER_SKELETON"), EntityType.WITHER_SKELETON);
        this.bossName = config.getString("boss.name", "&4&lThe Depth Warden");
        this.bossHealthMultiplier = Math.max(1.0, config.getDouble("boss.health-multiplier", 6.0));
        this.bossDamageMultiplier = Math.max(1.0, config.getDouble("boss.damage-multiplier", 2.5));
        this.bossRoomExtraHeight = Math.max(0, config.getInt("boss.room-extra-height", 3));
    }

    void start() {
        this.floorIndex = 0;
        beginFloor(dungeon.cellsX / 2, dungeon.cellsZ / 2);
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    private void beginFloor(int startX, int startZ) {
        this.maze = MazeGenerator.generate(dungeon.cellsX, dungeon.cellsZ, startX, startZ,
                dungeon.seed + floorIndex, extraLoopChance);
        this.farthestCell = maze.farthestCell();
        this.floorBaseY = dungeon.originY - floorIndex * dungeon.floorPitchY();
        this.cellCursor = 0;
    }

    @Override
    public void run() {
        int budget = blocksPerTick;
        while (budget > 0) {
            if (cellCursor >= maze.cellsX * maze.cellsZ) {
                sealPerimeter();
                decorateFloor();
                // Checkpoint after every floor, not just at the very end -- a server restart
                // mid-generation still loses the in-progress carve (this task doesn't resume),
                // but at least leaves dungeons.yml with real, up-to-date bounds for whatever was
                // actually finished, rather than the stale single-point box from creation time.
                registry.save();
                if (floorIndex + 1 >= dungeon.floors) {
                    finish();
                    return;
                }
                floorIndex++;
                beginFloor(nextStartX, nextStartZ);
                continue;
            }
            int cx = cellCursor % maze.cellsX;
            int cz = cellCursor / maze.cellsX;
            budget -= carveCell(cx, cz);
            cellCursor++;
        }
    }

    // ---- carving ----

    private int carveCell(int cx, int cz) {
        int pitch = dungeon.pitch();
        int cw = dungeon.corridorWidth;
        int wt = dungeon.wallThickness;
        int worldX0 = dungeon.originX + cx * pitch;
        int worldZ0 = dungeon.originZ + cz * pitch;
        int fh = dungeon.floorHeight;
        boolean isBossCell = isLastFloor() && cx == farthestCell[0] && cz == farthestCell[1];
        if (isBossCell) {
            fh += bossRoomExtraHeight;
        }

        int blocks = 0;
        blocks += writeColumn(worldX0, worldZ0, cw, cw, floorBaseY, wt, fh, true);

        if (cx < maze.cellsX - 1) {
            blocks += writeColumn(worldX0 + cw, worldZ0, wt, cw, floorBaseY, wt, dungeon.floorHeight, maze.isLinkedEast(cx, cz));
        } else {
            blocks += writeColumn(worldX0 + cw, worldZ0, wt, cw, floorBaseY, wt, dungeon.floorHeight, false);
        }
        if (cz < maze.cellsZ - 1) {
            blocks += writeColumn(worldX0, worldZ0 + cw, cw, wt, floorBaseY, wt, dungeon.floorHeight, maze.isLinkedSouth(cx, cz));
        } else {
            blocks += writeColumn(worldX0, worldZ0 + cw, cw, wt, floorBaseY, wt, dungeon.floorHeight, false);
        }
        blocks += writeColumn(worldX0 + cw, worldZ0 + cw, wt, wt, floorBaseY, wt, dungeon.floorHeight, false);

        expandBoundsForCell(worldX0, worldZ0, pitch);
        return blocks;
    }

    /** Writes one owned column: a solid floor slab, then either real air (open) or solid rock
     *  (closed) for the room-height band, then a solid ceiling slab. Returns the block count so
     *  the caller can charge it against the per-tick budget. An open column's top floor layer
     *  occasionally gets a scattered accent block (gravel/moss) instead of the plain wall
     *  material, for a floor that doesn't look laid down in one uniform pass. */
    private int writeColumn(int x0, int z0, int sizeX, int sizeZ, int baseY, int wt, int fh, boolean open) {
        World world = Bukkit.getWorld(dungeon.world);
        if (world == null) {
            return sizeX * sizeZ * (fh + 2 * wt);
        }
        int count = 0;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                for (int layer = 0; layer < wt; layer++) {
                    int y = baseY - layer;
                    Material floorMat = (open && layer == 0 && random.nextDouble() < floorAccentChance)
                            ? randomOf(floorAccents) : randomOf(wallPalette);
                    world.getBlockAt(x, y, z).setType(floorMat);
                    count++;
                }
                if (open) {
                    for (int layer = 1; layer <= fh; layer++) {
                        world.getBlockAt(x, baseY + layer, z).setType(Material.AIR);
                        count++;
                    }
                } else {
                    for (int layer = 1; layer <= fh; layer++) {
                        world.getBlockAt(x, baseY + layer, z).setType(randomOf(wallPalette));
                        count++;
                    }
                }
                for (int layer = 1; layer <= wt; layer++) {
                    int y = baseY + fh + layer;
                    world.getBlockAt(x, y, z).setType(randomOf(wallPalette));
                    count++;
                }
            }
        }
        return count;
    }

    /** Closes the two sides no cell owns a wall for on its own -- the west face of column 0 and
     *  the north face of row 0. (The east/south perimeter is already solid: a boundary cell's
     *  bridge to a neighbor that doesn't exist is always closed, per {@link MazeLayout}.) Bounded
     *  by the floor's perimeter, not its area, so this runs in one go rather than being throttled. */
    private void sealPerimeter() {
        int pitch = dungeon.pitch();
        int wt = dungeon.wallThickness;
        int spanX = maze.cellsX * pitch;
        int spanZ = maze.cellsZ * pitch;
        writeColumn(dungeon.originX - wt, dungeon.originZ - wt, wt, spanZ + wt, floorBaseY, wt, dungeon.floorHeight, false);
        writeColumn(dungeon.originX, dungeon.originZ - wt, spanX, wt, floorBaseY, wt, dungeon.floorHeight, false);
    }

    private void expandBoundsForCell(int worldX0, int worldZ0, int pitch) {
        int wt = dungeon.wallThickness;
        dungeon.expandBounds(worldX0 - wt, floorBaseY - wt, worldZ0 - wt);
        dungeon.expandBounds(worldX0 + pitch, floorBaseY + dungeon.floorHeight + bossRoomExtraHeight + wt, worldZ0 + pitch);
    }

    // ---- per-floor decoration, once its structure is fully carved ----

    private void decorateFloor() {
        World world = Bukkit.getWorld(dungeon.world);
        if (world == null) {
            return;
        }

        if (floorIndex == 0) {
            carveEntranceSeal(world);
        } else {
            openShaftFromAbove(world);
        }

        placeTorches(world);
        List<int[]> deadEnds = MazeGenerator.shuffledCopy(maze.deadEnds(), random);

        boolean placedPrison = false;
        for (int[] cell : deadEnds) {
            if (cell[0] == farthestCell[0] && cell[1] == farthestCell[1]) {
                continue;
            }
            double roll = random.nextDouble();
            if (!placedPrison && roll < prisonChance) {
                placePrisonCell(world, cell[0], cell[1]);
                placedPrison = true;
            } else if (roll < prisonChance + cryptChance) {
                placeCryptRoom(world, cell[0], cell[1]);
            } else if (roll < prisonChance + cryptChance + treasureChance) {
                placeTreasureRoom(world, cell[0], cell[1]);
            } else if (roll < prisonChance + cryptChance + treasureChance + trapChance) {
                placeTrap(world, cell[0], cell[1]);
            }
        }

        placeDenizens(world);

        if (isLastFloor()) {
            placeBossRoom(world, farthestCell[0], farthestCell[1]);
        } else {
            // The actual passable shaft down to this cell is carved by the next floor's own
            // openShaftFromAbove() -- see that method's doc comment for why it has to happen
            // from below rather than here.
            this.nextStartX = farthestCell[0];
            this.nextStartZ = farthestCell[1];
        }
    }

    private void carveEntranceSeal(World world) {
        int pitch = dungeon.pitch();
        int wt = dungeon.wallThickness;
        int cw = dungeon.corridorWidth;
        int cellWorldX0 = dungeon.originX + maze.startX * pitch;
        int cellWorldZ0 = dungeon.originZ + maze.startZ * pitch;
        int sealX0 = cellWorldX0 - wt;
        int sealY0 = floorBaseY + 1;
        int sealZ0 = cellWorldZ0;
        for (int dx = 0; dx < wt; dx++) {
            for (int dy = 0; dy < dungeon.floorHeight; dy++) {
                for (int dz = 0; dz < cw; dz++) {
                    world.getBlockAt(sealX0 + dx, sealY0 + dy, sealZ0 + dz).setType(Material.IRON_BLOCK);
                }
            }
        }
        dungeon.entranceX = sealX0;
        dungeon.entranceY = sealY0;
        dungeon.entranceZ = sealZ0;
    }

    private void placeTorches(World world) {
        int pitch = dungeon.pitch();
        int cw = dungeon.corridorWidth;
        int count = 0;
        for (int z = 0; z < maze.cellsZ; z++) {
            for (int x = 0; x < maze.cellsX; x++) {
                if (maze.degree(x, z) == 0) {
                    continue;
                }
                count++;
                if (count % torchInterval != 0) {
                    continue;
                }
                int wx = dungeon.originX + x * pitch;
                int wz = dungeon.originZ + z * pitch;
                world.getBlockAt(wx, floorBaseY + 1, wz + Math.max(0, cw - 1)).setType(Material.TORCH);
            }
        }
    }

    private void placeTreasureRoom(World world, int cx, int cz) {
        Location center = cellCenter(cx, cz);
        Block block = world.getBlockAt(center.getBlockX(), floorBaseY + 1, center.getBlockZ());
        block.setType(Material.CHEST);
        fillChest(block, "common");
    }

    private void placeCryptRoom(World world, int cx, int cz) {
        Location center = cellCenter(cx, cz);
        int x = center.getBlockX();
        int z = center.getBlockZ();
        Block block = world.getBlockAt(x, floorBaseY + 1, z);
        block.setType(Material.CHEST);
        fillChest(block, "crypt");
        world.getBlockAt(x + 1, floorBaseY + 1, z).setType(Material.BONE_BLOCK);
        world.getBlockAt(x - 1, floorBaseY + dungeon.floorHeight, z).setType(Material.COBWEB);
        spawnDenizen(world, x, floorBaseY + 1, z, EntityType.SKELETON, null);
    }

    private void placePrisonCell(World world, int cx, int cz) {
        Location center = cellCenter(cx, cz);
        int pitch = dungeon.pitch();
        int worldX0 = dungeon.originX + cx * pitch;
        int worldZ0 = dungeon.originZ + cz * pitch;
        // The door sits on the cell's own north wall column; a lever one block outside it is the
        // only real way to open it -- vanilla iron doors already can't be hand-opened, which is
        // the whole "prisoners can't just let themselves out" mechanic, for free.
        int doorX = center.getBlockX();
        int doorZ = worldZ0;
        world.getBlockAt(doorX, floorBaseY + 1, doorZ).setType(Material.IRON_DOOR);
        world.getBlockAt(doorX, floorBaseY + 2, doorZ).setType(Material.IRON_DOOR);
        world.getBlockAt(doorX, floorBaseY + 1, doorZ - 1).setType(Material.LEVER);
    }

    private void placeTrap(World world, int cx, int cz) {
        Location center = cellCenter(cx, cz);
        world.getBlockAt(center.getBlockX(), floorBaseY + 1, center.getBlockZ()).setType(Material.STONE_PRESSURE_PLATE);
    }

    private void placeDenizens(World world) {
        int cellCount = maze.cellsX * maze.cellsZ;
        int target = Math.max(1, (int) Math.round(cellCount * (denizenCountPer100Cells / 100.0)));
        for (int i = 0; i < target; i++) {
            int cx = random.nextInt(maze.cellsX);
            int cz = random.nextInt(maze.cellsZ);
            if (maze.degree(cx, cz) == 0) {
                continue;
            }
            Location center = cellCenter(cx, cz);
            EntityType type = randomOf(denizenMobs);
            spawnDenizen(world, center.getBlockX(), floorBaseY + 1, center.getBlockZ(), type, null);
        }
    }

    /** Carves the one real, passable connection between this floor and the floor directly above
     *  it -- a solid column of LADDER (itself climbable, so no separate air hole is needed)
     *  running from this floor's own walkable surface up through the floor above's floor slab to
     *  that floor's walkable surface. Deliberately done from THIS (lower) floor rather than the
     *  one above at the moment its farthest cell is chosen: the floor above finishes its own
     *  structural carve (and picks that farthest cell) before this floor's geometry -- in
     *  particular this floor's own floorBaseY, which the shaft's top end is computed from --
     *  even exists yet. this floor's start cell is always exactly that farthest cell (see
     *  DungeonBuilder#run()), so maze.startX/startZ already names the right column. */
    private void openShaftFromAbove(World world) {
        int aboveFloorBaseY = floorBaseY + dungeon.floorPitchY();
        Location center = cellCenter(maze.startX, maze.startZ);
        int x = center.getBlockX();
        int z = center.getBlockZ();
        for (int y = floorBaseY + 1; y <= aboveFloorBaseY; y++) {
            world.getBlockAt(x, y, z).setType(Material.LADDER);
        }
    }

    private void placeBossRoom(World world, int cx, int cz) {
        Location center = cellCenter(cx, cz);
        int x = center.getBlockX();
        int z = center.getBlockZ();

        world.getBlockAt(x - 1, floorBaseY + 1, z).setType(Material.IRON_BARS);
        world.getBlockAt(x + 1, floorBaseY + 1, z).setType(Material.IRON_BARS);
        world.getBlockAt(x, floorBaseY + 1, z - 1).setType(Material.WITHER_SKELETON_SKULL);

        LivingEntity boss = (LivingEntity) world.spawnEntity(new Location(world, x, floorBaseY + 1, z), bossBaseType);
        boss.getPersistentDataContainer().set(keys.dungeonBossId, DungeonKeys.STRING, dungeon.id.toString());
        boss.setCustomName(Colors.color(bossName));
        boss.setCustomNameVisible(true);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(boss.getAttribute(Attribute.MAX_HEALTH).getBaseValue() * bossHealthMultiplier);
        boss.setHealth(boss.getAttribute(Attribute.MAX_HEALTH).getValue());
        boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(boss.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() * bossDamageMultiplier);

        dungeon.bossChestX = x + 2;
        dungeon.bossChestY = floorBaseY + 1;
        dungeon.bossChestZ = z;
        // Left as AIR on purpose -- DungeonBossListener places and fills the real chest here the
        // instant this boss dies, rather than pre-placing it sealed behind a marker block.
    }

    // ---- helpers ----

    private void fillChest(Block block, String tier) {
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
            return;
        }
        org.bukkit.inventory.Inventory inventory = chest.getInventory();
        for (ItemStack item : lootTable.roll(tier, chestItemsMin, chestItemsMax, random)) {
            inventory.addItem(item);
        }
    }

    private void spawnDenizen(World world, int x, int y, int z, EntityType type, String nameOrNull) {
        LivingEntity entity = (LivingEntity) world.spawnEntity(new Location(world, x, y, z), type);
        if (nameOrNull != null) {
            entity.setCustomName(Colors.color(nameOrNull));
            entity.setCustomNameVisible(true);
        }
    }

    private Location cellCenter(int cx, int cz) {
        int pitch = dungeon.pitch();
        int cw = dungeon.corridorWidth;
        int wx = dungeon.originX + cx * pitch + cw / 2;
        int wz = dungeon.originZ + cz * pitch + cw / 2;
        World world = Bukkit.getWorld(dungeon.world);
        return new Location(world, wx, floorBaseY, wz);
    }

    private boolean isLastFloor() {
        return floorIndex + 1 == dungeon.floors;
    }

    private void finish() {
        if (task != null) {
            task.cancel();
        }
        dungeon.ready = true;
        registry.save();
        plugin.getLogger().info("[NexusDungeons] Finished carving dungeon '" + dungeon.name + "' ("
                + dungeon.floors + " floor(s)).");
    }

    private Material randomOf(Material[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    private <T> T randomOf(T[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    private static Material[] materialListOrDefault(List<String> names, Material[] fallback) {
        if (names == null || names.isEmpty()) {
            return fallback;
        }
        java.util.List<Material> out = new java.util.ArrayList<>();
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                out.add(m);
            }
        }
        return out.isEmpty() ? fallback : out.toArray(new Material[0]);
    }

    private static EntityType[] entityListOrDefault(List<String> names, EntityType[] fallback) {
        if (names == null || names.isEmpty()) {
            return fallback;
        }
        java.util.List<EntityType> out = new java.util.ArrayList<>();
        for (String name : names) {
            EntityType type = entityTypeOrDefault(name, null);
            if (type != null) {
                out.add(type);
            }
        }
        return out.isEmpty() ? fallback : out.toArray(new EntityType[0]);
    }

    private static EntityType entityTypeOrDefault(String name, EntityType fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

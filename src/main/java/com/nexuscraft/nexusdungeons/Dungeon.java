package com.nexuscraft.nexusdungeons;

import java.util.UUID;

/**
 * One dungeon's full blueprint plus its live state. The blueprint fields (everything but the
 * last few) are fixed the instant {@code /dungeons create} runs and never change afterward; the
 * state fields fill in as {@link DungeonBuilder} finishes carving it, since a dungeon this size
 * takes real time (many ticks) to generate and isn't safe to enter -- or even protect -- until
 * that's done.
 */
final class Dungeon {

    final UUID id;
    final String name;
    final String world;

    /** The floor-1 start cell's lowest solid-floor block -- everything else (per-floor Y, per-cell
     *  X/Z) is computed from this plus the geometry fields below, same convention DungeonBuilder,
     *  the protection listener, and the entrance/boss-chest lookups all share. */
    final int originX;
    final int originY;
    final int originZ;

    final int cellsX;
    final int cellsZ;
    final int floors;
    final int corridorWidth;
    final int wallThickness;
    final int floorHeight;
    final boolean keyRequired;
    final long seed;

    /** True once DungeonBuilder has finished carving every floor -- before that, the bounding box
     *  below is provisional (still growing) and the dungeon is not enterable. */
    volatile boolean ready;

    /** True once the final boss has been killed -- unseals the boss loot chest and is shown in
     *  /dungeons info and /dungeons list. */
    volatile boolean cleared;

    /** The sealed vault entrance's two-wide, three-tall marker blocks -- see DungeonBuilder's
     *  carving of the start cell's west wall. Right-clicking any of these six blocks is what
     *  DungeonEntranceListener listens for. */
    int entranceX;
    int entranceY;
    int entranceZ;

    /** True once the entrance vault has actually been opened (key spent, or none required) --
     *  after that it stays open forever, same as a real door doesn't re-lock itself. */
    volatile boolean entranceOpen;

    /** The single sealed chest behind the final boss -- DungeonBossListener unseals the block
     *  directly above it (an IRON_BLOCK marker, same sealed-vault pattern as the entrance) the
     *  instant that boss dies. */
    int bossChestX;
    int bossChestY;
    int bossChestZ;

    /** The whole carved volume's bounding box, in world coordinates -- grows floor by floor as
     *  DungeonBuilder works and is final once ready is true. Every block-protection check in
     *  DungeonProtectionListener is just "is this location inside this cuboid". */
    int minX;
    int minY;
    int minZ;
    int maxX;
    int maxY;
    int maxZ;

    Dungeon(UUID id, String name, String world, int originX, int originY, int originZ,
            int cellsX, int cellsZ, int floors, int corridorWidth, int wallThickness, int floorHeight,
            boolean keyRequired, long seed) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        this.floors = floors;
        this.corridorWidth = corridorWidth;
        this.wallThickness = wallThickness;
        this.floorHeight = floorHeight;
        this.keyRequired = keyRequired;
        this.seed = seed;
        this.minX = originX;
        this.maxX = originX;
        this.minY = originY;
        this.maxY = originY;
        this.minZ = originZ;
        this.maxZ = originZ;
    }

    /** How many logical blocks between the same point on one cell and the next -- the corridor
     *  itself plus the wall (and, at the boundary, half the neighboring wall) separating it from
     *  its neighbor. Every coordinate conversion in DungeonBuilder is built from this one number. */
    int pitch() {
        return corridorWidth + wallThickness;
    }

    /** The real, whole-block Y gap between one floor's carved ceiling and the next floor's carved
     *  floor below it -- room height plus a floor slab and a ceiling slab. */
    int floorPitchY() {
        return floorHeight + wallThickness + wallThickness;
    }

    void expandBounds(int x, int y, int z) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        if (z < minZ) minZ = z;
        if (z > maxZ) maxZ = z;
    }

    boolean contains(String worldName, int x, int y, int z) {
        return world.equalsIgnoreCase(worldName)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /** Whether (x,y,z) is one of the sealed entrance vault's marker blocks -- the plug
     *  DungeonBuilder#carveEntranceSeal filled with IRON_BLOCK, wallThickness blocks deep,
     *  floorHeight blocks tall, corridorWidth blocks wide, starting at entranceX/Y/Z. */
    boolean isEntranceBlock(String worldName, int x, int y, int z) {
        return world.equalsIgnoreCase(worldName)
                && x >= entranceX && x < entranceX + wallThickness
                && y >= entranceY && y < entranceY + floorHeight
                && z >= entranceZ && z < entranceZ + corridorWidth;
    }
}

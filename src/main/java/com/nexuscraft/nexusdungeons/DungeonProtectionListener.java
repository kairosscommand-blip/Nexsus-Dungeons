package com.nexuscraft.nexusdungeons;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.Iterator;

/**
 * This is what "you can't break through it" actually enforces at the player-action level (real,
 * solid walls -- see DungeonBuilder's own class comment -- are what makes it true in the first
 * place; this is what keeps it true forever after). Every block inside any dungeon's carved
 * bounding box is off-limits to breaking, placing, and exploding, full stop -- no exceptions for
 * "but this one's air" or "but this one's a chest", since a maze is only a maze while none of its
 * walls can be shortcut. A player can still freely interact with what a dungeon actually placed
 * for them (open a chest, flip a lever, walk through a door) -- none of that is a break or a
 * place, so none of it is touched here.
 */
public final class DungeonProtectionListener implements Listener {

    private final DungeonRegistry registry;

    public DungeonProtectionListener(DungeonRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (inDungeon(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (inDungeon(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (inDungeon(event.getBlockClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        stripProtectedBlocks(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        stripProtectedBlocks(event.blockList());
    }

    /** Explosions are allowed to still hurt entities inside a dungeon (a creeper ambush should
     *  stay dangerous) -- only the block damage is stripped, and only for blocks actually inside
     *  a dungeon, so an explosion straddling a dungeon's boundary still damages terrain outside it
     *  normally. */
    private void stripProtectedBlocks(java.util.List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (inDungeon(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private boolean inDungeon(Block block) {
        return block != null && registry.dungeonContaining(block.getLocation()) != null;
    }
}

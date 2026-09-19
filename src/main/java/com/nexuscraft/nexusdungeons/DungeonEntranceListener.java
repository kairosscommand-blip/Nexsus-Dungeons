package com.nexuscraft.nexusdungeons;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking any block of a dungeon's sealed IRON_BLOCK vault entrance (see
 * DungeonBuilder#carveEntranceSeal) is what actually opens it. A key-gated dungeon needs the
 * matching, unspoofable-tagged key ({@link DungeonKeys#dungeonKeyId}) in hand -- consumed on
 * success, same one-shot-use-it-up shape every other consumable item in this project family uses
 * -- and a dungeon that isn't finished generating yet simply can't be opened at all, key or not.
 */
public final class DungeonEntranceListener implements Listener {

    private final DungeonRegistry registry;
    private final DungeonKeys keys;

    public DungeonEntranceListener(DungeonRegistry registry, DungeonKeys keys) {
        this.registry = registry;
        this.keys = keys;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
            return;
        }
        Dungeon dungeon = registry.entranceDungeonAt(event.getClickedBlock().getLocation());
        if (dungeon == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!dungeon.ready) {
            player.sendMessage(Colors.color("&7This dungeon is still being carved -- come back shortly."));
            return;
        }

        if (dungeon.keyRequired) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (!isKeyFor(inHand, dungeon)) {
                player.sendMessage(Colors.color("&cThis vault is sealed. You need " + dungeon.name + "'s key to open it."));
                return;
            }
            int remaining = inHand.getAmount() - 1;
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                inHand.setAmount(remaining);
            }
        }

        openEntrance(dungeon);
        player.sendMessage(Colors.color("&6The vault door to " + dungeon.name + " grinds open..."));
    }

    private boolean isKeyFor(ItemStack item, Dungeon dungeon) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keys.dungeonKeyId, DungeonKeys.STRING);
        return dungeon.id.toString().equals(id);
    }

    private void openEntrance(Dungeon dungeon) {
        World world = org.bukkit.Bukkit.getWorld(dungeon.world);
        if (world != null) {
            for (int dx = 0; dx < dungeon.wallThickness; dx++) {
                for (int dy = 0; dy < dungeon.floorHeight; dy++) {
                    for (int dz = 0; dz < dungeon.corridorWidth; dz++) {
                        world.getBlockAt(dungeon.entranceX + dx, dungeon.entranceY + dy, dungeon.entranceZ + dz)
                                .setType(Material.AIR);
                    }
                }
            }
        }
        dungeon.entranceOpen = true;
        registry.save();
    }
}

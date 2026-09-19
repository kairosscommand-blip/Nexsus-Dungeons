package com.nexuscraft.nexusdungeons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.UUID;

/**
 * The final payoff: when a mob tagged as a dungeon's boss (see DungeonBuilder#placeBossRoom)
 * actually dies, this places and fills the real chest behind it -- left as bare AIR until this
 * moment, rather than a pre-placed-but-sealed chest, so there is genuinely nothing to find back
 * there before the boss is beaten -- marks the dungeon cleared, and credits the killer's whole
 * party, not just whoever landed the final hit.
 */
public final class DungeonBossListener implements Listener {

    private final JavaPlugin plugin;
    private final DungeonRegistry registry;
    private final DungeonLootTable lootTable;
    private final DungeonKeys keys;
    private final DungeonParty party;
    private final PulseBridge pulse;
    private final int chestItemsMin;
    private final int chestItemsMax;
    private final Random random = new Random();

    public DungeonBossListener(JavaPlugin plugin, DungeonRegistry registry, DungeonLootTable lootTable,
            DungeonKeys keys, DungeonParty party) {
        this.plugin = plugin;
        this.registry = registry;
        this.lootTable = lootTable;
        this.keys = keys;
        this.party = party;
        this.pulse = new PulseBridge(plugin);
        this.chestItemsMin = Math.max(1, plugin.getConfig().getInt("boss.chest-items-min", 4));
        this.chestItemsMax = Math.max(chestItemsMin, plugin.getConfig().getInt("boss.chest-items-max", 7));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        String dungeonIdRaw = entity.getPersistentDataContainer().get(keys.dungeonBossId, DungeonKeys.STRING);
        if (dungeonIdRaw == null) {
            return;
        }
        UUID dungeonId;
        try {
            dungeonId = UUID.fromString(dungeonIdRaw);
        } catch (IllegalArgumentException badId) {
            return;
        }
        Dungeon dungeon = registry.byId(dungeonId);
        if (dungeon == null || dungeon.cleared) {
            return;
        }

        dungeon.cleared = true;
        registry.save();

        World world = Bukkit.getWorld(dungeon.world);
        if (world != null) {
            Block chestBlock = world.getBlockAt(dungeon.bossChestX, dungeon.bossChestY, dungeon.bossChestZ);
            chestBlock.setType(Material.TRAPPED_CHEST);
            if (chestBlock.getState() instanceof org.bukkit.block.Chest chest) {
                for (ItemStack item : lootTable.roll("boss", chestItemsMin, chestItemsMax, random)) {
                    chest.getInventory().addItem(item);
                }
            }
        }

        String message = Colors.color("&6&l" + dungeon.name + " &r&6has fallen! The depths grow quiet.");
        Player killer = entity.getKiller();
        if (killer != null) {
            for (UUID memberId : party.partyOf(killer.getUniqueId())) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(message);
                }
            }
        } else {
            Bukkit.broadcastMessage(message);
        }

        pulse.submit("DUNGEON", dungeon.name + " has been cleared!");
    }
}

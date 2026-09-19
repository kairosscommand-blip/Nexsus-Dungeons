package com.nexuscraft.nexusdungeons;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Stepping on one of the STONE_PRESSURE_PLATE traps DungeonBuilder scatters through a floor's
 * dead ends fires a real vanilla {@code PlayerInteractEvent} with {@link Action#PHYSICAL} --
 * this listens for exactly that, inside a dungeon's bounds, and answers with real damage and a
 * chance of an ambush. Deliberately never touches a single block (no explosion, no cave-in) --
 * whatever a trap does to a player, the maze around them stays exactly as solid and as protected
 * as DungeonProtectionListener promises.
 */
public final class DungeonTrapListener implements Listener {

    private final DungeonRegistry registry;
    private final double damageMin;
    private final double damageMax;
    private final double ambushMobChance;
    private final long cooldownMillis;
    private final EntityType[] ambushMobs;
    private final Random random = new Random();
    private final Map<String, Long> lastTriggered = new HashMap<>();

    public DungeonTrapListener(JavaPlugin plugin, DungeonRegistry registry) {
        this.registry = registry;
        this.damageMin = plugin.getConfig().getDouble("traps.damage-min", 3.0);
        this.damageMax = Math.max(damageMin, plugin.getConfig().getDouble("traps.damage-max", 7.0));
        this.ambushMobChance = plugin.getConfig().getDouble("traps.ambush-mob-chance", 0.4);
        this.cooldownMillis = Math.max(1, plugin.getConfig().getInt("traps.cooldown-seconds", 5)) * 1000L;
        this.ambushMobs = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
    }

    @EventHandler
    public void onTrigger(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || !event.hasBlock()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != Material.STONE_PRESSURE_PLATE) {
            return;
        }
        if (registry.dungeonContaining(block.getLocation()) == null) {
            return;
        }

        String key = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        long now = System.currentTimeMillis();
        Long last = lastTriggered.get(key);
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastTriggered.put(key, now);

        Player player = event.getPlayer();
        double damage = damageMin + random.nextDouble() * (damageMax - damageMin);
        player.setHealth(Math.max(0.0, player.getHealth() - damage));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 20);
        player.sendMessage(Colors.color("&cA trap springs beneath your feet!"));

        if (random.nextDouble() < ambushMobChance) {
            spawnAmbush(block, player);
        }
    }

    private void spawnAmbush(Block block, Player player) {
        World world = block.getWorld();
        EntityType type = ambushMobs[random.nextInt(ambushMobs.length)];
        Location at = block.getLocation().add(0, 1, 0);
        LivingEntity mob = (LivingEntity) world.spawnEntity(at, type);
        mob.setCustomName(Colors.color("&cAmbush!"));
        player.sendMessage(Colors.color("&4Something was waiting in the dark..."));
    }
}

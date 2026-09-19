package com.nexuscraft.nexusdungeons;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Reports a "dungeon.kill" NexusEndeavors progress point every time a player lands a kill while
 * standing inside a dungeon's carved bounds -- not tied to the boss specifically (see
 * DungeonBossListener for that separate payoff), just ordinary denizen kills, since that's the
 * steady, repeatable activity an endeavor objective is meant to reward. Attributed by the KILLING
 * PLAYER's own location at the moment of the kill, not the dead mob's (this stub's Entity#getLocation()
 * has no real body -- see Entity.java's own class -- but LivingEntity#getKiller() returns a real
 * Player, whose getLocation() does), which is also the more correct real-world semantics: what
 * matters is that the player was in the dungeon when they did it.
 */
final class DungeonEndeavorsListener implements Listener {

    private final DungeonRegistry registry;
    private final EndeavorsBridge endeavors;

    DungeonEndeavorsListener(DungeonRegistry registry, EndeavorsBridge endeavors) {
        this.registry = registry;
        this.endeavors = endeavors;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null || killer.getLocation() == null) {
            return;
        }
        if (registry.dungeonContaining(killer.getLocation()) == null) {
            return;
        }
        endeavors.report(killer.getUniqueId(), killer.getName(), "dungeon.kill", 1);
    }
}

package com.nexuscraft.nexusdungeons;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PersistentDataContainer keys used across this plugin -- same unspoofable-tag philosophy every
 * other Nexus plugin uses: a dungeon key item is tagged with the exact dungeon it opens the
 * instant it's minted ({@link DungeonCommandExecutor}), and a boss mob is tagged with the exact
 * dungeon it belongs to the instant it's spawned ({@link DungeonBuilder}) -- never identified by
 * display name, lore, or "the nearest boss-shaped mob," any of which could desync or be spoofed.
 */
public final class DungeonKeys {

    public final NamespacedKey dungeonKeyId;
    public final NamespacedKey dungeonBossId;

    public DungeonKeys(JavaPlugin plugin) {
        this.dungeonKeyId = new NamespacedKey(plugin, "dungeon-key-id");
        this.dungeonBossId = new NamespacedKey(plugin, "dungeon-boss-id");
    }

    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
}

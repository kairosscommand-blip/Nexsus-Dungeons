package com.nexuscraft.nexusdungeons;

import org.bukkit.plugin.java.JavaPlugin;

public final class NexusDungeons extends JavaPlugin {

    private DungeonRegistry registry;
    private DungeonLootTable lootTable;
    private DungeonKeys keys;
    private DungeonParty party;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.keys = new DungeonKeys(this);
        this.party = new DungeonParty();
        this.registry = new DungeonRegistry(this);
        registry.load();
        this.lootTable = new DungeonLootTable(this);
        lootTable.load();

        getServer().getPluginManager().registerEvents(new DungeonProtectionListener(registry), this);
        getServer().getPluginManager().registerEvents(new DungeonEntranceListener(registry, keys), this);
        getServer().getPluginManager().registerEvents(new DungeonTrapListener(this, registry), this);
        getServer().getPluginManager().registerEvents(new DungeonBossListener(this, registry, lootTable, keys, party), this);

        getCommand("dungeons").setExecutor(new DungeonCommandExecutor(this, registry, lootTable, keys, party));

        int forming = 0;
        for (Dungeon dungeon : registry.all()) {
            if (!dungeon.ready) {
                forming++;
            }
        }
        getLogger().info("NexusDungeons enabled. " + registry.all().size() + " dungeon(s) known"
                + (forming > 0 ? " (" + forming + " still forming as of last shutdown -- see README's "
                    + "honest limitation on generation that was interrupted mid-carve)." : "."));
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.save();
        }
        getLogger().info("NexusDungeons disabled.");
    }

    /** Reloads config.yml and re-applies settings to the loot table. Generation settings
     *  (corridor width, palettes, etc.) only take effect for dungeons created after the reload --
     *  an in-progress or already-carved dungeon keeps the shape it was built with. */
    public void reloadAll() {
        reloadConfig();
        lootTable.load();
    }
}

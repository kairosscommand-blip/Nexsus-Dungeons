package com.nexuscraft.nexusdungeons;

import org.bukkit.Material;

/** One weighted entry in a loot tier -- see {@link DungeonLootTable}. */
record LootItem(Material material, int min, int max, int weight) {
}

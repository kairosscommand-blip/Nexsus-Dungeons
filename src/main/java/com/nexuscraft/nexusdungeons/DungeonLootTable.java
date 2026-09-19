package com.nexuscraft.nexusdungeons;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Three independent weighted loot pools -- common (ordinary treasure-room chests), crypt (tomb/
 * ruin-flavored rooms), and boss (the one chest behind the final fight) -- loaded from
 * {@code loot.<tier>} in config.yml. Same weighted-roll-with-repeats-allowed shape as
 * NexusHouses' own TreasureLootTable and NexusHatchlings' trait roll, and the same defensive
 * per-entry config parsing every loot/trait table in this project family uses: one malformed
 * entry is skipped with a logged warning, never fatal to the rest of the pool.
 */
final class DungeonLootTable {

    private final JavaPlugin plugin;
    private final Map<String, List<LootItem>> tiers = new HashMap<>();

    DungeonLootTable(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        tiers.clear();
        ConfigurationSection loot = plugin.getConfig().getConfigurationSection("loot");
        if (loot == null) {
            return;
        }
        for (String tierName : loot.getKeys(false)) {
            ConfigurationSection tierSection = loot.getConfigurationSection(tierName);
            if (tierSection == null) {
                continue;
            }
            List<LootItem> items = new ArrayList<>();
            for (Map<?, ?> raw : tierSection.getMapList("items")) {
                LootItem item = parseItem(tierName, raw);
                if (item != null) {
                    items.add(item);
                }
            }
            tiers.put(tierName.toLowerCase(java.util.Locale.ROOT), items);
        }
    }

    private LootItem parseItem(String tierName, Map<?, ?> raw) {
        Object materialObj = raw.get("material");
        Material material = materialObj != null ? Material.matchMaterial(String.valueOf(materialObj)) : null;
        if (material == null) {
            plugin.getLogger().warning("[NexusDungeons] Skipping a malformed loot entry in tier '"
                    + tierName + "' (bad/missing material): " + raw);
            return null;
        }
        int min = intOf(raw, "min", 1);
        int max = intOf(raw, "max", Math.max(1, min));
        int weight = intOf(raw, "weight", 0);
        if (weight <= 0 || min <= 0 || max < min) {
            plugin.getLogger().warning("[NexusDungeons] Skipping a malformed loot entry in tier '"
                    + tierName + "' (bad min/max/weight): " + raw);
            return null;
        }
        return new LootItem(material, min, max, weight);
    }

    private int intOf(Map<?, ?> raw, String key, int def) {
        Object v = raw.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Rolls stackCount independent items (repeats allowed, each with its own random amount)
     *  from the named tier. An unknown/empty tier just yields nothing rather than erroring --
     *  a dungeon carved before a tier existed in config shouldn't fail to place its chests. */
    List<ItemStack> roll(String tier, int stackCountMin, int stackCountMax, Random random) {
        List<ItemStack> result = new ArrayList<>();
        List<LootItem> pool = tiers.get(tier.toLowerCase(java.util.Locale.ROOT));
        if (pool == null || pool.isEmpty()) {
            return result;
        }
        int totalWeight = 0;
        for (LootItem item : pool) {
            totalWeight += item.weight();
        }
        if (totalWeight <= 0) {
            return result;
        }
        int stackCount = stackCountMin + (stackCountMax > stackCountMin
                ? random.nextInt(stackCountMax - stackCountMin + 1) : 0);
        for (int i = 0; i < stackCount; i++) {
            int roll = random.nextInt(totalWeight);
            int cursor = 0;
            for (LootItem item : pool) {
                cursor += item.weight();
                if (roll < cursor) {
                    int amount = item.min() + (item.max() > item.min()
                            ? random.nextInt(item.max() - item.min() + 1) : 0);
                    result.add(new ItemStack(item.material(), amount));
                    break;
                }
            }
        }
        return result;
    }
}

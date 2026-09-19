package com.nexuscraft.nexusdungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public final class DungeonCommandExecutor implements CommandExecutor {

    private final NexusDungeons plugin;
    private final DungeonRegistry registry;
    private final DungeonLootTable lootTable;
    private final DungeonKeys keys;
    private final DungeonParty party;

    public DungeonCommandExecutor(NexusDungeons plugin, DungeonRegistry registry, DungeonLootTable lootTable,
            DungeonKeys keys, DungeonParty party) {
        this.plugin = plugin;
        this.registry = registry;
        this.lootTable = lootTable;
        this.keys = keys;
        this.party = party;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                handleCreate(sender, args);
                return true;
            case "list":
                handleList(sender);
                return true;
            case "info":
                handleInfo(sender, args);
                return true;
            case "remove":
                handleRemove(sender, args);
                return true;
            case "key":
                handleKey(sender, args);
                return true;
            case "party":
                handleParty(sender, args);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusdungeons.admin")) {
            sender.sendMessage(color("&cYou don't have permission for that."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly a player can do that (the dungeon is centered on where you stand)."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(color("&6Usage: &f/dungeons create <name> [floors] [cellSize]"));
            return;
        }
        String name = args[1];
        if (registry.byName(name) != null) {
            player.sendMessage(color("&cA dungeon by that name already exists."));
            return;
        }

        int maxFloors = Math.max(1, plugin.getConfig().getInt("generation.max-floors", 6));
        int maxCells = Math.max(11, plugin.getConfig().getInt("generation.max-cells", 141));
        int defaultFloors = plugin.getConfig().getInt("generation.default-floors", 3);
        int defaultCells = plugin.getConfig().getInt("generation.default-cells", 61);

        int floors = intArgClamped(args, 2, defaultFloors, 1, maxFloors);
        int cells = intArgClamped(args, 3, defaultCells, 11, maxCells);
        if (cells % 2 == 0) {
            cells += 1; // an odd cell count keeps the grid's midline (the default start column) centered
        }

        int corridorWidth = Math.max(1, plugin.getConfig().getInt("generation.corridor-width", 3));
        int wallThickness = Math.max(1, plugin.getConfig().getInt("generation.wall-thickness", 2));
        int floorHeight = Math.max(2, plugin.getConfig().getInt("generation.floor-height", 4));
        boolean keyRequired = plugin.getConfig().getBoolean("keys.required-by-default", true);

        Location origin = player.getLocation();
        Dungeon dungeon = new Dungeon(UUID.randomUUID(), name, origin.getWorld().getName(),
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                cells, cells, floors, corridorWidth, wallThickness, floorHeight,
                keyRequired, System.currentTimeMillis());
        registry.add(dungeon);

        new DungeonBuilder(plugin, registry, lootTable, keys, dungeon).start();

        player.sendMessage(color("&6Carving " + name + ": " + floors + " floor(s), " + cells + "x" + cells
                + " cells each. This happens gradually in the background -- it will announce itself when ready."));
    }

    private int intArgClamped(String[] args, int index, int def, int min, int max) {
        if (args.length <= index) {
            return def;
        }
        try {
            int value = Integer.parseInt(args[index]);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void handleList(CommandSender sender) {
        if (registry.all().isEmpty()) {
            sender.sendMessage(color("&7No dungeons have been carved yet."));
            return;
        }
        sender.sendMessage(color("&6Dungeons:"));
        for (Dungeon dungeon : registry.all()) {
            String status = !dungeon.ready ? "&7(still forming)" : dungeon.cleared ? "&aCleared" : "&eUncleared";
            sender.sendMessage(color("&7- &f" + dungeon.name + " &7(" + dungeon.floors + " floor(s)) " + status));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color("&6Usage: &f/dungeons info <name>"));
            return;
        }
        Dungeon dungeon = registry.byName(args[1]);
        if (dungeon == null) {
            sender.sendMessage(color("&cNo dungeon by that name."));
            return;
        }
        sender.sendMessage(color("&6" + dungeon.name + " &7-- " + dungeon.floors + " floor(s), "
                + dungeon.cellsX + "x" + dungeon.cellsZ + " cells, world " + dungeon.world));
        sender.sendMessage(color("&7Status: " + (!dungeon.ready ? "&7still forming"
                : dungeon.cleared ? "&aboss defeated" : "&euncleared")));
        sender.sendMessage(color("&7Key required: " + (dungeon.keyRequired ? "&fyes" : "&fno")
                + " &7-- Entrance: " + (dungeon.entranceOpen ? "&fopen" : "&fsealed")));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusdungeons.admin")) {
            sender.sendMessage(color("&cYou don't have permission for that."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(color("&6Usage: &f/dungeons remove <name>"));
            return;
        }
        if (registry.removeByName(args[1])) {
            sender.sendMessage(color("&aDungeon " + args[1] + " removed from tracking. Its carved blocks are"
                    + " left in the world (and no longer protected) -- this doesn't undo the dig."));
        } else {
            sender.sendMessage(color("&cNo dungeon by that name."));
        }
    }

    private void handleKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusdungeons.admin")) {
            sender.sendMessage(color("&cYou don't have permission for that."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(color("&6Usage: &f/dungeons key <player> <dungeonName>"));
            return;
        }
        Dungeon dungeon = registry.byName(args[2]);
        if (dungeon == null) {
            sender.sendMessage(color("&cNo dungeon by that name."));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(color("&cThat player isn't online."));
            return;
        }
        target.getInventory().addItem(buildKeyItem(dungeon));
        sender.sendMessage(color("&aGave " + target.getName() + " a key to " + dungeon.name + "."));
    }

    private ItemStack buildKeyItem(Dungeon dungeon) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&lKey to " + dungeon.name));
        meta.setLore(List.of(color("&7A sealed writ of passage."), color("&7Use it at the vault door.")));
        meta.getPersistentDataContainer().set(keys.dungeonKeyId, DungeonKeys.STRING, dungeon.id.toString());
        item.setItemMeta(meta);
        return item;
    }

    // ---- party ----

    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly a player can do that."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(color("&6Usage: &f/dungeons party <invite|accept|leave|disband>"));
            return;
        }
        String partySub = args[1].toLowerCase();
        switch (partySub) {
            case "invite": {
                if (args.length < 3) {
                    player.sendMessage(color("&6Usage: &f/dungeons party invite <player>"));
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage(color("&cThat player isn't online."));
                    return;
                }
                party.invite(party.leaderOf(player.getUniqueId()), target.getUniqueId());
                player.sendMessage(color("&aInvited " + target.getName() + " to your dungeon party."));
                target.sendMessage(color("&6" + player.getName() + " invited you to a dungeon party -- /dungeons party accept"));
                return;
            }
            case "accept": {
                UUID leaderId = party.acceptInvite(player.getUniqueId());
                if (leaderId == null) {
                    player.sendMessage(color("&cYou have no pending party invite."));
                    return;
                }
                OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
                player.sendMessage(color("&aJoined " + (leader.getName() != null ? leader.getName() : "the") + "'s dungeon party."));
                return;
            }
            case "leave": {
                party.leave(player.getUniqueId());
                player.sendMessage(color("&7You left your dungeon party."));
                return;
            }
            case "disband": {
                party.disband(player.getUniqueId());
                player.sendMessage(color("&7Your dungeon party has been disbanded."));
                return;
            }
            default:
                player.sendMessage(color("&6Usage: &f/dungeons party <invite|accept|leave|disband>"));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexusdungeons.admin")) {
            sender.sendMessage(color("&cYou don't have permission for that."));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(color("&aNexusDungeons config reloaded (new dungeons only -- existing ones keep their shape)."));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(color("&6NexusDungeons &7-- /dungeons <create|list|info|party>"
                + " &7-- admin: <remove|key|reload>"));
    }

    private String color(String s) {
        return Colors.color(s);
    }
}

package com.nexuscraft.nexusdungeons;

import org.bukkit.ChatColor;

final class Colors {

    private Colors() {
    }

    static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}

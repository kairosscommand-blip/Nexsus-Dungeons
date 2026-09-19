package com.nexuscraft.nexusdungeons;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * A soft, reflection-based, OUTBOUND bridge to NexusEndeavors' NexusEndeavorsApi -- the same
 * Class.forName + ServicesManager pattern every other cross-plugin surface in this project family
 * uses (see this plugin's own PulseBridge, copied and adapted here). The moment
 * DungeonEndeavorsListener sees a player land a kill inside a dungeon's bounds, it reports that as
 * "dungeon.kill" progress -- NexusDungeons works exactly the same with or without NexusEndeavors
 * installed at all; report() is a silent no-op either way.
 */
final class EndeavorsBridge {

    private static final String API_CLASS_NAME = "com.nexuscraft.nexusendeavors.api.NexusEndeavorsApi";

    private final JavaPlugin plugin;
    private Class<?> apiClass;
    private Object apiInstance;
    private boolean attemptedResolve;
    private boolean warned;

    EndeavorsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void report(UUID playerId, String playerName, String objectiveKey, int amount) {
        if (!ensureResolved()) {
            return;
        }
        try {
            Method method = apiClass.getMethod("reportProgress", UUID.class, String.class, String.class, int.class);
            method.invoke(apiInstance, playerId, playerName, objectiveKey, amount);
        } catch (Exception e) {
            warnOnce(e);
        }
    }

    private boolean ensureResolved() {
        if (apiInstance != null) {
            return true;
        }
        if (attemptedResolve) {
            return false;
        }
        attemptedResolve = true;
        try {
            Class<?> found = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(found);
            if (registration != null) {
                apiClass = found;
                apiInstance = registration.getProvider();
            }
            return apiInstance != null;
        } catch (ClassNotFoundException e) {
            return false; // NexusEndeavors isn't installed -- perfectly fine, nothing to feed
        } catch (Exception e) {
            warnOnce(e);
            return false;
        }
    }

    private void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning("[NexusDungeons] NexusEndeavors is installed, but couldn't accept a "
                + "progress report the way this version expects -- endeavor progress just won't count here. ("
                + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }
}

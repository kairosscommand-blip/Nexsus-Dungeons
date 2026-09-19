package com.nexuscraft.nexusdungeons;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * A soft, reflection-based, outbound bridge to NexusPulse's NexusPulseApi -- the same
 * Class.forName + ServicesManager pattern every other cross-plugin surface in this project
 * family uses (see NexusHouses' own PulseBridge, copied here). A cleared dungeon submits a
 * headline the instant its boss dies; NexusDungeons works exactly the same with or without
 * NexusPulse installed at all -- submit() is a silent no-op either way.
 */
final class PulseBridge {

    private static final String API_CLASS_NAME = "com.nexuscraft.nexuspulse.api.NexusPulseApi";

    private final JavaPlugin plugin;
    private Class<?> apiClass;
    private Object apiInstance;
    private boolean attemptedResolve;
    private boolean warned;

    PulseBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void submit(String category, String message) {
        if (!ensureResolved()) {
            return;
        }
        try {
            Method method = apiClass.getMethod("submit", String.class, String.class);
            method.invoke(apiInstance, category, message);
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
            return false; // NexusPulse isn't installed -- perfectly fine, nothing to feed
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
        plugin.getLogger().warning("[NexusDungeons] NexusPulse is installed, but couldn't accept an "
                + "event the way this version expects -- headlines just won't appear there. ("
                + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }
}

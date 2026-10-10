package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PathPlugin extends JavaPlugin implements Listener {

    private final PathConfig cfg = new PathConfig();
    private final Catalog catalog = new Catalog();
    private PathStore store;
    private PathService service;
    private ListenerGate gate;
    private BlockOwners owners;
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("custom-catalog.yml", false);
        cfg.load(getConfig());
        loadCatalog();
        store = new PathStore(new File(getDataFolder(), "players.yml"), getLogger()::warning);
        store.load();
        owners = new BlockOwners(System::currentTimeMillis);
        service = new PathService(cfg, catalog, store, PathPlugin::enabledPluginNames, () -> Bukkit.getOnlinePlayers(),
                System::currentTimeMillis, new PathExporter(this, cfg));
        gate = new ListenerGate(service, owners, this::shouldGate);
        gate.setEnabled(cfg.eventsEnabled);
        getServer().getPluginManager().registerEvents(new PathListener(this, service, owners), this);
        getServer().getPluginManager().registerEvents(this, this);
        PathCommand command = new PathCommand(service, gate, this::reload, () -> Bukkit.getPluginManager().isPluginEnabled("NexusTutorial"));
        getCommand("path").setExecutor(command);
        getCommand("path").setTabCompleter(command);
        tickTask = getServer().getScheduler().runTaskTimer(this, service::tick, 20L, 20L);
        saveTask = getServer().getScheduler().runTaskTimer(this, store::flush, 600L, 600L);
        // after every plugin has finished enabling, so their handlers all exist
        getServer().getScheduler().runTask(this, () -> {
            int n = gate.installAll();
            service.refreshAll();
            getLogger().info("NexusPath: " + n + " event handlers of other plugins are now gated.");
        });
        getLogger().info("NexusPath enabled -- " + catalog.systems().size() + " systems, " + catalog.styles().size() + " styles.");
        for (String w : catalog.warnings()) {
            getLogger().warning(w);
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (gate != null) {
            gate.uninstall();
        }
        if (store != null) {
            store.flush();
        }
    }

    /** A plugin that enables after the first tick (a reload tool, say) gets gated too. */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (gate != null && event.getPlugin() != this) {
            gate.install(event.getPlugin());
            service.refreshAll();
        }
    }

    private void reload() {
        reloadConfig();
        cfg.load(getConfig());
        loadCatalog();
        gate.setEnabled(cfg.eventsEnabled);
        gate.uninstall();
        gate.installAll();
        service.refreshAll();
    }

    /** Should the handlers of this plugin be wrapped? Only systems players can hide, that opted in. */
    private boolean shouldGate(String plugin) {
        String key = Catalog.key(plugin);
        if (PathService.infrastructure(key)) {
            return false;
        }
        SystemInfo s = catalog.system(plugin);
        if (s == null) {
            return key.startsWith("nexus");
        }
        return !s.locked() && s.events();
    }

    private static Collection<String> enabledPluginNames() {
        List<String> names = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.isEnabled()) {
                names.add(p.getName());
            }
        }
        return names;
    }

    /** The catalog in the jar, then the server's own custom-catalog.yml on top. */
    private void loadCatalog() {
        catalog.clear();
        try (InputStream in = getResource("catalog.yml")) {
            if (in != null) {
                catalog.load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), "catalog.yml");
            }
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not read the built-in catalog: " + ex.getMessage());
        }
        File custom = new File(getDataFolder(), "custom-catalog.yml");
        if (custom.exists()) {
            catalog.load(YamlConfiguration.loadConfiguration(custom), "custom-catalog.yml");
        }
        catalog.finish();
    }
}

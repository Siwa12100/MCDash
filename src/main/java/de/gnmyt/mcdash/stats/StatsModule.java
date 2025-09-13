package de.gnmyt.mcdash.stats;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import de.gnmyt.mcdash.listener.PlayerSessionListener;

public final class StatsModule {

    private final JavaPlugin plugin;
    private final String serverId;

    private StatsService statsService;
    private ConcurrencySampler sampler;

    public StatsModule(JavaPlugin plugin, String serverId) {
        this.plugin = plugin;
        this.serverId = serverId;
    }

    public void start() {
        // Implémentation : SQLite
        this.statsService = new SqliteStatsService(plugin, serverId);
        this.statsService.onStartup();

        // Listener JOIN/QUIT
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerSessionListener(statsService, serverId), plugin);

        // Sampler chaque minute
        this.sampler = new ConcurrencySampler(plugin, statsService, serverId);
        this.sampler.start();

        plugin.getLogger().info("[StatsModule] started (serverId=" + serverId + ")");
    }

    public void stop() {
        if (sampler != null) {
            sampler.cancel();
            sampler = null;
        }
        if (statsService != null) {
            statsService.close();
            statsService = null;
        }
        plugin.getLogger().info("[StatsModule] stopped");
    }

    public StatsService getStatsService() {
        return statsService;
    }
}
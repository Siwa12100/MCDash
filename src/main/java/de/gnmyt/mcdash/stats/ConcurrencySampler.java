package de.gnmyt.mcdash.stats;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ConcurrencySampler extends BukkitRunnable {

    private final Plugin plugin;
    private final StatsService stats;
    private final String serverId;

    public ConcurrencySampler(Plugin plugin, StatsService stats, String serverId) {
        this.plugin = plugin;
        this.stats = stats;
        this.serverId = serverId;
    }

    @Override
    public void run() {
        int online = Bukkit.getOnlinePlayers().size();
        stats.sampleConcurrency(serverId, online, "ok", Instant.now());
    }

    /** planifie toutes les 60s (20 ticks = 1s) */
    public void start() {
        this.runTaskTimer(plugin, 20L, 20L * 60L);
    }
}
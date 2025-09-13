package de.gnmyt.mcdash.listener;

import java.time.Instant;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import de.gnmyt.mcdash.stats.StatsService;

public class PlayerSessionListener implements Listener {

    private final StatsService stats;
    private final String serverId;

    public PlayerSessionListener(StatsService stats, String serverId) {
        this.stats = stats;
        this.serverId = serverId;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        stats.logJoin(p.getUniqueId(), p.getName(), serverId, Instant.now());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var p = e.getPlayer();
        stats.logQuit(p.getUniqueId(), p.getName(), serverId, Instant.now());
    }
}
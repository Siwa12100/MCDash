package de.gnmyt.mcdash.stats;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StatsService {
    void logJoin(UUID playerUuid, String playerName, String serverId, Instant tsUtc);
    void logQuit(UUID playerUuid, String playerName, String serverId, Instant tsUtc);
    void sampleConcurrency(String serverId, int onlinePlayers, String status, Instant tsUtc);

    List<ConcurrencyPoint> queryConcurrency(Instant from, Instant to, int bucketMinutes);
    List<SessionEvent> getRecentEvents(int limit);

    /** Optionnel: housekeeping au démarrage (fermer sessions “ouvertes” > Xh, etc.) */
    default void onStartup() {}

    /** Flush/close ressources */
    void close();
}

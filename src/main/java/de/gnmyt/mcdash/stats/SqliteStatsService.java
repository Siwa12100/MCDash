package de.gnmyt.mcdash.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

public class SqliteStatsService implements StatsService, AutoCloseable {

    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    private final Plugin plugin;
    private final String serverId;
    private Connection conn;

    public SqliteStatsService(Plugin plugin, String serverId) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.conn = open();
        initSchema();
    }

    private Connection open() {
        try {
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists() && !dataDir.mkdirs()) {
                throw new IllegalStateException("Cannot create data dir: " + dataDir);
            }
            File dbFile = new File(dataDir, "players.db");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");     // perf & sécurité
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA foreign_keys=ON");
            }
            return c;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open SQLite connection", e);
        }
    }

    private void initSchema() {
        final String ddlEvents = """
            CREATE TABLE IF NOT EXISTS player_session_events (
              ts_utc      TEXT    NOT NULL,
              event       TEXT    NOT NULL CHECK (event IN ('join','quit')),
              player_uuid TEXT    NOT NULL,
              player_name TEXT    NOT NULL,
              server_id   TEXT    NOT NULL,
              PRIMARY KEY (ts_utc, player_uuid, event)
            );
            """;
        final String ddlIdx1 = "CREATE INDEX IF NOT EXISTS idx_pse_uuid_ts ON player_session_events(player_uuid, ts_utc);";
        final String ddlIdx2 = "CREATE INDEX IF NOT EXISTS idx_pse_server_ts ON player_session_events(server_id, ts_utc);";

        final String ddlConc = """
            CREATE TABLE IF NOT EXISTS player_concurrency (
              ts_utc         TEXT    NOT NULL,
              server_id      TEXT    NOT NULL,
              online_players INTEGER NOT NULL,
              status         TEXT    NOT NULL DEFAULT 'ok',
              PRIMARY KEY (ts_utc, server_id)
            );
            """;
        final String ddlIdx3 = "CREATE INDEX IF NOT EXISTS idx_pc_server_ts ON player_concurrency(server_id, ts_utc);";

        try (Statement s = conn.createStatement()) {
            s.execute(ddlEvents);
            s.execute(ddlIdx1);
            s.execute(ddlIdx2);
            s.execute(ddlConc);
            s.execute(ddlIdx3);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init schema", e);
        }
    }

    @Override
    public void logJoin(UUID playerUuid, String playerName, String serverId, Instant tsUtc) {
        insertEvent("join", playerUuid, playerName, serverId, tsUtc);
    }

    @Override
    public void logQuit(UUID playerUuid, String playerName, String serverId, Instant tsUtc) {
        insertEvent("quit", playerUuid, playerName, serverId, tsUtc);
    }

    private void insertEvent(String event, UUID uuid, String name, String serverId, Instant tsUtc) {
        final String sql = """
            INSERT OR IGNORE INTO player_session_events
            (ts_utc, event, player_uuid, player_name, server_id)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ISO_UTC.format(tsUtc));
            ps.setString(2, event);
            ps.setString(3, uuid.toString());
            ps.setString(4, name);
            ps.setString(5, serverId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to insert " + event + " for " + name + ": " + e.getMessage());
        }
    }

    @Override
    public void sampleConcurrency(String serverId, int onlinePlayers, String status, Instant tsUtc) {
        final String sql = """
            INSERT OR REPLACE INTO player_concurrency
            (ts_utc, server_id, online_players, status)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ISO_UTC.format(tsUtc));
            ps.setString(2, serverId);
            ps.setInt(3, onlinePlayers);
            ps.setString(4, status == null ? "ok" : status);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to insert concurrency sample: " + e.getMessage());
        }
    }

    @Override
    public void onStartup() {
        // Exemple: marquer un statut 'offline' si le serveur n'échantillonne pas (optionnel)
    }

    @Override
    public void close() {
        if (this.conn != null) {
            try { this.conn.close(); } catch (SQLException ignored) {}
            this.conn = null;
        }
    }

    @Override
    public List<ConcurrencyPoint> queryConcurrency(Instant from, Instant to, int bucketMinutes) {
        // On bucketise en minutes côté SQL via l'epoch (strftime('%s', ...)),
        // puis on arrondit à la borne de début de bucket.
        final String sql = """
            SELECT
            ((strftime('%s', ts_utc) / (? * 60)) * (? * 60)) AS bucket_epoch,
            AVG(online_players) AS avg_players
            FROM player_concurrency
            WHERE ts_utc >= ? AND ts_utc <= ?
            GROUP BY bucket_epoch
            ORDER BY bucket_epoch
            """;

        var list = new ArrayList<ConcurrencyPoint>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int bucket = Math.max(1, bucketMinutes);
            ps.setInt(1, bucket);                  // ? => bucket minutes
            ps.setInt(2, bucket);                  // ? => bucket minutes
            ps.setString(3, from.toString());      // bornes ISO-8601 (ce qu'on stocke déjà)
            ps.setString(4, to.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long epochSec = rs.getLong("bucket_epoch");
                    Instant t = Instant.ofEpochSecond(epochSec);   // évite Instant.parse() fragile
                    int v = (int) Math.round(rs.getDouble("avg_players"));
                    list.add(new ConcurrencyPoint(t, v));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("queryConcurrency failed: " + e.getMessage());
        }
        return list;
    }
    
    @Override
    public List<SessionEvent> getRecentEvents(int limit) {
        final String sql = """
        SELECT ts_utc,event,player_uuid,player_name
        FROM player_session_events
        ORDER BY ts_utc DESC
        LIMIT ?
        """;
        var list = new ArrayList<SessionEvent>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(1000, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SessionEvent(
                        Instant.parse(rs.getString("ts_utc")),
                        rs.getString("event"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getRecentEvents failed: " + e.getMessage());
        }
        return list;
    }




}
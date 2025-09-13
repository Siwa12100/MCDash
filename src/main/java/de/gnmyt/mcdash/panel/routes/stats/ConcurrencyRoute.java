package de.gnmyt.mcdash.panel.routes.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.gnmyt.mcdash.MinecraftDashboard;
import de.gnmyt.mcdash.api.handler.DefaultHandler;
import de.gnmyt.mcdash.api.http.Request;
import de.gnmyt.mcdash.api.http.ResponseController;
import de.gnmyt.mcdash.stats.ConcurrencyPoint;
import de.gnmyt.mcdash.stats.StatsService;

public class ConcurrencyRoute extends DefaultHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())                  // support des types java.time
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // => ISO-8601 et pas des timestamps

    @Override
    public String path() {
        return "players/concurrency";
    }

    @Override
    public void get(Request request, ResponseController response) throws Exception {
        // Params supportés : from, to (ISO ou epoch sec/ms), bucket (minutes), sinon fallback "days"
        Instant now = Instant.now();
        Instant to = parseInstant(request.getQuery().get("to"), now);
        Instant from = parseInstant(request.getQuery().get("from"), null);

        if (from == null) {
            int days = parseInt(request.getQuery().getOrDefault("days", "7"), 7);
            if (days < 1) days = 1;
            from = to.minus(Duration.ofDays(days));
        }

        int bucket = parseInt(request.getQuery().getOrDefault("bucket", "5"), 5);
        if (bucket < 1) bucket = 1;

        StatsService stats = MinecraftDashboard.getInstance().getStatsModule().getStatsService();
        List<ConcurrencyPoint> points = stats.queryConcurrency(from, to, bucket);

        response.header("Content-Type", "application/json; charset=utf-8");
        response.bytes(MAPPER.writeValueAsBytes(points));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static Instant parseInstant(String raw, Instant def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            // nombre => epoch sec (10) ou ms (13)
            long n = Long.parseLong(raw);
            if (n < 2_000_000_000L) return Instant.ofEpochSecond(n);
            return Instant.ofEpochMilli(n);
        } catch (NumberFormatException ignore) {
            try { return Instant.parse(raw); } catch (Exception e) { return def; }
        }
    }
}

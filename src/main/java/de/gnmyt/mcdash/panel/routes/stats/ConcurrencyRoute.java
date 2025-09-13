package de.gnmyt.mcdash.panel.routes.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.gnmyt.mcdash.MinecraftDashboard;
import de.gnmyt.mcdash.api.handler.DefaultHandler;
import de.gnmyt.mcdash.api.http.Request;
import de.gnmyt.mcdash.api.http.ResponseController;
import de.gnmyt.mcdash.stats.ConcurrencyPoint;
import de.gnmyt.mcdash.stats.StatsModule;
import de.gnmyt.mcdash.stats.StatsService;

public class ConcurrencyRoute extends DefaultHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String path() {
        // L’URL finale sera: /stats/players/concurrency (package "stats" + ce path)
        return "players/concurrency";
    }

    @Override
    public void get(Request request, ResponseController response) throws Exception {
        int days   = parseInt(getStringFromQuery(request, "days"),   7);
        int bucket = parseInt(getStringFromQuery(request, "bucket"), 5); // minutes

        if (days <= 0) days = 7;
        if (bucket <= 0) bucket = 5;

        Instant to   = Instant.now();
        Instant from = to.minus(Duration.ofDays(days));

        StatsService stats = getStatsService();
        List<ConcurrencyPoint> points = stats.queryConcurrency(from, to, bucket);

        // Si ResponseController a response.json(...), tu peux l'utiliser.
        // Ici on reste générique:
        byte[] body = MAPPER.writeValueAsBytes(points);
        response.header("Content-Type", "application/json; charset=utf-8");
        response.bytes(body);
    }

    private StatsService getStatsService() {
        StatsModule m = MinecraftDashboard.getInstance().getStatsModule();
        return m.getStatsService();
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}

package de.gnmyt.mcdash.stats;
import java.time.Instant;
import java.util.UUID;

public record SessionEvent(Instant tsUtc, String event, UUID playerUuid, String playerName) {}

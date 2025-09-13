package de.gnmyt.mcdash.stats;
import java.time.Instant;

public record ConcurrencyPoint(Instant tsUtc, int players) {}
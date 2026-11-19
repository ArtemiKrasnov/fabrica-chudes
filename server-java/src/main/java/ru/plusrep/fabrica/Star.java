package ru.plusrep.fabrica;

import java.time.Instant;

public record Star(
    String id,
    String name,
    String project,
    long amount,
    double x,
    double y,
    Instant createdAt
) {}

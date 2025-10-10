package com.svenruppert.urlshortener.core;

import java.time.Instant;
import java.util.Optional;

public record ShortUrlMapping(
    String shortCode,
    String originalUrl,
    Instant createdAt,
    Optional<Instant> expiresAt
) { }
package com.svenruppert.urlshortener.core.urlmapping;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ShortUrlMapping {
  private final String shortCode;
  private final String originalUrl;
  private final Instant createdAt;
  private final Optional<Instant> expiresAt;

  public ShortUrlMapping(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Optional<Instant> expiresAt
  ) {
    this.shortCode = shortCode;
    this.originalUrl = originalUrl;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public String shortCode() {
    return shortCode;
  }

  public String originalUrl() {
    return originalUrl;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> expiresAt() {
    return expiresAt;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ShortUrlMapping) obj;
    return Objects.equals(this.shortCode, that.shortCode) &&
        Objects.equals(this.originalUrl, that.originalUrl) &&
        Objects.equals(this.createdAt, that.createdAt) &&
        Objects.equals(this.expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shortCode, originalUrl, createdAt, expiresAt);
  }

  @Override
  public String toString() {
    return "ShortUrlMapping[" +
        "shortCode=" + shortCode + ", " +
        "originalUrl=" + originalUrl + ", " +
        "createdAt=" + createdAt + ", " +
        "expiresAt=" + expiresAt + ']';
  }
}
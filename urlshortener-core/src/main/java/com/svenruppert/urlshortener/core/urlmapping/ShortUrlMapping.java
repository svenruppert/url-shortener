package com.svenruppert.urlshortener.core.urlmapping;





import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ShortUrlMapping {
  private  String shortCode;
  private  String originalUrl;
  private  Instant createdAt;
  private  Instant expiresAt;
  private  boolean active;
  private  String ownerUsername;

  public ShortUrlMapping() {
  }

  public ShortUrlMapping(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      boolean active
  ) {
    this(shortCode, originalUrl, createdAt, expiresAt, active, null);
  }

  public ShortUrlMapping(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      boolean active,
      String ownerUsername
  ) {
    this.shortCode = shortCode;
    this.originalUrl = originalUrl;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.active = active;
    this.ownerUsername = ownerUsername;
  }

  public String getShortCode() {
    return shortCode;
  }

  public String getOriginalUrl() {
    return originalUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isActive() {
    return active;
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
    return Optional.ofNullable(expiresAt);
  }

  public boolean active() {
    return active;
  }

  public String getOwnerUsername() {
    return ownerUsername;
  }

  public String ownerUsername() {
    return ownerUsername;
  }

  // --- COPY-WITH methods ---
  public ShortUrlMapping withShortCode(String shortCode) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }

  public ShortUrlMapping withOriginalUrl(String originalUrl) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }

  public ShortUrlMapping withCreatedAt(Instant createdAt) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }

  public ShortUrlMapping withExpiresAt(Instant expiresAt) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }

  public ShortUrlMapping withActive(boolean active) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }

  public ShortUrlMapping withOwnerUsername(String ownerUsername) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ShortUrlMapping) obj;
    return Objects.equals(this.shortCode, that.shortCode) &&
        Objects.equals(this.originalUrl, that.originalUrl) &&
        Objects.equals(this.createdAt, that.createdAt) &&
        Objects.equals(this.expiresAt, that.expiresAt) &&
        this.active == that.active &&
        Objects.equals(this.ownerUsername, that.ownerUsername);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shortCode, originalUrl, createdAt, expiresAt, active, ownerUsername);
  }

  @Override
  public String toString() {
    return "ShortUrlMapping[" +
        "shortCode=" + shortCode + ", " +
        "originalUrl=" + originalUrl + ", " +
        "createdAt=" + createdAt + ", " +
        "expiresAt=" + expiresAt + ", " +
        "active=" + active + ", " +
        "ownerUsername=" + ownerUsername +
        ']';
  }

}
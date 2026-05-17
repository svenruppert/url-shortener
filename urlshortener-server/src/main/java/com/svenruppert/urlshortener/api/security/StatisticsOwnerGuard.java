package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.JsonWriter;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-resource owner check for statistics handlers. The coarse REST filter
 * already verified {@code link:stats:own}; this guard adds the per-shortCode
 * refinement: callers with only {@code link:stats:own} must own the mapping;
 * callers with {@code link:stats:all} (typically administrators) bypass the
 * check.
 * <p>
 * Unknown shortCodes are <em>allowed</em> through the guard — the downstream
 * reader will translate them into an empty/zero result, and we do not want to
 * leak existence information via 403/404 differentiation.
 */
public final class StatisticsOwnerGuard {

  public static final String PERMISSION_STATS_ALL = "link:stats:all";

  private static final String FORBIDDEN_BODY =
      "{\"error\":\"forbidden\",\"message\":\"not the owner of this short code\"}";

  private final UrlMappingStore mappingStore;

  public StatisticsOwnerGuard(UrlMappingStore mappingStore) {
    this.mappingStore = Objects.requireNonNull(mappingStore, "mappingStore");
  }

  /**
   * Validates that the current subject may access statistics for the given
   * shortCode. Returns {@code true} when the caller is allowed and the handler
   * may continue. Returns {@code false} after writing a 403 to the exchange,
   * meaning the handler must abort.
   */
  public boolean allow(HttpExchange exchange, String shortCode) throws IOException {
    if (shortCode == null || shortCode.isBlank()) {
      // Defer the bad-request decision to the caller; we have nothing to gate on.
      return true;
    }
    Optional<ShortUrlMapping> mapping = mappingStore.findByShortCode(shortCode);
    if (mapping.isEmpty()) {
      // Allow through — the reader's 404/empty result must not be conflated
      // with an authorization failure.
      return true;
    }
    if (OwnerCheck.isOwnerOrHasAll(mapping.get(), PERMISSION_STATS_ALL)) {
      return true;
    }
    JsonWriter.writeJson(exchange, 403, FORBIDDEN_BODY);
    return false;
  }
}

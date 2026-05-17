package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveResponse;

import java.time.Instant;

public interface UrlMappingUpdater {

  Result<ShortUrlMapping> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active, String ownerUsername);

  Result<ShortUrlMapping> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active, String ownerUsername);

  default Result<ShortUrlMapping> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active) {
    return createMapping(createdAt, shortCode, originalUrl, expiredAt, active, null);
  }

  default Result<ShortUrlMapping> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active) {
    return createMapping(shortCode, originalUrl, expiredAt, active, null);
  }

  Result<ShortUrlMapping> editMapping(String alias, String url, Instant expiredAt, Boolean active);

  boolean delete(String shortCode);

  Result<ToggleActiveResponse> toggleActive(String shortCode, boolean newActiveValue);

  /**
   * Replaces the owner of an existing mapping. Used for one-shot migrations of
   * legacy data created before owner tracking. Returns a failure if the
   * mapping is unknown.
   */
  Result<ShortUrlMapping> assignOwner(String shortCode, String ownerUsername);
}
package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveResponse;

import java.time.Instant;

public interface UrlMappingUpdater {
  Result<ShortUrlMapping> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active);

  Result<ShortUrlMapping> editMapping(String alias, String url, Instant expiredAt, Boolean active);

  boolean delete(String shortCode);

  Result<ToggleActiveResponse> toggleActive(String shortCode, boolean newActiveValue);
}
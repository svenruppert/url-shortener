package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.time.Instant;

public interface UrlMappingUpdater {

  Result<ShortUrlMapping> createMapping(String originalUrl);
  Result<ShortUrlMapping> createMapping(String alias, String url);
  Result<ShortUrlMapping> createMapping(String alias, String url, Instant expiredAt);

  Result<ShortUrlMapping> editMapping(String alias, String url, Instant expiredAt);

  boolean delete(String shortCode);

}
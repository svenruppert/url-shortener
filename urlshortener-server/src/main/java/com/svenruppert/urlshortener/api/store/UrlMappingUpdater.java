package com.svenruppert.urlshortener.api.store;

import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.time.Instant;

public interface UrlMappingUpdater {

  Result<ShortUrlMapping> createMapping(String originalUrl);
  Result<ShortUrlMapping> createMapping(String alias, String url);
  Result<ShortUrlMapping> createMapping(String alias, String url, Instant expiredAt);

  boolean delete(String shortCode);

}
package com.svenruppert.urlshortener.api.store;

import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

public interface UrlMappingUpdater {

  Result<ShortUrlMapping> createMapping(String originalUrl);
  Result<ShortUrlMapping> createMapping(String alias, String url);

  boolean delete(String shortCode);
  boolean existsByCode(String shortCode);
}
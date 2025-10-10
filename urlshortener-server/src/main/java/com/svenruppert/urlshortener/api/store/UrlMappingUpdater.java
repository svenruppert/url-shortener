package com.svenruppert.urlshortener.api.store;

import com.svenruppert.urlshortener.core.ShortUrlMapping;

public interface UrlMappingUpdater {
  ShortUrlMapping createMapping(String originalUrl);
  boolean delete(String shortCode);
  ShortUrlMapping createCustomMapping(String alias, String url);

}
package com.svenruppert.urlshortener.api.store;

import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.util.List;
import java.util.Optional;

public interface UrlMappingLookup {

  Optional<ShortUrlMapping> findByShortCode(String shortCode);
  boolean exists(String shortCode);
  List<ShortUrlMapping> findAll();

  int mappingCount();

}
package com.svenruppert.urlshortener.api.store;

import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.util.List;
import java.util.Optional;

public interface UrlMappingLookup {

  boolean exists(String shortCode);

  Optional<ShortUrlMapping> findByShortCode(String shortCode);
  List<ShortUrlMapping> findAll();
  List<ShortUrlMapping> find(UrlMappingFilter filter);

  int mappingCount();
  int count(UrlMappingFilter filter);
}
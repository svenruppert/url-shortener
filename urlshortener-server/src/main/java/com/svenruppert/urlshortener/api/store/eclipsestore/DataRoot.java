package com.svenruppert.urlshortener.api.store.eclipsestore;

import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataRoot
    implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Map<String, ShortUrlMapping> mappings = new ConcurrentHashMap<>();
  //  private final Map<String, ShortUrlMapping> mappings = new HashMap<>();

  public Map<String, ShortUrlMapping> mappings() {
    return mappings;
  }
}
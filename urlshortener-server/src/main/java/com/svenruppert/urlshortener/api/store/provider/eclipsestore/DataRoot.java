package com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataRoot
    implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Map<String, Map<String, Map<String, Boolean>>> columnVisibilityByUserAndView
      = new ConcurrentHashMap<>();

  private final Map<String, ShortUrlMapping> shortUrlMappingMap = new ConcurrentHashMap<>();

  public Map<String, ShortUrlMapping> shortUrlMappings() {
    return shortUrlMappingMap;
  }

  public Map<String, Map<String, Map<String, Boolean>>> columnVisibilityPreferences() {
    return columnVisibilityByUserAndView;
  }
}
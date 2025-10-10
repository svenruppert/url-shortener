package com.svenruppert.urlshortener.api.store;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final Map<String, ShortUrlMapping> store = new ConcurrentHashMap<>();
  private final ShortCodeGenerator generator;

  public InMemoryUrlMappingStore() {
    this.generator = new ShortCodeGenerator(1L);
  }

  @Override
  public ShortUrlMapping createMapping(String originalUrl) {
    logger().info("originalUrl: {} ->", originalUrl);
    String alias = generator.nextCode();
    return createCustomMapping(alias, originalUrl);
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    return Optional.ofNullable(store.get(shortCode));
  }

  @Override
  public boolean exists(String shortCode) {
    return store.containsKey(shortCode);
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    return new ArrayList<>(store.values());
  }

  @Override
  public boolean delete(String shortCode) {
    return store.remove(shortCode) != null;
  }

  @Override
  public int mappingCount() {
    return store.size();
  }

  @Override
  public ShortUrlMapping createCustomMapping(String alias, String url) {
    if (store.containsKey(alias)) {
      throw new RuntimeException("Alias already exists");
    }
    ShortUrlMapping shortMapping = new ShortUrlMapping(
        alias,
        url,
        Instant.now(),
        Optional.empty()
    );
    store.put(alias, shortMapping);
    return shortMapping;
  }
}
package com.svenruppert.urlshortener.api.store;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.handler.AliasPolicy;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

public class InMemoryUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final Map<String, ShortUrlMapping> store = new ConcurrentHashMap<>();
  private final ShortCodeGenerator generator;

  public InMemoryUrlMappingStore(ShortCodeGenerator generator) {
    this.generator = generator;
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String originalUrl) {
    logger().info("originalUrl: {} ->", originalUrl);
    return createMapping(null, originalUrl);
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
  public Result<ShortUrlMapping> createMapping(String alias, String url) {
    String shortCode;
    logger().info("createMapping - alias - {} / url - {} ", alias, url);
    if (!isNullOrBlank(alias)) {
      if (!AliasPolicy.isValid(alias)) {
        var errorMessage = toJson("400", "Invalid alias (allowed: [A-Za-z0-9_-], length 3..32, not reserved)");
        logger().warn("createMapping - {}", errorMessage);
        return Result.failure(errorMessage);
      }
      final String normalized = AliasPolicy.normalize(alias);
      if (store.containsKey(normalized)) {
        var errorMessage = toJson("409", "Alias already in use");
        logger().warn("createMapping - {}", errorMessage);
        return Result.failure(errorMessage);
      }
      shortCode = normalized; // Alias wird verwendet
    } else {
      String generated = generator.nextCode();
      while (existsByCode(generated)) {
        generated = generator.nextCode();
      }
      shortCode = generated;
    }

    ShortUrlMapping shortMapping = new ShortUrlMapping(
        shortCode,
        url,
        Instant.now(),
        Optional.empty()
    );
    store.put(shortCode, shortMapping);
    return Result.success(shortMapping);
  }

  @Override
  public boolean existsByCode(String shortCode) {
    return store.containsKey(shortCode);
  }
}
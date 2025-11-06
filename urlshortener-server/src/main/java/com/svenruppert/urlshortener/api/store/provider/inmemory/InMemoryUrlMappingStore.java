package com.svenruppert.urlshortener.api.store.provider.inmemory;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.urlmapping.MappingCreator;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.filterSortAndPage;
import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.matches;
import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;

public class InMemoryUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final Map<String, ShortUrlMapping> store = new ConcurrentHashMap<>();

  private final MappingCreator creator;

  public InMemoryUrlMappingStore(ShortCodeGenerator generator) {
    this.creator = new MappingCreator(
        generator,
        this::existsByCode,
        this::storeMapping,
        java.time.Clock.systemUTC(),
        err -> toJson(err.httpStatus(), err.message(), err.reasonCode()) // dein bisheriges error JSON
    );
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String originalUrl) {
    logger().info("originalUrl: {} ->", originalUrl);
    return createMapping(null, originalUrl);
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    logger().info("findByShortCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("findByShortCode normalized for search '{}'", shortCode);
    return Optional.ofNullable(store.get(normalized));
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    return new ArrayList<>(store.values());
  }

  @Override
  public List<ShortUrlMapping> find(UrlMappingFilter filter) {
    Objects.requireNonNull(filter, "filter");
    var valueStream = store.values().stream();
    return filterSortAndPage(filter, valueStream);
  }

  @Override
  public int count(UrlMappingFilter filter) {
    int c = 0;
    for (ShortUrlMapping m : store.values()) {
      if (matches(filter, m)) c++;
    }
    return c;
  }

  @Override
  public int countAll() {
    return store.size();
  }

  @Override
  public boolean delete(String shortCode) {
    logger().info("delete '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("delete - normalized for deletion '{}'", normalized);
    return store.remove(normalized) != null;
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String shortCode, String originalUrl) {
    logger().info("createMapping - shortCode: {} - originalUrl: {} ", shortCode, originalUrl);
    return creator.create(shortCode, originalUrl, null);
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String shortCode, String originalUrl, Instant expiredAt) {
    logger().info("createMapping - shortCode: {} - originalUrl: {} - expiredAt: {} ", shortCode, originalUrl, expiredAt);
    return creator.create(shortCode, originalUrl, expiredAt);
  }

  @Override
  public Result<ShortUrlMapping> editMapping(String shortCode, String url, Instant expiredAt) {
    logger().info("editMapping - shortCode: {} - originalUrl: {} - expiredAt: {} ", shortCode, url, expiredAt);
    var existsByCode = existsByCode(shortCode);
    if (existsByCode) {
      var shortUrlMappingOLD = store.get(shortCode);
      var originalOrNewUrl = url != null ? url : shortUrlMappingOLD.originalUrl();
      var instant = expiredAt != null ? Optional.of(expiredAt) : shortUrlMappingOLD.expiresAt();
      var shortUrlMapping = new ShortUrlMapping(shortCode, originalOrNewUrl, shortUrlMappingOLD.createdAt(), instant);
      store.put(shortUrlMapping.shortCode(), shortUrlMapping);
      return Result.success(shortUrlMapping);
    } else {
      logger().info("editMapping - shortCode {} does not exists", shortCode);
      return Result.failure("editMapping - shortCode '" + shortCode + "' does not exists");
    }
  }

  private void storeMapping(ShortUrlMapping shortMapping) {
    store.put(shortMapping.shortCode(), shortMapping);
  }

  @Override
  public boolean existsByCode(String shortCode) {
    logger().info("existsByCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("existsByCode - normalized for search '{}'", normalized);
    return store.containsKey(normalized);
  }
}
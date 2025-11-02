package com.svenruppert.urlshortener.api.store.inmemory;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.MappingCreator;
import com.svenruppert.urlshortener.api.store.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.svenruppert.urlshortener.api.store.UrlMappingFilterHelper.filterSortAndPage;
import static com.svenruppert.urlshortener.api.store.UrlMappingFilterHelper.matches;
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
  public Result<ShortUrlMapping> createMapping(String alias, String originalUrl) {
    logger().info("alias: {} - originalUrl: {} ", alias, originalUrl);
    return creator.create(alias, originalUrl, null);
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String alias, String originalUrl, Instant expiredAt) {
    logger().info("alias: {} - originalUrl: {} - expiredAt: {} ", alias, originalUrl, expiredAt);
    return creator.create(alias, originalUrl, expiredAt);
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
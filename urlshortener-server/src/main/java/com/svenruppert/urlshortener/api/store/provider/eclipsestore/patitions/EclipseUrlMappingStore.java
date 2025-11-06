package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.urlmapping.MappingCreator;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.eclipse.store.storage.types.StorageManager;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.filterSortAndPage;
import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.matches;
import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;

public class EclipseUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final StorageManager storage;
  private final MappingCreator creator;

  public EclipseUrlMappingStore(StorageManager storage, Clock clock, ShortCodeGenerator generator) {
    this.storage = storage;

    var clockToUse = (clock == null) ? Clock.systemUTC() : clock;
    Function<MappingCreator.ErrorInfo, String> errorMapper = err -> JsonUtils.toJson(err.httpStatus(), err.message(), err.reasonCode());
    this.creator = new MappingCreator(
        generator,
        this::existsByCode,
        this::storeMappingAndPersist,
        clockToUse,
        errorMapper
    );

  }

  @Override
  public Result<ShortUrlMapping> createMapping(String originalUrl) {
    return createMapping(null, originalUrl, null);
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String alias, String originalUrl) {
    logger().info("alias: {} - originalUrl: {} ", alias, originalUrl);
    return creator.create(alias, originalUrl, null);
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String alias, String originalUrl, Instant expiredArt) {
    logger().info("alias: {} - originalUrl: {} - expiredAt: {}", alias, originalUrl, expiredArt);
    return creator.create(alias, originalUrl, expiredArt);
  }

  @Override
  public Result<ShortUrlMapping> editMapping(String shortCode, String url, Instant expiredAt) {
    logger().info("editMapping - shortCode: {} - originalUrl: {} - expiredAt: {} ", shortCode, url, expiredAt);
    var existsByCode = existsByCode(shortCode);
    if (existsByCode) {
      var urlMappings = dataRoot().shortUrlMappings();
      var shortUrlMappingOLD = urlMappings.get(shortCode);
      var originalOrNewUrl = url != null ? url : shortUrlMappingOLD.originalUrl();
      var instant = expiredAt != null ? Optional.of(expiredAt) : shortUrlMappingOLD.expiresAt();
      var shortUrlMapping = new ShortUrlMapping(shortCode, originalOrNewUrl, shortUrlMappingOLD.createdAt(), instant);
      urlMappings.put(shortUrlMapping.shortCode(), shortUrlMapping);
      storage.store(dataRoot().shortUrlMappings());
      return Result.success(shortUrlMapping);
    } else {
      logger().info("editMapping - shortCode {} does not exists", shortCode);
      return Result.failure("editMapping - shortCode '" + shortCode + "' does not exists");
    }
  }

  @Override
  public boolean delete(String shortCode) {
    logger().info("delete '{}'", shortCode);
    var normalized = normalize(shortCode);
    logger().info("delete - normalized for deletion '{}'", normalized);
    var removed = dataRoot().shortUrlMappings().remove(normalized) != null;
    logger().info("mapping removed from store {} ", removed);
    if (removed) {
      storage.store(dataRoot().shortUrlMappings());
      logger().info("changes persisted in store");
    }
    return removed;
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }

  @Override
  public boolean existsByCode(String shortCode) {
    logger().info("existsByCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("existsByCode - normalized for search '{}'", normalized);
    var containsKey = dataRoot().shortUrlMappings().containsKey(normalized);
    logger().info("existsByCode - containsKey = {}", containsKey);
    return containsKey;
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    logger().info("findByShortCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("findByShortCode normalized for search '{}'", shortCode);
    return Optional.ofNullable(dataRoot().shortUrlMappings().get(normalized));
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    logger().info("findAll");
    return new ArrayList<>(dataRoot().shortUrlMappings().values());
  }

  @Override
  public List<ShortUrlMapping> find(UrlMappingFilter filter) {
    Objects.requireNonNull(filter, "filter");
    var valueStream = dataRoot().shortUrlMappings().values().stream();
    return filterSortAndPage(filter, valueStream);
  }

  @Override
  public int count(UrlMappingFilter filter) {
    int c = 0;
    for (ShortUrlMapping m : dataRoot().shortUrlMappings().values()) {
      if (matches(filter, m)) c++;
    }
    return c;
  }

  @Override
  public int countAll() {
    return dataRoot().shortUrlMappings().size();
  }
  /* ---------- intern ---------- */

  private void storeMappingAndPersist(ShortUrlMapping m) {
    var dataRoot = dataRoot();
    var mappings = dataRoot.shortUrlMappings();
    logger().info("storeMappingAndPersist - mappings size {}", mappings.size());
    mappings.put(m.shortCode(), m);
    logger().info("storeMappingAndPersist {}", m);
    var stored = storage.store(mappings);
    logger().info("storeMappingAndPersist stored - {}", stored);
  }
}

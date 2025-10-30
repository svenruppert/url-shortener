package com.svenruppert.urlshortener.api.store.eclipsestore;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.MappingCreator;
import com.svenruppert.urlshortener.api.store.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import com.svenruppert.urlshortener.core.ShortUrlMapping;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.types.StorageManager;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;

import static com.svenruppert.urlshortener.api.store.UrlMappingFilterHelper.filterSortAndPage;
import static com.svenruppert.urlshortener.api.store.UrlMappingFilterHelper.matches;
import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;

public final class EclipseStoreUrlMappingStore
    implements UrlMappingStore, HasLogger, Closeable {

  private final StorageManager storage;
  private final MappingCreator creator;
  private final Clock clock;


  public EclipseStoreUrlMappingStore(String storageDir,
                                     ShortCodeGenerator generator,
                                     Clock clock,
                                     Function<MappingCreator.ErrorInfo, String> errorMapper) {

    var storagePath = Paths.get(storageDir);
    logger().info("StoragePath is '{}'", storagePath.toAbsolutePath());
    try {
      Files.createDirectories(storagePath);
    } catch (Exception ignored) {
      logger().info("createDirectories ... {}", ignored);
    }

    this.storage = EmbeddedStorage.start(storagePath);

    DataRoot r = dataRoot();
    if (r != null) {
      logger().info("found existing DataRoot");
      var size = r.mappings().size();
      logger().info("DataRoot Mappings contains {} elements", size);
    } else {
      logger().info("no DataRoot found creating a new one..");
      storage.setRoot(new DataRoot());
      storage.storeRoot();
    }

    this.clock = clock == null ? Clock.systemUTC() : clock;

    this.creator = new MappingCreator(
        generator,
        this::existsByCode,
        this::storeMappingAndPersist,
        this.clock,
        errorMapper
    );
  }

  /* ---------- UrlMappingStore API ---------- */

  @Override
  public Result<ShortUrlMapping> createMapping(String originalUrl) {
    return createMapping(null, originalUrl);
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String alias, String originalUrl) {
    logger().info("alias: {} - originalUrl: {} ->", alias, originalUrl);
    return creator.create(alias, originalUrl);
  }

  @Override
  public boolean delete(String shortCode) {
    logger().info("delete '{}'", shortCode);
    var normalized = normalize(shortCode);
    logger().info("delete - normalized for deletion '{}'", normalized);
    var removed = dataRoot().mappings().remove(normalized) != null;
    logger().info("mapping removed from store {} ", removed);
    if (removed) {
      storage.store(dataRoot().mappings());
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
    var containsKey = dataRoot().mappings().containsKey(normalized);
    logger().info("existsByCode - containsKey = {}" , containsKey);
    return containsKey;
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    logger().info("findByShortCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("findByShortCode normalized for search '{}'", shortCode);
    return Optional.ofNullable(dataRoot().mappings().get(normalized));
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    logger().info("findAll");
    return new ArrayList<>(dataRoot().mappings().values());
  }

  @Override
  public List<ShortUrlMapping> find(UrlMappingFilter filter) {
    Objects.requireNonNull(filter, "filter");
    var valueStream = dataRoot().mappings().values().stream();
    return filterSortAndPage(filter, valueStream);
  }

  @Override
  public int count(UrlMappingFilter filter) {
    int c = 0;
    for (ShortUrlMapping m : dataRoot().mappings().values()) {
      if (matches(filter, m)) c++;
    }
    return c;
  }

  @Override
  public int countAll() {
    return dataRoot().mappings().size();
  }
  /* ---------- intern ---------- */

  private void storeMappingAndPersist(ShortUrlMapping m) {
    var dataRoot = dataRoot();
    var mappings = dataRoot.mappings();
    logger().info("storeMappingAndPersist - mappings size {}", mappings.size());
    mappings.put(m.shortCode(), m);
    logger().info("storeMappingAndPersist {}", m);
    var stored = storage.store(mappings);
    logger().info("storeMappingAndPersist stored - {}", stored);
  }

  @Override
  public void close() {
    try {
      storage.close();
    } catch (Exception e) {
      logger().warn("Storage close failed", e);
    }
  }
}

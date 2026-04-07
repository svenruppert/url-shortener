package com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipsePreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseStatisticsStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseUrlMappingStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.types.StorageManager;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;

public final class EclipseStore
    implements HasLogger, Closeable {

  private final StorageManager storage;

  private final EclipseUrlMappingStore eclipseUrlMappingStore;
  private final EclipsePreferencesStore preferencesStore;
  private final EclipseStatisticsStore statisticsStore;


  public EclipseStore(String storageDir,
                      ShortCodeGenerator generator,
                      Clock clock) {

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
      logger().info("Found existing DataRoot");
      var size = r.shortUrlMappings().size();
      logger().info("DataRoot Mappings contains {} elements", size);
      // Ensure statistics fields are initialized (for backwards compatibility)
      if (r.ensureStatisticsInitialized()) {
        logger().info("Initialized missing statistics fields in existing DataRoot");
        storage.store(r.redirectEvents());
        storage.store(r.hourlyAggregates());
        storage.store(r.dailyAggregates());
        storage.store(r.statisticsConfig());
        storage.storeRoot();
      }
    } else {
      logger().info("No DataRoot found, creating a new one...");
      storage.setRoot(new DataRoot());
      storage.storeRoot();
    }

    this.eclipseUrlMappingStore = new EclipseUrlMappingStore(storage, clock, generator);
    this.preferencesStore = new EclipsePreferencesStore(storage);
    this.statisticsStore = new EclipseStatisticsStore(storage, clock);
  }

  public UrlMappingStore getUrlMappingStore() {
    return eclipseUrlMappingStore;
  }

  public PreferencesStore getPreferencesStore() {
    return preferencesStore;
  }

  public StatisticsStore getStatisticsStore() {
    return statisticsStore;
  }

  /**
   * Starts background processing threads for statistics.
   * Should be called once during application startup.
   */
  public void start() {
    logger().info("Starting EclipseStore services");
    statisticsStore.start();
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }


  /* ---------- internal ---------- */

  @Override
  public void close() {
    try {
      logger().info("Closing EclipseStore");
      statisticsStore.stop();
      storage.close();
    } catch (Exception e) {
      logger().warn("Storage close failed", e);
    }
  }
}

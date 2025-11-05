package com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipsePreferencesStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseUrlMappingStore;
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
  private final PreferencesStore preferencesStore;


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
      logger().info("found existing DataRoot");
      var size = r.shortUrlMappings().size();
      logger().info("DataRoot Mappings contains {} elements", size);
    } else {
      logger().info("no DataRoot found creating a new one..");
      storage.setRoot(new DataRoot());
      storage.storeRoot();
    }

    this.eclipseUrlMappingStore = new EclipseUrlMappingStore(storage, clock, generator);
    this.preferencesStore = new EclipsePreferencesStore(storage);
  }

  public UrlMappingStore getUrlMappingStore() {
    return eclipseUrlMappingStore;
  }

  public PreferencesStore getPreferencesStore() {
    return preferencesStore;
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }


  /* ---------- intern ---------- */

  @Override
  public void close() {
    try {
      storage.close();
    } catch (Exception e) {
      logger().warn("Storage close failed", e);
    }
  }
}

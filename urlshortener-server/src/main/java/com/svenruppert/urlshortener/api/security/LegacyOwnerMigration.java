package com.svenruppert.urlshortener.api.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.util.Objects;

/**
 * One-shot, idempotent migration that assigns a concrete owner to all URL
 * mappings whose {@code ownerUsername} is {@code null}. Such "legacy" mappings
 * originate from data sets created before owner tracking was introduced and
 * are otherwise treated as admin-only by {@link OwnerCheck}.
 * <p>
 * Triggered from {@code ShortenerServer.init(...)} via the system property
 * {@value #SYSPROP_LEGACY_OWNER}. The target user must exist in the
 * {@link UserStore}; otherwise the migration is skipped with a warning.
 */
public final class LegacyOwnerMigration implements HasLogger {

  public static final String SYSPROP_LEGACY_OWNER = "urlshortener.security.legacy-owner";

  private final UrlMappingStore mappingStore;
  private final UserStore userStore;

  public LegacyOwnerMigration(UrlMappingStore mappingStore, UserStore userStore) {
    this.mappingStore = Objects.requireNonNull(mappingStore, "mappingStore");
    this.userStore = Objects.requireNonNull(userStore, "userStore");
  }

  /**
   * Reads the configured target username from {@link #SYSPROP_LEGACY_OWNER}
   * and invokes {@link #migrate(String)}. Logs and returns 0 if the property
   * is absent or the target user does not exist.
   */
  public int runIfConfigured() {
    String target = System.getProperty(SYSPROP_LEGACY_OWNER);
    if (target == null || target.isBlank()) {
      return 0;
    }
    return migrate(target.trim());
  }

  /**
   * Iterates the mapping store, assigns {@code targetUsername} to every entry
   * with {@code ownerUsername == null}, and returns the number of migrated
   * mappings. Idempotent — a second invocation with no legacy entries returns
   * 0.
   */
  public int migrate(String targetUsername) {
    if (targetUsername == null || targetUsername.isBlank()) {
      logger().warn("LegacyOwnerMigration skipped: target username is blank");
      return 0;
    }
    if (userStore.findByUsername(targetUsername).isEmpty()) {
      logger().warn("LegacyOwnerMigration skipped: target user '{}' does not exist",
          targetUsername);
      return 0;
    }

    int migrated = 0;
    int skipped = 0;
    int[] failures = {0};
    for (ShortUrlMapping mapping : mappingStore.findAll()) {
      if (mapping.ownerUsername() != null) {
        skipped++;
        continue;
      }
      var result = mappingStore.assignOwner(mapping.shortCode(), targetUsername);
      if (result.isPresent()) {
        migrated++;
      } else {
        failures[0]++;
        result.ifFailed(msg -> logger().warn(
            "Failed to assign owner for mapping '{}': {}", mapping.shortCode(), msg));
      }
    }
    if (failures[0] > 0) {
      logger().warn("LegacyOwnerMigration encountered {} failures", failures[0]);
    }
    logger().info("LegacyOwnerMigration finished: migrated={}, skippedAlreadyOwned={}, target='{}'",
        migrated, skipped, targetUsername);
    return migrated;
  }
}

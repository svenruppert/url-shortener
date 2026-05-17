package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.security.LegacyOwnerMigration;
import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;
import com.svenruppert.urlshortener.api.security.user.InMemoryUserStore;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryUrlMappingStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.vaadin.security.authentication.PasswordHasher;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyOwnerMigrationTest {

  private UrlMappingStore mappings;
  private UserStore users;
  private LegacyOwnerMigration migration;

  @BeforeEach
  void setUp() {
    mappings = new InMemoryUrlMappingStore(new ShortCodeGenerator(1));
    PasswordHasher hasher = SecurityServiceResolver.passwordHashingService();
    users = new InMemoryUserStore(hasher);
    users.create("legacy-admin", "legacy-pw", "Legacy Admin", ShortenerRole.ROLE_ADMIN);
    migration = new LegacyOwnerMigration(mappings, users);
  }

  @Test
  @DisplayName("migrate assigns target owner only to mappings with null owner")
  void migrate_assigns_only_nulls() {
    mappings.createMapping(Instant.now(), "alpha", "https://example.com/a", null, true, null);
    mappings.createMapping(Instant.now(), "bravo", "https://example.com/b", null, true, "already-owned");
    mappings.createMapping(Instant.now(), "gamma", "https://example.com/g", null, true, null);

    int migrated = migration.migrate("legacy-admin");

    assertEquals(2, migrated);
    assertEquals("legacy-admin", findByCode("alpha").ownerUsername());
    assertEquals("already-owned", findByCode("bravo").ownerUsername());
    assertEquals("legacy-admin", findByCode("gamma").ownerUsername());
  }

  @Test
  @DisplayName("migrate is idempotent — second run finds nothing")
  void migrate_is_idempotent() {
    mappings.createMapping(Instant.now(), "delta", "https://example.com/d", null, true, null);
    assertEquals(1, migration.migrate("legacy-admin"));
    assertEquals(0, migration.migrate("legacy-admin"));
  }

  @Test
  @DisplayName("migrate skips when target user does not exist")
  void migrate_skips_unknown_target() {
    mappings.createMapping(Instant.now(), "epsilon", "https://example.com/e", null, true, null);
    int migrated = migration.migrate("does-not-exist");
    assertEquals(0, migrated);
    assertNull(findByCode("epsilon").ownerUsername(),
        "mapping owner must remain untouched when target user is unknown");
  }

  @Test
  @DisplayName("runIfConfigured is a no-op when system property is unset")
  void runIfConfigured_no_property() {
    String previous = System.clearProperty(LegacyOwnerMigration.SYSPROP_LEGACY_OWNER);
    try {
      mappings.createMapping(Instant.now(), "zeta", "https://example.com/z", null, true, null);
      assertEquals(0, migration.runIfConfigured());
      assertNull(findByCode("zeta").ownerUsername());
    } finally {
      if (previous != null) System.setProperty(LegacyOwnerMigration.SYSPROP_LEGACY_OWNER, previous);
    }
  }

  @Test
  @DisplayName("runIfConfigured reads the username from the system property")
  void runIfConfigured_reads_property() {
    String previous = System.getProperty(LegacyOwnerMigration.SYSPROP_LEGACY_OWNER);
    System.setProperty(LegacyOwnerMigration.SYSPROP_LEGACY_OWNER, "legacy-admin");
    try {
      mappings.createMapping(Instant.now(), "eta", "https://example.com/eta", null, true, null);
      assertEquals(1, migration.runIfConfigured());
      assertEquals("legacy-admin", findByCode("eta").ownerUsername());
    } finally {
      if (previous == null) {
        System.clearProperty(LegacyOwnerMigration.SYSPROP_LEGACY_OWNER);
      } else {
        System.setProperty(LegacyOwnerMigration.SYSPROP_LEGACY_OWNER, previous);
      }
    }
  }

  @Test
  @DisplayName("blank target username is rejected")
  void blank_target_is_rejected() {
    mappings.createMapping(Instant.now(), "theta", "https://example.com/t", null, true, null);
    assertEquals(0, migration.migrate("   "));
    assertFalse(findByCode("theta").ownerUsername() != null);
  }

  private ShortUrlMapping findByCode(String code) {
    return mappings.findByShortCode(code).orElseThrow();
  }
}

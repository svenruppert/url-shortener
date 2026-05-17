package junit.com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EclipseUserStorePersistenceTest {

  @Test
  @DisplayName("User created in one session can authenticate after restart")
  void user_survives_restart(@TempDir Path tempDir) {
    String username = "alice";
    String password = "alice-pw";

    try (EclipseStore first = open(tempDir)) {
      UserStore users = first.getUserStore();
      users.create(username, password, "Alice", ShortenerRole.ROLE_USER);
      assertTrue(users.findByUsername(username).isPresent());
    }

    try (EclipseStore second = open(tempDir)) {
      UserStore users = second.getUserStore();
      Optional<ShortenerUser> found = users.findByUsername(username);
      assertTrue(found.isPresent(), "user must be persisted across restarts");
      assertEquals(ShortenerRole.ROLE_USER, found.get().role());
      assertTrue(users.authenticate(username, password).isPresent(),
          "authenticate must succeed against the persisted password hash");
    }
  }

  @Test
  @DisplayName("setRole, setEnabled, delete are persisted")
  void mutations_are_persisted(@TempDir Path tempDir) {
    try (EclipseStore first = open(tempDir)) {
      UserStore users = first.getUserStore();
      users.create("bob", "bob-pw", "Bob", ShortenerRole.ROLE_USER);
      users.create("carol", "carol-pw", "Carol", ShortenerRole.ROLE_USER);
      assertTrue(users.setRole("bob", ShortenerRole.ROLE_ADMIN));
      assertTrue(users.setEnabled("carol", false));
    }

    try (EclipseStore second = open(tempDir)) {
      UserStore users = second.getUserStore();
      assertEquals(ShortenerRole.ROLE_ADMIN, users.findByUsername("bob").orElseThrow().role());
      assertFalse(users.findByUsername("carol").orElseThrow().enabled());
      assertFalse(users.authenticate("carol", "carol-pw").isPresent(),
          "disabled user must not authenticate after restart");
      assertTrue(users.deleteUser("bob"));
    }

    try (EclipseStore third = open(tempDir)) {
      UserStore users = third.getUserStore();
      assertFalse(users.findByUsername("bob").isPresent());
      assertTrue(users.findByUsername("carol").isPresent());
    }
  }

  @Test
  @DisplayName("hasAnyAdministrator reflects persisted role assignments")
  void hasAnyAdministrator_persists(@TempDir Path tempDir) {
    try (EclipseStore first = open(tempDir)) {
      UserStore users = first.getUserStore();
      assertFalse(users.hasAnyAdministrator());
      users.create("root", "root-pw", "Root", ShortenerRole.ROLE_ADMIN);
      assertTrue(users.hasAnyAdministrator());
    }

    try (EclipseStore second = open(tempDir)) {
      assertTrue(second.getUserStore().hasAnyAdministrator(),
          "admin presence must persist across restart");
    }
  }

  private static EclipseStore open(Path dir) {
    return new EclipseStore(dir.toString(), new ShortCodeGenerator(1), Clock.systemUTC());
  }
}

package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.client.AuthFailureRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthFailureRegistryTest {

  @AfterEach
  void resetRegistry() {
    AuthFailureRegistry.clear();
  }

  @Test
  @DisplayName("fire() is a no-op when no listener is registered")
  void fire_without_listener_is_silent() {
    // Must not throw — silent no-op.
    AuthFailureRegistry.fire();
  }

  @Test
  @DisplayName("set(listener) + fire() invokes the listener exactly once per call")
  void set_and_fire_invokes_listener() {
    AtomicInteger counter = new AtomicInteger();
    AuthFailureRegistry.set(counter::incrementAndGet);
    AuthFailureRegistry.fire();
    AuthFailureRegistry.fire();
    assertEquals(2, counter.get());
  }

  @Test
  @DisplayName("notifyIfUnauthorized triggers only on 401")
  void notify_only_on_401() {
    AtomicInteger counter = new AtomicInteger();
    AuthFailureRegistry.set(counter::incrementAndGet);

    AuthFailureRegistry.notifyIfUnauthorized(200);
    AuthFailureRegistry.notifyIfUnauthorized(403);
    AuthFailureRegistry.notifyIfUnauthorized(404);
    AuthFailureRegistry.notifyIfUnauthorized(500);
    assertEquals(0, counter.get(), "non-401 must not trigger the listener");

    AuthFailureRegistry.notifyIfUnauthorized(401);
    assertEquals(1, counter.get(), "401 must trigger the listener exactly once");
  }

  @Test
  @DisplayName("a faulty listener never propagates failures to the caller")
  void faulty_listener_is_swallowed() {
    AuthFailureRegistry.set(() -> { throw new IllegalStateException("boom"); });
    // Must not throw — fire() is required to swallow listener exceptions.
    AuthFailureRegistry.fire();
    AuthFailureRegistry.notifyIfUnauthorized(401);
  }

  @Test
  @DisplayName("clear() removes the listener so subsequent fires are silent")
  void clear_removes_listener() {
    AtomicInteger counter = new AtomicInteger();
    AuthFailureRegistry.set(counter::incrementAndGet);
    AuthFailureRegistry.clear();
    AuthFailureRegistry.fire();
    AuthFailureRegistry.notifyIfUnauthorized(401);
    assertEquals(0, counter.get());
  }
}

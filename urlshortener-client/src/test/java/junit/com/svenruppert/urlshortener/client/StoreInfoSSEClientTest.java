package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient.ConnectionState;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.StoreInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StoreInfoSSEClientTest {

  private ShortenerServer server;
  private URLShortenerClient urlClient;
  private StoreInfoSSEClient sseClient;

  @BeforeEach
  void startServer()
      throws IOException {
    server = new ShortenerServer();
    server.init();
    urlClient = new URLShortenerClient();
  }

  @AfterEach
  void stopServer() {
    if (sseClient != null) {
      sseClient.close();
    }
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  @DisplayName("Client empfängt initialen StoreInfo nach Verbindungsaufbau")
  void receivesInitialStoreInfo()
      throws Exception {
    var latch = new CountDownLatch(1);
    var receivedInfo = new AtomicReference<StoreInfo>();

    sseClient = new StoreInfoSSEClient(info -> {
      receivedInfo.set(info);
      latch.countDown();
    });

    sseClient.start();

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout beim Warten auf StoreInfo");

    StoreInfo info = receivedInfo.get();
    assertNotNull(info);
    assertEquals("InMemory", info.mode());
    assertEquals(0, info.mappings());
    assertTrue(info.startedAtEpochMs() > 0);
  }

  @Test
  @DisplayName("Client meldet ConnectionState.CONNECTED nach erfolgreichem Verbindungsaufbau")
  void reportsConnectedState()
      throws Exception {
    var connectedLatch = new CountDownLatch(1);
    var states = Collections.synchronizedList(new ArrayList<ConnectionState>());

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          states.add(state);
          if (state == ConnectionState.CONNECTED) {
            connectedLatch.countDown();
          }
        }
    );

    sseClient.start();

    assertTrue(connectedLatch.await(5, TimeUnit.SECONDS), "Timeout beim Warten auf CONNECTED");
    assertTrue(states.contains(ConnectionState.CONNECTING));
    assertTrue(states.contains(ConnectionState.CONNECTED));
  }

  @Test
  @DisplayName("Client empfängt Updates wenn sich Mappings ändern")
  void receivesUpdatesOnMappingChange()
      throws Exception {
    var receivedInfos = Collections.synchronizedList(new ArrayList<StoreInfo>());
    var initialLatch = new CountDownLatch(1);
    var updateLatch = new CountDownLatch(2);

    sseClient = new StoreInfoSSEClient(info -> {
      receivedInfos.add(info);
      initialLatch.countDown();
      updateLatch.countDown();
    });

    sseClient.start();

    // Warten auf initiales Event
    assertTrue(initialLatch.await(5, TimeUnit.SECONDS));
    assertEquals(0, receivedInfos.get(0).mappings());

    // Mapping erstellen - Server sendet Update nach max 10 Sekunden
    urlClient.createCustomMapping("test123", "https://example.com");

    // Warten auf Update (mit etwas Puffer für den 10-Sekunden-Zyklus)
    assertTrue(updateLatch.await(15, TimeUnit.SECONDS), "Timeout beim Warten auf Update");

    // Mindestens ein Update mit mappings > 0 sollte dabei sein
    boolean hasUpdate = receivedInfos.stream()
        .anyMatch(info -> info.mappings() > 0);
    assertTrue(hasUpdate, "Kein Update mit erhöhtem Mapping-Count empfangen");

    // Aufräumen
    urlClient.delete("test123");
  }

  @Test
  @DisplayName("Client stoppt sauber ohne Exception")
  void stopsCleanly()
      throws Exception {
    var connectedLatch = new CountDownLatch(1);
    var disconnectedLatch = new CountDownLatch(1);

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          if (state == ConnectionState.CONNECTED) {
            connectedLatch.countDown();
          }
          if (state == ConnectionState.DISCONNECTED) {
            disconnectedLatch.countDown();
          }
        }
    );

    sseClient.start();
    assertTrue(sseClient.isRunning());

    assertTrue(connectedLatch.await(5, TimeUnit.SECONDS));

    // Stoppen
    sseClient.stop();

    assertTrue(disconnectedLatch.await(2, TimeUnit.SECONDS));
    assertFalse(sseClient.isRunning());
  }

  @Test
  @DisplayName("Client kann mehrfach start() aufrufen ohne Fehler")
  void multipleStartCallsAreIdempotent()
      throws Exception {
    var latch = new CountDownLatch(1);

    sseClient = new StoreInfoSSEClient(info -> latch.countDown());

    sseClient.start();
    sseClient.start(); // zweiter Aufruf sollte ignoriert werden
    sseClient.start(); // dritter Aufruf auch

    assertTrue(sseClient.isRunning());
    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("Client kann stop() ohne vorheriges start() aufrufen")
  void stopWithoutStartDoesNotThrow() {
    sseClient = new StoreInfoSSEClient(info -> { });

    assertFalse(sseClient.isRunning());
    assertDoesNotThrow(() -> sseClient.stop());
    assertFalse(sseClient.isRunning());
  }

  @Test
  @DisplayName("close() stoppt den Client")
  void closeStopsClient()
      throws Exception {
    var connectedLatch = new CountDownLatch(1);

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          if (state == ConnectionState.CONNECTED) {
            connectedLatch.countDown();
          }
        }
    );

    sseClient.start();
    assertTrue(connectedLatch.await(5, TimeUnit.SECONDS));
    assertTrue(sseClient.isRunning());

    sseClient.close();

    assertFalse(sseClient.isRunning());
  }
}
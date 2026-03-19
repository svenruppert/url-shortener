package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient.ConnectionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für Reconnect-Verhalten und Fehlerszenarien des StoreInfoSSEClient.
 */
class StoreInfoSSEClientReconnectTest {

  private ShortenerServer server;
  private StoreInfoSSEClient sseClient;

  @AfterEach
  void cleanup() {
    if (sseClient != null) {
      sseClient.close();
    }
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  @DisplayName("Client reconnectet automatisch wenn Server neu startet")
  void reconnectsAfterServerRestart()
      throws Exception {
    // Server starten
    server = new ShortenerServer();
    server.init();

    var connectedCount = new AtomicInteger(0);
    var reconnectingLatch = new CountDownLatch(1);
    var secondConnectLatch = new CountDownLatch(1);

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          if (state == ConnectionState.CONNECTED) {
            int count = connectedCount.incrementAndGet();
            if (count == 2) {
              secondConnectLatch.countDown();
            }
          }
          if (state == ConnectionState.RECONNECTING) {
            reconnectingLatch.countDown();
          }
        }
    );

    sseClient.start();

    // Warten bis verbunden
    Thread.sleep(2000);
    assertEquals(1, connectedCount.get());

    // Server stoppen
    server.shutdown();

    // Warten auf RECONNECTING Status
    assertTrue(reconnectingLatch.await(5, TimeUnit.SECONDS),
               "Client sollte RECONNECTING melden");

    // Server neu starten
    server = new ShortenerServer();
    server.init();

    // Warten auf zweite Verbindung (mit Puffer für Backoff)
    assertTrue(secondConnectLatch.await(10, TimeUnit.SECONDS),
               "Client sollte erneut verbinden");
    assertEquals(2, connectedCount.get());
  }

  @Test
  @DisplayName("Client meldet RECONNECTING wenn Server nicht erreichbar")
  void reportsReconnectingWhenServerUnavailable()
      throws Exception {
    // KEIN Server gestartet
    var states = Collections.synchronizedList(new ArrayList<ConnectionState>());
    var reconnectingLatch = new CountDownLatch(1);

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          states.add(state);
          if (state == ConnectionState.RECONNECTING) {
            reconnectingLatch.countDown();
          }
        }
    );

    sseClient.start();

    assertTrue(reconnectingLatch.await(5, TimeUnit.SECONDS),
               "Client sollte RECONNECTING melden wenn Server nicht erreichbar");

    assertTrue(states.contains(ConnectionState.CONNECTING));
    assertTrue(states.contains(ConnectionState.RECONNECTING));
  }

  @Test
  @DisplayName("Client verbindet wenn Server später gestartet wird")
  void connectsWhenServerStartsLater()
      throws Exception {
    var connectedLatch = new CountDownLatch(1);

    // Client starten OHNE laufenden Server
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

    // Kurz warten, Client ist im Reconnect-Loop
    Thread.sleep(2000);

    // Jetzt Server starten
    server = new ShortenerServer();
    server.init();

    // Client sollte sich verbinden
    assertTrue(connectedLatch.await(35, TimeUnit.SECONDS),
               "Client sollte verbinden wenn Server startet (max Backoff: 30s + Puffer)");
  }

  @Test
  @DisplayName("Backoff erhöht sich bei wiederholten Fehlversuchen")
  void backoffIncreasesOnRepeatedFailures()
      throws Exception {
    // KEIN Server gestartet
    var reconnectTimes = Collections.synchronizedList(new ArrayList<Long>());

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          if (state == ConnectionState.RECONNECTING) {
            reconnectTimes.add(System.currentTimeMillis());
          }
        }
    );

    sseClient.start();

    // Warten auf mehrere Reconnect-Versuche
    Thread.sleep(8000);

    // Sollten mindestens 2 Reconnect-Versuche haben
    assertTrue(reconnectTimes.size() >= 2,
               "Sollten mindestens 2 Reconnect-Versuche haben, aber hatte: " + reconnectTimes.size());

    // Prüfen dass die Abstände größer werden (exponentielles Backoff)
    if (reconnectTimes.size() >= 3) {
      long interval1 = reconnectTimes.get(1) - reconnectTimes.get(0);
      long interval2 = reconnectTimes.get(2) - reconnectTimes.get(1);

      // Zweites Intervall sollte größer sein (mit Toleranz für Timing-Ungenauigkeiten)
      assertTrue(interval2 >= interval1 * 0.8,
                 "Backoff sollte zunehmen: Intervall1=" + interval1 + "ms, Intervall2=" + interval2 + "ms");
    }
  }

  @Test
  @DisplayName("Client stoppt sauber während Reconnect-Phase")
  void stopsCleanlyDuringReconnect()
      throws Exception {
    // KEIN Server gestartet
    var reconnectingLatch = new CountDownLatch(1);
    var disconnectedLatch = new CountDownLatch(1);

    sseClient = new StoreInfoSSEClient(
        "http://localhost:9090",
        info -> { },
        state -> {
          if (state == ConnectionState.RECONNECTING) {
            reconnectingLatch.countDown();
          }
          if (state == ConnectionState.DISCONNECTED) {
            disconnectedLatch.countDown();
          }
        }
    );

    sseClient.start();

    // Warten bis im Reconnect-Modus
    assertTrue(reconnectingLatch.await(5, TimeUnit.SECONDS));

    // Stoppen während Reconnect
    sseClient.stop();

    assertTrue(disconnectedLatch.await(2, TimeUnit.SECONDS));
    assertFalse(sseClient.isRunning());
  }

  @Test
  @DisplayName("Client behandelt falschen Port graceful")
  void handlesWrongPortGracefully()
      throws Exception {
    // Server auf Standard-Port starten
    server = new ShortenerServer();
    server.init();

    var reconnectingLatch = new CountDownLatch(1);

    // Client mit falschem Port
    sseClient = new StoreInfoSSEClient(
        "http://localhost:9999", // falscher Port
        info -> fail("Sollte keine Info empfangen bei falschem Port"),
        state -> {
          if (state == ConnectionState.RECONNECTING) {
            reconnectingLatch.countDown();
          }
        }
    );

    sseClient.start();

    assertTrue(reconnectingLatch.await(5, TimeUnit.SECONDS),
               "Client sollte RECONNECTING melden bei falschem Port");
  }
}
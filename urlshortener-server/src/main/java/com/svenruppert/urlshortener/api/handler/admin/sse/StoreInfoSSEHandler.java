package com.svenruppert.urlshortener.api.handler.admin.sse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * HTTP-Handler für Server-Sent Events (SSE) zum Streaming von StoreInfo-Updates.
 * Hält die Verbindung offen und delegiert das Event-Senden an den StoreInfoBroadcaster.
 */
public class StoreInfoSSEHandler
    implements HttpHandler, HasLogger {

  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String CACHE_CONTROL = "no-cache";
  private static final String CONNECTION = "keep-alive";

  private final StoreInfoBroadcaster broadcaster;

  public StoreInfoSSEHandler(StoreInfoBroadcaster broadcaster) {
    this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) {
      return;
    }

    logger().info("New SSE-connection from {}", exchange.getRemoteAddress());

    // SSE-Header setzen
    var headers = exchange.getResponseHeaders();
    headers.set("Content-Type", CONTENT_TYPE_SSE + "; charset=utf-8");
    headers.set("Cache-Control", CACHE_CONTROL);
    headers.set("Connection", CONNECTION);
    headers.set("Access-Control-Allow-Origin", "*");

    // Chunked Transfer: 0 bedeutet "unbekannte Länge" → Streaming-Modus
    exchange.sendResponseHeaders(HttpStatus.OK.code(), 0);

    OutputStream outputStream = exchange.getResponseBody();

    // Client beim Broadcaster registrieren (sendet sofort initialen Status)
    broadcaster.register(outputStream);

    try {
      // Verbindung offen halten bis der Client abbricht
      // Der Broadcaster sendet Events und Heartbeats über den OutputStream
      keepConnectionAlive(outputStream);
    } catch (Exception e) {
      logger().debug("SSE-connection closed: {}", e.getMessage());
    } finally {
      broadcaster.unregister(outputStream);
      closeQuietly(outputStream);
      exchange.close();
      logger().info("SSE-connection closed");
    }
  }

  /**
   * Hält die Verbindung offen, indem periodisch geprüft wird ob der Stream noch schreibbar ist.
   * Der Thread blockiert hier und wird erst beendet wenn die Verbindung abbricht.
   */
  private void keepConnectionAlive(OutputStream outputStream) {
    try {
      // Wir nutzen eine einfache Strategie: warten bis eine IOException auftritt
      // Das passiert wenn der Client die Verbindung schließt oder ein Netzwerkfehler auftritt
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(1000);

        // Prüfen ob der Stream noch gültig ist (wird bei flush() eine Exception werfen wenn nicht)
        // Der eigentliche Heartbeat wird vom Broadcaster gesendet
        try {
          outputStream.flush();
        } catch (IOException e) {
          // Verbindung ist weg
          break;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void closeQuietly(OutputStream outputStream) {
    try {
      outputStream.close();
    } catch (IOException ignored) {
      // Kann beim Schließen einer bereits geschlossenen Verbindung passieren
    }
  }
}
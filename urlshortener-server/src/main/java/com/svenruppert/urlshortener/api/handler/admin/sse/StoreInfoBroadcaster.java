package com.svenruppert.urlshortener.api.handler.admin.sse;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions.EclipseUrlMappingStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central broadcaster for StoreInfo updates via SSE.
 * Polls the store every second and sends an event to all connected
 * clients when changes are detected. Heartbeats are sent every 10
 * seconds to prevent proxy timeouts.
 */
public class StoreInfoBroadcaster
    implements HasLogger, AutoCloseable {

  private static final long UPDATE_INTERVAL_MS = 1_000;
  private static final long HEARTBEAT_INTERVAL_MS = 10_000;

  private final UrlMappingStore store;
  private final long startedAtMs;
  private final List<SseClient> clients = new CopyOnWriteArrayList<>();

  private final ScheduledExecutorService scheduler;
  private StoreInfo lastBroadcastedInfo;

  public StoreInfoBroadcaster(UrlMappingStore store, long startedAtMs) {
    this.store = Objects.requireNonNull(store, "store");
    this.startedAtMs = startedAtMs;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "StoreInfoBroadcaster");
      t.setDaemon(true);
      return t;
    });

    scheduler.scheduleAtFixedRate(
        this::checkAndBroadcast,
        UPDATE_INTERVAL_MS,
        UPDATE_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    );

    scheduler.scheduleAtFixedRate(
        this::sendHeartbeat,
        HEARTBEAT_INTERVAL_MS,
        HEARTBEAT_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    );

    logger().info("StoreInfoBroadcaster started - Update interval: {}ms, Heartbeat: {}ms",
                  UPDATE_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
  }

  /**
   * Registers a new SSE client and immediately sends the current status.
   */
  public void register(OutputStream outputStream) {
    var client = new SseClient(outputStream);
    clients.add(client);
    logger().info("SSE client registered, active clients: {}", clients.size());

    // Immediately send current status
    var currentInfo = buildStoreInfo();
    sendToClient(client, currentInfo);
  }

  /**
   * Removes a client from the list (called on connection loss).
   */
  public void unregister(OutputStream outputStream) {
    clients.removeIf(c -> c.outputStream() == outputStream);
    logger().info("SSE client removed, remaining clients: {}", clients.size());
  }

  /**
   * Checks if the store status has changed and sends an update if needed.
   */
  private void checkAndBroadcast() {
    try {
      var currentInfo = buildStoreInfo();

      if (hasChanged(currentInfo)) {
        lastBroadcastedInfo = currentInfo;
        broadcast(currentInfo);
      }
    } catch (Exception e) {
      logger().warn("Error during broadcast: {}", e.getMessage());
    }
  }

  private boolean hasChanged(StoreInfo current) {
    if (lastBroadcastedInfo == null) {
      return true;
    }
    return current.mappings() != lastBroadcastedInfo.mappings()
        || !Objects.equals(current.mode(), lastBroadcastedInfo.mode());
  }

  private StoreInfo buildStoreInfo() {
    String mode = (store instanceof EclipseUrlMappingStore) ? "EclipseStore" : "InMemory";
    int mappings = store.countAll();
    return new StoreInfo(mode, mappings, startedAtMs);
  }

  private void broadcast(StoreInfo info) {
    if (clients.isEmpty()) {
      return;
    }

    logger().debug("Broadcasting StoreInfo to {} clients: {}", clients.size(), info);

    for (var client : clients) {
      sendToClient(client, info);
    }
  }

  private void sendToClient(SseClient client, StoreInfo info) {
    try {
      String json = JsonUtils.toJson(info);
      String sseEvent = "data: " + json + "\n\n";
      client.outputStream().write(sseEvent.getBytes(StandardCharsets.UTF_8));
      client.outputStream().flush();
    } catch (IOException e) {
      logger().debug("Client unreachable, will be removed: {}", e.getMessage());
      clients.remove(client);
    }
  }

  private void sendHeartbeat() {
    if (clients.isEmpty()) {
      return;
    }

    byte[] heartbeat = ":keepalive\n\n".getBytes(StandardCharsets.UTF_8);

    for (var client : clients) {
      try {
        client.outputStream().write(heartbeat);
        client.outputStream().flush();
      } catch (IOException e) {
        logger().debug("Heartbeat failed, client is being removed.");
        clients.remove(client);
      }
    }
  }

  public int getConnectedClientCount() {
    return clients.size();
  }

  @Override
  public void close() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    clients.clear();
    logger().info("StoreInfoBroadcaster stopped");
  }

  private record SseClient(OutputStream outputStream) { }
}
package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.DefaultValues;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;

/**
 * SSE client for Server-Sent Events from the StoreInfo endpoint.
 * Automatically establishes a connection, receives events and
 * reconnects on connection loss with exponential backoff.
 */
public class StoreInfoSSEClient
    implements HasLogger, AutoCloseable {

  private static final int INITIAL_BACKOFF_MS = 1_000;
  private static final int MAX_BACKOFF_MS = 10_000;
  private static final int CONNECT_TIMEOUT_MS = 1_000;
  private static final int READ_TIMEOUT_MS = 0; // no timeout for SSE

  private final URI sseEndpoint;
  private final Consumer<StoreInfo> onStoreInfo;
  private final Consumer<ConnectionState> onConnectionStateChange;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicInteger currentBackoffMs = new AtomicInteger(INITIAL_BACKOFF_MS);

  private Thread connectionThread;
  private HttpURLConnection currentConnection;

  /**
   * Creates an SSE client with the default server URL.
   *
   * @param onStoreInfo callback for incoming StoreInfo updates
   */
  public StoreInfoSSEClient(Consumer<StoreInfo> onStoreInfo) {
    this(ADMIN_SERVER_URL, onStoreInfo, state -> { });
  }

  /**
   * Creates an SSE client with a configurable server URL.
   *
   * @param serverBaseUrl           base URL of the admin server (e.g. "http://localhost:9090")
   * @param onStoreInfo             callback for incoming StoreInfo updates
   * @param onConnectionStateChange callback for connection state changes
   */
  public StoreInfoSSEClient(String serverBaseUrl,
                            Consumer<StoreInfo> onStoreInfo,
                            Consumer<ConnectionState> onConnectionStateChange) {
    Objects.requireNonNull(serverBaseUrl, "serverBaseUrl");
    this.onStoreInfo = Objects.requireNonNull(onStoreInfo, "onStoreInfo");
    this.onConnectionStateChange = Objects.requireNonNull(onConnectionStateChange, "onConnectionStateChange");

    String baseUrl = serverBaseUrl.endsWith("/")
        ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1)
        : serverBaseUrl;
    this.sseEndpoint = URI.create(baseUrl + DefaultValues.PATH_ADMIN_STORE_INFO_SSE);

    logger().info("StoreInfoSSEClient init for {}", sseEndpoint);
  }

  /**
   * Starts the SSE connection in a background thread.
   * Can be called multiple times but only starts once.
   */
  public void start() {
    if (running.compareAndSet(false, true)) {
      connectionThread = new Thread(this::connectionLoop, "StoreInfoSSEClient");
      connectionThread.setDaemon(true);
      connectionThread.start();
      logger().info("StoreInfoSSEClient started");
    }
  }

  /**
   * Stops the SSE connection and terminates the background thread.
   */
  public void stop() {
    if (running.compareAndSet(true, false)) {
      logger().info("StoreInfoSSEClient stopping...");

      // Close connection to interrupt the blocking read
      if (currentConnection != null) {
        currentConnection.disconnect();
      }

      // Interrupt thread if it's sleeping
      if (connectionThread != null) {
        connectionThread.interrupt();
      }

      notifyConnectionState(ConnectionState.DISCONNECTED);
      logger().info("StoreInfoSSEClient stopped");
    }
  }

  @Override
  public void close() {
    stop();
  }

  public boolean isRunning() {
    return running.get();
  }

  /**
   * Main loop: connects, reads events, reconnects on error.
   */
  private void connectionLoop() {
    while (running.get()) {
      try {
        notifyConnectionState(ConnectionState.CONNECTING);
        connect();

        // Successful connection: reset backoff
        currentBackoffMs.set(INITIAL_BACKOFF_MS);
        notifyConnectionState(ConnectionState.CONNECTED);

        readEvents();

      } catch (IOException e) {
        if (!running.get()) {
          // Intentional abort via stop()
          break;
        }
        logger().warn("SSE connection failed: {}", e.getMessage());
        notifyConnectionState(ConnectionState.RECONNECTING);
        waitBeforeReconnect();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void connect()
      throws IOException {
    URL url = sseEndpoint.toURL();
    currentConnection = (HttpURLConnection) url.openConnection();
    currentConnection.setRequestMethod("GET");
    currentConnection.setRequestProperty("Accept", "text/event-stream");
    currentConnection.setRequestProperty("Cache-Control", "no-cache");
    currentConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    currentConnection.setReadTimeout(READ_TIMEOUT_MS);
    currentConnection.setDoInput(true);

    int responseCode = currentConnection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("Server responded with HTTP " + responseCode);
    }

    logger().info("SSE connection established to {}", sseEndpoint);
  }

  private void readEvents()
      throws IOException, InterruptedException {
    try (var reader = new BufferedReader(
        new InputStreamReader(currentConnection.getInputStream(), StandardCharsets.UTF_8))) {

      StringBuilder eventData = new StringBuilder();
      String line;

      while (running.get() && (line = reader.readLine()) != null) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }

        if (line.startsWith("data:")) {
          // Data line: "data: {...}"
          String data = line.substring(5).trim();
          eventData.append(data);

        } else if (line.startsWith(":")) {
          // Comment (e.g. ":keepalive") - ignore
          logger().trace("SSE heartbeat received");

        } else if (line.isEmpty() && !eventData.isEmpty()) {
          // Empty line = end of event
          processEvent(eventData.toString());
          eventData.setLength(0);
        }
      }
    }
  }

  private void processEvent(String jsonData) {
    try {
      StoreInfo storeInfo = JsonUtils.fromJson(jsonData, StoreInfo.class);
      logger().debug("StoreInfo received: {}", storeInfo);
      onStoreInfo.accept(storeInfo);
    } catch (Exception e) {
      logger().warn("Error parsing SSE event: {} - data: {}", e.getMessage(), jsonData);
    }
  }

  private void waitBeforeReconnect() {
    int backoff = currentBackoffMs.get();
    logger().info("Reconnecting in {}ms...", backoff);

    try {
      Thread.sleep(backoff);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    // Exponential backoff up to maximum
    int nextBackoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
    currentBackoffMs.set(nextBackoff);
  }

  private void notifyConnectionState(ConnectionState state) {
    try {
      onConnectionStateChange.accept(state);
    } catch (Exception e) {
      logger().warn("Error in ConnectionState callback: {}", e.getMessage());
    }
  }

  /**
   * Connection state of the SSE client.
   */
  public enum ConnectionState {
    /**
     * Connection is being established
     */
    CONNECTING,
    /**
     * Connection is active, events are being received
     */
    CONNECTED,
    /**
     * Connection lost, reconnect in progress
     */
    RECONNECTING,
    /**
     * Client has been stopped
     */
    DISCONNECTED
  }
}
package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient.ConnectionState;
import com.svenruppert.urlshortener.core.StoreInfo;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;

/**
 * Session-scoped service that manages the SSE connection and notifies
 * UI components about changes.
 *
 * <p>Uses the Vaadin broadcaster pattern for thread-safe UI updates.
 *
 * <p>Usage in components:
 * <pre>{@code
 * StoreInfoService service = StoreInfoService.get();
 * Registration reg = service.register(getUI().orElseThrow(), (info, state) -> {
 *     // update UI...
 * });
 * // In onDetach: reg.remove();
 * }</pre>
 */
public class StoreInfoService implements HasLogger, AutoCloseable {

  /**
   * Initial StoreInfo value when no data has been received yet.
   */
  private static final StoreInfo INITIAL_STORE_INFO =
      new StoreInfo("Unknown", 0, 0L);

  private final AtomicReference<StoreInfo> currentStoreInfo =
      new AtomicReference<>(INITIAL_STORE_INFO);
  private final AtomicReference<ConnectionState> currentConnectionState =
      new AtomicReference<>(ConnectionState.DISCONNECTED);

  // Map from UI to callback - ConcurrentHashMap for thread safety
  private final Map<UI, BiConsumer<StoreInfo, ConnectionState>> listeners =
      new ConcurrentHashMap<>();

  private final StoreInfoSSEClient sseClient;

  /**
   * Creates a new StoreInfoService with the default server URL.
   */
  public StoreInfoService() {
    this(ADMIN_SERVER_URL);
  }

  /**
   * Creates a new StoreInfoService with a configurable server URL.
   *
   * @param serverBaseUrl base URL of the admin server
   */
  public StoreInfoService(String serverBaseUrl) {
    Objects.requireNonNull(serverBaseUrl, "serverBaseUrl");

    // Create SSE client with callbacks
    this.sseClient = new StoreInfoSSEClient(
        serverBaseUrl,
        this::onStoreInfoReceived,
        this::onConnectionStateChanged
    );

    // Start connection
    sseClient.start();
    logger().info("StoreInfoService started for session");
  }

  /**
   * Gets the StoreInfoService instance for the current VaadinSession.
   * Creates a new instance if none exists yet.
   *
   * @return the service instance for this session
   * @throws IllegalStateException if no VaadinSession is active
   */
  public static StoreInfoService get() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) {
      throw new IllegalStateException("No active VaadinSession");
    }

    StoreInfoService service = session.getAttribute(StoreInfoService.class);
    if (service == null) {
      service = new StoreInfoService();
      session.setAttribute(StoreInfoService.class, service);

      // Register cleanup on session end
      final StoreInfoService finalService = service;
      session.getService().addSessionDestroyListener(event -> {
        if (event.getSession() == session) {
          finalService.close();
        }
      });
    }
    return service;
  }

  /**
   * Registers a listener for StoreInfo and ConnectionState updates.
   * The callback is invoked via ui.access(), so it is thread-safe.
   *
   * @param ui       the UI instance for thread-safe updates
   * @param callback the callback to invoke on updates
   * @return Registration to remove the listener
   */
  public Registration register(UI ui, BiConsumer<StoreInfo, ConnectionState> callback) {
    Objects.requireNonNull(ui, "ui");
    Objects.requireNonNull(callback, "callback");

    listeners.put(ui, callback);
    logger().info("Listener registered for UI {}, active listeners: {}", ui.getUIId(), listeners.size());

    // Immediately send current status
    ui.access(() -> {
      callback.accept(currentStoreInfo.get(), currentConnectionState.get());
      ui.push();
    });

    return () -> {
      listeners.remove(ui);
      logger().debug("Listener removed, active listeners: {}", listeners.size());
    };
  }

  /**
   * Current StoreInfo value.
   *
   * @return the current StoreInfo value
   */
  public StoreInfo currentStoreInfo() {
    return currentStoreInfo.get();
  }

  /**
   * Current connection state.
   *
   * @return the current ConnectionState
   */
  public ConnectionState currentConnectionState() {
    return currentConnectionState.get();
  }

  /**
   * Checks if the SSE connection is active.
   *
   * @return true if connected
   */
  public boolean isConnected() {
    return currentConnectionState.get() == ConnectionState.CONNECTED;
  }

  @Override
  public void close() {
    logger().info("StoreInfoService closing");
    listeners.clear();
    sseClient.close();
  }

  // --- Callbacks from the SSE client ---

  private void onStoreInfoReceived(StoreInfo info) {
    logger().debug("StoreInfo received: mode={}, mappings={}", info.mode(), info.mappings());
    currentStoreInfo.set(info);
    broadcast();
  }

  private void onConnectionStateChanged(ConnectionState state) {
    logger().debug("ConnectionState changed: {}", state);
    currentConnectionState.set(state);
    broadcast();
  }

  /**
   * Notifies all registered listeners about changes.
   * Uses ui.access() for thread-safe UI updates.
   */
  private void broadcast() {
    StoreInfo info = currentStoreInfo.get();
    ConnectionState state = currentConnectionState.get();

    logger().info("Broadcasting to {} listeners: state={}, mode={}, mappings={}",
                  listeners.size(), state, info.mode(), info.mappings());

    // Copy keys to avoid ConcurrentModificationException
    for (UI ui : listeners.keySet().toArray(new UI[0])) {
      BiConsumer<StoreInfo, ConnectionState> callback = listeners.get(ui);
      if (callback == null) {
        continue;
      }

      // Check if UI is still attached
      if (ui.isAttached()) {
        try {
          ui.access(() -> {
            logger().debug("UI.access() executing callback for UI {}", ui.getUIId());
            callback.accept(info, state);
            // Explicit push in case automatic push doesn't work
            //ui.push();
          });
        } catch (Exception e) {
          logger().warn("Error during UI update, removing listener: {}", e.getMessage());
          listeners.remove(ui);
        }
      } else {
        // UI no longer attached, remove listener
        listeners.remove(ui);
        logger().debug("UI no longer attached, listener removed");
      }
    }
  }
}
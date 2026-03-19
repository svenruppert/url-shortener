package com.svenruppert.urlshortener.ui.vaadin.components;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.StoreInfoSSEClient.ConnectionState;
import com.svenruppert.urlshortener.core.StoreInfo;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreMode;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.StoreInfoService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.shared.Registration;

import java.util.Objects;

/**
 * Displays whether the application is currently using InMemory or EclipseStore.
 * Uses color coding (green = persistent, blue = volatile, red = error).
 *
 * <p>Receives updates via SSE through the StoreInfoService.
 */
@CssImport("./styles/store-indicator.css")
public class StoreIndicator
    extends HorizontalLayout
    implements HasLogger, I18nSupport {

  // CSS classes
  private static final String CLASS_ROOT = "store-indicator";
  private static final String CLASS_ICON = "store-indicator__icon";
  private static final String CLASS_BADGE = "store-indicator__badge";
  private static final String CLASS_DETAILS = "store-indicator__details";
  private static final String STATE_ECLIPSE = "store-indicator--eclipse";
  private static final String STATE_MEMORY = "store-indicator--memory";
  private static final String STATE_DOWN = "store-indicator--unavailable";
  private static final String STATE_CONNECTING = "store-indicator--connecting";

  // i18n keys
  private static final String NS = "storeIndicator";
  private static final String K_BADGE_ECLIPSE = NS + ".badge.eclipse";
  private static final String K_BADGE_MEMORY = NS + ".badge.memory";
  private static final String K_BADGE_UNAVAILABLE = NS + ".badge.unavailable";
  private static final String K_BADGE_CONNECTING = NS + ".badge.connecting";
  private static final String K_BADGE_RECONNECTING = NS + ".badge.reconnecting";
  private static final String K_DETAILS_ITEMS = NS + ".details.items";
  private static final String K_TITLE_ECLIPSE = NS + ".title.eclipse";
  private static final String K_TITLE_MEMORY = NS + ".title.memory";
  private static final String K_TITLE_UNAVAILABLE = NS + ".title.unavailable";
  private static final String K_TITLE_CONNECTING = NS + ".title.connecting";

  private final Icon dbIcon = VaadinIcon.DATABASE.create();
  private final Span badge = new Span();
  private final Span details = new Span();

  private Registration serviceRegistration;

  public StoreIndicator() {
    setAlignItems(FlexComponent.Alignment.CENTER);
    setSpacing(true);
    setPadding(false);

    addClassName(CLASS_ROOT);
    dbIcon.addClassName(CLASS_ICON);
    badge.addClassName(CLASS_BADGE);
    details.addClassName(CLASS_DETAILS);

    add(dbIcon, badge, details);

    // Initial state
    applyState(StoreMode.UNAVAILABLE);
    applyTexts(StoreMode.UNAVAILABLE, 0);
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);

    UI ui = attachEvent.getUI();
    StoreInfoService service = StoreInfoService.get();

    // Register with service for updates
    serviceRegistration = service.register(ui, this::updateDisplay);

    logger().debug("StoreIndicator attached, registered with the service");
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);

    if (serviceRegistration != null) {
      serviceRegistration.remove();
      serviceRegistration = null;
    }

    logger().debug("StoreIndicator detached");
  }

  /**
   * Updates the display based on StoreInfo and ConnectionState.
   * ConnectionState takes priority when not CONNECTED.
   */
  private void updateDisplay(StoreInfo info, ConnectionState state) {
    if (state == null) {
      state = ConnectionState.DISCONNECTED;
    }

    switch (state) {
      case CONNECTING -> {
        logger().info("updateDisplay - CONNECTING");
        badge.setText(tr(K_BADGE_CONNECTING, "Connecting..."));
        details.setText("");
        getElement().setAttribute("title", tr(K_TITLE_CONNECTING, "Connection is being established"));
        applyConnectingState();
      }
      case RECONNECTING -> {
        logger().info("updateDisplay - RECONNECTING");
        badge.setText(tr(K_BADGE_RECONNECTING, "Reconnecting..."));
        details.setText("");
        getElement().setAttribute("title", tr(K_TITLE_CONNECTING, "Connection is being restored"));
        applyConnectingState();
      }
      case DISCONNECTED -> {
        logger().info("updateDisplay - DISCONNECTED");
        applyTexts(StoreMode.UNAVAILABLE, 0);
        applyState(StoreMode.UNAVAILABLE);
      }
      case CONNECTED -> {
        logger().info("updateDisplay - CONNECTED info {}", info);
        // Only show StoreInfo when CONNECTED
        if (info != null) {
          StoreMode mode = parseMode(info.mode());
          applyTexts(mode, info.mappings());
          applyState(mode);
        }
      }
      default -> {
        logger().info("updateDisplay - DEFAULT");
      }
    }
  }

  private StoreMode parseMode(String modeString) {
    if (modeString == null) {
      return StoreMode.UNAVAILABLE;
    }
    return switch (modeString.toLowerCase()) {
      case "eclipsestore" -> StoreMode.ECLIPSE_STORE;
      case "inmemory" -> StoreMode.IN_MEMORY;
      default -> StoreMode.UNAVAILABLE;
    };
  }

  private void applyTexts(StoreMode mode, long mappings) {
    Objects.requireNonNull(mode, "mode");

    switch (mode) {
      case ECLIPSE_STORE -> {
        badge.setText(tr(K_BADGE_ECLIPSE, "EclipseStore"));
        details.setText(tr(K_DETAILS_ITEMS, "· {0} items", mappings));
        getElement().setAttribute("title", tr(K_TITLE_ECLIPSE, "Persistent via EclipseStore"));
      }
      case IN_MEMORY -> {
        badge.setText(tr(K_BADGE_MEMORY, "InMemory"));
        details.setText(tr(K_DETAILS_ITEMS, "· {0} items", mappings));
        getElement().setAttribute("title", tr(K_TITLE_MEMORY, "Volatile (InMemory)"));
      }
      default -> {
        badge.setText(tr(K_BADGE_UNAVAILABLE, "Unavailable"));
        details.setText("");
        getElement().setAttribute("title", tr(K_TITLE_UNAVAILABLE, "StoreInfo endpoint unavailable"));
      }
    }
  }

  private void applyState(StoreMode mode) {
    removeClassNames(STATE_ECLIPSE, STATE_MEMORY, STATE_DOWN, STATE_CONNECTING);

    if (Objects.requireNonNull(mode) == StoreMode.ECLIPSE_STORE) {
      addClassName(STATE_ECLIPSE);
    } else if (mode == StoreMode.IN_MEMORY) {
      addClassName(STATE_MEMORY);
    } else {
      addClassName(STATE_DOWN);
    }
  }

  private void applyConnectingState() {
    removeClassNames(STATE_ECLIPSE, STATE_MEMORY, STATE_DOWN, STATE_CONNECTING);
    addClassName(STATE_CONNECTING);
  }
}
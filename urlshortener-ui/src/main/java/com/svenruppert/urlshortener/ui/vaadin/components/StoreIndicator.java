package com.svenruppert.urlshortener.ui.vaadin.components;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.AdminClient;
import com.svenruppert.urlshortener.core.StoreInfo;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreConnectionChanged;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreEvents;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreMode;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Objects;

/**
 * Zeigt an, ob die Anwendung aktuell InMemory oder EclipseStore verwendet.
 * Nutzt Farbcodierung (grün = persistent, blau = volatil, rot = Fehler).
 */
@CssImport("./styles/store-indicator.css")
public class StoreIndicator
    extends HorizontalLayout
    implements HasLogger {

  private final AdminClient adminClient;
  private final Icon dbIcon = VaadinIcon.DATABASE.create();
  private final Span badge = new Span();
  private final Span details = new Span();

  private StoreMode lastMode = StoreMode.UNAVAILABLE;

  // CSS classes
  private static final String CLASS_ROOT = "store-indicator";
  private static final String CLASS_ICON = "store-indicator__icon";
  private static final String CLASS_BADGE = "store-indicator__badge";
  private static final String CLASS_DETAILS = "store-indicator__details";

  private static final String STATE_ECLIPSE = "store-indicator--eclipse";
  private static final String STATE_MEMORY  = "store-indicator--memory";
  private static final String STATE_DOWN    = "store-indicator--unavailable";

  public StoreIndicator(AdminClient adminClient) {
    this.adminClient = adminClient;

    // --- Layoutgrundlage ---
    setAlignItems(FlexComponent.Alignment.CENTER);
    setSpacing(true);
    setPadding(false);

    addClassName(CLASS_ROOT);
    dbIcon.addClassName(CLASS_ICON);
    badge.addClassName(CLASS_BADGE);
    details.addClassName(CLASS_DETAILS);

    add(dbIcon, badge, details);
    applyState(StoreMode.UNAVAILABLE);
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    refreshOnce();
    UI ui = attachEvent.getUI();
    ui.setPollInterval(10000); // alle 10 Sekunden aktualisieren
    ui.addPollListener(_ -> refreshOnce());
  }

  /**
   * Holt die StoreInfo und aktualisiert das UI.
   */
  public void refreshOnce() {
    getUI().ifPresent(ui -> ui.access(() -> {
      try {
        StoreInfo info = adminClient.getStoreInfo();
        boolean persistent = "EclipseStore".equalsIgnoreCase(info.mode());

        badge.setText(persistent ? "EclipseStore" : "InMemory");
        details.setText("· " + info.mappings() + " items");
        getElement().setAttribute("title",
                                  persistent ? "Persistent via EclipseStore" : "Volatile (InMemory)");
        var newMode = persistent ? StoreMode.ECLIPSE_STORE : StoreMode.IN_MEMORY;
        applyState(newMode);
        if (newMode != lastMode) {
          lastMode = newMode;
          StoreEvents.publish(new StoreConnectionChanged(newMode, info.mappings()));
        }
//        if (persistent) {
//          badge.getStyle()
//              .set("background-color", "var(--lumo-success-color-10pct)")
//              .set("color", "var(--lumo-success-text-color)");
//          dbIcon.getStyle().set("color", "var(--lumo-success-color)");
//        } else {
//          badge.getStyle()
//              .set("background-color", "var(--lumo-primary-color-10pct)")
//              .set("color", "var(--lumo-primary-text-color)");
//          dbIcon.getStyle().set("color", "var(--lumo-primary-color)");
//        }


      } catch (Exception e) {
        badge.setText("Unavailable");
//        badge.getStyle()
//            .set("background-color", "var(--lumo-error-color-10pct)")
//            .set("color", "var(--lumo-error-text-color)");
//        dbIcon.getStyle().set("color", "var(--lumo-error-color)");
        details.setText("");
        getElement().setAttribute("title", "StoreInfo endpoint unavailable");

        applyState(StoreMode.UNAVAILABLE);

        if (lastMode != StoreMode.UNAVAILABLE) {
          lastMode = StoreMode.UNAVAILABLE;
          StoreEvents.publish(new StoreConnectionChanged(StoreMode.UNAVAILABLE, 0));
        }
      }
    }));
  }
  private void applyState(StoreMode mode) {
    // nur die drei Zustandsklassen managen
    removeClassNames(STATE_ECLIPSE, STATE_MEMORY, STATE_DOWN);

    if (Objects.requireNonNull(mode) == StoreMode.ECLIPSE_STORE) {
      addClassName(STATE_ECLIPSE);
    } else if (mode == StoreMode.IN_MEMORY) {
      addClassName(STATE_MEMORY);
    } else if (mode == StoreMode.UNAVAILABLE) {
      addClassName(STATE_DOWN);
    }
  }
}

package com.svenruppert.urlshortener.ui.vaadin.components;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.AdminClient;
import com.svenruppert.urlshortener.core.StoreInfo;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreConnectionChanged;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreEvents;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreMode;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Zeigt an, ob die Anwendung aktuell InMemory oder EclipseStore verwendet.
 * Nutzt Farbcodierung (grün = persistent, blau = volatil, rot = Fehler).
 */
public class StoreIndicator
    extends HorizontalLayout
    implements HasLogger {

  private final AdminClient adminClient;
  private final Icon dbIcon = VaadinIcon.DATABASE.create();
  private final Span badge = new Span();
  private final Span details = new Span();

  private StoreMode lastMode = StoreMode.UNAVAILABLE;

  public StoreIndicator(AdminClient adminClient) {
    this.adminClient = adminClient;

    // --- Layoutgrundlage ---
    setAlignItems(FlexComponent.Alignment.CENTER);
    setSpacing(true);
    setPadding(false);

    dbIcon.setSize("16px");
    dbIcon.getStyle().set("color", "var(--lumo-secondary-text-color)");

    badge.getStyle()
        .set("font-size", "12px")
        .set("font-weight", "600")
        .set("padding", "0.2rem 0.5rem")
        .set("border-radius", "0.4rem")
        .set("background-color", "var(--lumo-contrast-10pct)")
        .set("color", "var(--lumo-body-text-color)");

    details.getStyle()
        .set("font-size", "12px")
        .set("opacity", "0.8");

    add(dbIcon, badge, details);
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    refreshOnce();
    UI ui = attachEvent.getUI();
    ui.setPollInterval(10000); // alle 10 Sekunden aktualisieren
    ui.addPollListener(e -> refreshOnce());
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

        if (persistent) {
          badge.getStyle()
              .set("background-color", "var(--lumo-success-color-10pct)")
              .set("color", "var(--lumo-success-text-color)");
          dbIcon.getStyle().set("color", "var(--lumo-success-color)");
        } else {
          badge.getStyle()
              .set("background-color", "var(--lumo-primary-color-10pct)")
              .set("color", "var(--lumo-primary-text-color)");
          dbIcon.getStyle().set("color", "var(--lumo-primary-color)");
        }

        details.setText("· " + info.mappings() + " items");
        getElement().setAttribute("title",
                                  persistent ? "Persistent via EclipseStore" : "Volatile (InMemory)");

        var newMode = persistent ? StoreMode.ECLIPSE_STORE : StoreMode.IN_MEMORY;
        if (newMode != lastMode) {
          lastMode = newMode;
          StoreEvents.publish(new StoreConnectionChanged(newMode, info.mappings()));
        }

      } catch (Exception e) {
        badge.setText("Unavailable");
        badge.getStyle()
            .set("background-color", "var(--lumo-error-color-10pct)")
            .set("color", "var(--lumo-error-text-color)");
        dbIcon.getStyle().set("color", "var(--lumo-error-color)");
        details.setText("");
        getElement().setAttribute("title", "StoreInfo endpoint unavailable");

        if (lastMode != StoreMode.UNAVAILABLE) {
          lastMode = StoreMode.UNAVAILABLE;
          StoreEvents.publish(new StoreConnectionChanged(StoreMode.UNAVAILABLE, 0));
        }
      }
    }));
  }
}

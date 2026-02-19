package com.svenruppert.urlshortener.ui.vaadin;

import com.svenruppert.urlshortener.ui.vaadin.tools.LocaleSelection;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.*;

import java.util.Locale;

/**
 * Vaadin 25 ServiceInitListener
 * - Browser-Locale als Default
 * - Beschränkt auf DE / EN / FI
 * - Session-basierte Sprachwahl
 */
public class AppServiceInitListener implements VaadinServiceInitListener {

  @Override
  public void serviceInit(ServiceInitEvent event) {

    event.getSource().addUIInitListener(uiEvent -> {

      UI ui = uiEvent.getUI();
      VaadinSession session = ui.getSession();

      // 1) Falls Benutzer in Session bereits Sprache gewählt hat
      Locale sessionLocale = LocaleSelection.getFromSession(session);
      if (sessionLocale != null) {
        ui.setLocale(sessionLocale);
        session.setLocale(sessionLocale);
        return;
      }

      // 2) Sonst Browser-Locale verwenden (Accept-Language)
      Locale browserLocale = ui.getLocale();
      Locale effective = LocaleSelection.match(browserLocale);

      ui.setLocale(effective);
      session.setLocale(effective);
      LocaleSelection.setToSession(session, effective);
    });
  }
}

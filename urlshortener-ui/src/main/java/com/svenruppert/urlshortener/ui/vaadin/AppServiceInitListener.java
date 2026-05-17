package com.svenruppert.urlshortener.ui.vaadin;



import com.svenruppert.urlshortener.client.AuthFailureRegistry;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.tools.LocaleSelection;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.*;

import java.util.Locale;

/**
 * Vaadin 25 ServiceInitListener
 * - Browser-Locale als Default
 * - Beschränkt auf DE / EN / FI
 * - Session-basierte Sprachwahl
 * - Registriert den globalen 401-Handler: klare lokale Session, navigiere zur Login-View
 */
public class AppServiceInitListener implements VaadinServiceInitListener {

  @Override
  public void serviceInit(ServiceInitEvent event) {

    AuthFailureRegistry.set(AppServiceInitListener::onAuthFailure);

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

  private static void onAuthFailure() {
    try {
      SubjectStores.subjectStore().deleteCurrentSubject(AppUser.class);
    } catch (RuntimeException ignored) {
      // store may already be detached in non-UI thread contexts
    }
    UI ui = UI.getCurrent();
    if (ui != null) {
      ui.access(() -> ui.navigate(LoginView.class));
    }
  }
}
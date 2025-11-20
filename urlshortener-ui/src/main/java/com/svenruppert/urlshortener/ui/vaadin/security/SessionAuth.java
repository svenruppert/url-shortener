package com.svenruppert.urlshortener.ui.vaadin.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.VaadinSession;

import static com.svenruppert.dependencies.core.logger.HasLogger.staticLogger;
import static java.lang.Boolean.TRUE;

public final class SessionAuth
    implements HasLogger {

  private static final String ATTR_AUTH = "authenticated";

  private SessionAuth() {
  }

  public static boolean isAuthenticated() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return false;
    var attribute = session.getAttribute(ATTR_AUTH);
    staticLogger().info("isAuthenticated.. {}", attribute);
    return TRUE.equals(attribute);
  }

  public static void markAuthenticated() {
    staticLogger().info("markAuthenticated.. ");
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      session.setAttribute(ATTR_AUTH, TRUE);
    }
  }

  public static void clearAuthentication() {
    staticLogger().info("clearAuthentication.. ");
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      session.setAttribute(ATTR_AUTH, null);
      session.close();
    }
  }
}

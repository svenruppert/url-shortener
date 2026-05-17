package com.svenruppert.urlshortener.client;

/**
 * Process-wide hook fired by the REST clients whenever the server responds
 * with HTTP 401. Hosts (typically the Vaadin UI) register a listener to
 * react — e.g. clear the local session and redirect to the login view.
 * <p>
 * The listener is invoked from the calling thread, before the IO method
 * decides how to map the failure (exception vs. boolean false). Listeners
 * must be cheap and must not throw.
 */
public final class AuthFailureRegistry {

  private static volatile Runnable listener;

  private AuthFailureRegistry() {
  }

  /** Sets (or replaces) the global listener. Pass {@code null} to clear. */
  public static void set(Runnable handler) {
    listener = handler;
  }

  /** Removes the currently registered listener, if any. */
  public static void clear() {
    listener = null;
  }

  /**
   * Invokes the listener, if any. Swallows exceptions so a faulty listener
   * never breaks the original IO path.
   */
  public static void fire() {
    Runnable current = listener;
    if (current == null) return;
    try {
      current.run();
    } catch (RuntimeException ignored) {
      // never propagate listener failures into IO callers
    }
  }

  /** Triggers the listener iff {@code code == 401}. Convenience for client wiring. */
  public static void notifyIfUnauthorized(int code) {
    if (code == 401) {
      fire();
    }
  }
}

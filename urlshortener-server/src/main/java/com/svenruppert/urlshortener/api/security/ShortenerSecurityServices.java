package com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.security.auth.ShortenerAuthenticationService;
import com.svenruppert.urlshortener.api.security.auth.ShortenerAuthorizationService;

/**
 * Holder for the active in-process authentication and authorization service
 * instances. The Vaadin UI consumes these in the same JVM during integration
 * tests; production deployments are expected to rely on SPI discovery
 * instead.
 */
public final class ShortenerSecurityServices {

  private static volatile ShortenerAuthenticationService authentication;
  private static volatile ShortenerAuthorizationService authorization;

  private ShortenerSecurityServices() {
  }

  public static void set(ShortenerAuthenticationService auth, ShortenerAuthorizationService authz) {
    authentication = auth;
    authorization = authz;
  }

  public static ShortenerAuthenticationService authentication() {
    return authentication;
  }

  public static ShortenerAuthorizationService authorization() {
    return authorization;
  }
}

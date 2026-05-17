package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;

import java.util.Optional;

/**
 * Reads the bearer token from the current Vaadin session's {@link AppUser}.
 * Used by the client factories to inject the auth token into every REST
 * client instance they hand out.
 */
public final class AuthTokenAccessor {

  private AuthTokenAccessor() {
  }

  public static Optional<String> currentToken() {
    return SubjectStores.subjectStore().currentSubject(AppUser.class)
        .map(AppUser::accessToken);
  }
}

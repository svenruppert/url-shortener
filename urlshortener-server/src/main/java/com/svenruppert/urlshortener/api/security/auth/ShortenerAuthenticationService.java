package com.svenruppert.urlshortener.api.security.auth;

import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.vaadin.security.authentication.AuthenticationService;

import java.util.Objects;

public final class ShortenerAuthenticationService
    implements AuthenticationService<Credentials, ShortenerUser> {

  private final UserStore userStore;

  public ShortenerAuthenticationService(UserStore userStore) {
    this.userStore = Objects.requireNonNull(userStore, "userStore");
  }

  @Override
  public boolean checkCredentials(Credentials credentials) {
    if (credentials == null
        || credentials.username() == null
        || credentials.password() == null) {
      return false;
    }
    return userStore.authenticate(credentials.username(), credentials.password()).isPresent();
  }

  @Override
  public ShortenerUser loadSubject(Credentials credentials) {
    return userStore.authenticate(credentials.username(), credentials.password())
        .orElseThrow(() -> new IllegalStateException("authenticate must succeed before loadSubject"));
  }

  @Override
  public Class<ShortenerUser> subjectType() {
    return ShortenerUser.class;
  }
}

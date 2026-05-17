package com.svenruppert.urlshortener.api.security.bootstrap;

import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.vaadin.security.bootstrap.AdministratorAccountStore;
import com.svenruppert.vaadin.security.bootstrap.NewAdministrator;

public final class ShortenerAdministratorAccountStore implements AdministratorAccountStore {

  private final UserStore userStore;

  public ShortenerAdministratorAccountStore(UserStore userStore) {
    this.userStore = userStore;
  }

  @Override
  public boolean hasAnyAdministrator() {
    return userStore.hasAnyAdministrator();
  }

  @Override
  public void createAdministrator(NewAdministrator newAdministrator) {
    String displayName = newAdministrator.displayName() == null
        || newAdministrator.displayName().isBlank()
        ? newAdministrator.username()
        : newAdministrator.displayName();
    userStore.register(new ShortenerUser(
        newAdministrator.username(),
        displayName,
        newAdministrator.passwordHash(),
        ShortenerRole.ROLE_ADMIN,
        true));
  }
}

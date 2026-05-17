package com.svenruppert.urlshortener.api.security.permissions;

import com.svenruppert.vaadin.security.authorization.api.roles.RoleName;

public enum ShortenerRole {
  ROLE_USER,
  ROLE_ADMIN;

  public RoleName roleName() {
    return new RoleName(name());
  }
}

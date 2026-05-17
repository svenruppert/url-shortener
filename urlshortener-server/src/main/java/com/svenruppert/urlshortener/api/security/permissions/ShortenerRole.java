package com.svenruppert.urlshortener.api.security.permissions;

import com.svenruppert.vaadin.security.authorization.api.roles.RoleName;

public enum ShortenerRole {
  /** Read-only role: own links + own stats. No mutating operations. */
  ROLE_VIEWER,
  ROLE_USER,
  ROLE_ADMIN;

  public RoleName roleName() {
    return new RoleName(name());
  }
}

package com.svenruppert.urlshortener.api.security.permissions;

import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;

public enum ShortenerPermission {
  LINK_READ_OWN("link:read:own"),
  LINK_READ_ALL("link:read:all"),
  LINK_CREATE("link:create"),
  LINK_UPDATE_OWN("link:update:own"),
  LINK_UPDATE_ALL("link:update:all"),
  LINK_DELETE_OWN("link:delete:own"),
  LINK_DELETE_ALL("link:delete:all"),
  LINK_STATS_OWN("link:stats:own"),
  LINK_STATS_ALL("link:stats:all"),
  USER_READ("user:read"),
  USER_CREATE("user:create"),
  USER_UPDATE("user:update"),
  USER_DELETE("user:delete"),
  USER_ROLE_ASSIGN("user:role:assign"),
  ADMIN_ACCESS("admin:access");

  private final PermissionName permissionName;

  ShortenerPermission(String value) {
    this.permissionName = new PermissionName(value);
  }

  public PermissionName permissionName() {
    return permissionName;
  }
}

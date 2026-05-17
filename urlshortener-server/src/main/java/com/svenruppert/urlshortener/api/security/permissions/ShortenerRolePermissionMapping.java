package com.svenruppert.urlshortener.api.security.permissions;

import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;
import com.svenruppert.vaadin.security.authorization.api.permissions.RolePermissionMapping;
import com.svenruppert.vaadin.security.authorization.api.permissions.StaticRolePermissionMapping;
import com.svenruppert.vaadin.security.authorization.api.roles.RoleName;

import java.util.Set;

import static com.svenruppert.urlshortener.api.security.permissions.ShortenerPermission.*;

public final class ShortenerRolePermissionMapping implements RolePermissionMapping {

  private final StaticRolePermissionMapping delegate = StaticRolePermissionMapping.builder()
      .put(ShortenerRole.ROLE_USER.roleName(), Set.of(
          LINK_READ_OWN.permissionName(),
          LINK_CREATE.permissionName(),
          LINK_UPDATE_OWN.permissionName(),
          LINK_DELETE_OWN.permissionName(),
          LINK_STATS_OWN.permissionName()))
      .put(ShortenerRole.ROLE_ADMIN.roleName(), Set.of(
          LINK_READ_OWN.permissionName(),
          LINK_READ_ALL.permissionName(),
          LINK_CREATE.permissionName(),
          LINK_UPDATE_OWN.permissionName(),
          LINK_UPDATE_ALL.permissionName(),
          LINK_DELETE_OWN.permissionName(),
          LINK_DELETE_ALL.permissionName(),
          LINK_STATS_OWN.permissionName(),
          LINK_STATS_ALL.permissionName(),
          USER_READ.permissionName(),
          USER_CREATE.permissionName(),
          USER_UPDATE.permissionName(),
          USER_DELETE.permissionName(),
          USER_ROLE_ASSIGN.permissionName(),
          ADMIN_ACCESS.permissionName()))
      .build();

  @Override
  public Set<PermissionName> permissionsFor(RoleName role) {
    return delegate.permissionsFor(role);
  }
}

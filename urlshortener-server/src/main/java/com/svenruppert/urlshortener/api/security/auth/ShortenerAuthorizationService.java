package com.svenruppert.urlshortener.api.security.auth;

import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.vaadin.security.authorization.api.AuthorizationService;
import com.svenruppert.vaadin.security.authorization.api.permissions.HasPermissions;
import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;
import com.svenruppert.vaadin.security.authorization.api.permissions.RolePermissionMapping;
import com.svenruppert.vaadin.security.authorization.api.roles.HasRoles;
import com.svenruppert.vaadin.security.authorization.api.roles.RoleName;

import java.util.Objects;
import java.util.Set;

public final class ShortenerAuthorizationService implements AuthorizationService<ShortenerUser> {

  private final RolePermissionMapping mapping;

  public ShortenerAuthorizationService(RolePermissionMapping mapping) {
    this.mapping = Objects.requireNonNull(mapping, "mapping");
  }

  @Override
  public HasRoles rolesFor(ShortenerUser subject) {
    Objects.requireNonNull(subject, "subject");
    RoleName roleName = subject.role().roleName();
    return () -> Set.of(roleName);
  }

  @Override
  public HasPermissions permissionsFor(ShortenerUser subject) {
    Objects.requireNonNull(subject, "subject");
    Set<PermissionName> permissions = mapping.permissionsFor(subject.role().roleName());
    return () -> permissions;
  }
}

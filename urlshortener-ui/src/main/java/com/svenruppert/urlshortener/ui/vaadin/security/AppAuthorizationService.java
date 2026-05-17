package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.vaadin.security.authorization.api.AuthorizationService;
import com.svenruppert.vaadin.security.authorization.api.permissions.HasPermissions;
import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;
import com.svenruppert.vaadin.security.authorization.api.roles.HasRoles;
import com.svenruppert.vaadin.security.authorization.api.roles.RoleName;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AppAuthorizationService
    implements AuthorizationService<AppUser> {

  @Override
  public HasRoles rolesFor(AppUser subject) {
    Objects.requireNonNull(subject);
    return () -> subject.roles()
        .stream()
        .map(role -> new RoleName(role.name()))
        .toList();
  }

  @Override
  public HasPermissions permissionsFor(AppUser subject) {
    Objects.requireNonNull(subject);
    Set<PermissionName> names = subject.permissions().stream()
        .map(PermissionName::new)
        .collect(Collectors.toSet());
    return () -> names;
  }
}

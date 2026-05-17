package com.svenruppert.urlshortener.api.security.auth;

import com.svenruppert.urlshortener.api.security.token.InMemoryTokenStore;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.vaadin.security.authorization.api.SecuritySubject;
import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;
import com.svenruppert.vaadin.security.authorization.api.permissions.RolePermissionMapping;
import com.svenruppert.vaadin.security.authorization.api.roles.RoleName;
import com.svenruppert.vaadin.security.rest.BearerTokenExtractor;
import com.svenruppert.vaadin.security.rest.RestRequest;
import com.svenruppert.vaadin.security.rest.RestSubjectResolver;
import com.svenruppert.vaadin.security.session.SessionMetadata;

import java.util.Optional;
import java.util.Set;

public final class ShortenerRestSubjectResolver implements RestSubjectResolver {

  private static final BearerTokenExtractor BEARER = new BearerTokenExtractor();

  private final InMemoryTokenStore tokens;
  private final RolePermissionMapping mapping;

  public ShortenerRestSubjectResolver(InMemoryTokenStore tokens, RolePermissionMapping mapping) {
    this.tokens = tokens;
    this.mapping = mapping;
  }

  @Override
  public Optional<SecuritySubject> resolveSubject(RestRequest request) {
    return BEARER.extract(request).flatMap(tokens::resolve).map(this::toSubject);
  }

  @Override
  public Optional<SessionMetadata> resolveSessionMetadata(RestRequest request) {
    Optional<String> token = BEARER.extract(request);
    if (token.isEmpty()) {
      return Optional.empty();
    }
    Optional<InMemoryTokenStore.Metadata> metadata = tokens.resolveMetadata(token.get());
    if (metadata.isEmpty()) {
      return Optional.empty();
    }
    InMemoryTokenStore.Metadata m = metadata.get();
    SessionMetadata snapshot = new SessionMetadata(
        m.user().username(), m.createdAt(), m.lastActivityAt());
    tokens.markActivity(token.get());
    return Optional.of(snapshot);
  }

  private SecuritySubject toSubject(ShortenerUser user) {
    RoleName roleName = user.role().roleName();
    Set<PermissionName> permissions = mapping.permissionsFor(roleName);
    return new SecuritySubject(
        user.username(),
        user.displayName(),
        Set.of(roleName),
        permissions);
  }
}

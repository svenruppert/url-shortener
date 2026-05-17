package com.svenruppert.urlshortener.api.security;

import com.svenruppert.vaadin.security.authorization.api.SecuritySubject;
import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;

import java.util.Optional;
import java.util.Set;

/**
 * Thread-bound holder for the {@link SecuritySubject} of the request currently
 * being handled. Set by {@code SecurityHttpHandler} just before delegating to
 * the underlying handler and cleared right after the handler returns.
 * <p>
 * Handlers can read {@link #username()}, {@link #permissions()} etc. to
 * perform owner checks or fine-grained per-resource decisions that go beyond
 * the coarse permission-level filter.
 */
public final class CurrentSubject {

  private static final ThreadLocal<SecuritySubject> CURRENT = new ThreadLocal<>();

  private CurrentSubject() {
  }

  public static void set(SecuritySubject subject) {
    if (subject == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(subject);
    }
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static Optional<SecuritySubject> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static Optional<String> username() {
    return current().map(SecuritySubject::subjectId);
  }

  public static Set<PermissionName> permissions() {
    SecuritySubject subject = CURRENT.get();
    return subject == null ? Set.of() : subject.permissions();
  }

  public static boolean hasPermission(String permissionName) {
    return permissions().stream()
        .anyMatch(p -> p.permissionName().equals(permissionName));
  }
}

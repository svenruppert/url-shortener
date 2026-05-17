package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import com.svenruppert.vaadin.security.audit.AuditEvent;
import com.svenruppert.vaadin.security.audit.RoleAssigned;
import com.svenruppert.vaadin.security.audit.RoleRevoked;
import com.svenruppert.vaadin.security.audit.SecurityAuditService;
import com.svenruppert.vaadin.security.audit.UserCreated;
import com.svenruppert.vaadin.security.audit.UserDeleted;
import com.svenruppert.vaadin.security.authentication.PasswordHasher;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;
import org.eclipse.store.storage.types.StorageManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link UserStore} backed by the EclipseStore {@link DataRoot}. Every mutation
 * is persisted via {@code storage.store(users)} so accounts survive restarts.
 * Authenticate/rehash semantics mirror the in-memory variant.
 * <p>
 * Bearer tokens are intentionally <strong>not</strong> persisted; restart
 * invalidates all sessions and forces re-login.
 */
public final class EclipseUserStore implements UserStore, HasLogger {

  private final StorageManager storage;
  private final PasswordHasher hasher;
  private final Clock clock;

  public EclipseUserStore(StorageManager storage, PasswordHasher hasher) {
    this(storage, hasher, Clock.systemUTC());
  }

  public EclipseUserStore(StorageManager storage, PasswordHasher hasher, Clock clock) {
    this.storage = Objects.requireNonNull(storage, "storage");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }

  private Map<String, ShortenerUser> users() {
    return dataRoot().users();
  }

  @Override
  public synchronized Optional<ShortenerUser> authenticate(String username, String password) {
    ShortenerUser user = users().get(username);
    if (user == null || !user.enabled()) {
      return Optional.empty();
    }
    char[] raw = password.toCharArray();
    if (!hasher.verify(raw, user.passwordHash())) {
      return Optional.empty();
    }
    ShortenerUser current = user;
    if (hasher.needsRehash(current.passwordHash())) {
      try {
        String freshHash = hasher.hash(raw);
        ShortenerUser upgraded = new ShortenerUser(
            current.username(), current.displayName(), freshHash, current.role(), current.enabled());
        users().put(upgraded.username(), upgraded);
        storage.store(users());
        current = upgraded;
      } catch (RuntimeException ignored) {
        // login succeeded against the existing hash; rehash failure is not fatal
      }
    }
    return Optional.of(current);
  }

  @Override
  public synchronized Optional<ShortenerUser> findByUsername(String username) {
    return Optional.ofNullable(users().get(username));
  }

  @Override
  public synchronized ShortenerUser create(
      String username, String plaintextPassword, String displayName, ShortenerRole role) {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(plaintextPassword, "password");
    Objects.requireNonNull(role, "role");
    if (users().containsKey(username)) {
      throw new IllegalStateException("user already exists: " + username);
    }
    String hash = hasher.hash(plaintextPassword.toCharArray());
    ShortenerUser user = new ShortenerUser(
        username, displayName == null ? username : displayName, hash, role, true);
    users().put(username, user);
    storage.store(users());
    audit(new UserCreated(Instant.now(clock), username, role.name(), null));
    return user;
  }

  @Override
  public synchronized void register(ShortenerUser user) {
    if (users().containsKey(user.username())) {
      throw new IllegalStateException("user already exists: " + user.username());
    }
    users().put(user.username(), user);
    storage.store(users());
    audit(new UserCreated(Instant.now(clock), user.username(), user.role().name(), null));
  }

  @Override
  public synchronized boolean deleteUser(String username) {
    Objects.requireNonNull(username, "username");
    if (users().remove(username) == null) {
      return false;
    }
    storage.store(users());
    audit(new UserDeleted(Instant.now(clock), username, null));
    return true;
  }

  @Override
  public synchronized boolean setRole(String username, ShortenerRole newRole) {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(newRole, "newRole");
    ShortenerUser current = users().get(username);
    if (current == null || current.role() == newRole) {
      return false;
    }
    ShortenerUser updated = new ShortenerUser(
        current.username(), current.displayName(), current.passwordHash(), newRole, current.enabled());
    users().put(username, updated);
    storage.store(users());
    audit(new RoleRevoked(Instant.now(clock), username, current.role().name(), null));
    audit(new RoleAssigned(Instant.now(clock), username, newRole.name(), null));
    return true;
  }

  @Override
  public synchronized boolean setEnabled(String username, boolean enabled) {
    ShortenerUser current = users().get(username);
    if (current == null || current.enabled() == enabled) {
      return false;
    }
    users().put(username, new ShortenerUser(
        current.username(), current.displayName(), current.passwordHash(), current.role(), enabled));
    storage.store(users());
    return true;
  }

  @Override
  public synchronized boolean setDisplayName(String username, String displayName) {
    Objects.requireNonNull(username, "username");
    String effective = (displayName == null || displayName.isBlank()) ? username : displayName;
    ShortenerUser current = users().get(username);
    if (current == null || Objects.equals(current.displayName(), effective)) {
      return false;
    }
    users().put(username, new ShortenerUser(
        current.username(), effective, current.passwordHash(), current.role(), current.enabled()));
    storage.store(users());
    return true;
  }

  @Override
  public synchronized boolean changePassword(String username, String oldPlainPassword, String newPlainPassword) {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(oldPlainPassword, "oldPlainPassword");
    Objects.requireNonNull(newPlainPassword, "newPlainPassword");
    ShortenerUser current = users().get(username);
    if (current == null || !current.enabled()) {
      return false;
    }
    if (!hasher.verify(oldPlainPassword.toCharArray(), current.passwordHash())) {
      return false;
    }
    String newHash = hasher.hash(newPlainPassword.toCharArray());
    users().put(username, new ShortenerUser(
        current.username(), current.displayName(), newHash, current.role(), current.enabled()));
    storage.store(users());
    return true;
  }

  @Override
  public synchronized boolean resetPassword(String username, String newPlainPassword) {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(newPlainPassword, "newPlainPassword");
    ShortenerUser current = users().get(username);
    if (current == null) {
      return false;
    }
    String newHash = hasher.hash(newPlainPassword.toCharArray());
    users().put(username, new ShortenerUser(
        current.username(), current.displayName(), newHash, current.role(), current.enabled()));
    storage.store(users());
    return true;
  }

  @Override
  public synchronized boolean hasAnyAdministrator() {
    return users().values().stream().anyMatch(u -> u.role() == ShortenerRole.ROLE_ADMIN);
  }

  @Override
  public synchronized List<ShortenerUser> listAll() {
    return new ArrayList<>(users().values());
  }

  private static void audit(AuditEvent event) {
    try {
      SecurityAuditService sink = SecurityServiceResolver.securityAuditService();
      sink.publish(event);
    } catch (RuntimeException ignored) {
      // never block user-store mutation because the audit sink failed
    }
  }
}

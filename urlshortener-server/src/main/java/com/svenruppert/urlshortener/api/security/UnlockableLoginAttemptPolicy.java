package com.svenruppert.urlshortener.api.security;

import com.svenruppert.vaadin.security.bruteforce.LoginAttemptContext;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptDecision;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptPolicy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Decorator over a {@link LoginAttemptPolicy} that supports admin-initiated
 * unlock of a specific user. The underlying in-memory policy tracks two keys
 * per attempt: combined ({@code username + clientAddress}) and username-only.
 * Calling {@link LoginAttemptPolicy#recordSuccess(LoginAttemptContext)} with
 * a {@code null} client address only clears the username-only key — the
 * combined key for whatever address triggered the lockout stays.
 * <p>
 * This wrapper remembers every {@code clientAddress} the user has been seen
 * with on a failed attempt, so {@link #unlock(String)} can issue a
 * {@code recordSuccess} per address and wipe the lockout completely.
 */
public final class UnlockableLoginAttemptPolicy implements LoginAttemptPolicy {

  private final LoginAttemptPolicy delegate;
  private final ConcurrentMap<String, Set<String>> seenAddressesByUsername = new ConcurrentHashMap<>();

  public UnlockableLoginAttemptPolicy(LoginAttemptPolicy delegate) {
    this.delegate = delegate;
  }

  @Override
  public LoginAttemptDecision beforeAttempt(LoginAttemptContext context) {
    return delegate.beforeAttempt(context);
  }

  @Override
  public void recordSuccess(LoginAttemptContext context) {
    delegate.recordSuccess(context);
    if (context.username() != null) {
      seenAddressesByUsername.remove(context.username());
    }
  }

  @Override
  public void recordFailure(LoginAttemptContext context) {
    delegate.recordFailure(context);
    if (context.username() != null) {
      String address = context.clientAddress();
      seenAddressesByUsername
          .computeIfAbsent(context.username(), k -> ConcurrentHashMap.newKeySet())
          .add(address == null ? "" : address);
    }
  }

  /**
   * Clears every recorded lockout key for {@code username}: one
   * {@code recordSuccess} for each seen {@code (username, clientAddress)}
   * pair, plus one for the username-only key. Safe to call when there is
   * nothing to clear.
   */
  public void unlock(String username) {
    if (username == null) return;
    Set<String> addresses = seenAddressesByUsername.remove(username);
    if (addresses != null) {
      for (String addr : addresses) {
        delegate.recordSuccess(LoginAttemptContext.now(
            username, addr.isEmpty() ? null : addr, null));
      }
    }
    // Always issue a username-only success so the username key is gone too.
    delegate.recordSuccess(LoginAttemptContext.now(username, null, null));
  }
}

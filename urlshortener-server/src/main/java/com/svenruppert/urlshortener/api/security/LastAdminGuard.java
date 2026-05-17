package com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;

import java.util.Objects;

/**
 * Single-source guard for "the system must always have at least one enabled
 * administrator". Consulted before any mutation that could otherwise leave
 * the platform without a usable admin account: delete, role demotion, disable.
 */
public final class LastAdminGuard {

  private LastAdminGuard() {
  }

  /**
   * Returns {@code true} if {@code username} is currently the only enabled
   * administrator in the user store. Any operation that would remove this
   * user's admin status — delete, role demotion, or disable — must be
   * rejected when this returns {@code true}.
   */
  public static boolean isOnlyEnabledAdmin(UserStore users, String username) {
    Objects.requireNonNull(users, "users");
    Objects.requireNonNull(username, "username");
    int enabledAdmins = 0;
    boolean targetIsEnabledAdmin = false;
    for (ShortenerUser u : users.listAll()) {
      if (u.role() == ShortenerRole.ROLE_ADMIN && u.enabled()) {
        enabledAdmins++;
        if (u.username().equals(username)) {
          targetIsEnabledAdmin = true;
        }
      }
    }
    return targetIsEnabledAdmin && enabledAdmins == 1;
  }
}

package com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

/**
 * Owner-check helper for handlers that protect {@code :own}-style permissions.
 * <p>
 * The {@code RestAuthorizationFilter} only confirms that the current subject
 * has the coarse permission (e.g. {@code link:update:own}). This helper adds
 * the per-resource check: the requested mapping must belong to the current
 * subject — unless the subject also carries the corresponding {@code :all}
 * permission, in which case any owner is acceptable.
 */
public final class OwnerCheck {

  private OwnerCheck() {
  }

  /**
   * Returns {@code true} if the current subject may operate on the given mapping
   * based on owner-or-all semantics.
   *
   * @param mapping             the resource being touched (must not be null)
   * @param allPermissionName   the corresponding {@code :all} permission that
   *                            unconditionally allows operation, e.g.
   *                            {@code "link:update:all"}
   */
  public static boolean isOwnerOrHasAll(ShortUrlMapping mapping, String allPermissionName) {
    if (CurrentSubject.current().isEmpty()) {
      // No authentication context — either the test bypass is active or the
      // handler ran outside the security filter chain. We cannot enforce an
      // owner check here; the calling filter is responsible for the coarse
      // permission gate.
      return true;
    }
    if (CurrentSubject.hasPermission(allPermissionName)) {
      return true;
    }
    String owner = mapping.ownerUsername();
    if (owner == null) {
      // Legacy mappings without an owner are treated as admin-only.
      return false;
    }
    return CurrentSubject.username().map(owner::equals).orElse(false);
  }
}

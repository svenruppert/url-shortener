package com.svenruppert.urlshortener.api.security.user;

import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;

import java.util.List;
import java.util.Optional;

public interface UserStore {

  Optional<ShortenerUser> authenticate(String username, String password);

  Optional<ShortenerUser> findByUsername(String username);

  ShortenerUser create(String username, String plaintextPassword, String displayName, ShortenerRole role);

  void register(ShortenerUser user);

  boolean deleteUser(String username);

  boolean setRole(String username, ShortenerRole newRole);

  boolean setEnabled(String username, boolean enabled);

  /**
   * Updates the display name. Returns {@code false} if the user does not exist
   * or the display name is already equal.
   */
  boolean setDisplayName(String username, String displayName);

  /**
   * Verifies {@code oldPlainPassword} and replaces the stored hash with one
   * derived from {@code newPlainPassword}. Returns {@code false} when the user
   * does not exist, is disabled, or the old password does not match.
   */
  boolean changePassword(String username, String oldPlainPassword, String newPlainPassword);

  /**
   * Replaces the stored password hash without verifying the previous password.
   * Reserved for administrative reset flows. Returns {@code false} when the
   * user does not exist.
   */
  boolean resetPassword(String username, String newPlainPassword);

  boolean hasAnyAdministrator();

  List<ShortenerUser> listAll();
}

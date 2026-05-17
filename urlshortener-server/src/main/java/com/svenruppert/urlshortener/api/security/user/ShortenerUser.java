package com.svenruppert.urlshortener.api.security.user;

import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;

public record ShortenerUser(
    String username,
    String displayName,
    String passwordHash,
    ShortenerRole role,
    boolean enabled
) {
  public ShortenerUser {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("username must not be blank");
    }
    if (role == null) {
      throw new IllegalArgumentException("role must not be null");
    }
  }
}

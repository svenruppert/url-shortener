package com.svenruppert.urlshortener.core.users;

/**
 * Partial-update payload for {@code PUT /api/users/{username}}. Any field set
 * to {@code null} stays unchanged on the server side.
 */
public record UpdateUserRequest(
    String displayName,
    String role,
    Boolean enabled
) {
}

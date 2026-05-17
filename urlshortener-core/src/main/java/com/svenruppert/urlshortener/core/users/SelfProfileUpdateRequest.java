package com.svenruppert.urlshortener.core.users;

/**
 * Self-service profile update payload for {@code PUT /api/me/profile}. Only
 * fields the user is allowed to change themselves; security-sensitive
 * properties (role, enabled, password) are managed elsewhere.
 */
public record SelfProfileUpdateRequest(String displayName) {
}

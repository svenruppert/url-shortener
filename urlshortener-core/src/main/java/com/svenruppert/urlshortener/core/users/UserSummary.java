package com.svenruppert.urlshortener.core.users;

/**
 * Public projection of a user account exposed via the management REST API.
 * Carries no credentials — the password hash never leaves the server.
 */
public record UserSummary(
    String username,
    String displayName,
    String role,
    boolean enabled
) {
}

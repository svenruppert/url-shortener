package com.svenruppert.urlshortener.core.users;

public record CreateUserRequest(
    String username,
    String password,
    String displayName,
    String role
) {
}

package com.svenruppert.urlshortener.core.users;

public record SelfChangePasswordRequest(
    String oldPassword,
    String newPassword
) {
}

package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.api.security.token.InMemoryTokenStore;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.users.SelfChangePasswordRequest;
import com.svenruppert.vaadin.security.logout.SubjectId;
import com.svenruppert.vaadin.security.rest.BearerTokenExtractor;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Self-service password change: any authenticated user may change their own
 * password by submitting the current password plus a new one. Successful
 * change revokes <em>all</em> active tokens for that user, forcing re-login
 * on every device. The endpoint is mounted outside the
 * {@code RestAuthorizationFilter} — authentication is verified by resolving
 * the bearer token directly.
 */
public final class SelfChangePasswordHandler implements HttpHandler, HasLogger {

  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final BearerTokenExtractor BEARER = new BearerTokenExtractor();

  private final UserStore userStore;
  private final InMemoryTokenStore tokenStore;

  public SelfChangePasswordHandler(UserStore userStore, InMemoryTokenStore tokenStore) {
    this.userStore = Objects.requireNonNull(userStore, "userStore");
    this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore");
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    try {
      if (!"POST".equals(exchange.getRequestMethod())) {
        writeJson(response, 405, Map.of("error", "method_not_allowed"));
        response.writeTo(exchange);
        return;
      }
      HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
      Optional<String> token = BEARER.extract(request);
      if (token.isEmpty()) {
        writeJson(response, 401, Map.of("error", "unauthenticated"));
        response.writeTo(exchange);
        return;
      }
      Optional<ShortenerUser> subject = tokenStore.resolve(token.get());
      if (subject.isEmpty()) {
        writeJson(response, 401, Map.of("error", "unauthenticated"));
        response.writeTo(exchange);
        return;
      }
      SelfChangePasswordRequest body = parse(request.bodyBytes());
      if (body == null
          || body.oldPassword() == null
          || body.newPassword() == null) {
        writeJson(response, 400, Map.of("error", "bad_request"));
        response.writeTo(exchange);
        return;
      }
      if (body.newPassword().length() < MIN_PASSWORD_LENGTH) {
        writeJson(response, 400, Map.of("error", "password_too_short"));
        response.writeTo(exchange);
        return;
      }
      String username = subject.get().username();
      boolean changed = userStore.changePassword(
          username, body.oldPassword(), body.newPassword());
      if (!changed) {
        writeJson(response, 401, Map.of("error", "invalid_credentials"));
        response.writeTo(exchange);
        return;
      }
      // Revoke every active session for this user so all devices must re-login.
      tokenStore.clearAll(SubjectId.of(username));
      response.status(204);
      response.body("");
      response.writeTo(exchange);
    } catch (RuntimeException e) {
      logger().warn("self password change failed: {}", e.getMessage());
      HttpExchangeRestResponse error = new HttpExchangeRestResponse();
      writeJson(error, 500, Map.of("error", "internal_error"));
      error.writeTo(exchange);
    }
  }

  private static SelfChangePasswordRequest parse(byte[] body) {
    try {
      if (body == null || body.length == 0) return null;
      return JacksonJson.mapper().readValue(body, SelfChangePasswordRequest.class);
    } catch (Exception e) {
      return null;
    }
  }

  private static void writeJson(HttpExchangeRestResponse response, int status, Object payload) {
    try {
      response.status(status);
      response.body(JacksonJson.mapper().writeValueAsString(payload));
    } catch (Exception e) {
      response.status(500);
      response.body("{\"error\":\"serialization\"}");
    }
  }
}

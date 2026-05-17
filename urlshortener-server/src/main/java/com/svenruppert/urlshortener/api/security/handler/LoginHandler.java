package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.api.security.auth.Credentials;
import com.svenruppert.urlshortener.api.security.token.InMemoryTokenStore;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.vaadin.security.audit.AuditEvent;
import com.svenruppert.vaadin.security.audit.LoginFailed;
import com.svenruppert.vaadin.security.audit.LoginSucceeded;
import com.svenruppert.vaadin.security.audit.SecurityAuditService;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptContext;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptDecision;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptPolicy;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class LoginHandler implements HttpHandler, HasLogger {

  private final UserStore userStore;
  private final InMemoryTokenStore tokenStore;
  private final LoginAttemptPolicy attemptPolicy;

  public LoginHandler(UserStore userStore, InMemoryTokenStore tokenStore, LoginAttemptPolicy attemptPolicy) {
    this.userStore = userStore;
    this.tokenStore = tokenStore;
    this.attemptPolicy = attemptPolicy;
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
      Credentials credentials = parseCredentials(request.bodyBytes());
      if (credentials == null
          || credentials.username() == null
          || credentials.password() == null) {
        writeJson(response, 400, Map.of("error", "bad_request"));
        response.writeTo(exchange);
        return;
      }

      String clientAddress = request.headers().get(HttpExchangeRestRequest.REMOTE_ADDR_HEADER);
      LoginAttemptContext attempt = LoginAttemptContext.now(
          credentials.username(), clientAddress, null);

      LoginAttemptDecision throttle = attemptPolicy.beforeAttempt(attempt);
      if (throttle instanceof LoginAttemptDecision.LockedOut lockout) {
        response.header("Retry-After",
            Long.toString(Math.max(1L, lockout.remaining().toSeconds())));
        writeJson(response, 429, Map.of("error", "too_many_attempts"));
        response.writeTo(exchange);
        return;
      }

      Optional<ShortenerUser> authenticated = userStore.authenticate(
          credentials.username(), credentials.password());
      if (authenticated.isEmpty()) {
        attemptPolicy.recordFailure(attempt);
        audit(new LoginFailed(Instant.now(Clock.systemUTC()),
            credentials.username(), clientAddress, "invalid_credentials"));
        writeJson(response, 401, Map.of("error", "invalid_credentials"));
        response.writeTo(exchange);
        return;
      }

      attemptPolicy.recordSuccess(attempt);
      ShortenerUser user = authenticated.get();
      String token = tokenStore.issue(user);
      audit(new LoginSucceeded(Instant.now(Clock.systemUTC()),
          user.username(), clientAddress, null));

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("token", token);
      payload.put("username", user.username());
      payload.put("displayName", user.displayName());
      payload.put("role", user.role().name());
      writeJson(response, 200, payload);
      response.writeTo(exchange);
    } catch (RuntimeException e) {
      logger().warn("login failed: {}", e.getMessage());
      HttpExchangeRestResponse error = new HttpExchangeRestResponse();
      writeJson(error, 500, Map.of("error", "internal_error"));
      error.writeTo(exchange);
    }
  }

  private static Credentials parseCredentials(byte[] body) {
    try {
      var node = JacksonJson.mapper().readTree(body);
      if (node == null || !node.isObject()) return null;
      String username = node.hasNonNull("username") ? node.get("username").asText() : null;
      String password = node.hasNonNull("password") ? node.get("password").asText() : null;
      return new Credentials(username, password);
    } catch (Exception e) {
      return null;
    }
  }

  private static void writeJson(HttpExchangeRestResponse response, int status, Map<String, Object> payload) {
    try {
      response.status(status);
      response.body(JacksonJson.mapper().writeValueAsString(payload));
    } catch (Exception e) {
      response.status(500);
      response.body("{\"error\":\"serialization\"}");
    }
  }

  private static void audit(AuditEvent event) {
    try {
      SecurityAuditService sink = SecurityServiceResolver.securityAuditService();
      sink.publish(event);
    } catch (RuntimeException ignored) {
    }
  }
}

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
import com.svenruppert.urlshortener.core.users.SelfProfileUpdateRequest;
import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.vaadin.security.rest.BearerTokenExtractor;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Self-service profile editor. Any authenticated user may change their own
 * display name. Mounted outside the {@code RestAuthorizationFilter}
 * (analogous to {@code /api/me/password}); the bearer token is resolved
 * directly and authorization is "is the request authenticated at all".
 */
public final class SelfProfileUpdateHandler implements HttpHandler, HasLogger {

  private static final BearerTokenExtractor BEARER = new BearerTokenExtractor();

  private final UserStore userStore;
  private final InMemoryTokenStore tokenStore;

  public SelfProfileUpdateHandler(UserStore userStore, InMemoryTokenStore tokenStore) {
    this.userStore = Objects.requireNonNull(userStore, "userStore");
    this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore");
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    try {
      String method = exchange.getRequestMethod();
      if (!"GET".equals(method) && !"PUT".equals(method)) {
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
      String username = subject.get().username();

      if ("PUT".equals(method)) {
        SelfProfileUpdateRequest body = parse(request.bodyBytes());
        if (body == null) {
          writeJson(response, 400, Map.of("error", "bad_request"));
          response.writeTo(exchange);
          return;
        }
        if (body.displayName() != null) {
          userStore.setDisplayName(username, body.displayName());
        }
      }

      ShortenerUser refreshed = userStore.findByUsername(username).orElse(subject.get());
      UserSummary summary = new UserSummary(
          refreshed.username(), refreshed.displayName(),
          refreshed.role().name(), refreshed.enabled());
      writeJson(response, 200, summary);
      response.writeTo(exchange);
    } catch (RuntimeException e) {
      logger().warn("self profile update failed: {}", e.getMessage());
      HttpExchangeRestResponse err = new HttpExchangeRestResponse();
      writeJson(err, 500, Map.of("error", "internal_error"));
      err.writeTo(exchange);
    }
  }

  private static SelfProfileUpdateRequest parse(byte[] body) {
    try {
      if (body == null || body.length == 0) return null;
      return JacksonJson.mapper().readValue(body, SelfProfileUpdateRequest.class);
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

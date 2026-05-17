package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.vaadin.security.bootstrap.CreateInitialAdminCommand;
import com.svenruppert.vaadin.security.bootstrap.InitialAdminBootstrapService;
import com.svenruppert.vaadin.security.bootstrap.InitialAdminCreationResult;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptContext;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptDecision;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptPolicy;
import com.svenruppert.vaadin.security.rest.BootstrapRestStatusMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BootstrapAdminHandler implements HttpHandler {

  private static final BootstrapRestStatusMapper STATUS_MAPPER = new BootstrapRestStatusMapper();
  private static final String BOOTSTRAP_THROTTLE_KEY = "__bootstrap__";

  private final InitialAdminBootstrapService bootstrapService;
  private final LoginAttemptPolicy bootstrapAttemptPolicy;

  public BootstrapAdminHandler(
      InitialAdminBootstrapService bootstrapService,
      LoginAttemptPolicy bootstrapAttemptPolicy) {
    this.bootstrapService = bootstrapService;
    this.bootstrapAttemptPolicy = Objects.requireNonNull(bootstrapAttemptPolicy);
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    if (!"POST".equals(exchange.getRequestMethod())) {
      writeJson(response, 405, Map.of("error", "method_not_allowed"));
      response.writeTo(exchange);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    String clientAddress = request.headers().get(HttpExchangeRestRequest.REMOTE_ADDR_HEADER);
    LoginAttemptContext attempt = LoginAttemptContext.now(
        BOOTSTRAP_THROTTLE_KEY, clientAddress, null);

    LoginAttemptDecision throttle = bootstrapAttemptPolicy.beforeAttempt(attempt);
    if (throttle instanceof LoginAttemptDecision.LockedOut lockout) {
      response.header("Retry-After",
          Long.toString(Math.max(1L, lockout.remaining().toSeconds())));
      writeJson(response, 429, Map.of("error", "too_many_requests"));
      response.writeTo(exchange);
      return;
    }

    tools.jackson.databind.JsonNode body;
    try {
      body = JacksonJson.mapper().readTree(request.bodyBytes());
      if (body == null || !body.isObject()) {
        throw new IllegalArgumentException("not_an_object");
      }
    } catch (Exception e) {
      writeJson(response, 400, Map.of("error", "bad_request"));
      response.writeTo(exchange);
      return;
    }
    String token = textOrNull(body, "bootstrapToken");
    String username = textOrNull(body, "username");
    String password = textOrNull(body, "password");
    String displayName = textOrNull(body, "displayName");
    String email = textOrNull(body, "email");
    if (token == null || username == null || password == null) {
      writeJson(response, 400, Map.of("error", "bad_request"));
      response.writeTo(exchange);
      return;
    }
    char[] pwd = password.toCharArray();
    InitialAdminCreationResult result = bootstrapService.createInitialAdmin(
        new CreateInitialAdminCommand(token, username, pwd, displayName, email));

    if (result instanceof InitialAdminCreationResult.Created) {
      bootstrapAttemptPolicy.recordSuccess(attempt);
    } else if (result instanceof InitialAdminCreationResult.InvalidBootstrapToken) {
      bootstrapAttemptPolicy.recordFailure(attempt);
    }

    int status = STATUS_MAPPER.statusFor(result);
    String code = STATUS_MAPPER.errorCodeFor(result);
    Map<String, Object> payload = new LinkedHashMap<>();
    if (result instanceof InitialAdminCreationResult.Created) {
      payload.put("status", code);
    } else {
      payload.put("error", code);
      if (result instanceof InitialAdminCreationResult.PasswordPolicyViolation policy) {
        payload.put("reason", policy.reason() == null ? "" : policy.reason());
      } else if (result instanceof InitialAdminCreationResult.InvalidUsername invalid) {
        payload.put("reason", invalid.reason() == null ? "" : invalid.reason());
      }
    }
    writeJson(response, status, payload);
    response.writeTo(exchange);
  }

  private static String textOrNull(tools.jackson.databind.JsonNode body, String key) {
    tools.jackson.databind.JsonNode node = body.get(key);
    return node != null && node.isTextual() ? node.asText() : null;
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
}

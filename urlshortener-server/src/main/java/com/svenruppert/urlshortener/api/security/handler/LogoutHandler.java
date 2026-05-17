package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.api.security.token.InMemoryTokenStore;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.vaadin.security.audit.AuditEvent;
import com.svenruppert.vaadin.security.audit.LogoutPerformed;
import com.svenruppert.vaadin.security.audit.SecurityAuditService;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;
import com.svenruppert.vaadin.security.logout.LogoutScope;
import com.svenruppert.vaadin.security.rest.BearerTokenExtractor;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public final class LogoutHandler implements HttpHandler {

  private static final BearerTokenExtractor BEARER = new BearerTokenExtractor();

  private final InMemoryTokenStore tokenStore;

  public LogoutHandler(InMemoryTokenStore tokenStore) {
    this.tokenStore = tokenStore;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    if (!"POST".equals(exchange.getRequestMethod())) {
      response.status(405);
      response.body("{\"error\":\"method_not_allowed\"}");
      response.writeTo(exchange);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    Optional<String> token = BEARER.extract(request);
    if (token.isPresent()) {
      Optional<ShortenerUser> user = tokenStore.resolve(token.get());
      tokenStore.revoke(token.get());
      user.ifPresent(u -> audit(new LogoutPerformed(
          Instant.now(Clock.systemUTC()), u.username(), null, LogoutScope.CurrentSession)));
    }
    response.status(204);
    response.body("");
    response.writeTo(exchange);
  }

  private static void audit(AuditEvent event) {
    try {
      SecurityAuditService sink = SecurityServiceResolver.securityAuditService();
      sink.publish(event);
    } catch (RuntimeException ignored) {
    }
  }

}

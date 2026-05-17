package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.vaadin.security.authorization.api.SecuritySubject;
import com.svenruppert.vaadin.security.rest.RestSubjectResolver;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MeHandler implements HttpHandler {

  private final RestSubjectResolver subjectResolver;

  public MeHandler(RestSubjectResolver subjectResolver) {
    this.subjectResolver = subjectResolver;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    if (!"GET".equals(exchange.getRequestMethod())) {
      response.status(405);
      response.body("{\"error\":\"method_not_allowed\"}");
      response.writeTo(exchange);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    Optional<SecuritySubject> subject = subjectResolver.resolveSubject(request);
    if (subject.isEmpty()) {
      response.status(401);
      response.body("{\"error\":\"unauthenticated\"}");
      response.writeTo(exchange);
      return;
    }
    SecuritySubject s = subject.get();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("username", s.subjectId());
    payload.put("displayName", s.displayName());
    payload.put("roles", s.roles().stream()
        .map(r -> r.roleName())
        .collect(Collectors.toList()));
    payload.put("permissions", s.permissions().stream()
        .map(p -> p.permissionName())
        .collect(Collectors.toList()));
    try {
      response.status(200);
      response.body(JacksonJson.mapper().writeValueAsString(payload));
    } catch (Exception e) {
      response.status(500);
      response.body("{\"error\":\"serialization\"}");
    }
    response.writeTo(exchange);
  }
}

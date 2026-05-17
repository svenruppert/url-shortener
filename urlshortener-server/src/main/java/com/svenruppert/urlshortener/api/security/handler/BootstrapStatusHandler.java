package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.vaadin.security.bootstrap.BootstrapStateService;
import com.svenruppert.vaadin.security.bootstrap.BootstrapStatus;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BootstrapStatusHandler implements HttpHandler {

  private final BootstrapStateService stateService;

  public BootstrapStatusHandler(BootstrapStateService stateService) {
    this.stateService = stateService;
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
    BootstrapStatus snapshot = BootstrapStatus.from(stateService);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("bootstrapRequired", snapshot.bootstrapRequired());
    payload.put("mode", snapshot.mode().name());
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

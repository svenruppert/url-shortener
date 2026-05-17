package com.svenruppert.urlshortener.api.security.adapter;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.vaadin.security.rest.RestResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpExchangeRestResponse implements RestResponse {

  private int status = 200;
  private String body = "";
  private final Map<String, String> headers = new LinkedHashMap<>();

  @Override
  public void status(int statusCode) {
    this.status = statusCode;
  }

  @Override
  public void body(String body) {
    this.body = body == null ? "" : body;
  }

  @Override
  public void header(String name, String value) {
    if (name == null || name.isBlank() || value == null) {
      return;
    }
    headers.put(name, value);
  }

  public int status() {
    return status;
  }

  public String getBody() {
    return body;
  }

  public Map<String, String> getHeaders() {
    return Map.copyOf(headers);
  }

  public void writeTo(HttpExchange exchange) throws IOException {
    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    headers.forEach((n, v) -> exchange.getResponseHeaders().add(n, v));
    int len = payload.length == 0 ? -1 : payload.length;
    exchange.sendResponseHeaders(status, len);
    if (payload.length > 0) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(payload);
      }
    }
  }
}

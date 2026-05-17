package com.svenruppert.urlshortener.api.security.adapter;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.vaadin.security.rest.BodyRestRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpExchangeRestRequest implements BodyRestRequest {

  public static final String REMOTE_ADDR_HEADER = "X-Forwarded-For";

  private final HttpExchange exchange;
  private final Map<String, String> headers;
  private final Map<String, String> queryParameters;
  private byte[] cachedBody;

  public HttpExchangeRestRequest(HttpExchange exchange) {
    this.exchange = exchange;
    this.headers = flattenHeaders(exchange);
    URI uri = exchange.getRequestURI();
    this.queryParameters = parseQuery(uri.getRawQuery());
  }

  @Override
  public String method() {
    return exchange.getRequestMethod();
  }

  @Override
  public String path() {
    return exchange.getRequestURI().getPath();
  }

  @Override
  public Map<String, String> headers() {
    return headers;
  }

  @Override
  public Map<String, String> queryParameters() {
    return queryParameters;
  }

  @Override
  public byte[] bodyBytes() {
    if (cachedBody == null) {
      try (InputStream in = exchange.getRequestBody()) {
        cachedBody = in.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return cachedBody.clone();
  }

  private static Map<String, String> flattenHeaders(HttpExchange exchange) {
    Map<String, String> map = new LinkedHashMap<>();
    for (var entry : exchange.getRequestHeaders().entrySet()) {
      if (!entry.getValue().isEmpty()) {
        map.put(entry.getKey(), entry.getValue().get(0));
      }
    }
    var remote = exchange.getRemoteAddress();
    if (remote != null && remote.getAddress() != null
        && !map.containsKey(REMOTE_ADDR_HEADER)) {
      map.put(REMOTE_ADDR_HEADER, remote.getAddress().getHostAddress());
    }
    return Map.copyOf(map);
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> map = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return Map.of();
    }
    for (String pair : rawQuery.split("&")) {
      int eq = pair.indexOf('=');
      if (eq < 0) {
        map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
      } else {
        String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
        String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
        map.put(k, v);
      }
    }
    return Map.copyOf(map);
  }
}

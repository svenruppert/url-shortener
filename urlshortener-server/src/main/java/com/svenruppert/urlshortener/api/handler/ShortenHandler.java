package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingUpdater;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.svenruppert.urlshortener.core.JsonUtils.parseJson;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;


public class ShortenHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingUpdater store;

  public ShortenHandler(UrlMappingUpdater store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    InputStream body = exchange.getRequestBody();

    Map<String, String> payload = null;
    try {
      payload = parseJson(body);
    } catch (IOException e) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }

    String originalUrl = payload.get("url");
    logger().info("Received request to shorten url: {}", originalUrl);
    if (originalUrl == null || originalUrl.isBlank()) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }

    ShortUrlMapping mapping = store.createMapping(originalUrl);
    logger().info("Created mapping for {} -> {}", originalUrl, mapping.shortCode());

    byte[] response = toJson(Map.of("shortCode", mapping.shortCode())).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);

    try (OutputStream os = exchange.getResponseBody()) {
      os.write(response);
    }
  }
}
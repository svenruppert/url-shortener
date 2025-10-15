package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingLookup;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static com.svenruppert.urlshortener.core.JsonUtils.toJsonListing;
import static java.time.Instant.now;

public class ListHandler
    implements HttpHandler, HasLogger {

//  public static final String PATH_LIST_ALL = "/list/all";
//  public static final String PATH_LIST_EXPIRED = "/list/expired";
//  public static final String PATH_LIST_ACTIVE = "/list/active";

  private final UrlMappingLookup store;

  public ListHandler(UrlMappingLookup store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    final String path = exchange.getRequestURI().getPath();
    logger().info("List request: {}", path);

    String responseJson;
    if (path.endsWith(PATH_LIST_ALL)) {
      responseJson = listAll();
    } else if (path.endsWith(PATH_LIST_EXPIRED)) {
      responseJson = listExpired();
    } else if (path.endsWith(PATH_LIST_ACTIVE)) {
      responseJson = listActive();
    } else {
      logger().info("undefined path {}", path);
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    final byte[] payload = responseJson.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(200, payload.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(payload);
    }
  }

  private String listAll() {
    return filterAndBuild("all", m -> true);
  }

  private String listExpired() {
    final Instant now = Instant.now();
    return filterAndBuild("expired",
                          m -> m.expiresAt().isPresent() && m.expiresAt().get().isBefore(now));
  }

  private String listActive() {
    final Instant now = Instant.now();
    return filterAndBuild("active",
                          m -> m.expiresAt().map(exp -> !exp.isBefore(now)).orElse(true));
  }
  // ————————————————————————————————————————————————————————————————————————————
  // Hilfsfunktionen
  // ————————————————————————————————————————————————————————————————————————————

  private String filterAndBuild(String mode, Predicate<ShortUrlMapping> predicate) {
    logger().info("filterAndBuild - mode {}", mode);

    final Instant now = now();
    final List<Map<String, String>> data = store
        .findAll()
        .stream()
        .filter(predicate)
        .map(m -> toDto(m, now))
        .toList();

    logger().info("List {} -> {} items", mode, data.size());
    return toJsonListing(mode, data.size(), data);
  }

  private Map<String, String> toDto(ShortUrlMapping m, Instant now) {
    Optional<String> expires = m.expiresAt().map(Instant::toString);
    boolean expired = m.expiresAt().map(t -> t.isBefore(now)).orElse(false);
    String status = expired ? "expired" : "active";

    return Map.of(
        "shortCode", m.shortCode(),
        "originalUrl", m.originalUrl(),
        "createdAt", m.createdAt().toString(),
        "expiresAt", expires.orElse(""),
        "status", status
    );
  }
}
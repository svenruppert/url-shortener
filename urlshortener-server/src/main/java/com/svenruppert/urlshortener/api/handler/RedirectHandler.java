package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.time.Instant;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_REDIRECT;

public class RedirectHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;

  public RedirectHandler(UrlMappingLookup store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) return;

    final String path = exchange.getRequestURI().getPath(); // z.B. "/ABC123"
    if (path == null || !path.startsWith(PATH_REDIRECT)) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    final String code = path.substring((PATH_REDIRECT).length());
    if (code.isBlank()) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    logger().info("looking for short code {}", code);
    var mappingOpt = store.findByShortCode(code);

    if (mappingOpt.isEmpty()) {
      logger().info("no mapping found for short code {}", code);
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    var mapping = mappingOpt.get();
    if (isExpired(mapping)) {
      logger().info("short code {} is expired at {}", code, mapping.expiresAt().orElse(null));
      exchange.sendResponseHeaders(410, -1);
      return;
    }

    if (!mapping.active()) {
      logger().info("short code {} is inactive", code);
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    exchange.getResponseHeaders().add("Location", mapping.originalUrl());
    exchange.sendResponseHeaders(302, -1);
  }

  private boolean isExpired(ShortUrlMapping mapping) {
    return mapping.expiresAt()
        .map(exp -> exp.isBefore(Instant.now()))
        .orElse(false);
  }


}
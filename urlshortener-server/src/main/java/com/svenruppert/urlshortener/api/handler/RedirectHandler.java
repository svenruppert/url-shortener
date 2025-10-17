package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingLookup;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.io.IOException;
import java.util.Optional;

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

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().add("Allow", "GET");
      exchange.sendResponseHeaders(405, -1);
      return;
    }
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
    Optional<String> target = store
        .findByShortCode(code)
        .map(ShortUrlMapping::originalUrl);

    if (target.isPresent()) {
      exchange.getResponseHeaders().add("Location", target.get());
      exchange.sendResponseHeaders(302, -1);
    } else {
      exchange.sendResponseHeaders(404, -1);
    }
  }
}
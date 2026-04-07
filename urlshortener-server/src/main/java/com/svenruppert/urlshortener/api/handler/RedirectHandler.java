package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.statistics.RequestDataExtractor;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsWriter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.time.Instant;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_REDIRECT;

public class RedirectHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;
  private final StatisticsWriter statisticsWriter;
  private final RequestDataExtractor requestDataExtractor;

  public RedirectHandler(UrlMappingLookup store, StatisticsWriter statisticsWriter) {
    this.store = store;
    this.statisticsWriter = statisticsWriter;
    this.requestDataExtractor = new RequestDataExtractor();
  }

  /**
   * Constructor for backwards compatibility (without statistics).
   */
  public RedirectHandler(UrlMappingLookup store) {
    this(store, null);
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) return;

    final String path = exchange.getRequestURI().getPath();
    if (path == null || !path.startsWith(PATH_REDIRECT)) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    final String code = path.substring((PATH_REDIRECT).length());
    if (code.isBlank()) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    logger().info("Looking for short code {}", code);
    var mappingOpt = store.findByShortCode(code);

    if (mappingOpt.isEmpty()) {
      logger().info("No mapping found for short code {}", code);
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    var mapping = mappingOpt.get();
    if (isExpired(mapping)) {
      logger().info("Short code {} is expired at {}", code, mapping.expiresAt().orElse(null));
      exchange.sendResponseHeaders(410, -1);
      return;
    }

    if (!mapping.active()) {
      logger().info("Short code {} is inactive", code);
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    // Record the redirect event (non-blocking)
    recordRedirectEvent(exchange, code);

    exchange.getResponseHeaders().add("Location", mapping.originalUrl());
    exchange.sendResponseHeaders(302, -1);
  }

  private void recordRedirectEvent(HttpExchange exchange, String shortCode) {
    if (statisticsWriter == null) {
      return;
    }
    try {
      var event = requestDataExtractor.extractEvent(exchange, shortCode);
      statisticsWriter.recordEvent(event);
      logger().debug("Recorded redirect event for shortCode={}", shortCode);
    } catch (Exception e) {
      // Never let statistics recording fail the redirect
      logger().warn("Failed to record redirect event for shortCode={}", shortCode, e);
    }
  }

  private boolean isExpired(ShortUrlMapping mapping) {
    return mapping.expiresAt()
        .map(exp -> exp.isBefore(Instant.now()))
        .orElse(false);
  }
}
package com.svenruppert.urlshortener.api.filter;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.IOException;

public class BlockBrowserPreflightFilter
    extends Filter
    implements HasLogger {

  @Override
  public String description() {
    return "Blocks all browser-originated preflight (OPTIONS) requests";
  }

  @Override
  public void doFilter(HttpExchange exchange, Chain chain)
      throws IOException {
    if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())
        && exchange.getRequestHeaders().getFirst("Origin") != null) {
      logger().info("OPTIONS Request  - blocking now");
      // Block all preflight requests
      exchange.sendResponseHeaders(403, -1);
      exchange.close();
      return;
    }

    chain.doFilter(exchange);
  }
}

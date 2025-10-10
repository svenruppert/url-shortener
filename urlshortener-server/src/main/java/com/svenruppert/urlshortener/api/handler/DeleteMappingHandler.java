package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingUpdater;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeleteMappingHandler implements HttpHandler, HasLogger {

  // Erlaubt klassische Base62-ähnliche Shortcodes; passe den Regex bei Bedarf an dein Projekt an.
  private static final Pattern PATH = Pattern.compile("^/delete/([A-Za-z0-9]+)$");
  private final UrlMappingUpdater updater;

  public DeleteMappingHandler(UrlMappingUpdater updater) {
    this.updater = updater;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    final String method = exchange.getRequestMethod();
    final String path   = exchange.getRequestURI().getPath();
    logger().info("DeleteMappingHandler invoked: {} {}", method, path);

    if (!"DELETE".equalsIgnoreCase(method)) {
      exchange.getResponseHeaders().add("Allow", "DELETE");
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    final Matcher m = PATH.matcher(path);
    if (!m.matches()) {
      sendJson(exchange, 400, "{\"error\":\"bad_request\",\"message\":\"expected DELETE /delete/{shortCode}\"}");
      return;
    }

    final String shortCode = m.group(1);

    // Delegation an den Updater/Store
    try {
      boolean removed = updater.delete(shortCode); // ggf. auf remove(...) umbenennen, falls deine API so heißt
      if (removed) {
        exchange.sendResponseHeaders(204, -1); // No Content
      } else {
        sendJson(exchange, 404, "{\"error\":\"not_found\",\"message\":\"shortCode not found\"}");
      }
    } catch (Exception e) {
      logger().error("Delete failed for shortCode={}", shortCode, e);
      sendJson(exchange, 500, "{\"error\":\"internal_error\"}");
    }
  }

  private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }
}

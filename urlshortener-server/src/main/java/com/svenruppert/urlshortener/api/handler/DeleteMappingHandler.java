package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingUpdater;
import com.svenruppert.urlshortener.core.AliasPolicy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_DELETE;


public final class DeleteMappingHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingUpdater updater;

  public DeleteMappingHandler(UrlMappingUpdater updater) {
    this.updater = updater;
  }

  private static void sendJson(HttpExchange ex, int status, String body)
      throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    final String method = exchange.getRequestMethod();
    final String path = exchange.getRequestURI().getPath();
    logger().info("DeleteMappingHandler invoked: {} {}", method, path);

    if (!"DELETE".equalsIgnoreCase(method)) {
      logger().warn("no DELETE Req - {}", method);
      exchange.getResponseHeaders().add("Allow", "DELETE");
      exchange.sendResponseHeaders(405, -1);
      return;
    }


    //TODO check with AliasPolicy.isValid(alias)
    if (!path.startsWith(PATH_ADMIN_DELETE)) {
      logger().warn("no matches - {}", path);
      sendJson(exchange, 400, "{\"error\":\"bad_request\",\"message\":\"expected DELETE /delete/{shortCode}\"}");
      return;
    }

    var shortCode = path.substring((PATH_ADMIN_DELETE.length() + 1));
    logger().info("Alias to delete '{}'", shortCode);

    var validated = AliasPolicy.validate(shortCode);
    if (!validated.valid()) {
      logger().info("kein valider ShortCode/Alias .. Abbruch ");
      sendJson(exchange, 400, "{\"error\":\"bad_request\",\"message\":\"expected valid shortCode\"}");
      return;
    }

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
}

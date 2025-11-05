package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingUpdater;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.AliasPolicy;

import java.io.IOException;

import static com.svenruppert.dependencies.core.net.HttpStatus.fromCode;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_DELETE;


public final class DeleteMappingHandler
    implements HttpHandler, HasLogger {

  private static final String BAD_REQUEST_ERROR_RESPONSE = "{\"error\":\"bad_request\",\"message\":\"expected DELETE /delete/{shortCode}\"}";
  private static final String INVALID_SHORT_CODE_ERROR_JSON = "{\"error\":\"bad_request\",\"message\":\"expected valid shortCode\"}";
  private static final String SHORT_CODE_NOT_FOUND = "{\"error\":\"not_found\",\"message\":\"shortCode not found\"}";
  private static final String INTERNAL_ERROR = "{\"error\":\"internal_error\"}";
  private final UrlMappingUpdater updater;

  public DeleteMappingHandler(UrlMappingUpdater updater) {
    this.updater = updater;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    final String method = exchange.getRequestMethod();
    final String path = exchange.getRequestURI().getPath();
    logger().info("DeleteMappingHandler invoked: {} {}", method, path);
    if (!RequestMethodUtils.requireDelete(exchange)) return;
//    if (!"DELETE".equalsIgnoreCase(method)) {
//      logger().warn("no DELETE Req - {}", method);
//      exchange.getResponseHeaders().add("Allow", "DELETE");
//      exchange.sendResponseHeaders(405, -1);
//      return;
//    }

    //TODO check with AliasPolicy.isValid(alias)
    if (!path.startsWith(PATH_ADMIN_DELETE)) {
      logger().warn("no matches - {}", path);
      writeJson(exchange, fromCode(400), BAD_REQUEST_ERROR_RESPONSE);
      return;
    }

    var shortCode = path.substring((PATH_ADMIN_DELETE.length() + 1));
    logger().info("Alias to delete '{}'", shortCode);

    var validated = AliasPolicy.validate(shortCode);
    if (!validated.valid()) {
      logger().info("no valid shortcode/alias .. Abort ");
      writeJson(exchange, fromCode(400), INVALID_SHORT_CODE_ERROR_JSON);
      return;
    }

    try {
      boolean removed = updater.delete(shortCode); // ggf. auf remove(...) umbenennen, falls deine API so heißt
      if (removed) {
        exchange.sendResponseHeaders(204, -1); // No Content
      } else {
        writeJson(exchange, fromCode(404), SHORT_CODE_NOT_FOUND);
      }
    } catch (Exception e) {
      logger().error("Delete failed for shortCode={}", shortCode, e);
      writeJson(exchange, fromCode(500), INTERNAL_ERROR);
    }
  }
}

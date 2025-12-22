package com.svenruppert.urlshortener.api.handler.admin.columns;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.core.prefs.ColumnInfoRequest;

import java.io.IOException;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;

public class ColumnVisibilityHandler
    implements HttpHandler, HasLogger {

  private final PreferencesStore store;

  public ColumnVisibilityHandler(PreferencesStore store) {
    this.store = store;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    try {
      switch (ex.getRequestMethod()) {
        case "POST" -> handleLoad(ex);
        case "DELETE" -> handleDeleteAll(ex);
        case "OPTIONS" -> allow(ex, "POST, DELETE, OPTIONS");
        default -> methodNotAllowed(ex, "POST, DELETE, OPTIONS");
      }
    } catch (Exception e) {
      logger().error("Unhandled error in {}: {}", getClass().getSimpleName(), e.toString(), e);
      writeJson(ex, INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  private void handleLoad(HttpExchange ex)
      throws IOException {
    logger().info("handleLoad..");
    var req = fromJson(readBody(ex.getRequestBody()), ColumnInfoRequest.class);
    if (isBlank(req.userId()) || isBlank(req.viewId())) {
      writeJson(ex, BAD_REQUEST, "userId and viewId required");
      return;
    }
    var vis = store.load(req.userId(), req.viewId());
    writeJson(ex, OK, toJson(vis == null ? Map.of() : vis));
  }

  private void handleDeleteAll(HttpExchange ex)
      throws IOException {
    logger().info("handleDeleteAll..");
    var req = fromJson(readBody(ex.getRequestBody()), ColumnInfoRequest.class);
    store.delete(req.userId(), req.viewId());
    ex.sendResponseHeaders(NO_CONTENT.code(), -1);
  }

  private void allow(HttpExchange ex, String allow)
      throws IOException {
    ex.getResponseHeaders().add("Allow", allow);
    ex.sendResponseHeaders(NO_CONTENT.code(), -1);
  }

  private void methodNotAllowed(HttpExchange ex, String allow)
      throws IOException {
    ex.getResponseHeaders().add("Allow", allow);
    writeJson(ex, METHOD_NOT_ALLOWED, "Method not allowed");
  }
}

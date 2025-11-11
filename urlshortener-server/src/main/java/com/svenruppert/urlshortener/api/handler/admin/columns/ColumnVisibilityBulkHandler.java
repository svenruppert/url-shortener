package com.svenruppert.urlshortener.api.handler.admin.columns;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.core.prefs.ColumnEditRequest;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;

public class ColumnVisibilityBulkHandler
    implements HttpHandler, HasLogger {

  private final PreferencesStore store;

  public ColumnVisibilityBulkHandler(PreferencesStore store) {
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
        case "PUT" -> handleBulkEdit(ex);
        case "OPTIONS" -> allow(ex, "PUT, OPTIONS");
        default -> methodNotAllowed(ex, "PUT, OPTIONS");
      }
    } catch (Exception e) {
      logger().error("Unhandled error in {}: {}", getClass().getSimpleName(), e.toString(), e);
      writeJson(ex, INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  private void handleBulkEdit(HttpExchange ex)
      throws IOException {
    var bodyAsString = readBody(ex.getRequestBody());
    logger().info("handleBulkEdit - bodyAsString: {}", bodyAsString);
    var req = fromJson(bodyAsString, ColumnEditRequest.class);
    logger().info("handleBulkEdit - ColumnEditRequest - {}", req);
    var blankUserID = isBlank(req.userId());
    var blankViewId = isBlank(req.viewId());
    var hasNoChanges = req.changes() == null;
    logger().info("blankUserID: {} , blankViewId: {}, hasNoChanges: {}", blankUserID, blankViewId, hasNoChanges);
    if (blankUserID || blankViewId || hasNoChanges || req.changes().isEmpty()) {
      writeJson(ex, BAD_REQUEST, "userId, viewId and non-empty changes required");
      return;
    }

    var oldPreferences = store.load(req.userId(), req.viewId());
    Map<String, Boolean> merged = Stream
        .of(oldPreferences, req.changes())
        .flatMap(m -> m.entrySet().stream())
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (v1, v2) -> v2
        ));

    store.saveColumnVisibilities(req.userId(), req.viewId(), merged);
    writeJson(ex, OK, toJson(Map.of("status", "ok")));
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

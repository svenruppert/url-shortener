package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveRequest;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveResponse;

import java.io.IOException;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * Toggles the "active" state of a ShortUrlMapping.
 * <p>
 * Request JSON:
 * {
 * "shortURL": "abc123",
 * "active": true
 * }
 * <p>
 * Response:
 * 200 OK + updated mapping as JSON
 * 400 Bad Request (missing fields, invalid alias)
 * 404 Not Found (mapping not found)
 */
public class ToggleActiveHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore store;

  public ToggleActiveHandler(UrlMappingStore store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    logger().info("handle ... {} ", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePut(ex)) return;

    try {
      final String body = readBody(ex.getRequestBody());
      ToggleActiveRequest req = fromJson(body, ToggleActiveRequest.class);
      var shortCode = req.shortCode();
      if (isNullOrBlank(shortCode)) {
        writeJson(ex, BAD_REQUEST, "Missing 'shortCode'");
        return;
      }
      boolean newActiveValue = req.active();
      logger().info("Toggling active status for {} to {}", shortCode, newActiveValue);
      Result<ToggleActiveResponse> mapping = store.toggleActive(shortCode, newActiveValue);

      if (mapping.isPresent()) {
        logger().info("Toggling active status successfully");
        writeJson(ex, OK, toJson(mapping.get()));
      } else {
        mapping.
            ifFailed(failed -> logger().info("Toggling active status failed: {}", failed));
        writeJson(ex, BAD_REQUEST, "Toggling active status failed");
      }
    } catch (RuntimeException e) {
      logger().warn("catch - {}", e.toString());
      writeJson(ex, INTERNAL_SERVER_ERROR);
    } finally {
      logger().info("ToggleActiveHandler .. finally");
      ex.close();
    }
  }
}

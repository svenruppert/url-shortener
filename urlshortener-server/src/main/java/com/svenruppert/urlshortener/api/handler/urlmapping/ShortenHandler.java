package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;

import java.io.IOException;
import java.net.URI;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * POST /shorten
 * Request JSON: { "url": "https://example.org", "alias": "my-nice-code" } (alias optional)
 * Responses:
 * - 201 Created, body: ShortUrlMapping, Location: /r/{code}
 * - 400 Bad Request (invalid JSON/URL/Alias)
 * - 409 Conflict (alias already used)
 * - 405 Method Not Allowed (others)
 * <p>
 * Hint: Aliasse sind case-insensitive (werden intern lowercased gespeichert).
 */
public class ShortenHandler
    implements HttpHandler, HasLogger {


  protected static final String HTTP = "http";
  protected static final String HTTPS = "https";

  private final UrlMappingStore store;

  public ShortenHandler(UrlMappingStore store) {
    this.store = store;
  }


  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    logger().info("handle ... {} ", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePost(ex)) return;
    try {
      // Optional: CORS Preflight
      //      if (REQUEST_METHOD_OPTIONS.equalsIgnoreCase(ex.getRequestMethod())) {
      //        ex.sendResponseHeaders(NO_CONTENT.code(), -1);
      //        return;
      //      }
      //
      //      if (!REQUEST_METHOD_POST.equalsIgnoreCase(ex.getRequestMethod())) {
      //        ex.getResponseHeaders().add("Allow", "POST, OPTIONS");
      //        writeJson(ex, METHOD_NOT_ALLOWED);
      //        return;
      //      }

      final String body = readBody(ex.getRequestBody());
      ShortenRequest req = fromJson(body, ShortenRequest.class);
      if (isNullOrBlank(req.getUrl())) {
        writeJson(ex, BAD_REQUEST, "Missing 'url'");
        return;
      }

      if (req.getUrl().contains("\r") || req.getUrl().contains("\n")) {
        writeJson(ex, BAD_REQUEST, "Invalid characters in 'url'");
        return;
      }

      // URL validieren (nur http/https)
      final URI target;
      try {
        target = URI.create(req.getUrl());
      } catch (IllegalArgumentException iae) {
        writeJson(ex, BAD_REQUEST, "Invalid 'url'");
        return;
      }
      final String scheme = target.getScheme();
      if (scheme == null
          || !(scheme.equalsIgnoreCase(HTTP)
          || scheme.equalsIgnoreCase(HTTPS))) {
        writeJson(ex, BAD_REQUEST, "Only http/https allowed");
        return;
      }

      logger().info("ShortenHandler - createMapping start");
      final Result<ShortUrlMapping> urlMappingResult = store.createMapping(req.getShortURL(),
                                                                           req.getUrl(),
                                                                           req.getExpiresAt());
      logger().info("ShortenHandler - createMapping stop");
      urlMappingResult
          .ifPresentOrElse(success -> logger().info("mapping created success {}", success.toString()),
                           failed -> logger().info("mapping created failed - {}", failed));

      logger().info("ShortenHandler - createMapping consuming urlMappingResult");
      urlMappingResult
          .ifFailed(errorJson -> {
            try {
              var parsed = JsonUtils.parseJson(errorJson);
              logger().info("parsed Json {}", parsed);
              var code = parsed.get("code");
              var errorCode = Integer.parseInt(code);
              var message = parsed.get("message");
              writeJson(ex, HttpStatus.fromCode(errorCode), message);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          })
          .ifPresent(mapping -> {
            // 201 + Location Header
            final Headers h = ex.getResponseHeaders();
            h.add("Location", "/r/" + mapping.shortCode());
            try {
              writeJson(ex, fromCode(201), toJson(mapping));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      logger().info("hortenHandler .. try block .. done");
    } catch (Exception e) {
      logger().warn("catch - {}", e.toString());
      writeJson(ex, INTERNAL_SERVER_ERROR);
    } finally {
      logger().info("ShortenHandler .. finally");
      ex.close();
    }
  }
}
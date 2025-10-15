package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.ShortenRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;
import static java.nio.charset.StandardCharsets.UTF_8;

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

  protected static final String REQUEST_METHOD_OPTIONS = "OPTIONS";
  protected static final String REQUEST_METHOD_POST = "POST";
  protected static final String HTTP = "http";
  protected static final String HTTPS = "https";
  protected static final String CONTENT_TYPE = "Content-Type";
  protected static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  private final UrlMappingStore store;
  private final ShortCodeGenerator generator;
  private final boolean enableCors;

  public ShortenHandler(UrlMappingStore store) {
    this.store = store;
    this.generator = new ShortCodeGenerator(0);
    this.enableCors = false;
  }

  public ShortenHandler(UrlMappingStore store, ShortCodeGenerator generator) {
    this(store, generator, false);
  }

  public ShortenHandler(UrlMappingStore store, ShortCodeGenerator generator, boolean enableCors) {
    this.store = store;
    this.generator = generator;
    this.enableCors = enableCors;
  }

  private static String readBody(InputStream is)
      throws IOException {
    if (is == null) return "";
    byte[] buf = is.readAllBytes();
    return new String(buf, UTF_8);
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    try {
      // Optional: CORS Preflight
      if (REQUEST_METHOD_OPTIONS.equalsIgnoreCase(ex.getRequestMethod())) {
        withCors(ex.getResponseHeaders());
        ex.sendResponseHeaders(NO_CONTENT.code(), -1);
        return;
      }

      if (!REQUEST_METHOD_POST.equalsIgnoreCase(ex.getRequestMethod())) {
        withCors(ex.getResponseHeaders());
        ex.getResponseHeaders().add("Allow", "POST, OPTIONS");
        writeJson(ex, METHOD_NOT_ALLOWED);
        return;
      }

      withCors(ex.getResponseHeaders());

      final String body = readBody(ex.getRequestBody());
      ShortenRequest req = JsonUtils.fromJson(body, ShortenRequest.class);
      if (req == null || isNullOrBlank(req.getUrl())) {
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
      var urlMappingResult = store.createMapping(req.getShortURL(), req.getUrl());

      urlMappingResult
          .ifFailed(errorJson -> {
            try {
              var parsed = JsonUtils.parseJson(errorJson);
              logger().info("createMapping failed . {}", errorJson);
              for (var entry : parsed.entrySet()) {
                logger().info("entry - {}", entry);
                var entryKey = entry.getKey();
                var errorCode = Integer.parseInt(entryKey);

                writeJson(ex, HttpStatus.fromCode(errorCode), entry.getValue());
              }
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          })
          .ifPresent(mapping -> {
            // 201 + Location Header
            final Headers h = ex.getResponseHeaders();
            h.add("Location", "/r/" + mapping.shortCode());
            try {
              writeJson(ex, fromCode(201), JsonUtils.toJson(mapping));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });

    } catch (Exception e) {
      logger().warn(e.toString());
      writeJson(ex, INTERNAL_SERVER_ERROR);
    } finally {
      ex.close();
    }
  }

  private void writeJson(HttpExchange ex, HttpStatus httpStatus)
      throws IOException {
    writeJson(ex, httpStatus, httpStatus.reason());
  }

  private void writeJson(HttpExchange ex, HttpStatus httpStatus, String message)
      throws IOException {
    byte[] data = message.getBytes(UTF_8);
    Headers h = ex.getResponseHeaders();
    h.set(CONTENT_TYPE, JSON_CONTENT_TYPE);
    withCors(h);
    ex.sendResponseHeaders(httpStatus.code(), data.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(data);
    }
  }

  //TODO extract into Utils Class
  private void withCors(Headers h) {
    if (!enableCors) return;
    // Ggf. anpassen oder deaktivieren, wenn CORS nicht gewünscht
    h.add("Access-Control-Allow-Origin", "*");
    h.add("Access-Control-Allow-Methods", "POST, OPTIONS");
    h.add("Access-Control-Allow-Headers", CONTENT_TYPE);
  }
}
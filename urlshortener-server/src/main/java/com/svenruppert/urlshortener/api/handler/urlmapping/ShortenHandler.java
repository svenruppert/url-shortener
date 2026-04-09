package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;
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
      final String body = readBody(ex.getRequestBody());
      ShortenRequest req = fromJson(body, ShortenRequest.class);
      if (isNullOrBlank(req.getUrl())) {
        ErrorResponses.badRequest(ex, "Missing 'url'");
        return;
      }

      var result = UrlValidator.validate(req.getUrl());
      if (!result.valid()) {
        ErrorResponses.badRequest(ex, result.message());
        return;
      } else {
        logger().info("valid URL {}", req.getUrl());
      }


      logger().info("ShortenHandler - createMapping start");
      final Result<ShortUrlMapping> urlMappingResult = store.createMapping(req.getShortURL(),
                                                                           req.getUrl(),
                                                                           req.getExpiresAt(),
                                                                           req.getActive());
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
              ErrorResponses.withStatus(ex, errorCode, message);

            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          })
          .ifPresent(mapping -> {
            // 201 + Location Header
            final Headers h = ex.getResponseHeaders();
            h.add("Location", "/r/" + mapping.shortCode());
            try {
              SuccessResponses.withStatus(ex, 201, mapping);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      logger().info("hortenHandler .. try block .. done");
    } catch (IllegalArgumentException e) {
      // z.B. "Invalid JSON: ..." oder fachliche Validierung
      logger().warn("bad request - {}", e.getMessage());
      ErrorResponses.badRequest(ex, e.getMessage());
    } catch (Exception e) {
      logger().error("internal error", e);
      ErrorResponses.internalServerError(ex, "internal_error");
    } finally {
      logger().info("ShortenHandler .. finally");
      // ex.close() entfernen
    }

  }
}
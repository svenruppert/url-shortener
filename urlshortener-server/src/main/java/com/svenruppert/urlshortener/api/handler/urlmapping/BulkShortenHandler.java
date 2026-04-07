package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenRequest;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse.BulkShortenItemResult;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;

/**
 * POST /api/shorten/bulk
 * Request JSON: { "urls": ["https://example.org", "https://other.com", ...] }
 * Responses:
 * - 200 OK, body: BulkShortenResponse with per-item results (succeeded + failed entries)
 * - 400 Bad Request (missing/empty urls list or invalid JSON)
 * - 405 Method Not Allowed
 *
 * Each URL is validated and processed independently. Invalid URLs produce an error entry
 * in the result without aborting the remaining items.
 */
public class BulkShortenHandler implements HttpHandler, HasLogger {

  private final UrlMappingStore store;

  public BulkShortenHandler(UrlMappingStore store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    logger().info("BulkShortenHandler - {}", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePost(ex)) return;

    try {
      final String body = readBody(ex.getRequestBody());
      final BulkShortenRequest req = fromJson(body, BulkShortenRequest.class);

      if (req.getUrls() == null || req.getUrls().isEmpty()) {
        ErrorResponses.badRequest(ex, "Missing or empty 'urls' list");
        return;
      }

      final List<BulkShortenItemResult> results = new ArrayList<>(req.getUrls().size());

      for (String rawUrl : req.getUrls()) {
        final String url = rawUrl == null ? "" : rawUrl.strip();

        if (url.isBlank()) {
          results.add(BulkShortenItemResult.error(rawUrl != null ? rawUrl : "", "URL must not be blank"));
          continue;
        }

        final var validation = UrlValidator.validate(url);
        if (!validation.valid()) {
          results.add(BulkShortenItemResult.error(url, validation.message()));
          continue;
        }

        final var mappingResult = store.createMapping(null, url, null, true);

        if (mappingResult.isPresent()) {
          results.add(BulkShortenItemResult.success(url, mappingResult.get().shortCode()));
        } else {
          final AtomicReference<String> errorMsg = new AtomicReference<>("Creation failed");
          mappingResult.ifFailed(errorJson -> {
            try {
              var parsed = JsonUtils.parseJson(errorJson);
              var message = parsed.get("message");
              if (message != null && !message.isBlank()) errorMsg.set(message);
            } catch (IOException ignored) {
            }
          });
          results.add(BulkShortenItemResult.error(url, errorMsg.get()));
        }
      }

      logger().info("BulkShortenHandler - processed {} URLs: {} succeeded, {} failed",
                    results.size(),
                    results.stream().filter(BulkShortenItemResult::isSuccess).count(),
                    results.stream().filter(r -> !r.isSuccess()).count());

      SuccessResponses.ok(ex, new BulkShortenResponse(results));

    } catch (IllegalArgumentException e) {
      logger().warn("bad request - {}", e.getMessage());
      ErrorResponses.badRequest(ex, e.getMessage());
    } catch (Exception e) {
      logger().error("internal error", e);
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }
}

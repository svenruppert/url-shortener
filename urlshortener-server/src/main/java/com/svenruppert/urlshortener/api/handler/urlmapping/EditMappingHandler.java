package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;


public class EditMappingHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore urlMappingStore;

  public EditMappingHandler(UrlMappingStore urlMappingStore) {
    this.urlMappingStore = urlMappingStore;
  }

  private static String readBody(InputStream in)
      throws IOException {
    if (in == null) return "";
    byte[] buf = in.readAllBytes();
    return new String(buf, StandardCharsets.UTF_8);
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    logger().info("EditMappingHandler invoked..");
    if (!RequestMethodUtils.requirePut(ex)) return;


    try {
      final String body = readBody(ex.getRequestBody());
      ShortenRequest req = fromJson(body, ShortenRequest.class);
      String shortCode = req.getShortURL();
      if (shortCode == null || shortCode.isBlank()) {
        ErrorResponses.badRequest(ex, "Missing shortCode in path");
        return;
      }

      var result = UrlValidator.validate(req.getUrl());
      if (!result.valid()) {
        ErrorResponses.badRequest(ex, result.message());
        return;
      } else {
        logger().info("valid URL {}", req.getUrl());
      }

      Optional<ShortUrlMapping> currentOpt = urlMappingStore.findByShortCode(shortCode);
      if (currentOpt.isEmpty()) {
        ErrorResponses.notFound(ex, "shortCode not found");
        return;
      }

      final Instant newExpires = req.getExpiresAt();
      final Boolean active = req.getActive();
      final Result<ShortUrlMapping> updated = urlMappingStore.editMapping(shortCode, req.getUrl(), newExpires, active);

      if (updated.isAbsent()) {
        updated.ifFailed(msg -> {
          logger().info("failed: {}", msg);
          try {
            ErrorResponses.notFound(ex, msg);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        return;
      }
      SuccessResponses.noContent(ex);
    } catch (Exception e) {
      logger().error("EditMapping failed", e);
      ErrorResponses.internalServerError(ex, "internal error");

    }
  }
}

package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import com.svenruppert.urlshortener.api.store.eclipsestore.EclipseStoreUrlMappingStore;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.svenruppert.dependencies.core.net.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;

public final class StoreInfoHandler
    implements HttpHandler, HasLogger {


  private final UrlMappingStore store;
  private final long startedAtMs;

  public StoreInfoHandler(UrlMappingStore store, long startedAtMs) {
    this.store = Objects.requireNonNull(store);
    this.startedAtMs = startedAtMs;
  }

  @Override
  public void handle(HttpExchange ex) {
    try {
      String mode = (store instanceof EclipseStoreUrlMappingStore) ? "EclipseStore" : "InMemory";
      logger().info("StoreInfoHandler - mode {}", mode);
      int mappings = store.countAll();
      logger().info("amount of mappings in db {}", mappings);

      var info = new StoreInfo(mode, mappings, startedAtMs);
      var responseJson = toJson(info);
      logger().info("responseJson {}", responseJson);
      byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);

      ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
      ex.sendResponseHeaders(OK.code(), body.length);
      ex.getResponseBody().write(body);
    } catch (Exception e) {
      byte[] body = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
      try {
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(INTERNAL_SERVER_ERROR.code(), body.length);
        ex.getResponseBody().write(body);
      } catch (Exception ignored) {
      }
    } finally {
      try {
        ex.close();
      } catch (Exception ignored) {
      }
    }
  }
}


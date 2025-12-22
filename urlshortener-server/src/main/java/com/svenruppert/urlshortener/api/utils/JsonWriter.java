package com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.svenruppert.dependencies.core.net.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.JSON_CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

public class JsonWriter
    implements HasLogger {

  private JsonWriter() {
  }

  public static void writeJson(HttpExchange ex, HttpStatus httpStatus)
      throws IOException {
    writeJson(ex, httpStatus, httpStatus.reason());
  }

  public static void writeJson(HttpExchange ex, HttpStatus httpStatus, String message)
      throws IOException {
    HasLogger.staticLogger().info("writeJson {}, {}", httpStatus, message);
    byte[] data = message.getBytes(UTF_8);
    Headers h = ex.getResponseHeaders();
    h.set(CONTENT_TYPE, JSON_CONTENT_TYPE);
    ex.sendResponseHeaders(httpStatus.code(), data.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(data);
    } catch (Exception e) {
      HasLogger.staticLogger().info("writeJson {} (catch)", e.getMessage());
      byte[] body = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
      try {
        ex.getResponseHeaders().set(CONTENT_TYPE, JSON_CONTENT_TYPE);
        ex.sendResponseHeaders(INTERNAL_SERVER_ERROR.code(), body.length);
        ex.getResponseBody().write(body);
      } catch (Exception ignoredI) {
        HasLogger.staticLogger().info("writeJson (catch - ignored I) {} ", ignoredI.getMessage());
      }
    } finally {
      try {
        ex.close();
      } catch (Exception ignoredII) {
        HasLogger.staticLogger().info("writeJson (catch - ignored II) {} ", ignoredII.getMessage());
      }
    }
  }
}

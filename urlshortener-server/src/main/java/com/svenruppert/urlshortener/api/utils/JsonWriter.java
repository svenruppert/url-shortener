package com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.core.JacksonJson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class JsonWriter
    implements HasLogger {

  private JsonWriter() {
  }

  public static void writeJson(HttpExchange ex, HttpStatus status, Object body)
      throws IOException {
    if (body instanceof String stringBody && isJsonObjectOrArray(stringBody)) {
      writeJsonRaw(ex, status, stringBody);
      return;
    }

    String json = JacksonJson.mapper().writeValueAsString(body);
    writeJsonRaw(ex, status, json);
  }


  public static void writeJson(HttpExchange ex, int statusCode, Object body)
      throws IOException {
    writeJson(ex, HttpStatus.fromCode(statusCode), body);
  }

  private static boolean isJsonObjectOrArray(String value) {
    if (value == null) {
      return false;
    }
    String trimmed = value.trim();
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
      return false;
    }
    try {
      JacksonJson.mapper().readTree(trimmed);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  public static void writeJsonRaw(HttpExchange ex, int statusCode, String rawJson)
      throws IOException {
    writeJsonRaw(ex, HttpStatus.fromCode(statusCode), rawJson);
  }

  public static void writeJsonRaw(HttpExchange ex, HttpStatus status, String rawJson)
      throws IOException {
    if (rawJson == null) rawJson = "null";
    byte[] bytes = rawJson.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    ex.sendResponseHeaders(status.code(), bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }
}

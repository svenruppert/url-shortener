package com.svenruppert.urlshortener.core;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.svenruppert.dependencies.core.logger.HasLogger.staticLogger;
import static java.util.stream.Collectors.joining;

public final class JsonUtils
    implements HasLogger {

  private JsonUtils() { }

  public static Map<String, String> parseJson(InputStream input)
      throws IOException {
    String json = readInputStream(input).trim();
    return parseJson(json);
  }

  @NotNull
  public static Map<String, String> parseJson(String json)
      throws IOException {
    if (!json.startsWith("{") || !json.endsWith("}")) {
      throw new IOException("Invalid JSON object");
    }

    Map<String, String> result = new HashMap<>();
    // Entferne geschweifte Klammern
    String body = json.substring(1, json.length() - 1).trim();

    if (body.isEmpty()) {
      return Collections.emptyMap();
    }

    if (!body.contains(":")) {
      throw new IOException("Invalid JSON object");
    }

    // Trenne key-value-Paare anhand von Kommas
    String[] entries = body.split(",");
    Arrays.stream(entries)
        .map(entry -> entry.split(":", 2))
        .filter(parts -> parts.length == 2)
        .forEachOrdered(parts -> {
          String key = unquote(parts[0].trim());
          String value = unquote(parts[1].trim());
          result.put(key, value);
        });

    return result;
  }

  public static String toJson(Map<String, String> map) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");

    boolean first = true;
    for (Map.Entry<String, String> entry : map.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("\"").append(escape(entry.getKey())).append("\":");
      sb.append("\"").append(escape(entry.getValue())).append("\"");
      first = false;
    }

    sb.append("}");
    return sb.toString();
  }

  public static String extractShortCode(String json)
      throws IOException {
    staticLogger().info("Extracting shortCode from JSON: {}", json);
    var attributeMap = JsonUtils.parseJson(json);
    if (attributeMap.containsKey("shortCode")) {
      var shortCode = attributeMap.get("shortCode");
      staticLogger().info("Extracted shortCode: ->|{}|<-", shortCode);
      return shortCode;
    } else throw new IOException("Invalid JSON response - no shortCode available : " + json);
  }

  private static String readInputStream(InputStream input)
      throws IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8))) {
      return reader.lines().collect(joining());
    }
  }

  private static String unquote(String s) {
    if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private static String escape(String s) {
    // einfache Escape-Logik für Anführungszeichen
    return s.replace("\"", "\\\"");
  }

  /**
   * Serialisiert eine Liste flacher Objekte (String->String) als JSON-Array.
   */
  public static String toJsonArrayOfObjects(List<Map<String, String>> items) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean firstItem = true;
    for (Map<String, String> item : items) {
      if (!firstItem) sb.append(",");
      sb.append("{");
      boolean firstField = true;
      for (Map.Entry<String, String> e : item.entrySet()) {
        if (!firstField) sb.append(",");
        sb.append("\"").append(escape(e.getKey())).append("\":");
        String v = e.getValue();
        if (v == null) {
          sb.append("null");
        } else {
          sb.append("\"").append(escape(v)).append("\"");
        }
        firstField = false;
      }
      sb.append("}");
      firstItem = false;
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Baut das Antwortobjekt für die List-Endpunkte.
   */
  public static String toJsonListing(String mode, int count, List<Map<String, String>> items) {
    StringBuilder sb = new StringBuilder(64 + items.size() * 64);
    sb.append("{");
    sb.append("\"mode\":\"").append(escape(mode)).append("\",");
    sb.append("\"count\":").append(count).append(",");
    sb.append("\"items\":").append(toJsonArrayOfObjects(items));
    sb.append("}");
    return sb.toString();
  }
}
package com.svenruppert.urlshortener.core;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.svenruppert.dependencies.core.logger.HasLogger.staticLogger;
import static java.util.stream.Collectors.joining;

public final class JsonUtils
    implements HasLogger {

  private static final Pattern FIELD_PATTERN =
      Pattern.compile("\"(url|alias)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|null)", Pattern.DOTALL);

  private JsonUtils() { }

  public static Map<String, String> parseJson(InputStream input)
      throws IOException {
    String json = readInputStream(input).trim();
    return parseJson(json);
  }

  private static ShortenRequest parseShortenRequest(String json) {
    String trimmed = json.trim();
    if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
      throw new IllegalArgumentException("Invalid JSON object");
    }

    String url = null;
    String alias = null;

    Matcher m = FIELD_PATTERN.matcher(trimmed);
    while (m.find()) {
      String key = m.group(1);
      String raw = m.group(2);
      String val = "null".equals(raw) ? null : unescape(unquote(raw));
      if ("url".equals(key)) url = val;
      else if ("alias".equals(key)) alias = val;
    }

    if (url == null || url.isEmpty()) {
      throw new IllegalArgumentException("Field 'url' missing or empty");
    }

    return new ShortenRequest(url, alias);
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

  @NotNull
  public static Stream<Map.Entry<String, String>> parseJsonToStream(String json)
      throws IOException {
    return parseJson(json).entrySet().stream();
  }


  /**
   * Minimal JSON parser for ShortenRequest.
   */
  public static ShortenRequest fromJson(String json, Class<ShortenRequest> type) {
    return parseShortenRequest(json);
  }

  public static String toJson(String key, String value) {
    return "{" +
        "\"" + escape(key) + "\":" +
        "\"" + escape(value) + "\"" +
        "}";
  }

//TODO - in ein DTO verpacken?
  public static String toJson(String httpCode, String message, String appCode) {
    return "{\"code\":\"" + httpCode + "\",\"appCode\":\"" + appCode + "\",\"message\":\"" + escape(message) + "\"}";
  }

  /**
   * Serializes a Map<String, ?> to JSON.
   */
  public static String toJson(Map<String, ?> map) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    boolean first = true;
    for (var e : map.entrySet()) {
      if (!first) sb.append(',');
      first = false;
      sb.append('"').append(escape(e.getKey())).append('"').append(':');
      Object v = e.getValue();
      if (v == null) {
        sb.append("null");
      } else if (v instanceof Number || v instanceof Boolean) {
        sb.append(v);
      } else {
        sb.append('"').append(escape(v.toString())).append('"');
      }
    }
    sb.append('}');
    return sb.toString();
  }

  /**
   * Serializes simple DTOs, e.g. ShortenRequest → JSON.
   */
  public static String toJson(Object dto) {
    if (dto instanceof ShortenRequest req) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("url", req.getUrl());
      m.put("alias", req.getShortURL());
      return toJson(m);
    }
    if (dto instanceof ShortenResponse res) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("shortCode", res.shortCode());
      m.put("originalUrl", res.originalUrl());
      return toJson(m);
    }
    if (dto instanceof ShortUrlMapping map) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("shortCode", map.shortCode());
      m.put("originalUrl", map.originalUrl());
      if (map.shortCode() != null && !map.shortCode().isBlank()) {
        m.put("alias", map.shortCode());
      }
      if (map.createdAt() != null) {
        m.put("createdAt", map.createdAt().toString());
      }
      return toJson(m);
    }

    throw new UnsupportedOperationException("Unsupported DTO: " + dto.getClass());
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

  @NotNull
  public static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20 || c > 0x7E) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  public static String unescape(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(++i);
        switch (n) {
          case '"':
            out.append('"');
            break;
          case '\\':
            out.append('\\');
            break;
          case '/':
            out.append('/');
            break;
          case 'b':
            out.append('\b');
            break;
          case 'f':
            out.append('\f');
            break;
          case 'n':
            out.append('\n');
            break;
          case 'r':
            out.append('\r');
            break;
          case 't':
            out.append('\t');
            break;
          case 'u':
            if (i + 4 < s.length()) {
              String hex = s.substring(i + 1, i + 5);
              try {
                out.append((char) Integer.parseInt(hex, 16));
              } catch (NumberFormatException ignored) {
              }
              i += 4;
            }
            break;
          default:
            out.append(n);
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Serializes a list of flat objects (String->String) as a JSON array.
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
   * Builds the response object for the list endpoints.
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
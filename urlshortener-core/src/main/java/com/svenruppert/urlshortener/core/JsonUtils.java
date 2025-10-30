package com.svenruppert.urlshortener.core;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static com.svenruppert.dependencies.core.logger.HasLogger.staticLogger;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static java.util.stream.Collectors.joining;

public final class JsonUtils
    implements HasLogger {

  private JsonUtils() {
  }

  /* ======================
           READ
     ====================== */

  public static Map<String, String> parseJson(InputStream in)
      throws IOException {
    return parseFlatObject(readInputStream(in));
  }

  @NotNull
  public static Map<String, String> parseJson(String json)
      throws IOException {
    return parseFlatObject(json);
  }

  @NotNull
  public static Stream<Map.Entry<String, String>> parseJsonToStream(String json)
      throws IOException {
    return parseFlatObject(json).entrySet().stream();
  }

  @SuppressWarnings("unchecked")
  public static <T> T fromJson(String json, Class<T> type) {
    if (json == null || json.isBlank()) throw new IllegalArgumentException("JSON input is null or blank");
    Map<String, String> m;
    try {
      m = parseFlatObject(json);
      HasLogger.staticLogger().info("fromJson {}", m);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
    }

    /* ---- StoreInfo ---- */
    if (type == StoreInfo.class) {
      var storeInfoJson = m.get(OK.code());

      String mode = m.getOrDefault("mode", "InMemory");
      int mappings = parseIntSafe(m.get("mappings"), 0);
      long startedAt = parseLongSafe(m.get("startedAtEpochMs"), 0L);
      return (T) new StoreInfo(mode, mappings, startedAt);
    }

    /* ---- ShortenRequest ---- */
    if (type == ShortenRequest.class) {
      String url = emptyToNull(m.get("url"));
      String alias = emptyToNull(m.get("alias"));
      if (url == null || url.isEmpty()) {
        throw new IllegalArgumentException("Field 'url' missing or empty");
      }
      return (T) new ShortenRequest(url, alias);
    }

    /* ---- ShortenResponse ---- */
    if (type == ShortenResponse.class) {
      String shortCode = m.get("shortCode");
      String originalUrl = m.get("originalUrl");
      return (T) new ShortenResponse(shortCode, originalUrl);
    }

    /* ---- ShortUrlMapping ---- */
    if (type == ShortUrlMapping.class) {
      String code = m.get("shortCode");
      String url = m.get("originalUrl");
      Instant createdAt = parseInstantSafe(m.get("createdAt"));
      Instant expiresAtI = parseInstantSafe(m.get("expiresAt"));
      return (T) new ShortUrlMapping(code, url, createdAt,
                                     Optional.ofNullable(expiresAtI));
    }

    throw new UnsupportedOperationException("Unsupported type for fromJson: " + type.getName());
  }

  /* ======================
           WRITE
     ====================== */

  public static String toJson(String key, String value) {
    return "{\"" + escape(key) + "\":\"" + escape(value) + "\"}";
  }

  //TODO in ein DTO verpacken?
  public static String toJson(String httpCode, String message, String appCode) {
    return "{\"code\":\"" + escape(httpCode) + "\",\"appCode\":\"" + escape(appCode)
        + "\",\"message\":\"" + escape(message) + "\"}";
  }

  /**
   * Serializes a Map<String, ?> to JSON (flach).
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
    if (dto instanceof ShortenResponse(String shortCode, String originalUrl)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("shortCode", shortCode);
      m.put("originalUrl", originalUrl);
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
      if (map.expiresAt().orElse(null) != null) {
        m.put("expiresAt", map.expiresAt().get().toString());
      }
      return toJson(m);
    }
    if (dto instanceof StoreInfo(String mode, int mappings, long startedAtEpochMs)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("mode", mode);
      m.put("mappings", mappings);
      m.put("startedAtEpochMs", startedAtEpochMs);
      return toJson(m);
    }
    throw new UnsupportedOperationException("Unsupported DTO: " + dto.getClass());
  }

  public static String toJsonArrayOfObjects(List<Map<String, String>> items) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean firstItem = true;
    for (Map<String, String> item : items) {
      if (!firstItem) sb.append(",");
      sb.append(toJson(item));
      firstItem = false;
    }
    sb.append("]");
    return sb.toString();
  }

  public static String toJsonListing(String mode, int count, List<Map<String, String>> items) {
    StringBuilder sb = new StringBuilder(64 + items.size() * 64);
    sb.append("{");
    sb.append("\"mode\":\"").append(escape(mode)).append("\",");
    sb.append("\"count\":").append(count).append(",");
    sb.append("\"items\":").append(toJsonArrayOfObjects(items));
    sb.append("}");
    return sb.toString();
  }

  public static String toJsonListingPaged(
      String mode,
      int countOnPage,
      List<Map<String, String>> items,
      int page, int size, int total,
      Object sort, Object dir
  ) {
    StringBuilder sb = new StringBuilder(128 + items.size() * 64);
    sb.append("{");
    sb.append("\"mode\":\"").append(escape(mode)).append("\",");
    sb.append("\"page\":").append(page).append(",");
    sb.append("\"size\":").append(size).append(",");
    sb.append("\"total\":").append(total).append(",");
    if (sort != null) sb.append("\"sort\":\"").append(escape(String.valueOf(sort))).append("\",");
    if (dir != null) sb.append("\"dir\":\"").append(escape(String.valueOf(dir))).append("\",");
    sb.append("\"count\":").append(countOnPage).append(",");
    sb.append("\"items\":").append(toJsonArrayOfObjects(items));
    sb.append("}");
    return sb.toString();
  }

  /* ======================
        SMALL HELPERS
     ====================== */

  public static String extractShortCode(String json)
      throws IOException {
    staticLogger().info("Extracting shortCode from JSON: {}", json);
    var attributeMap = parseFlatObject(json);
    var sc = attributeMap.get("shortCode");
    if (sc == null) throw new IOException("Invalid JSON response - no shortCode available : " + json);
    staticLogger().info("Extracted shortCode: ->|{}|<-", sc);
    return sc;
  }

  private static String readInputStream(InputStream input)
      throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      return reader.lines().collect(joining());
    }
  }

  private static int parseIntSafe(String s, int def) {
    try {
      return Integer.parseInt(s);
    } catch (Exception e) {
      return def;
    }
  }

  private static long parseLongSafe(String s, long def) {
    try {
      return Long.parseLong(s);
    } catch (Exception e) {
      return def;
    }
  }

  private static Instant parseInstantSafe(String s) {
    try {
      return (s == null || s.isBlank()) ? null : Instant.parse(s);
    } catch (Exception e) {
      return null;
    }
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
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
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          case '/' -> out.append('/');
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> {
            if (i + 4 < s.length()) {
              String hex = s.substring(i + 1, i + 5);
              try {
                out.append((char) Integer.parseInt(hex, 16));
              } catch (NumberFormatException ignored) {
              }
              i += 4;
            }
          }
          default -> out.append(n);
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Minimal parser for flat JSON objects:
   * - Strings, numbers, true/false, null
   * - No arrays/nesting
   * - Respects quotes and escapes → correctly splits at top-level commas.
   */
  private static Map<String, String> parseFlatObject(String json)
      throws IOException {
    if (json == null) throw new IOException("JSON is null");
    String s = json.trim();
    if (!(s.startsWith("{") && s.endsWith("}")))
      throw new IOException("Invalid JSON object");
    s = s.substring(1, s.length() - 1); // Inhalt ohne { }

    Map<String, String> map = new LinkedHashMap<>();
    var entries = getEntries(s);
    if (entries.isEmpty()) return Collections.emptyMap();

    for (String e : entries) {
      if (e.isEmpty()) continue;
      int colon = findTopLevelColon(e);
      if (colon < 0) throw new IOException("Invalid entry: " + e);
      String rawKey = e.substring(0, colon).trim();
      String rawVal = e.substring(colon + 1).trim();
      String key = stripQuotes(rawKey);
      String val = parseValueToString(rawVal);
      map.put(key, val);
    }
    return map;
  }

  @NotNull
  private static List<String> getEntries(String s) {
    StringBuilder token = new StringBuilder();
    boolean inString = false;
    boolean escape = false;
    List<String> entries = new ArrayList<>();

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inString) {
        token.append(c);
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '"') {
          inString = false;
        }
      } else {
        if (c == '"') {
          inString = true;
          token.append(c);
        } else if (c == ',') {
          entries.add(token.toString().trim());
          token.setLength(0);
        } else {
          token.append(c);
        }
      }
    }
    if (!token.isEmpty()) entries.add(token.toString().trim());
    return entries;
  }

  private static int findTopLevelColon(String s) {
    boolean inString = false, escape = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inString) {
        if (escape) escape = false;
        else if (c == '\\') escape = true;
        else if (c == '"') inString = false;
      } else {
        if (c == '"') inString = true;
        else if (c == ':') return i;
      }
    }
    return -1;
  }

  private static String stripQuotes(String s) {
    String t = s.trim();
    if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
      return unescape(t.substring(1, t.length() - 1));
    }
    return t;
  }

  private static String parseValueToString(String raw) {
    String t = raw.trim();
    if (t.equals("null")) return null;
    if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
      return unescape(t.substring(1, t.length() - 1));
    }
    // number / boolean → unverändert als String
    return t;
  }
}

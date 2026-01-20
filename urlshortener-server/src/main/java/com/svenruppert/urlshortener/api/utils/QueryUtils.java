package com.svenruppert.urlshortener.api.utils;

import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;

import java.net.URLDecoder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class QueryUtils {

  private QueryUtils() { }

  public static int parseIntOrDefault(String s, int def) {
    try {
      return (s == null || s.isBlank()) ? def : Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  public static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  public static Optional<UrlMappingFilter.SortBy> parseSort(String s) {
    if (s == null) return Optional.empty();
    return switch (s.toLowerCase(Locale.ROOT)) {
      case "createdat" -> Optional.of(UrlMappingFilter.SortBy.CREATED_AT);
      case "shortcode" -> Optional.of(UrlMappingFilter.SortBy.SHORT_CODE);
      case "originalurl" -> Optional.of(UrlMappingFilter.SortBy.ORIGINAL_URL);
      case "expiresat" -> Optional.of(UrlMappingFilter.SortBy.EXPIRES_AT);
      default -> Optional.empty();
    };
  }

  public static Optional<UrlMappingFilter.Direction> parseDir(String s) {
    if (s == null) return Optional.empty();
    return switch (s.toLowerCase(Locale.ROOT)) {
      case "asc" -> Optional.of(UrlMappingFilter.Direction.ASC);
      case "desc" -> Optional.of(UrlMappingFilter.Direction.DESC);
      default -> Optional.empty();
    };
  }

  public static String first(Map<String, List<String>> m, String k) {
    var list = m.get(k);
    return (list == null || list.isEmpty()) ? null : list.getFirst();
  }

  public static String urlDecode(String s) {
    return URLDecoder.decode(s, UTF_8);
  }

  public static  Map<String, List<String>> parseQueryParams(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) return Collections.emptyMap();
    Map<String, List<String>> map = new LinkedHashMap<>();
    for (String pair : rawQuery.split("&")) {
      if (pair.isBlank()) continue;
      String[] kv = pair.split("=", 2);
      String k = urlDecode(kv[0]);
      String v = kv.length > 1 ? urlDecode(kv[1]) : "";
      map.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
    }
    return map;
  }
//  private static Map<String, List<String>> parseQuery(String raw) {
//    Map<String, List<String>> m = new LinkedHashMap<>();
//    if (raw == null || raw.isBlank()) return m;
//    for (String pair : raw.split("&")) {
//      if (pair.isBlank()) continue;
//      int idx = pair.indexOf('=');
//      String k = idx >= 0 ? pair.substring(0, idx) : pair;
//      String v = idx >= 0 ? pair.substring(idx + 1) : "";
//      k = urlDecode(k);
//      v = urlDecode(v);
//      m.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
//    }
//    return m;
//  }

  public static Optional<Boolean> parseBoolean(String raw) {
    try {
      return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(Boolean.valueOf(raw));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static Optional<Instant> parseInstant(String raw, boolean startOfDayIfDate) {
    if (raw == null || raw.isBlank()) return Optional.empty();

    if (raw.matches("\\d{10,}")) {
      try {
        long epochMillis = Long.parseLong(raw);
        return Optional.of(Instant.ofEpochMilli(epochMillis));
      } catch (NumberFormatException ignore) {
      }
    }

    try {
      return Optional.of(Instant.parse(raw));
    } catch (DateTimeParseException ignore) {
    }

    try {
      LocalDate d = LocalDate.parse(raw);
      if (startOfDayIfDate) {
        return Optional.of(d.atStartOfDay(ZoneOffset.UTC).toInstant());
      } else {
        return Optional.of(d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1));
      }
    } catch (DateTimeParseException ignore) {
    }

    return Optional.empty();
  }
}

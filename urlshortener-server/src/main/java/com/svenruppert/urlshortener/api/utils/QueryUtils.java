package com.svenruppert.urlshortener.api.utils;

import com.svenruppert.urlshortener.api.store.UrlMappingFilter;

import java.util.Locale;
import java.util.Optional;

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
}

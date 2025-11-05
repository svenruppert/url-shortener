package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.api.utils.QueryUtils.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static com.svenruppert.urlshortener.core.JsonUtils.toJsonListingPaged;

public class ListHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;

  public ListHandler(UrlMappingLookup store) {
    this.store = store;
  }

  private static String first(Map<String, List<String>> q, String key) {
    var v = q.get(key);
    return (v == null || v.isEmpty()) ? null : v.getFirst();
  }

  private static Optional<Instant> parseInstant(String raw, boolean startOfDayIfDate) {
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

  private static String urlDecode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return s;
    }
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
//    if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
//      ex.sendResponseHeaders(405, -1);
//      return;
//    }
    final String path = ex.getRequestURI().getPath();
    logger().info("List request: {}", path);

    String responseJson;
    if (path.endsWith(PATH_ADMIN_LIST_ALL)) {
      responseJson = listAll();
    } else if (path.endsWith(PATH_ADMIN_LIST_EXPIRED)) {
      responseJson = listExpired();
    } else if (path.endsWith(PATH_ADMIN_LIST_ACTIVE)) {
      responseJson = listActive();
    } else if (path.endsWith(PATH_ADMIN_LIST)) {
      responseJson = listFiltered(ex);
    } else {
      logger().info("undefined path {}", path);
      ex.sendResponseHeaders(404, -1);
      return;
    }
    writeJson(ex, OK, responseJson);
  }

  private String listAll() {
    return filterAndBuild("all", _ -> true);
  }

  private String listExpired() {
    final Instant now = Instant.now();
    return filterAndBuild("expired", m -> m.expiresAt().isPresent() && m.expiresAt().get().isBefore(now));
  }

  private String listActive() {
    final Instant now = Instant.now();
    return filterAndBuild("active", m -> m.expiresAt().map(exp -> !exp.isBefore(now)).orElse(true));
  }

  private String listFiltered(HttpExchange exchange) {
    var query = parseQueryParams(Optional.ofNullable(exchange.getRequestURI().getRawQuery()).orElse(""));

    int page = parseIntOrDefault(first(query, "page"), 1);
    int size = clamp(parseIntOrDefault(first(query, "size"), 50), 1, 500);
    int offset = Math.max(0, (page - 1) * size);

    var sortBy = parseSort(first(query, "sort"));      // mapping String -> UrlMappingFilter.SortBy
    var dir = parseDir(first(query, "dir"));        // mapping String -> UrlMappingFilter.Direction

    boolean codeCase = Boolean.parseBoolean(Optional.ofNullable(first(query, "codeCase")).orElse("false"));
    boolean urlCase = Boolean.parseBoolean(Optional.ofNullable(first(query, "urlCase")).orElse("false"));

    var filter = UrlMappingFilter.builder()
        .codePart(first(query, "code"))
        .codeCaseSensitive(codeCase)
        .urlPart(first(query, "url"))
        .urlCaseSensitive(urlCase)
        .createdFrom(parseInstant(first(query, "from"), true).orElse(null))
        .createdTo(parseInstant(first(query, "to"), false).orElse(null))
        .offset(offset)
        .limit(size)
        .sortBy(sortBy.orElse(null))
        .direction(dir.orElse(null))
        .build();

    int total = store.count(filter);          // Gesamtanzahl der Treffer
    var now = Instant.now();
    var results = store.find(filter);         // Paged + Sorted
    var items = results.stream().map(m -> toDto(m, now)).toList();
    return toJsonListingPaged("filtered", items.size(), items, page, size, total, sortBy.orElse(null), dir.orElse(null));
  }

  private Map<String, List<String>> parseQueryParams(String rawQuery) {
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

  private String filterAndBuild(String mode, Predicate<ShortUrlMapping> predicate) {
    final Instant now = Instant.now();
    final var data = store.findAll().stream().filter(predicate).map(m -> toDto(m, now)).toList();
    return JsonUtils.toJsonListing(mode, data.size(), data);
  }

  private Map<String, String> toDto(ShortUrlMapping m, Instant now) {
    boolean expired = m.expiresAt().map(t -> t.isBefore(now)).orElse(false);
    return Map.of(
        "shortCode", m.shortCode(),
        "originalUrl", m.originalUrl(),
        "createdAt", m.createdAt().toString(),
        "expiresAt", m.expiresAt().map(Instant::toString).orElse(""),
        "status", expired ? "expired" : "active"
    );
  }
}
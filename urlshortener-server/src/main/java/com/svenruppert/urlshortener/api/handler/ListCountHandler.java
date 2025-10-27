// com.svenruppert.urlshortener.api.handler.ListCountHandler
package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.UrlMappingLookup;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class ListCountHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;

  public ListCountHandler(UrlMappingLookup store) {
    this.store = Objects.requireNonNull(store);
  }

  private static Map<String, List<String>> parseQuery(String raw) {
    Map<String, List<String>> m = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return m;
    for (String pair : raw.split("&")) {
      if (pair.isBlank()) continue;
      int idx = pair.indexOf('=');
      String k = idx >= 0 ? pair.substring(0, idx) : pair;
      String v = idx >= 0 ? pair.substring(idx + 1) : "";
      k = urlDecode(k);
      v = urlDecode(v);
      m.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
    }
    return m;
  }


  private static String first(Map<String, List<String>> m, String k) {
    var list = m.get(k);
    return (list == null || list.isEmpty()) ? null : list.get(0);
  }

  private static boolean bool(Map<String, List<String>> m, String k) {
    String v = first(m, k);
    return v != null && (v.equalsIgnoreCase("true") || v.equals("1"));
  }

  private static String urlDecode(String s) {
    return URLDecoder.decode(s, UTF_8);
  }

  private static Optional<Instant> parseInstant(String raw) {
    try {
      return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(Instant.parse(raw));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    Map<String, List<String>> q = parseQuery(
        Optional.ofNullable(exchange.getRequestURI().getRawQuery()).orElse(""));

    UrlMappingFilter filter = UrlMappingFilter.builder()
        .codePart(first(q, "code"))
        .codeCaseSensitive(bool(q, "codeCase"))
        .urlPart(first(q, "url"))
        .urlCaseSensitive(bool(q, "urlCase"))
        .createdFrom(parseInstant(first(q, "from")).orElse(null))
        .createdTo(parseInstant(first(q, "to")).orElse(null))
        // sort/page/size not relevant
        .build();

    int total = store.count(filter);
    byte[] body = ("{\"total\":" + total + "}").getBytes(UTF_8);

    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }
}

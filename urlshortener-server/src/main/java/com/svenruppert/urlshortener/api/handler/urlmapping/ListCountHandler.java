// com.svenruppert.urlshortener.api.handler.urlmapping.ListCountHandler
package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.*;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class ListCountHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;

  public ListCountHandler(UrlMappingLookup store) {
    this.store = store;
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
    return (list == null || list.isEmpty()) ? null : list.getFirst();
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
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
    //    if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
    //      ex.sendResponseHeaders(405, -1);
    //      return;
    //    }

    var rawQuery = ex.getRequestURI().getRawQuery();
    var queryString = Optional.ofNullable(rawQuery).orElse("");
    Map<String, List<String>> q = parseQuery(queryString);

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
    var data = "{\"total\":" + total + "}";

    writeJson(ex, OK, data);

  }
}

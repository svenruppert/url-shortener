package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.handler.urlmapping.exports.ZipWriter.writeZipStream;
import static com.svenruppert.urlshortener.api.utils.QueryUtils.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static com.svenruppert.urlshortener.core.JsonUtils.toJsonListingPaged;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ListHandler
    implements HttpHandler, HasLogger {

  private static final DateTimeFormatter EXPORT_TS =
      DateTimeFormatter.ofPattern(PATTERN_DATE_TIME_EXPORT)
          .withZone(ZoneOffset.UTC);

  private final UrlMappingLookup store;

  public ListHandler(UrlMappingLookup store) {
    this.store = store;
  }

  private static void writeUtf8(OutputStream out, String s)
      throws IOException {
    out.write(s.getBytes(UTF_8));
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGetOrHead(ex)) return;

    final String path = ex.getRequestURI().getPath();
    logger().info("List request: {}", path);
    if (path.endsWith(PATH_ADMIN_EXPORT)) {
      // HEAD: same headers as GET, but no body
      if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
        logger().info("HEAD Request for export");
        final Instant exportedAt = Instant.now();
        final String timestamp = EXPORT_TS.format(exportedAt);
        final String filename = EXPORT_FILE_NAME + "-" + timestamp + ".zip";
        // Align headers with ZipWriter
        var responseHeaders = ex.getResponseHeaders();
        responseHeaders.add(CONTENT_TYPE, APPLICATION_ZIP);
        responseHeaders.add(CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"");
        responseHeaders.add(EXPORT_TIMESTAMP_HEADER, exportedAt.toString());
        ex.sendResponseHeaders(OK.code(), -1);
        ex.close();
      } else {
        logger().info("GET Request for export");
        final Instant exportedAt = resolveExportInstant(ex);
        final String timestamp = EXPORT_TS.format(exportedAt);
        final String filename = EXPORT_FILE_NAME + "-" + timestamp + ".zip";
        writeZipStream(ex,
                       OK,
                       EXPORT_FILE_NAME + ".json",
                       filename,
                       (zipEntryOut) -> writeFilteredExportJsonTo(zipEntryOut, ex, exportedAt)
        );
      }
    } else {
      if (!RequestMethodUtils.requireGet(ex)) return;
      String responseJson;
      if (path.endsWith(PATH_ADMIN_LIST_ALL)) {
        responseJson = listAll();
      } else if (path.endsWith(PATH_ADMIN_LIST_EXPIRED)) {
        responseJson = listExpired();
      } else if (path.endsWith(PATH_ADMIN_LIST_ACTIVE)) {
        responseJson = listActive();
      } else if (path.endsWith(PATH_ADMIN_LIST_INACTIVE)) {
        responseJson = listInActive();
      } else if (path.endsWith(PATH_ADMIN_LIST)) {
        responseJson = listFiltered(ex);
      } else {
        logger().info("undefined path {}", path);
        ex.sendResponseHeaders(404, -1);
        return;
      }
      SuccessResponses.okJson(ex, responseJson);
    }
  }

  private Instant resolveExportInstant(HttpExchange ex) {
    var query = Optional.ofNullable(ex.getRequestURI().getRawQuery()).orElse("");
    var params = parseQueryParams(query);
    var ts = first(params, EXPORT_TIMESTAMP_PARAM);
    if (ts != null && !ts.isBlank()) {
      try {
        return Instant.parse(ts);
      } catch (Exception ignore) {
        // fall through
      }
    }
    return Instant.now();
  }


  private void writeFilteredExportJsonTo(OutputStream out, HttpExchange ex, Instant exportedAt)
      throws IOException {
    var rawQuery = Optional.ofNullable(ex.getRequestURI().getRawQuery()).orElse("");
    var query = parseQueryParams(rawQuery);

    // Batch size: reuse 'size' parameter, clamp to server constraints
    final int batchSize = clamp(parseIntOrDefault(first(query, "size"), 500), 1, 500);
    var sortBy = parseSort(first(query, "sort"));
    var dir = parseDir(first(query, "dir"));
    var baseBuilder = UrlMappingFilter.builder()
        .codePart(first(query, "code"))
        .urlPart(first(query, "url"))
        .createdFrom(parseInstant(first(query, "from"), true).orElse(null))
        .createdTo(parseInstant(first(query, "to"), false).orElse(null))
        .active(parseBoolean(first(query, "active")).orElse(null))
        .sortBy(sortBy.orElse(null))
        .direction(dir.orElse(null));

    // total for metadata (optional but helpful)
    final int total = store.count(baseBuilder.offset(0).limit(1).build());

    // --- JSON header (streaming) ---
    writeUtf8(out, "{");
    writeUtf8(out, "\"formatVersion\":\"" + EXPORT_FORMAT_VERSION + "\",");
    writeUtf8(out, "\"mode\":\"filtered\",");
    writeUtf8(out, "\"exportedAt\":\"" + exportedAt + "\",");
    writeUtf8(out, "\"total\":" + total + ",");
    writeUtf8(out, "\"items\":[");

    boolean firstItem = true;
    int offset = 0;

    // --- Page loop ---
    while (true) {
      var filter = baseBuilder.offset(offset).limit(batchSize).build();
      var page = store.find(filter);
      if (page == null || page.isEmpty()) break;

      for (var mapping : page) {
        final String jsonObj = JsonUtils.toJson(mapping);
        if (!firstItem) writeUtf8(out, ",");
        writeUtf8(out, jsonObj);
        firstItem = false;
      }
      offset += page.size();
      if (page.size() < batchSize) break;
    }
    // --- JSON footer ---
    writeUtf8(out, "]}");
  }

  private String listAll() {
    return filterAndBuild("all", _ -> true);
  }

  private String listExpired() {
    final Instant now = Instant.now();
    return filterAndBuild("expired",
                          m -> m.expiresAt()
                              .isPresent() && m.expiresAt()
                              .get()
                              .isBefore(now));
  }

  private String listActive() {
    final Instant now = Instant.now();
    return filterAndBuild("active",
                          m -> {
                            if (!m.active()) return false;
                            return m.expiresAt()
                                .map(exp -> !exp.isBefore(now))
                                .orElse(true);
                          });
  }

  private String listInActive() {
    final Instant now = Instant.now();
    return filterAndBuild("inactive",
                          m -> {
                            if (!m.active()) return true;
                            return m.expiresAt()
                                .map(exp -> exp.isBefore(now))
                                .orElse(false);
                          });
  }

  private String listFiltered(HttpExchange exchange) {
    var query = parseQueryParams(Optional.ofNullable(exchange.getRequestURI().getRawQuery()).orElse(""));

    int page = parseIntOrDefault(first(query, "page"), 1);
    int size = clamp(parseIntOrDefault(first(query, "size"), 50), 1, 500);
    int offset = Math.max(0, (page - 1) * size);

    var sortBy = parseSort(first(query, "sort"));      // mapping String -> UrlMappingFilter.SortBy
    var dir = parseDir(first(query, "dir"));        // mapping String -> UrlMappingFilter.Direction

    var filter = UrlMappingFilter.builder()
        .codePart(first(query, "code"))
        .urlPart(first(query, "url"))
        .createdFrom(parseInstant(first(query, "from"), true).orElse(null))
        .createdTo(parseInstant(first(query, "to"), false).orElse(null))
        .active(parseBoolean(first(query, "active")).orElse(null))
        .offset(offset)
        .limit(size)
        .sortBy(sortBy.orElse(null))
        .direction(dir.orElse(null))
        .build();


    int total = store.count(filter);          // Gesamtanzahl der Treffer
    var results = store.find(filter);         // Paged + Sorted
    var items = results.stream().map(this::toDto).toList();
    return toJsonListingPaged("filtered", items.size(), items, page, size, total, sortBy.orElse(null), dir.orElse(null));
  }

  private String filterAndBuild(String mode, Predicate<ShortUrlMapping> predicate) {
    final var data = store
        .findAll()
        .stream()
        .filter(predicate)
        .map(this::toDto)
        .toList();
    return JsonUtils.toJsonListing(mode, data.size(), data);
  }

  private Map<String, String> toDto(ShortUrlMapping m) {
    return Map.of(
        "shortCode", m.shortCode(),
        "originalUrl", m.originalUrl(),
        "createdAt", m.createdAt().toString(),
        "expiresAt", m.expiresAt().map(Instant::toString).orElse(""),
        "active", m.active() + ""
    );
  }
}
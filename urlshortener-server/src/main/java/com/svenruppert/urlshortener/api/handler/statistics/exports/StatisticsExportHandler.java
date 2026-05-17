package com.svenruppert.urlshortener.api.handler.statistics.exports;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsReader;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.api.handler.urlmapping.exports.ZipWriter.writeZipStream;
import static com.svenruppert.urlshortener.api.utils.QueryUtils.first;
import static com.svenruppert.urlshortener.api.utils.QueryUtils.parseQueryParams;
import static com.svenruppert.urlshortener.core.DefaultValues.*;

public class StatisticsExportHandler
    implements HttpHandler, HasLogger {

  private static final DateTimeFormatter EXPORT_TS =
      DateTimeFormatter.ofPattern(PATTERN_DATE_TIME_EXPORT)
          .withZone(ZoneOffset.UTC);

  private static final String PERMISSION_STATS_ALL = "link:stats:all";

  private final StatisticsReader reader;
  private final UrlMappingStore mappingStore;

  public StatisticsExportHandler(StatisticsReader reader, UrlMappingStore mappingStore) {
    this.reader = reader;
    this.mappingStore = mappingStore;
  }

  /** Legacy constructor without owner-filter. Admin-only behavior preserved. */
  public StatisticsExportHandler(StatisticsReader reader) {
    this(reader, null);
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGetOrHead(ex)) return;

    var query = parseQueryParams(Optional.ofNullable(ex.getRequestURI().getRawQuery()).orElse(""));

    LocalDate from;
    LocalDate to;
    try {
      from = parseDate(first(query, "from"));
      to = parseDate(first(query, "to"));
    } catch (DateTimeParseException e) {
      ErrorResponses.badRequest(ex, "invalid_date",
                                "Invalid date format. Use ISO format: yyyy-MM-dd");
      return;
    }
    if (from != null && to != null && from.isAfter(to)) {
      ErrorResponses.badRequest(ex, "invalid_range", "from must not be after to");
      return;
    }

    // null  = "no filter, export every known shortCode" (admin-only)
    // empty = "filter resulted in zero codes" (empty export)
    // populated = "exactly these codes"
    Set<String> parsedCodes = parseShortCodes(first(query, "shortCodes"));
    final Set<String> shortCodes = applyOwnerFilter(parsedCodes.isEmpty() ? null : parsedCodes);

    Instant exportedAt = resolveExportInstant(query);
    String timestamp = EXPORT_TS.format(exportedAt);
    String filename = STATISTICS_EXPORT_FILE_NAME + "-" + timestamp + ".zip";

    if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
      logger().info("HEAD statistics export — filename={}", filename);
      var headers = ex.getResponseHeaders();
      headers.add(CONTENT_TYPE, APPLICATION_ZIP);
      headers.add(CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
      headers.add(EXPORT_TIMESTAMP_HEADER, exportedAt.toString());
      ex.sendResponseHeaders(OK.code(), -1);
      ex.close();
      return;
    }

    logger().info("GET statistics export — from={} to={} shortCodes={} filename={}",
                  from, to,
                  (shortCodes == null) ? "<all>" : (shortCodes.isEmpty() ? "<none>" : shortCodes),
                  filename);

    var writer = new StatisticsExportWriter(reader);
    writeZipStream(ex,
                   OK,
                   STATISTICS_EXPORT_NDJSON_ENTRY,
                   filename,
                   out -> writer.writeNdjsonTo(out, exportedAt, from, to, shortCodes));
  }

  private Instant resolveExportInstant(java.util.Map<String, java.util.List<String>> query) {
    String ts = first(query, EXPORT_TIMESTAMP_PARAM);
    if (ts != null && !ts.isBlank()) {
      try {
        return Instant.parse(ts);
      } catch (Exception ignore) {
        // fall through to "now"
      }
    }
    return Instant.now();
  }

  private LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    return LocalDate.parse(s.trim());
  }

  private Set<String> parseShortCodes(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    Set<String> codes = new LinkedHashSet<>();
    Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(codes::add);
    return codes;
  }

  /**
   * Owner-aware refinement of the requested shortCodes:
   * <ul>
   *   <li>If no {@code mappingStore} was injected — legacy behavior, pass through.</li>
   *   <li>If the current subject has {@code link:stats:all} — pass through.</li>
   *   <li>Otherwise — replace/intersect with the subject's owned shortCodes.
   *       A user requesting a shortCode they do not own gets it silently
   *       dropped (no info leak about existence).</li>
   * </ul>
   * Contract: {@code null} means "no filter, export everything"; a populated
   * set means "exactly these codes". For non-admin callers we therefore
   * always return a {@code Set} — never {@code null} — so the writer can
   * never accidentally fall through to "all".
   */
  private Set<String> applyOwnerFilter(Set<String> requested) {
    if (mappingStore == null) return requested;
    if (CurrentSubject.hasPermission(PERMISSION_STATS_ALL)) return requested;

    String owner = CurrentSubject.username().orElse(null);
    if (owner == null) {
      // No authenticated subject in scope — return an empty set so the
      // export becomes empty rather than dumping every event in the store.
      return Set.of();
    }
    Set<String> owned = mappingStore.findAll().stream()
        .filter(m -> owner.equals(m.ownerUsername()))
        .map(ShortUrlMapping::shortCode)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    if (requested == null) {
      return owned;
    }
    Set<String> intersection = new LinkedHashSet<>(requested);
    intersection.retainAll(owned);
    return intersection;
  }
}

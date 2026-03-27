package com.svenruppert.urlshortener.api.handler.statistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsReader;
import com.svenruppert.urlshortener.api.utils.QueryUtils;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.statistics.DailyAggregate;
import com.svenruppert.urlshortener.core.statistics.StatisticsTimelineResponse;
import com.svenruppert.urlshortener.core.statistics.StatisticsTimelineResponse.DailyCount;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static com.svenruppert.dependencies.core.net.HttpStatus.fromCode;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_STATISTICS_TIMELINE;

/**
 * Handler for timeline statistics queries.
 * Returns daily counts over a date range.
 * <p>
 * Endpoint:
 * - GET /api/statistics/timeline/{shortCode}?from=2024-01-01&to=2024-01-31
 */
public class StatisticsTimelineHandler
    implements HttpHandler, HasLogger {

  private static final String ERROR_MISSING_SHORT_CODE =
      "{\"error\":\"bad_request\",\"message\":\"shortCode is required\"}";
  private static final String ERROR_MISSING_DATES =
      "{\"error\":\"bad_request\",\"message\":\"from and to parameters are required\"}";
  private static final String ERROR_INVALID_DATE =
      "{\"error\":\"bad_request\",\"message\":\"Invalid date format. Use ISO format: yyyy-MM-dd\"}";
  private static final String ERROR_INVALID_RANGE =
      "{\"error\":\"bad_request\",\"message\":\"from date must be before or equal to to date\"}";

  private final StatisticsReader statisticsReader;

  public StatisticsTimelineHandler(StatisticsReader statisticsReader) {
    this.statisticsReader = statisticsReader;
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    if (!RequestMethodUtils.requireGet(exchange)) return;

    String path = exchange.getRequestURI().getPath();
    logger().info("StatisticsTimelineHandler: GET {}", path);

    String shortCode = extractShortCode(path);
    logger().info("Shortcode {}", shortCode);
    if (shortCode == null || shortCode.isBlank()) {
      writeJson(exchange, fromCode(400), ERROR_MISSING_SHORT_CODE);
      return;
    }
    var queryParams = QueryUtils.parseQueryParams(exchange.getRequestURI().getRawQuery());
    String fromParam = QueryUtils.first(queryParams, "from");
    String toParam = QueryUtils.first(queryParams, "to");

    logger().info("QueryParams from {} , to {}", fromParam, toParam);
    if (fromParam == null || toParam == null || fromParam.isBlank() || toParam.isBlank()) {
      writeJson(exchange, fromCode(400), ERROR_MISSING_DATES);
      return;
    }

    try {
      LocalDate from = LocalDate.parse(fromParam);
      LocalDate to = LocalDate.parse(toParam);
      logger().info("LocalDate from {}", from);
      logger().info("LocalDate to {}", to);
      if (from.isAfter(to)) {
        writeJson(exchange, fromCode(400), ERROR_INVALID_RANGE);
        return;
      }

      List<DailyAggregate> aggregates = statisticsReader.getDailyAggregates(shortCode, from, to);
      logger().info("statisticsReader.getDailyAggregates {}", aggregates);
      List<DailyCount> dailyCounts = new ArrayList<>();

      for (DailyAggregate agg : aggregates) {
        dailyCounts.add(new DailyCount(agg.date(), agg.totalCount()));
      }

      StatisticsTimelineResponse response = StatisticsTimelineResponse.create(
          shortCode, from, to, dailyCounts
      );
      logger().info("Final StatisticsTimelineResponse {}", response);
      writeJson(exchange, fromCode(200), response);

    } catch (DateTimeParseException e) {
      logger().warn("Invalid date format: {}", e.getMessage());
      writeJson(exchange, fromCode(400), ERROR_INVALID_DATE);
    }
  }

  private String extractShortCode(String path) {
    if (!path.startsWith(PATH_ADMIN_STATISTICS_TIMELINE)) {
      return null;
    }
    String remainder = path.substring(PATH_ADMIN_STATISTICS_TIMELINE.length());
    if (remainder.startsWith("/")) {
      remainder = remainder.substring(1);
    }
    return remainder.isBlank() ? null : remainder;
  }
}

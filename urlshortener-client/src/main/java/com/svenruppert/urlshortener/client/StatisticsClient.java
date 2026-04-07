package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.statistics.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Client for accessing redirect statistics via the Statistics API.
 * Provides methods to query counts, hourly/daily aggregates, timelines, and configuration.
 */
public class StatisticsClient
    implements HasLogger {

  private static final int CONNECT_TIMEOUT = 3_000;
  private static final int READ_TIMEOUT = 5_000;

  private final URI serverBaseAdmin;

  /**
   * Creates a client with default admin server URL.
   */
  public StatisticsClient() {
    this(ADMIN_SERVER_URL);
  }

  /**
   * Creates a client with custom admin server URL.
   */
  public StatisticsClient(String serverBaseUrlAdmin) {
    var urlToServerAdmin = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";
    logger().info("StatisticsClient initialized with server URL: {}", urlToServerAdmin);
    this.serverBaseAdmin = URI.create(urlToServerAdmin);
  }

  // ============================================================================
  // Count Queries
  // ============================================================================

  /**
   * Gets the total redirect count for a shortCode (all time).
   *
   * @param shortCode the short code to query
   * @return the total count response
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if shortCode is null or blank
   */
  public StatisticsCountResponse getTotalCount(String shortCode)
      throws IOException {
    validateShortCode(shortCode);
    String path = PATH_ADMIN_STATISTICS_COUNT + "/" + shortCode;
    return executeGet(path, StatisticsCountResponse.class);
  }

  /**
   * Gets the redirect count for a shortCode on a specific date.
   *
   * @param shortCode the short code to query
   * @param date      the date to query
   * @return the count response for that date
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if shortCode is null or blank
   */
  public StatisticsCountResponse getCountForDate(String shortCode, LocalDate date)
      throws IOException {
    validateShortCode(shortCode);
    if (date == null) {
      throw new IllegalArgumentException("date must not be null");
    }
    String path = PATH_ADMIN_STATISTICS_COUNT + "/" + shortCode + "?date=" + date;
    return executeGet(path, StatisticsCountResponse.class);
  }

  /**
   * Gets the redirect count for a shortCode within a date range (inclusive).
   *
   * @param shortCode the short code to query
   * @param from      start date (inclusive)
   * @param to        end date (inclusive)
   * @return the count response for the date range
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if any parameter is null or blank
   */
  public StatisticsCountResponse getCountForDateRange(String shortCode, LocalDate from, LocalDate to)
      throws IOException {
    validateShortCode(shortCode);
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to must not be null");
    }
    String path = PATH_ADMIN_STATISTICS_COUNT + "/" + shortCode + "?from=" + from + "&to=" + to;
    return executeGet(path, StatisticsCountResponse.class);
  }

  // ============================================================================
  // Hourly Statistics (Hot Window only)
  // ============================================================================

  /**
   * Gets hourly statistics (24-hour breakdown) for a shortCode on a specific date.
   * Only available for dates within the hot window.
   *
   * @param shortCode the short code to query
   * @param date      the date to query
   * @return optional containing hourly statistics, empty if date is outside hot window
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if shortCode is null or blank
   */
  public Optional<HourlyStatisticsResponse> getHourlyStatistics(String shortCode, LocalDate date)
      throws IOException {
    validateShortCode(shortCode);
    if (date == null) {
      throw new IllegalArgumentException("date must not be null");
    }
    String path = PATH_ADMIN_STATISTICS_HOURLY + "/" + shortCode + "?date=" + date;
    return executeGetOptional(path, HourlyStatisticsResponse.class);
  }

  // ============================================================================
  // Daily Statistics
  // ============================================================================

  /**
   * Gets daily statistics for a shortCode on a specific date.
   *
   * @param shortCode the short code to query
   * @param date      the date to query
   * @return optional containing daily statistics, empty if no data exists
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if shortCode is null or blank
   */
  public Optional<DailyStatisticsResponse> getDailyStatistics(String shortCode, LocalDate date)
      throws IOException {
    validateShortCode(shortCode);
    if (date == null) {
      throw new IllegalArgumentException("date must not be null");
    }
    String path = PATH_ADMIN_STATISTICS_DAILY + "/" + shortCode + "?date=" + date;
    return executeGetOptional(path, DailyStatisticsResponse.class);
  }

  // ============================================================================
  // Timeline Queries
  // ============================================================================

  /**
   * Gets a timeline of daily counts for a shortCode within a date range.
   *
   * @param shortCode the short code to query
   * @param from      start date (inclusive)
   * @param to        end date (inclusive)
   * @return timeline response with daily counts
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if any parameter is null or blank
   */
  public StatisticsTimelineResponse getTimeline(String shortCode, LocalDate from, LocalDate to)
      throws IOException {
    validateShortCode(shortCode);
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to must not be null");
    }
    String path = PATH_ADMIN_STATISTICS_TIMELINE + "/" + shortCode + "?from=" + from + "&to=" + to;
    return executeGet(path, StatisticsTimelineResponse.class);
  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
   * Gets the current statistics configuration.
   *
   * @return the current configuration
   * @throws IOException if the request fails
   */
  public StatisticsConfigResponse getConfig()
      throws IOException {
    return executeGet(PATH_ADMIN_STATISTICS_CONFIG, StatisticsConfigResponse.class);
  }

  /**
   * Updates the statistics configuration.
   *
   * @param hotWindowDays             number of days in the hot window (minimum 1)
   * @param writerBatchSize           batch size for the writer thread (minimum 1)
   * @param aggregatorIntervalSeconds interval for aggregator thread (minimum 60)
   * @param statisticsEnabled         whether statistics are enabled
   * @return the updated configuration
   * @throws IOException              if the request fails
   * @throws IllegalArgumentException if parameters are invalid
   */
  public StatisticsConfigResponse updateConfig(
      int hotWindowDays,
      int writerBatchSize,
      int aggregatorIntervalSeconds,
      boolean statisticsEnabled
  )
      throws IOException {
    if (hotWindowDays < 1) {
      throw new IllegalArgumentException("hotWindowDays must be at least 1");
    }
    if (writerBatchSize < 1) {
      throw new IllegalArgumentException("writerBatchSize must be at least 1");
    }
    if (aggregatorIntervalSeconds < 60) {
      throw new IllegalArgumentException("aggregatorIntervalSeconds must be at least 60");
    }

    String jsonBody = String.format(
        "{\"hotWindowDays\":%d,\"writerBatchSize\":%d,\"aggregatorIntervalSeconds\":%d,\"statisticsEnabled\":%b}",
        hotWindowDays, writerBatchSize, aggregatorIntervalSeconds, statisticsEnabled
    );

    return executePut(PATH_ADMIN_STATISTICS_CONFIG, jsonBody, StatisticsConfigResponse.class);
  }

  /**
   * Enables or disables statistics collection.
   *
   * @param enabled true to enable, false to disable
   * @return the updated configuration
   * @throws IOException if the request fails
   */
  public StatisticsConfigResponse setStatisticsEnabled(boolean enabled)
      throws IOException {
    StatisticsConfigResponse current = getConfig();
    return updateConfig(
        current.hotWindowDays(),
        current.writerBatchSize(),
        current.aggregatorIntervalSeconds(),
        enabled
    );
  }

  // ============================================================================
  // Internal HTTP Methods
  // ============================================================================

  private void validateShortCode(String shortCode) {
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode must not be null or blank");
    }
  }

  private <T> T executeGet(String path, Class<T> responseType)
      throws IOException {
    URI uri = serverBaseAdmin.resolve(path);
    logger().info("GET {}", uri);

    HttpURLConnection con = openConnection(uri, "GET");
    try {
      int code = con.getResponseCode();
      String body = readResponse(con, code);
      logger().info("GET - responseBody {}", body);
      if (code == 200) {
        var fromJson = JsonUtils.fromJson(body, responseType);
        logger().info("GET - from JSON {}", fromJson);
        return fromJson;
      }

      throw new IOException("HTTP " + code + " for GET " + uri + ": " + body);
    } finally {
      con.disconnect();
    }
  }

  private <T> Optional<T> executeGetOptional(String path, Class<T> responseType)
      throws IOException {
    URI uri = serverBaseAdmin.resolve(path);
    logger().info("executeGetOptional - GET {}", uri);

    HttpURLConnection con = openConnection(uri, "GET");
    try {
      int code = con.getResponseCode();
      String body = readResponse(con, code);
      logger().info("executeGetOptional - body {}", body);
      if (code == 200) {
        logger().info("executeGetOptional - GET 200");
        var fromJson = JsonUtils.fromJson(body, responseType);
        logger().info("executeGetOptional - GET - from Json {}", fromJson);
        return Optional.of(fromJson);
      }
      if (code == 404) {
        logger().info("executeGetOptional - GET 404");
        return Optional.empty();
      }

      throw new IOException("HTTP " + code + " for GET " + uri + ": " + body);
    } finally {
      con.disconnect();
    }
  }

  private <T> T executePut(String path, String jsonBody, Class<T> responseType)
      throws IOException {
    URI uri = serverBaseAdmin.resolve(path);
    logger().info("PUT {} body={}", uri, jsonBody);

    HttpURLConnection con = openConnection(uri, "PUT");
    con.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);
    con.setDoOutput(true);

    try {
      byte[] payload = jsonBody.getBytes(UTF_8);
      con.setFixedLengthStreamingMode(payload.length);
      try (OutputStream os = con.getOutputStream()) {
        os.write(payload);
      }

      int code = con.getResponseCode();
      String body = readResponse(con, code);

      if (code == 200) {
        return JsonUtils.fromJson(body, responseType);
      }

      throw new IOException("HTTP " + code + " for PUT " + uri + ": " + body);
    } finally {
      con.disconnect();
    }
  }

  private HttpURLConnection openConnection(URI uri, String method)
      throws IOException {
    HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
    con.setRequestMethod(method);
    con.setConnectTimeout(CONNECT_TIMEOUT);
    con.setReadTimeout(READ_TIMEOUT);
    con.setRequestProperty("Accept", APPLICATION_JSON);
    return con;
  }

  private String readResponse(HttpURLConnection con, int code)
      throws IOException {
    InputStream is = (code >= 400) ? con.getErrorStream() : con.getInputStream();
    if (is == null) {
      return "";
    }
    try (is) {
      return new String(is.readAllBytes(), UTF_8);
    }
  }
}

package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.ShortenRequest;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UrlShortenerE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrl;
  private static HttpClient http;

  @BeforeAll
  static void startServer()
      throws Exception {
    // Server auf Port 0 starten -> Ephemeral-Port, kollisionsfrei
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    int port = server.getPort();
    baseUrl = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + port;
    http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)    // Redirects selbst prüfen
        .build();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- Helper ---------------------------------------------------------------

  private static HttpResponse<String> POST(String path, String json)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> GET(String path)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> DELETE(String path)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  // --- Tests ----------------------------------------------------------------

  @Test
  @Order(1)
  void shorten_returns201_andJson()
      throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest("https://example.com/x", "e2e-alias"));
    var res = POST("/shorten", body);
    assertEquals(201, res.statusCode());
    assertTrue(res.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("application/json"));
    assertTrue(res.body().contains("\"originalUrl\":\"https://example.com/x\""));
    assertTrue(res.body().contains("\"shortCode\":\"e2e-alias\"") || res.body().contains("\"alias\":\"e2e-alias\""));
  }

  @Test
  @Order(2)
  void redirect_returns302_with_location()
      throws Exception {
    var res = GET("/r/e2e-alias");
    assertEquals(302, res.statusCode());
    String loc = res.headers().firstValue("Location").orElse("");
    assertEquals("https://example.com/x", loc);
  }

  @Test
  @Order(3)
  void list_contains_mapping()
      throws Exception {
    var res = GET(PATH_LIST_ALL);
    assertEquals(200, res.statusCode());
    assertTrue(res.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("application/json"));
    assertTrue(res.body().contains("\"shortCode\":\"e2e-alias\""));
  }

  @Test
  @Order(4)
  void duplicate_alias_conflict_409()
      throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest("https://example.com/other", "e2e-alias"));
    var res = POST(PATH_SHORTEN, body);

    logger().info("expected response code - {} ", CONFLICT);

    assertEquals(CONFLICT.code(), res.statusCode(), "Doppelter Alias sollte Konflikt liefern");
    var responseBody = res.body();
    logger().info("responseBody - {}", responseBody);
    assertTrue(responseBody.contains("Alias already in use"));
  }

  @Test
  @Order(5)
  void invalid_json_returns500()
      throws Exception {
    var res = POST(PATH_SHORTEN, "{not-json}");

    logger().info("expected response code - {} ", INTERNAL_SERVER_ERROR);

    assertEquals(INTERNAL_SERVER_ERROR.code(), res.statusCode());
    assertTrue(res.body().contains(INTERNAL_SERVER_ERROR.reason()));
  }

  @Test
  @Order(6)
  void crlf_in_url_returns400()
      throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest("https://ex\r\nample.com", "bad"));
    var res = POST(PATH_SHORTEN, body);
    logger().info("response - {}", res.body());

    logger().info("expected response code - {} ", BAD_REQUEST);
    assertEquals(BAD_REQUEST.code(), res.statusCode());
    assertTrue(res.body().contains("Invalid characters in 'url'"));
  }

  @Test
  @Order(7)
  void delete_returns204_and_second_delete_404_or_204_idempotent()
      throws Exception {
    var res1 = DELETE(PATH_DELETE + "/e2e-alias"); // oder /mappings/e2e-alias – je nach API

    logger().info("expected response code - {} ", NO_CONTENT);
    assertEquals(NO_CONTENT.code(), res1.statusCode());

    var res2 = DELETE(PATH_DELETE + "/e2e-alias");

    logger().info("expected response code - {} ", NOT_FOUND);

    assertEquals(NOT_FOUND.code(), res2.statusCode());
  }
}

package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import junit.com.svenruppert.urlshortener.api.security.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsOwnerFilterIntegrationTest {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static String userToken;

  @BeforeAll
  static void startServer() throws Exception {
    SecurityTestSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = SecurityTestSupport.newClient();
    adminToken = SecurityTestSupport.loginAdmin(http, baseUrlAdmin);
    userToken = SecurityTestSupport.loginUser(http, baseUrlAdmin);
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    SecurityTestSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("User can read stats for own shortCode")
  void user_reads_own_stats() throws Exception {
    String alias = "stats-own-200";
    assertEquals(201, createLink(alias, "https://example.com/own", userToken));
    int status = statsCount(alias, userToken);
    assertEquals(200, status, "owner must be allowed to query own stats");
  }

  @Test
  @DisplayName("User cannot read stats for someone else's shortCode")
  void user_cannot_read_others_stats() throws Exception {
    String alias = "stats-stranger-403";
    assertEquals(201, createLink(alias, "https://example.com/stranger", adminToken));
    int status = statsCount(alias, userToken);
    assertEquals(403, status,
        "non-owner without link:stats:all must be denied at the stats endpoint");
  }

  @Test
  @DisplayName("Admin (link:stats:all) can read stats for a user-owned shortCode")
  void admin_reads_any_stats() throws Exception {
    String alias = "stats-admin-bypass";
    assertEquals(201, createLink(alias, "https://example.com/admin", userToken));
    int status = statsCount(alias, adminToken);
    assertEquals(200, status,
        "subject with link:stats:all must bypass the owner check");
  }

  @Test
  @DisplayName("Unknown shortCode is not differentiated from 403 by the guard")
  void unknown_shortcode_passes_guard() throws Exception {
    int status = statsCount("does-not-exist-xyz", userToken);
    // Reader returns total count of 0 -> 200. The guard must NOT short-circuit
    // unknown codes into 403, since that would leak existence to non-owners.
    assertEquals(200, status,
        "guard must allow unknown shortCodes through so the reader can answer");
  }

  @Test
  @DisplayName("Timeline endpoint enforces owner check")
  void timeline_enforces_owner_check() throws Exception {
    String alias = "stats-timeline-403";
    assertEquals(201, createLink(alias, "https://example.com/tl", adminToken));
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(
            baseUrlAdmin + PATH_ADMIN_STATISTICS_TIMELINE + "/" + alias
                + "?from=2020-01-01&to=2020-01-02")),
        userToken).GET().build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(403, res.statusCode());
  }

  private static int createLink(String alias, String url, String token) throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest(url, alias, null, null));
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        token).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    return res.statusCode();
  }

  private static int statsCount(String alias, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_STATISTICS_COUNT + "/" + alias)),
        token).GET().build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    return res.statusCode();
  }
}

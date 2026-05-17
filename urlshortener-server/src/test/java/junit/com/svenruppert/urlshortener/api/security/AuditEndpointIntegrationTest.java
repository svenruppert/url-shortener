package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortenerServer;
import junit.com.svenruppert.urlshortener.api.security.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Read-only audit endpoint: admins can query the in-memory audit ring
 * buffer; non-admins get 403; type/subject filters work.
 */
class AuditEndpointIntegrationTest {

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
  @DisplayName("Admin gets the recent audit events (admin logins were just recorded)")
  void admin_sees_recent_logins() throws Exception {
    HttpResponse<String> res = get(PATH_API_AUDIT, adminToken);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("LoginSucceeded"),
        "the @BeforeAll logins must be visible in the audit buffer");
    assertTrue(res.body().contains("\"events\""),
        "response must be wrapped in {events:[...]}");
  }

  @Test
  @DisplayName("Non-admin cannot read the audit log -> 403")
  void user_forbidden() throws Exception {
    HttpResponse<String> res = get(PATH_API_AUDIT, userToken);
    assertEquals(403, res.statusCode());
  }

  @Test
  @DisplayName("Filter by type returns only the requested event class")
  void filter_by_type() throws Exception {
    HttpResponse<String> res = get(PATH_API_AUDIT + "?type=LoginSucceeded", adminToken);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("LoginSucceeded"));
    assertTrue(!res.body().contains("\"type\":\"LoginFailed\""),
        "LoginFailed events must be filtered out");
  }

  @Test
  @DisplayName("Failed logins surface as LoginFailed events with the username")
  void failed_login_is_recorded() throws Exception {
    HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + "/api/login"))
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"username\":\"admin\",\"password\":\"audit-test-wrong-pw\"}"))
        .build();
    assertEquals(401, http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode());

    HttpResponse<String> res = get(PATH_API_AUDIT + "?type=LoginFailed", adminToken);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("LoginFailed"));
    assertTrue(res.body().contains("\"subject\":\"admin\""),
        "LoginFailed event must carry the attempted username as subject");
  }

  @Test
  @DisplayName("Subject filter narrows results to one user")
  void subject_filter() throws Exception {
    HttpResponse<String> res = get(PATH_API_AUDIT + "?subject=user", adminToken);
    assertEquals(200, res.statusCode());
    // every returned event's subject should be "user"
    assertTrue(res.body().contains("\"subject\":\"user\""));
    assertTrue(!res.body().contains("\"subject\":\"admin\""),
        "subject filter must exclude other subjects");
  }

  @Test
  @DisplayName("Invalid date parameter returns 400")
  void invalid_date_400() throws Exception {
    HttpResponse<String> res = get(PATH_API_AUDIT + "?from=not-a-date", adminToken);
    assertEquals(400, res.statusCode());
  }

  private static HttpResponse<String> get(String path, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path)), token).GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }
}

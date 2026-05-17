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
import java.nio.charset.StandardCharsets;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of the {@code ROLE_VIEWER} role: read-only access to own links and
 * own statistics, no mutating operations, no user management.
 */
class ViewerRoleIntegrationTest {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String viewerToken;

  @BeforeAll
  static void startServer() throws Exception {
    SecurityTestSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = SecurityTestSupport.newClient();
    viewerToken = SecurityTestSupport.login(http, baseUrlAdmin, "viewer", "viewer");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    SecurityTestSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("Viewer login carries ROLE_VIEWER and the read-only permission set")
  void viewer_login_role_and_permissions() throws Exception {
    HttpResponse<String> me = get("/api/me", viewerToken);
    assertEquals(200, me.statusCode());
    assertTrue(me.body().contains("ROLE_VIEWER"));
    assertTrue(me.body().contains("link:read:own"));
    assertTrue(me.body().contains("link:stats:own"));
    assertFalse(me.body().contains("link:create"),
        "ROLE_VIEWER must NOT carry link:create");
    assertFalse(me.body().contains("link:delete:own"),
        "ROLE_VIEWER must NOT carry link:delete:own");
    assertFalse(me.body().contains("user:read"),
        "ROLE_VIEWER must NOT carry user:read");
  }

  @Test
  @DisplayName("Viewer can list links")
  void viewer_can_list() throws Exception {
    HttpResponse<String> res = get(PATH_ADMIN_LIST, viewerToken);
    assertEquals(200, res.statusCode());
  }

  @Test
  @DisplayName("Viewer cannot create a link (link:create missing)")
  void viewer_cannot_create() throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"shortURL\":\"viewer-test\",\"url\":\"https://example.com\"}",
                StandardCharsets.UTF_8)),
        viewerToken).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(403, res.statusCode());
  }

  @Test
  @DisplayName("Viewer cannot list users (user:read missing)")
  void viewer_cannot_list_users() throws Exception {
    HttpResponse<String> res = get(PATH_API_USERS, viewerToken);
    assertEquals(403, res.statusCode());
  }

  @Test
  @DisplayName("Operations endpoint returns only read-style ops for viewer")
  void viewer_operations_filtered() throws Exception {
    HttpResponse<String> res = get("/api/operations", viewerToken);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("link.list-own"));
    assertTrue(res.body().contains("link.stats-own"));
    assertFalse(res.body().contains("link.create"));
    assertFalse(res.body().contains("link.delete-own"));
    assertFalse(res.body().contains("user.list"));
  }

  private static HttpResponse<String> get(String path, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path)), token).GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerCheckIntegrationTest {

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
  @DisplayName("User creates a link and can edit it themselves")
  void user_can_edit_own_link() throws Exception {
    String alias = "owner-own-edit";
    int created = createLink(alias, "https://example.com/own", userToken);
    assertEquals(201, created);

    int updated = editLink(alias, "https://example.com/own-updated", userToken);
    assertEquals(204, updated);
  }

  @Test
  @DisplayName("Admin (link:update:all) can edit a link owned by ROLE_USER")
  void admin_can_edit_users_link() throws Exception {
    String alias = "owner-admin-edit";
    int created = createLink(alias, "https://example.com/foo", userToken);
    assertEquals(201, created);

    int updated = editLink(alias, "https://example.com/foo-admin", adminToken);
    assertEquals(204, updated);
  }

  @Test
  @DisplayName("User cannot delete a link owned by someone else (legacy: only admin)")
  void user_cannot_delete_legacy_or_foreign_link() throws Exception {
    // Admin creates a link → owned by admin
    String alias = "owner-admin-link";
    assertEquals(201, createLink(alias, "https://example.com/admin", adminToken));

    // User (ROLE_USER) tries to delete it → 403
    int statusFromUser = deleteLink(alias, userToken);
    assertEquals(403, statusFromUser);

    // Admin can still delete it
    int statusFromAdmin = deleteLink(alias, adminToken);
    assertEquals(204, statusFromAdmin);
  }

  @Test
  @DisplayName("ROLE_USER without link:read:all only sees own links in /api/list")
  void user_list_only_contains_own_links() throws Exception {
    assertEquals(201, createLink("ownlist-user-1", "https://example.com/u1", userToken));
    assertEquals(201, createLink("ownlist-admin-1", "https://example.com/a1", adminToken));

    HttpResponse<String> userList = listAll(userToken);
    assertEquals(200, userList.statusCode());
    assertTrue(userList.body().contains("ownlist-user-1"),
        "user list must contain own short code");
    assertNotEquals(true, userList.body().contains("ownlist-admin-1"),
        "user list must NOT contain admin's short code");

    HttpResponse<String> adminList = listAll(adminToken);
    assertTrue(adminList.body().contains("ownlist-user-1"));
    assertTrue(adminList.body().contains("ownlist-admin-1"));
  }

  // ── helpers ─────────────────────────────────────────────────────

  private static int createLink(String alias, String url, String token) throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest(url, alias, null, null));
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
        .header("Content-Type", JSON_CONTENT_TYPE)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    SecurityTestSupport.authorize(b, token);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
  }

  private static int editLink(String alias, String newUrl, String token) throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest(newUrl, alias, null, null));
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_EDIT))
        .header("Content-Type", JSON_CONTENT_TYPE)
        .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    SecurityTestSupport.authorize(b, token);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
  }

  private static int deleteLink(String alias, String token) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_DELETE + "/" + alias))
        .DELETE();
    SecurityTestSupport.authorize(b, token);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
  }

  private static HttpResponse<String> listAll(String token) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_LIST_ALL)).GET();
    SecurityTestSupport.authorize(b, token);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}

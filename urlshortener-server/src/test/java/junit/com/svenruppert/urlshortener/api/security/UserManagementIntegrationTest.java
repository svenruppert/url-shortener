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

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementIntegrationTest {

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
  @DisplayName("Admin lists all users (admin + user seed)")
  void admin_lists_users() throws Exception {
    HttpResponse<String> res = get(PATH_API_USERS, adminToken);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("\"username\":\"admin\""));
    assertTrue(res.body().contains("\"username\":\"user\""));
  }

  @Test
  @DisplayName("ROLE_USER cannot list users -> 403")
  void user_cannot_list() throws Exception {
    HttpResponse<String> res = get(PATH_API_USERS, userToken);
    assertEquals(403, res.statusCode());
  }

  @Test
  @DisplayName("Admin creates and then deletes a user")
  void create_then_delete() throws Exception {
    String body = """
        {"username":"alice","password":"alice-pw-1","displayName":"Alice","role":"ROLE_USER"}
        """;
    HttpResponse<String> created = postJson(PATH_API_USERS, body, adminToken);
    assertEquals(201, created.statusCode());
    assertTrue(created.body().contains("\"username\":\"alice\""));
    assertTrue(created.body().contains("\"role\":\"ROLE_USER\""));

    HttpResponse<String> dup = postJson(PATH_API_USERS, body, adminToken);
    assertEquals(409, dup.statusCode());

    HttpResponse<String> deleted = delete(PATH_API_USERS + "/alice", adminToken);
    assertEquals(204, deleted.statusCode());
  }

  @Test
  @DisplayName("Create rejects too-short passwords")
  void create_rejects_short_password() throws Exception {
    String body = """
        {"username":"bob","password":"short","displayName":"Bob","role":"ROLE_USER"}
        """;
    HttpResponse<String> res = postJson(PATH_API_USERS, body, adminToken);
    assertEquals(400, res.statusCode());
    assertTrue(res.body().contains("password_too_short"));
  }

  @Test
  @DisplayName("Update changes role and enabled together")
  void update_role_and_enabled() throws Exception {
    String create = """
        {"username":"carol","password":"carol-pw-1","displayName":"Carol","role":"ROLE_USER"}
        """;
    assertEquals(201, postJson(PATH_API_USERS, create, adminToken).statusCode());

    String upd = """
        {"role":"ROLE_ADMIN","enabled":true,"displayName":"Carol Smith"}
        """;
    HttpResponse<String> res = putJson(PATH_API_USERS + "/carol", upd, adminToken);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("\"role\":\"ROLE_ADMIN\""));
    assertTrue(res.body().contains("Carol Smith"));

    assertEquals(204, delete(PATH_API_USERS + "/carol", adminToken).statusCode());
  }

  @Test
  @DisplayName("Self-delete is rejected with 409")
  void self_delete_rejected() throws Exception {
    HttpResponse<String> res = delete(PATH_API_USERS + "/admin", adminToken);
    assertEquals(409, res.statusCode());
    assertTrue(res.body().contains("self_delete_forbidden"));
  }

  @Test
  @DisplayName("Last admin cannot be demoted")
  void last_admin_cannot_be_demoted() throws Exception {
    String body = """
        {"role":"ROLE_USER"}
        """;
    HttpResponse<String> res = putJson(PATH_API_USERS + "/admin", body, adminToken);
    assertEquals(409, res.statusCode());
    assertTrue(res.body().contains("last_admin_protected"));
  }

  @Test
  @DisplayName("Last admin cannot be disabled")
  void last_admin_cannot_be_disabled() throws Exception {
    String body = """
        {"enabled":false}
        """;
    // Admin trying to disable themselves -> self_disable first
    HttpResponse<String> res = putJson(PATH_API_USERS + "/admin", body, adminToken);
    assertEquals(409, res.statusCode());
  }

  @Test
  @DisplayName("Admin reset password invalidates existing tokens for that user")
  void admin_reset_invalidates_tokens() throws Exception {
    String create = """
        {"username":"dave","password":"dave-pw-1","displayName":"Dave","role":"ROLE_USER"}
        """;
    assertEquals(201, postJson(PATH_API_USERS, create, adminToken).statusCode());
    String daveToken = SecurityTestSupport.login(http, baseUrlAdmin, "dave", "dave-pw-1");

    String reset = """
        {"newPassword":"dave-new-pw-2"}
        """;
    HttpResponse<String> r = postJson(PATH_API_USERS + "/dave/password", reset, adminToken);
    assertEquals(204, r.statusCode());

    // Old token is revoked -> /api/me returns 401
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + "/api/me")), daveToken).GET().build();
    HttpResponse<String> me = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(401, me.statusCode());

    // New password works for login
    String fresh = SecurityTestSupport.login(http, baseUrlAdmin, "dave", "dave-new-pw-2");
    assertNotEquals(daveToken, fresh);

    assertEquals(204, delete(PATH_API_USERS + "/dave", adminToken).statusCode());
  }

  @Test
  @DisplayName("Update with unknown role returns 400")
  void update_unknown_role() throws Exception {
    String body = """
        {"role":"ROLE_GHOST"}
        """;
    HttpResponse<String> res = putJson(PATH_API_USERS + "/user", body, adminToken);
    assertEquals(400, res.statusCode());
    assertTrue(res.body().contains("unknown_role"));
  }

  @Test
  @DisplayName("Update of unknown user returns 404")
  void update_unknown_user() throws Exception {
    String body = """
        {"displayName":"Ghost"}
        """;
    HttpResponse<String> res = putJson(PATH_API_USERS + "/no-such-user", body, adminToken);
    assertEquals(404, res.statusCode());
  }

  // ---- helpers ----

  private static HttpResponse<String> get(String path, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path)), token).GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> postJson(String path, String body, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        token).build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> putJson(String path, String body, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
            .header("Content-Type", "application/json; charset=utf-8")
            .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        token).build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> delete(String path, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path)), token).DELETE().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}

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
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_ME_PASSWORD;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfServicePasswordChangeIntegrationTest {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;

  @BeforeAll
  static void startServer() throws Exception {
    SecurityTestSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = SecurityTestSupport.newClient();
    adminToken = SecurityTestSupport.loginAdmin(http, baseUrlAdmin);
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    SecurityTestSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("Self-service change: correct old PW returns 204, old PW no longer works")
  void self_change_happy_path() throws Exception {
    String username = "self-pw-happy";
    createUser(username, "initial-pw-1");

    String token = SecurityTestSupport.login(http, baseUrlAdmin, username, "initial-pw-1");
    String body = """
        {"oldPassword":"initial-pw-1","newPassword":"updated-pw-2"}
        """;
    HttpResponse<String> r = postJson(PATH_API_ME_PASSWORD, body, token);
    assertEquals(204, r.statusCode());

    // Old token revoked
    HttpRequest probe = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + "/api/me")), token).GET().build();
    assertEquals(401, http.send(probe, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode());

    // Old PW no longer logs in
    String body2 = "{\"username\":\"" + username + "\",\"password\":\"initial-pw-1\"}";
    HttpResponse<String> reLogin = http.send(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + "/api/login"))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body2, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(401, reLogin.statusCode());

    // New PW works
    String fresh = SecurityTestSupport.login(http, baseUrlAdmin, username, "updated-pw-2");
    assertTrue(fresh != null && !fresh.isBlank());

    deleteUser(username);
  }

  @Test
  @DisplayName("Wrong old password returns 401")
  void wrong_old_password() throws Exception {
    String username = "self-pw-wrong";
    createUser(username, "initial-pw-1");
    String token = SecurityTestSupport.login(http, baseUrlAdmin, username, "initial-pw-1");

    String body = """
        {"oldPassword":"this-is-not-the-old","newPassword":"new-pw-okay-2"}
        """;
    HttpResponse<String> r = postJson(PATH_API_ME_PASSWORD, body, token);
    assertEquals(401, r.statusCode());

    deleteUser(username);
  }

  @Test
  @DisplayName("New password too short returns 400")
  void new_password_too_short() throws Exception {
    String username = "self-pw-short";
    createUser(username, "initial-pw-1");
    String token = SecurityTestSupport.login(http, baseUrlAdmin, username, "initial-pw-1");

    String body = """
        {"oldPassword":"initial-pw-1","newPassword":"short"}
        """;
    HttpResponse<String> r = postJson(PATH_API_ME_PASSWORD, body, token);
    assertEquals(400, r.statusCode());
    assertTrue(r.body().contains("password_too_short"));

    deleteUser(username);
  }

  @Test
  @DisplayName("Unauthenticated request returns 401")
  void unauthenticated() throws Exception {
    String body = """
        {"oldPassword":"x","newPassword":"y-very-long-enough"}
        """;
    HttpResponse<String> r = http.send(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_API_ME_PASSWORD))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(401, r.statusCode());
  }

  // ---- helpers ----

  private static void createUser(String username, String password) throws Exception {
    String body = "{\"username\":\"" + username + "\",\"password\":\"" + password
        + "\",\"displayName\":\"" + username + "\",\"role\":\"ROLE_USER\"}";
    HttpResponse<String> r = postJson(PATH_API_USERS, body, adminToken);
    assertEquals(201, r.statusCode(), "create user failed: " + r.body());
  }

  private static void deleteUser(String username) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_API_USERS + "/" + username)),
        adminToken).DELETE().build();
    http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> postJson(String path, String body, String token) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        token).build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}

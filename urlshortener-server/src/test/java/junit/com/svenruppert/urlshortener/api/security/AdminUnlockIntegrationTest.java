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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Admin-unlock for accounts locked out by the brute-force policy:
 * <ol>
 *   <li>Bring the account into a locked-out state with 5 failed attempts.</li>
 *   <li>Verify the next login attempt returns 429.</li>
 *   <li>Call {@code POST /api/users/{username}/unlock} as admin.</li>
 *   <li>Verify the account can log in again immediately.</li>
 * </ol>
 */
class AdminUnlockIntegrationTest {

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
  @DisplayName("Admin unlock restores login for a locked-out account")
  void admin_unlock_restores_login() throws Exception {
    String username = "lockout-victim";
    String password = "victim-pw-1";
    createUser(username, password);

    // 5 failed attempts -> threshold reached. 6th attempt returns 429.
    for (int i = 0; i < 5; i++) {
      assertEquals(401, attemptLogin(username, "wrong-pw").statusCode(),
          "attempt " + (i + 1) + " must return 401");
    }
    HttpResponse<String> lockedOut = attemptLogin(username, password);
    assertEquals(429, lockedOut.statusCode(),
        "after 5 failures the next attempt must be locked out, even with correct password");

    // Admin unlocks the account
    HttpRequest unlock = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(
            baseUrlAdmin + PATH_API_USERS + "/" + username + PATH_API_USERS_UNLOCK_SUFFIX))
            .POST(HttpRequest.BodyPublishers.noBody()),
        adminToken).build();
    HttpResponse<String> unlockRes = http.send(unlock, HttpResponse.BodyHandlers.ofString());
    assertEquals(204, unlockRes.statusCode());

    // Now login works again
    HttpResponse<String> ok = attemptLogin(username, password);
    assertEquals(200, ok.statusCode(),
        "after unlock the correct password must authenticate");
    assertTrue(ok.body().contains("token"));

    cleanup(username);
  }

  @Test
  @DisplayName("Unlock of unknown user returns 404")
  void unlock_unknown_user_404() throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(
            baseUrlAdmin + PATH_API_USERS + "/never-existed-xyz" + PATH_API_USERS_UNLOCK_SUFFIX))
            .POST(HttpRequest.BodyPublishers.noBody()),
        adminToken).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(404, res.statusCode());
  }

  @Test
  @DisplayName("Non-admin cannot unlock (user:update missing)")
  void non_admin_cannot_unlock() throws Exception {
    String username = "lockout-perm-victim";
    createUser(username, "perm-pw-1");

    String userToken = SecurityTestSupport.loginUser(http, baseUrlAdmin);
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(
            baseUrlAdmin + PATH_API_USERS + "/" + username + PATH_API_USERS_UNLOCK_SUFFIX))
            .POST(HttpRequest.BodyPublishers.noBody()),
        userToken).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(403, res.statusCode());

    cleanup(username);
  }

  // ---- helpers ----

  private static HttpResponse<String> attemptLogin(String username, String password) throws Exception {
    String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + "/api/login"))
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static void createUser(String username, String password) throws Exception {
    String body = "{\"username\":\"" + username + "\",\"password\":\"" + password
        + "\",\"displayName\":\"" + username + "\",\"role\":\"ROLE_USER\"}";
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_API_USERS))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        adminToken).build();
    int code = http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    assertEquals(201, code);
  }

  private static void cleanup(String username) throws Exception {
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_API_USERS + "/" + username))
            .DELETE(),
        adminToken).build();
    http.send(req, HttpResponse.BodyHandlers.discarding());
  }
}

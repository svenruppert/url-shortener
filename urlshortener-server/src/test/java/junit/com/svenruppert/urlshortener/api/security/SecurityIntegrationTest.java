package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.security.ShortenerSecurityModule;
import com.svenruppert.urlshortener.core.JacksonJson;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityIntegrationTest {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static String baseUrlRedirect;
  private static HttpClient http;
  private static String adminToken;
  private static String userToken;

  @BeforeAll
  static void startServer() throws Exception {
    SecurityTestSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    baseUrlRedirect = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    http = SecurityTestSupport.newClient();
    adminToken = SecurityTestSupport.loginAdmin(http, baseUrlAdmin);
    userToken = SecurityTestSupport.loginUser(http, baseUrlAdmin);
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    SecurityTestSupport.disableTestBootstrap();
  }

  // ── Authentication ───────────────────────────────────────────────

  @Test
  @DisplayName("Login with correct admin credentials returns 200 + token")
  void login_admin_returns_200_and_token() {
    String token = adminToken;
    assertNotEquals(null, token);
    assertFalse(token.isBlank());
  }

  @Test
  @DisplayName("Login with wrong password returns 401 and no token")
  void login_bad_credentials_returns_401() throws Exception {
    HttpResponse<String> res = postJson(baseUrlAdmin + ShortenerSecurityModule.PATH_API_LOGIN,
        "{\"username\":\"admin\",\"password\":\"wrong\"}");
    assertEquals(401, res.statusCode());
    assertFalse(res.body().contains("\"token\""), "401 body must not leak token");
  }

  @Test
  @DisplayName("Login with unknown user returns 401 (no user-existence leak)")
  void login_unknown_user_returns_401() throws Exception {
    HttpResponse<String> res = postJson(baseUrlAdmin + ShortenerSecurityModule.PATH_API_LOGIN,
        "{\"username\":\"nobody\",\"password\":\"whatever\"}");
    assertEquals(401, res.statusCode());
  }

  @Test
  @DisplayName("Bad request body on /api/login returns 400")
  void login_bad_request_returns_400() throws Exception {
    HttpResponse<String> res = postJson(baseUrlAdmin + ShortenerSecurityModule.PATH_API_LOGIN,
        "{\"username\":\"admin\"}");
    assertEquals(400, res.statusCode());
  }

  // ── Authorization on protected endpoints ─────────────────────────

  @Test
  @DisplayName("Protected endpoint without token returns 401")
  void protected_endpoint_without_token_returns_401() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + PATH_ADMIN_LIST, null);
    assertEquals(401, res.statusCode());
  }

  @Test
  @DisplayName("Protected endpoint with valid token returns 200")
  void protected_endpoint_with_token_returns_200() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + PATH_ADMIN_LIST, adminToken);
    assertEquals(200, res.statusCode());
  }

  @Test
  @DisplayName("ROLE_USER cannot reach admin-only endpoint (store-info) -> 403")
  void user_cannot_call_admin_only_endpoint() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + PATH_ADMIN_STORE_INFO, userToken);
    assertEquals(403, res.statusCode());
  }

  @Test
  @DisplayName("ROLE_ADMIN can reach admin-only endpoint (store-info) -> 200")
  void admin_can_call_admin_only_endpoint() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + PATH_ADMIN_STORE_INFO, adminToken);
    assertEquals(200, res.statusCode());
  }

  // ── /api/me ─────────────────────────────────────────────────────

  @Test
  @DisplayName("/api/me without token returns 401")
  void me_without_token_returns_401() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_ME, null);
    assertEquals(401, res.statusCode());
  }

  @Test
  @DisplayName("/api/me with token returns subject info")
  void me_with_token_returns_subject() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_ME, adminToken);
    assertEquals(200, res.statusCode());
    var node = JacksonJson.mapper().readTree(res.body());
    assertEquals("admin", node.get("username").asText());
    assertTrue(node.get("roles").toString().contains("ROLE_ADMIN"));
  }

  // ── /api/operations ─────────────────────────────────────────────

  @Test
  @DisplayName("ROLE_USER sees fewer operations than ROLE_ADMIN")
  void operations_filtered_by_permission() throws Exception {
    HttpResponse<String> adminOps = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_OPERATIONS, adminToken);
    HttpResponse<String> userOps = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_OPERATIONS, userToken);
    assertEquals(200, adminOps.statusCode());
    assertEquals(200, userOps.statusCode());
    int adminCount = countOps(adminOps.body());
    int userCount = countOps(userOps.body());
    assertTrue(adminCount > userCount,
        "admin (" + adminCount + ") must see more ops than user (" + userCount + ")");
    assertFalse(userOps.body().contains("admin.dashboard"),
        "ROLE_USER must not see admin.dashboard");
  }

  // ── Logout ──────────────────────────────────────────────────────

  @Test
  @DisplayName("Logout revokes the token (re-use returns 401)")
  void logout_invalidates_token() throws Exception {
    // Fresh user token so we do not invalidate the shared userToken.
    String token = SecurityTestSupport.loginUser(http, baseUrlAdmin);
    HttpResponse<String> meBefore = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_ME, token);
    assertEquals(200, meBefore.statusCode());

    HttpResponse<String> logout = post(baseUrlAdmin + ShortenerSecurityModule.PATH_API_LOGOUT, "", token);
    assertEquals(204, logout.statusCode());

    HttpResponse<String> meAfter = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_ME, token);
    assertEquals(401, meAfter.statusCode());
  }

  // ── Public redirect must remain unauthenticated ─────────────────

  @Test
  @DisplayName("Public redirect endpoint does not require authentication")
  void public_redirect_does_not_require_auth() throws Exception {
    HttpResponse<String> res = get(baseUrlRedirect + "/does-not-exist", null);
    // Without a known short code we expect a 404 but never a 401 or 403.
    assertNotEquals(401, res.statusCode(), "redirect endpoint must not gate behind auth");
    assertNotEquals(403, res.statusCode(), "redirect endpoint must not gate behind auth");
  }

  @Test
  @DisplayName("Created short code resolves through the public redirect endpoint")
  void created_link_resolves_publicly() throws Exception {
    String alias = "sec-redirect-target";
    String body = JsonUtils.toJson(new ShortenRequest("https://example.com/secure", alias, null, null));
    HttpResponse<String> created = postJson(baseUrlAdmin + PATH_ADMIN_SHORTEN, body, adminToken);
    assertEquals(201, created.statusCode(), created.body());

    HttpResponse<String> redirect = get(baseUrlRedirect + "/" + alias, null);
    assertEquals(302, redirect.statusCode());
    assertEquals("https://example.com/secure",
        redirect.headers().firstValue("Location").orElse(""));
  }

  // ── Bootstrap status endpoint must not leak the token ───────────

  @Test
  @DisplayName("/api/bootstrap/status does not leak the bootstrap token")
  void bootstrap_status_does_not_leak_token() throws Exception {
    HttpResponse<String> res = get(baseUrlAdmin + ShortenerSecurityModule.PATH_API_BOOTSTRAP_STATUS, null);
    assertEquals(200, res.statusCode());
    String body = res.body().toLowerCase();
    assertFalse(body.contains("\"token\""), "status response must not contain a token field");
    assertFalse(body.contains("bootstraptoken"), "status response must not contain bootstrap token");
    // Required flag is present and a boolean.
    var node = JacksonJson.mapper().readTree(res.body());
    assertTrue(node.has("bootstrapRequired"));
    assertTrue(node.get("bootstrapRequired").isBoolean());
  }

  // ── helpers ─────────────────────────────────────────────────────

  private static HttpResponse<String> get(String url, String token) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
    if (token != null) SecurityTestSupport.authorize(builder, token);
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> post(String url, String body, String token) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", JSON_CONTENT_TYPE)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (token != null) SecurityTestSupport.authorize(builder, token);
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static HttpResponse<String> postJson(String url, String body) throws Exception {
    return post(url, body, null);
  }

  private static HttpResponse<String> postJson(String url, String body, String token) throws Exception {
    return post(url, body, token);
  }

  private static int countOps(String json) throws Exception {
    var node = JacksonJson.mapper().readTree(json);
    var ops = node.get("operations");
    return ops == null ? 0 : ops.size();
  }
}

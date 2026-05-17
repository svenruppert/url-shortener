package junit.com.svenruppert.urlshortener.api.security.support;

import com.svenruppert.urlshortener.api.security.ShortenerSecurityModule;
import com.svenruppert.urlshortener.core.JacksonJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Test scaffolding for the secured shortener REST API.
 * <p>
 * Activates {@code BootstrapMode.DISABLED} so a default {@code admin/admin}
 * seed user is registered, then performs a real {@code POST /api/login}
 * against the started server and exposes the resulting bearer token.
 * <p>
 * Tests should call {@link #enableTestBootstrap()} before constructing
 * {@code ShortenerServer} and use {@link #loginAdmin(HttpClient, String)} to
 * obtain a bearer token they then add via {@link #authorize(HttpRequest.Builder, String)}.
 */
public final class SecurityTestSupport {

  public static final String ADMIN_USER = "admin";
  public static final String ADMIN_PASSWORD = "admin";
  public static final String USER_USER = "user";
  public static final String USER_PASSWORD = "user";

  private SecurityTestSupport() {
  }

  /** Switches the security module into test mode (seed admin/admin, no bootstrap token). */
  public static void enableTestBootstrap() {
    System.setProperty(ShortenerSecurityModule.SYSPROP_BOOTSTRAP_MODE, "DISABLED");
  }

  public static void disableTestBootstrap() {
    System.clearProperty(ShortenerSecurityModule.SYSPROP_BOOTSTRAP_MODE);
  }

  public static HttpClient newClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  /**
   * POSTs admin/admin to {@code /api/login} on the given admin base URL and
   * returns the bearer token from the response body.
   */
  public static String loginAdmin(HttpClient http, String baseUrlAdmin) throws Exception {
    return login(http, baseUrlAdmin, ADMIN_USER, ADMIN_PASSWORD);
  }

  public static String loginUser(HttpClient http, String baseUrlAdmin) throws Exception {
    return login(http, baseUrlAdmin, USER_USER, USER_PASSWORD);
  }

  public static String login(HttpClient http, String baseUrlAdmin, String user, String password) throws Exception {
    String body = "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}";
    HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + ShortenerSecurityModule.PATH_API_LOGIN))
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (res.statusCode() != 200) {
      throw new IllegalStateException("login failed: status=" + res.statusCode() + " body=" + res.body());
    }
    var node = JacksonJson.mapper().readTree(res.body());
    var token = node.get("token");
    if (token == null || !token.isTextual()) {
      throw new IllegalStateException("login response did not contain a token: " + res.body());
    }
    return token.asText();
  }

  /** Adds {@code Authorization: Bearer <token>} to the given request builder. */
  public static HttpRequest.Builder authorize(HttpRequest.Builder builder, String token) {
    return builder.header("Authorization", "Bearer " + token);
  }
}

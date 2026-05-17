package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.LoginClient;
import junit.com.svenruppert.urlshortener.client.support.ClientAuthSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginClientTest {

  private static ShortenerServer server;
  private static String adminBaseUrl;

  @BeforeAll
  static void startServer() throws IOException {
    ClientAuthSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init("localhost", 0);
    adminBaseUrl = "http://localhost:" + server.getPortAdmin();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    ClientAuthSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("login with admin/admin returns a non-empty token and the username")
  void login_admin_succeeds() throws IOException {
    LoginClient client = new LoginClient(adminBaseUrl);
    LoginClient.AuthSession session = client.login("admin", "admin");

    assertNotNull(session);
    assertNotNull(session.token());
    assertFalse(session.token().isBlank(), "token must not be blank");
    assertEquals("admin", session.username());
    assertTrue(session.roles().contains("ROLE_ADMIN"),
        "admin login must carry ROLE_ADMIN: " + session.roles());
  }

  @Test
  @DisplayName("login with wrong password throws AuthenticationException")
  void login_wrong_password_throws() {
    LoginClient client = new LoginClient(adminBaseUrl);
    LoginClient.AuthenticationException ex = assertThrows(
        LoginClient.AuthenticationException.class,
        () -> client.login("admin", "wrong"));
    assertEquals("invalid_credentials", ex.getMessage());
  }

  @Test
  @DisplayName("login with unknown user throws AuthenticationException (no user-existence leak)")
  void login_unknown_user_throws() {
    LoginClient client = new LoginClient(adminBaseUrl);
    LoginClient.AuthenticationException ex = assertThrows(
        LoginClient.AuthenticationException.class,
        () -> client.login("does-not-exist", "irrelevant"));
    assertEquals("invalid_credentials", ex.getMessage());
  }

  @Test
  @DisplayName("logout best-effort: revokes the token, subsequent reuse returns 401")
  void logout_revokes_token() throws IOException {
    LoginClient client = new LoginClient(adminBaseUrl);
    String token = client.login("admin", "admin").token();

    client.logout(token);
    // Re-using the revoked token on a protected endpoint must trigger 401.
    // We assert via the AuthFailureRegistry hook because the raw HTTP call
    // is wrapped inside the production clients.
    var triggered = new boolean[]{false};
    com.svenruppert.urlshortener.client.AuthFailureRegistry.set(() -> triggered[0] = true);
    try {
      com.svenruppert.urlshortener.client.URLShortenerClient probe =
          new com.svenruppert.urlshortener.client.URLShortenerClient(adminBaseUrl, adminBaseUrl);
      probe.setAuthToken(token);
      assertThrows(IOException.class, probe::listAllAsJson);
      assertTrue(triggered[0], "401 listener must fire after the token was revoked");
    } finally {
      com.svenruppert.urlshortener.client.AuthFailureRegistry.clear();
    }
  }
}

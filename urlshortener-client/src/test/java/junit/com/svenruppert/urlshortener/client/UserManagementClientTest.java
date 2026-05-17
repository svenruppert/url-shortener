package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.LoginClient;
import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.users.CreateUserRequest;
import com.svenruppert.urlshortener.core.users.UpdateUserRequest;
import com.svenruppert.urlshortener.core.users.UserSummary;
import junit.com.svenruppert.urlshortener.client.support.ClientAuthSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementClientTest {

  private static ShortenerServer server;
  private static String adminBaseUrl;
  private static String adminToken;

  @BeforeAll
  static void startServer() throws IOException {
    ClientAuthSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init("localhost", 0);
    adminBaseUrl = "http://localhost:" + server.getPortAdmin();
    adminToken = ClientAuthSupport.loginAdmin(adminBaseUrl);
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    ClientAuthSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("listUsers returns the seed accounts")
  void list_users() throws IOException {
    UserManagementClient client = new UserManagementClient(adminBaseUrl);
    client.setAuthToken(adminToken);

    List<UserSummary> users = client.listUsers();
    assertTrue(users.stream().anyMatch(u -> "admin".equals(u.username())));
    assertTrue(users.stream().anyMatch(u -> "user".equals(u.username())));
  }

  @Test
  @DisplayName("createUser returns a UserSummary; duplicate throws IOException")
  void create_user_and_duplicate() throws IOException {
    UserManagementClient client = new UserManagementClient(adminBaseUrl);
    client.setAuthToken(adminToken);

    UserSummary created = client.createUser(new CreateUserRequest(
        "client-test-1", "client-pw-1", "Client Test 1", "ROLE_USER"));
    assertEquals("client-test-1", created.username());
    assertEquals("ROLE_USER", created.role());
    assertTrue(created.enabled());

    IOException dup = assertThrows(IOException.class, () -> client.createUser(new CreateUserRequest(
        "client-test-1", "client-pw-1", "Dup", "ROLE_USER")));
    assertTrue(dup.getMessage().contains("409"), "expected 409 in: " + dup.getMessage());

    assertTrue(client.deleteUser("client-test-1"));
  }

  @Test
  @DisplayName("updateUser changes displayName and role")
  void update_user() throws IOException {
    UserManagementClient client = new UserManagementClient(adminBaseUrl);
    client.setAuthToken(adminToken);

    client.createUser(new CreateUserRequest("client-test-2", "client-pw-1", "C2", "ROLE_USER"));
    try {
      UserSummary updated = client.updateUser("client-test-2",
          new UpdateUserRequest("Renamed", "ROLE_ADMIN", null));
      assertEquals("Renamed", updated.displayName());
      assertEquals("ROLE_ADMIN", updated.role());
    } finally {
      client.deleteUser("client-test-2");
    }
  }

  @Test
  @DisplayName("deleteUser returns false on 404")
  void delete_unknown_returns_false() throws IOException {
    UserManagementClient client = new UserManagementClient(adminBaseUrl);
    client.setAuthToken(adminToken);
    assertFalse(client.deleteUser("never-existed-xyz"));
  }

  @Test
  @DisplayName("resetPassword sets new password and revokes existing tokens")
  void reset_password_revokes_tokens() throws IOException {
    UserManagementClient client = new UserManagementClient(adminBaseUrl);
    client.setAuthToken(adminToken);

    client.createUser(new CreateUserRequest("client-test-3", "client-pw-1", "C3", "ROLE_USER"));
    try {
      LoginClient login = new LoginClient(adminBaseUrl);
      String victimToken = login.login("client-test-3", "client-pw-1").token();
      assertNotNull(victimToken);

      assertTrue(client.resetPassword("client-test-3", "client-pw-2-new"));

      // Old token no longer works -> re-login fails
      assertThrows(LoginClient.AuthenticationException.class,
          () -> login.login("client-test-3", "client-pw-1"));
      // New password works
      assertNotNull(login.login("client-test-3", "client-pw-2-new").token());
    } finally {
      client.deleteUser("client-test-3");
    }
  }

  @Test
  @DisplayName("changeOwnPassword: correct old -> true, wrong old -> false")
  void change_own_password() throws IOException {
    UserManagementClient admin = new UserManagementClient(adminBaseUrl);
    admin.setAuthToken(adminToken);
    admin.createUser(new CreateUserRequest("client-test-4", "client-pw-1", "C4", "ROLE_USER"));
    try {
      LoginClient login = new LoginClient(adminBaseUrl);
      String ownToken = login.login("client-test-4", "client-pw-1").token();

      UserManagementClient own = new UserManagementClient(adminBaseUrl);
      own.setAuthToken(ownToken);

      assertFalse(own.changeOwnPassword("wrong-old", "client-pw-2-new"),
          "wrong old -> 401 -> false");
      assertTrue(own.changeOwnPassword("client-pw-1", "client-pw-2-new"),
          "correct old -> 204 -> true");
    } finally {
      admin.deleteUser("client-test-4");
    }
  }
}

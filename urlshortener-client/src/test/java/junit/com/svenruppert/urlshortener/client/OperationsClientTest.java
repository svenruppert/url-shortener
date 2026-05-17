package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.LoginClient;
import com.svenruppert.urlshortener.client.OperationsClient;
import junit.com.svenruppert.urlshortener.client.support.ClientAuthSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationsClientTest {

  private static ShortenerServer server;
  private static String adminBaseUrl;
  private static String adminToken;
  private static String userToken;

  @BeforeAll
  static void startServer() throws IOException {
    ClientAuthSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init("localhost", 0);
    adminBaseUrl = "http://localhost:" + server.getPortAdmin();
    adminToken = new LoginClient(adminBaseUrl).login("admin", "admin").token();
    userToken = new LoginClient(adminBaseUrl).login("user", "user").token();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    ClientAuthSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("admin sees more operations than user")
  void admin_sees_more_operations() throws IOException {
    OperationsClient client = new OperationsClient(adminBaseUrl);
    client.setAuthToken(adminToken);
    List<OperationsClient.Operation> adminOps = client.fetch();

    client.setAuthToken(userToken);
    List<OperationsClient.Operation> userOps = client.fetch();

    assertTrue(adminOps.size() > userOps.size(),
        "admin (" + adminOps.size() + ") must see more ops than user (" + userOps.size() + ")");
  }

  @Test
  @DisplayName("user does NOT see admin-only operations")
  void user_does_not_see_admin_ops() throws IOException {
    OperationsClient client = new OperationsClient(adminBaseUrl);
    client.setAuthToken(userToken);
    List<OperationsClient.Operation> ops = client.fetch();

    boolean hasAdmin = ops.stream().anyMatch(o -> "admin.dashboard".equals(o.id()));
    assertFalse(hasAdmin, "ROLE_USER must not see admin.dashboard");

    boolean hasUserList = ops.stream().anyMatch(o -> "user.list".equals(o.id()));
    assertFalse(hasUserList, "ROLE_USER must not see user.list");
  }

  @Test
  @DisplayName("user sees link operations they may invoke")
  void user_sees_link_ops() throws IOException {
    OperationsClient client = new OperationsClient(adminBaseUrl);
    client.setAuthToken(userToken);
    List<OperationsClient.Operation> ops = client.fetch();

    assertTrue(ops.stream().anyMatch(o -> "link.create".equals(o.id())),
        "ROLE_USER must see link.create");
    assertTrue(ops.stream().anyMatch(o -> "link.list-own".equals(o.id())),
        "ROLE_USER must see link.list-own");
  }

  @Test
  @DisplayName("no token -> IOException from /api/operations (401 on server)")
  void no_token_fails() {
    OperationsClient client = new OperationsClient(adminBaseUrl);
    IOException ex = assertThrows(IOException.class, client::fetch);
    assertTrue(ex.getMessage().contains("401"),
        "expected the server to reject anonymous access with HTTP 401: " + ex.getMessage());
  }

  @Test
  @DisplayName("operation entries carry id, label, and permission")
  void operation_entries_are_complete() throws IOException {
    OperationsClient client = new OperationsClient(adminBaseUrl);
    client.setAuthToken(adminToken);
    List<OperationsClient.Operation> ops = client.fetch();
    assertFalse(ops.isEmpty(), "admin must see at least one operation");
    OperationsClient.Operation any = ops.get(0);
    assertEquals(true, any.id() != null && !any.id().isBlank());
    assertEquals(true, any.permission() != null && !any.permission().isBlank());
  }
}

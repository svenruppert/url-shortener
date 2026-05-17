package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.AdminClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.StoreInfo;
import junit.com.svenruppert.urlshortener.client.support.ClientAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class AdminClientTest {

  private AdminClient adminClient;
  private URLShortenerClient client;
  private ShortenerServer server;

  @BeforeEach
  public void startServer()
      throws IOException {
    ClientAuthSupport.enableTestBootstrap();
    server = new ShortenerServer();
    server.init();
    String token = ClientAuthSupport.loginAdmin();
    adminClient = new AdminClient();
    adminClient.setAuthToken(token);
    client = new URLShortenerClient();
    client.setAuthToken(token);
  }

  @AfterEach
  public void stopServer() {
    server.shutdown();
    ClientAuthSupport.disableTestBootstrap();
  }

  @Test
  void getStoreInfo_ok_200_parsesStoreInfo() throws Exception {
    StoreInfo info = adminClient.getStoreInfo();
    assertNotNull(info);
    assertEquals("InMemory", info.mode());
    assertEquals(0, info.mappings());
    assertTrue(1700000000000L < info.startedAtEpochMs());

    var shortURL = "abc";
    var url = "https://svenruppert.com";

    client.createCustomMapping(shortURL, url);
    info = adminClient.getStoreInfo();
    assertNotNull(info);
    assertEquals("InMemory", info.mode());
    assertEquals(1, info.mappings());
    client.delete(shortURL);
    info = adminClient.getStoreInfo();
    assertNotNull(info);
    assertEquals("InMemory", info.mode());
    assertEquals(0, info.mappings());
  }

}

package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import junit.com.svenruppert.urlshortener.api.security.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owner-aware {@code /api/statistics/export} behavior:
 * <ul>
 *   <li>An admin (with {@code link:stats:all}) sees every event.</li>
 *   <li>A regular user sees only events for shortCodes they own.</li>
 *   <li>A user requesting someone else's shortCode gets it silently dropped
 *       (no information leak).</li>
 * </ul>
 */
class StatisticsExportOwnerFilterIntegrationTest {

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

    createLink("owned-by-admin", "https://example.com/admin", adminToken);
    createLink("owned-by-user", "https://example.com/user", userToken);

    // Generate one redirect event per shortCode by hitting the public path.
    triggerRedirect("owned-by-admin");
    triggerRedirect("owned-by-user");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
    SecurityTestSupport.disableTestBootstrap();
  }

  @Test
  @DisplayName("Admin export contains BOTH owners' shortCodes (no filter)")
  void admin_export_full() throws Exception {
    List<String> shortCodes = exportedShortCodes(adminToken, null);
    assertTrue(shortCodes.contains("owned-by-admin"));
    assertTrue(shortCodes.contains("owned-by-user"));
  }

  @Test
  @DisplayName("User export without filter returns ONLY their own shortCodes")
  void user_export_only_own() throws Exception {
    List<String> shortCodes = exportedShortCodes(userToken, null);
    assertTrue(shortCodes.contains("owned-by-user"));
    assertFalse(shortCodes.contains("owned-by-admin"),
        "user export must not include shortCodes owned by someone else");
  }

  @Test
  @DisplayName("User explicitly requesting someone else's shortCode gets it silently dropped")
  void user_export_drops_foreign_codes() throws Exception {
    List<String> shortCodes = exportedShortCodes(userToken, "owned-by-admin");
    assertFalse(shortCodes.contains("owned-by-admin"),
        "explicitly requested foreign shortCode must be filtered out");
    assertFalse(shortCodes.contains("owned-by-user"),
        "user explicitly asked for owned-by-admin only — owned-by-user must NOT appear");
    // Result: empty filtered list -> empty export
    assertTrue(shortCodes.isEmpty(),
        "with only foreign codes requested, the export must be empty");
  }

  // ---- helpers ----

  private static void createLink(String alias, String url, String token) throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest(url, alias, null, null));
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
        token).build();
    int code = http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    if (code != 201) throw new IllegalStateException("create link failed: " + code);
  }

  private static void triggerRedirect(String shortCode) throws Exception {
    HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrlRedirect + "/" + shortCode)).GET().build();
    HttpClient noRedirect = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();
    int code = noRedirect.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    if (code != 302 && code != 301) {
      throw new IllegalStateException("redirect not happening for " + shortCode + ": " + code);
    }
  }

  /**
   * Performs the export and parses the header line's {@code shortCodes}
   * array out of the NDJSON entry. Returns it as a flat list.
   */
  private static List<String> exportedShortCodes(String token, String shortCodesQuery) throws Exception {
    String url = baseUrlAdmin + PATH_ADMIN_STATISTICS_EXPORT;
    if (shortCodesQuery != null) {
      url += "?shortCodes=" + shortCodesQuery;
    }
    HttpRequest req = SecurityTestSupport.authorize(
        HttpRequest.newBuilder(URI.create(url)), token).GET().build();
    HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("export failed: " + res.statusCode());
    }
    return parseShortCodesFromZip(res.body());
  }

  private static List<String> parseShortCodesFromZip(byte[] zip) throws Exception {
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        if (!entry.getName().endsWith(".ndjson")) continue;
        byte[] all = zin.readAllBytes();
        String text = new String(all, StandardCharsets.UTF_8);
        // First non-empty line is the header.
        for (String line : text.split("\n")) {
          if (line.isBlank()) continue;
          var node = com.svenruppert.urlshortener.core.JacksonJson.mapper().readTree(line);
          var codesNode = node.get("shortCodes");
          assertNotNull(codesNode, "header must carry a shortCodes array");
          List<String> codes = new ArrayList<>();
          codesNode.forEach(c -> codes.add(c.asText()));
          return codes;
        }
      }
    }
    throw new IllegalStateException("no ndjson entry in the export zip");
  }
}

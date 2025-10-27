package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;

public class URLShortenerClientTest
    implements HasLogger {

  protected static final String HTTP_SVENRUPPERT_COM = "http://svenruppert.com";
  private URLShortenerClient client;
  private ShortenerServer server;

  @BeforeEach
  public void startServer()
      throws IOException {
    server = new ShortenerServer();
    server.init();
  }

  @AfterEach
  public void stopServer() {
    server.shutdown();
  }

  @BeforeEach
  void setUp() {
    client = new URLShortenerClient();
  }

  @Test
  void listEndpoints_useGET_and_returnJson() throws IOException {
    var allRaw = client.listAllAsJson();
    assertNotNull(allRaw);
    assertTrue(allRaw.contains("\"mode\""));
    assertTrue(allRaw.contains("\"count\""));
    assertTrue(allRaw.contains("\"items\""));

    var activeRaw = client.listActiveAsJson();
    assertNotNull(activeRaw);
    assertTrue(activeRaw.contains("\"mode\""));
    assertTrue(activeRaw.contains("\"items\""));

    var expiredRaw = client.listExpiredAsJson();
    assertNotNull(expiredRaw);
    assertTrue(expiredRaw.contains("\"mode\""));
    assertTrue(expiredRaw.contains("\"items\""));
  }

  @Test
  void testCreateMappingAndResolve()
      throws IOException {
    var shortCode = client.createMapping(HTTP_SVENRUPPERT_COM);
    logger().info("shortCode: {}", shortCode);

    assertEquals("100", shortCode.shortCode()); //TODO will break soo after int will be replaced with hash value
    assertEquals(HTTP_SVENRUPPERT_COM, shortCode.originalUrl());
    assertTrue(shortCode.expiresAt().isEmpty());
    var createdAt = shortCode.createdAt();
    assertNotNull(createdAt);
    assertTrue(createdAt.isBefore(now()));

    var resolvedURL = client.resolveShortcode(shortCode.shortCode());
    logger().info("resolvedURL: {}", resolvedURL);
    assertEquals(HTTP_SVENRUPPERT_COM, resolvedURL);

    //TODO - impl. ShortCode with ExpiredAt
    //create second with expiredAt
//    var shortCodeWithExpired = client.createMapping(HTTP_SVENRUPPERT_COM);
//    assertEquals("1", shortCode.shortCode()); //TODO will break soo after int will be replaced with hash value

  }


  @Test
  void test002()
      throws IOException {
    var emptyList = client.listAll();
    assertTrue(emptyList.isEmpty());

    var mapping = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(mapping);

    var listOneEntry = client.listAll();
    assertFalse(listOneEntry.isEmpty());
    assertEquals(1, listOneEntry.size());

  }

  // ————————————————————————————————————————————————————————————————
  // NEU: listActive – leer -> 1 entry after Create
  // ————————————————————————————————————————————————————————————————
  @Test
  void test003_findActive_initiallyEmpty_thenOne()
      throws IOException {
    var active0 = client.listActive();
    assertNotNull(active0);
    assertTrue(active0.isEmpty(), "active list should be empty initially");

    client.createMapping(HTTP_SVENRUPPERT_COM);

    var active1 = client.listActive();
    assertNotNull(active1);
    assertEquals(1, active1.size(), "active list should contain exactly one entry after first create");
    assertEquals(HTTP_SVENRUPPERT_COM, active1.getFirst().originalUrl());
  }

  // ————————————————————————————————————————————————————————————————
  // listExpired – currently always empty (no expiries in store)
  // ————————————————————————————————————————————————————————————————
  @Test
  void test004_findExpired_isEmpty_withoutExpirySupport()
      throws IOException {
    // Vorbedingung: keine Expiry-Unterstützung im Store -> erwartete leere Liste
    var expired0 = client.listExpired();
    assertNotNull(expired0);
    assertTrue(expired0.isEmpty(), "expired list should be empty initially");

    client.createMapping(HTTP_SVENRUPPERT_COM);

    var expired1 = client.listExpired();
    assertNotNull(expired1);
    assertTrue(expired1.isEmpty(), "expired list should still be empty (no expiresAt set)");
  }

  // ————————————————————————————————————————————————————————————————
  // Consistency check findAll vs. findActive (without expiries)
  // ————————————————————————————————————————————————————————————————
  @Test
  void test005_findAll_equals_findActive_withoutExpiries()
      throws IOException {
    client.createMapping(HTTP_SVENRUPPERT_COM);

    var all = client.listAll();
    var active = client.listActive();

    assertNotNull(all);
    assertNotNull(active);
    assertEquals(all.size(), active.size(),
                 "Without expiry support, findAll and findActive should have equal size");
    assertFalse(all.isEmpty(), "There should be at least one mapping");
  }


  @Test
  void delete_existingMapping_returnsTrue_andRemovesIt() throws IOException {
    // Arrange: Create a mapping and verify its existence
    var created = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(created);
    var before = client.listAll();
    assertEquals(1, before.size());

    // Act: delete
    boolean removed = client.delete(created.shortCode());

    // Assert: true returns, list empty, second deletion attempt -> false
    assertTrue(removed, "delete(...) must return true for HTTP 204");
    var after = client.listAll();
    assertEquals(0, after.size(), "After deletion, the list must be empty");

    boolean removedAgain = client.delete(created.shortCode());
    assertFalse(removedAgain, "Second deletion attempt on the same resource must return false (404 → false)");
  }

  @Test
  void delete_nonExistingMapping_returnsFalse() throws IOException {
    boolean removed = client.delete("NoSuch123");
    assertFalse(removed, "Non-existent shortcode must return false (HTTP 404)");
  }

  @Test
  void deleteOrThrow_nonExistingMapping_throwsIOException() {
    assertThrows(IOException.class, () -> client.deleteOrThrow("NoSuch999"),
                 "deleteOrThrow(...) must throw an IOException on 404");
  }

  @Test
  void delete_isIdempotent_and_doesNotAffectOtherEntries() throws IOException {
    // Arrange: Zwei Mappings anlegen
    var m1 = client.createMapping(HTTP_SVENRUPPERT_COM);
    var m2 = client.createMapping("https://example.org/path");
    assertNotNull(m1);
    assertNotNull(m2);

    var allBefore = client.listAll();
    assertEquals(2, allBefore.size(), "Before deleting, 2 entries must exist");

    // Act: m1 löschen (zweimal, um Idempotenz zu prüfen)
    assertTrue(client.delete(m1.shortCode()), "First delete operation must return true");
    assertFalse(client.delete(m1.shortCode()), "Second deletion must return false (already deleted)");

    // Assert: m2 weiterhin vorhanden, m1 entfernt
    var allAfter = client.listAll();
    assertEquals(1, allAfter.size(), "After deletion, exactly 1 entry must remain");
    assertEquals(m2.shortCode(), allAfter.getFirst().shortCode(),
                 "The remaining entry must be m2");
  }

  @Test
  void delete_then_listActive_reflectsRemoval() throws IOException {
    // Arrange
    var m = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(m);
    assertEquals(1, client.listActive().size(), "An active entry is expected");

    // Act
    assertTrue(client.delete(m.shortCode()));

    // Assert
    assertTrue(client.listActive().isEmpty(), "No active entry may remain after deletion");
  }


}
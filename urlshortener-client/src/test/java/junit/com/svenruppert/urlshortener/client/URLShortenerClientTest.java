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
    var allRaw = client.listAllJson();
    assertNotNull(allRaw);
    assertTrue(allRaw.contains("\"mode\""));
    assertTrue(allRaw.contains("\"count\""));
    assertTrue(allRaw.contains("\"items\""));

    var activeRaw = client.listActiveJson();
    assertNotNull(activeRaw);
    assertTrue(activeRaw.contains("\"mode\""));
    assertTrue(activeRaw.contains("\"items\""));

    var expiredRaw = client.listExpiredJson();
    assertNotNull(expiredRaw);
    assertTrue(expiredRaw.contains("\"mode\""));
    assertTrue(expiredRaw.contains("\"items\""));
  }

  @Test
  void testCreateMappingAndResolve()
      throws IOException {
    var shortCode = client.createMapping(HTTP_SVENRUPPERT_COM);
    logger().info("shortCode: {}", shortCode);

    assertEquals("1", shortCode.shortCode()); //TODO will break soo after int will be replaced with hash value
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
  // NEU: listActive – leer -> 1 Eintrag nach Create
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
  // NEU: listExpired – derzeit immer leer (keine Expiries in Store)
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
  // NEU: Konsistenzcheck findAll vs. findActive (ohne Expiries)
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
    // Arrange: Ein Mapping anlegen und Existenz verifizieren
    var created = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(created);
    var before = client.listAll();
    assertEquals(1, before.size());

    // Act: Löschen
    boolean removed = client.delete(created.shortCode());

    // Assert: true zurück, Liste leer, zweiter Löschversuch -> false
    assertTrue(removed, "delete(...) muss true liefern bei HTTP 204");
    var after = client.listAll();
    assertEquals(0, after.size(), "Nach dem Löschen muss die Liste leer sein");

    boolean removedAgain = client.delete(created.shortCode());
    assertFalse(removedAgain, "Zweiter Löschversuch auf derselben Ressource muss false liefern (404 → false)");
  }

  @Test
  void delete_nonExistingMapping_returnsFalse() throws IOException {
    boolean removed = client.delete("NoSuch123");
    assertFalse(removed, "Nicht vorhandener Shortcode muss false liefern (HTTP 404)");
  }

  @Test
  void deleteOrThrow_nonExistingMapping_throwsIOException() {
    assertThrows(IOException.class, () -> client.deleteOrThrow("NoSuch999"),
                 "deleteOrThrow(...) muss bei 404 eine IOException werfen");
  }

  @Test
  void delete_isIdempotent_and_doesNotAffectOtherEntries() throws IOException {
    // Arrange: Zwei Mappings anlegen
    var m1 = client.createMapping(HTTP_SVENRUPPERT_COM);
    var m2 = client.createMapping("https://example.org/path");
    assertNotNull(m1);
    assertNotNull(m2);

    var allBefore = client.listAll();
    assertEquals(2, allBefore.size(), "Vor dem Löschen müssen 2 Einträge existieren");

    // Act: m1 löschen (zweimal, um Idempotenz zu prüfen)
    assertTrue(client.delete(m1.shortCode()), "Erster Löschvorgang muss true liefern");
    assertFalse(client.delete(m1.shortCode()), "Zweiter Löschvorgang muss false liefern (bereits gelöscht)");

    // Assert: m2 weiterhin vorhanden, m1 entfernt
    var allAfter = client.listAll();
    assertEquals(1, allAfter.size(), "Nach dem Löschen muss genau 1 Eintrag verbleiben");
    assertEquals(m2.shortCode(), allAfter.getFirst().shortCode(),
                 "Der verbleibende Eintrag muss m2 sein");
  }

  @Test
  void delete_then_listActive_reflectsRemoval() throws IOException {
    // Arrange
    var m = client.createMapping(HTTP_SVENRUPPERT_COM);
    assertNotNull(m);
    assertEquals(1, client.listActive().size(), "Ein aktiver Eintrag erwartet");

    // Act
    assertTrue(client.delete(m.shortCode()));

    // Assert
    assertTrue(client.listActive().isEmpty(), "Nach dem Löschen darf kein aktiver Eintrag verbleiben");
  }


}
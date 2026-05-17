package junit.com.svenruppert.urlshortener.ui.browserless;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.security.ShortenerSecurityModule;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;

/**
 * Common base for Vaadin Browserless tests against the URL shortener UI.
 * <p>
 * Spins up one real {@link ShortenerServer} for the whole test class (in-memory
 * stores, bootstrap mode {@code DISABLED} so the seed {@code admin/admin} and
 * {@code user/user} accounts are registered). The Vaadin environment itself
 * is reset by {@link BrowserlessTest} before every test method.
 * <p>
 * Test discipline (per the vaadin-mutation-browserless skill):
 * <ul>
 *   <li>No mocks for the units under test — real {@code LoginClient},
 *       {@code UserManagementClient}, etc. drive real REST round-trips.</li>
 *   <li>Reset the Vaadin {@link SubjectStores} subject between tests so we
 *       never accidentally rely on prior login state.</li>
 *   <li>Assert on observable state (component text, navigation target,
 *       persisted user records), never on whether a method got called.</li>
 * </ul>
 */
public abstract class AbstractBrowserlessTest extends BrowserlessTest {

  private static ShortenerServer server;

  @BeforeAll
  static void startServer() throws IOException {
    System.setProperty(ShortenerSecurityModule.SYSPROP_BOOTSTRAP_MODE, "DISABLED");
    server = new ShortenerServer();
    // Redirect server on a random free port; admin server is fixed to
    // DefaultValues.ADMIN_SERVER_PORT (9090) which the UI clients target.
    server.init("localhost", 0);
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.shutdown();
      server = null;
    }
    System.clearProperty(ShortenerSecurityModule.SYSPROP_BOOTSTRAP_MODE);
  }

  /**
   * Clears the Vaadin {@link SubjectStores} between tests. Must run AFTER
   * {@code BrowserlessTest.initVaadinEnvironment()} which sets up the
   * session — Vaadin annotation order guarantees parent {@code @BeforeEach}
   * runs first.
   */
  @BeforeEach
  void resetSession() {
    SubjectStores.subjectStore().deleteCurrentSubject(AppUser.class);
  }

  /**
   * Test fixture: places a fully populated {@link AppUser} into the current
   * Vaadin session, mimicking the state after a successful login. The
   * accessToken must be a valid bearer token issued by the test server.
   */
  protected static void signIn(AppUser user) {
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);
  }
}

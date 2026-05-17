package junit.com.svenruppert.urlshortener.client.support;

import com.svenruppert.urlshortener.client.LoginClient;

import java.io.IOException;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;

/**
 * Auth helper for legacy client integration tests that drive a real
 * {@code ShortenerServer}. Configures the server's bootstrap mode to
 * {@code DISABLED} so the seed {@code admin/admin} user is registered, then
 * exchanges those credentials for a bearer token via {@link LoginClient}.
 * <p>
 * Replaces the temporary {@code ClientSecurityBypassExtension} introduced
 * during phase 2 — tests now go through the real auth surface.
 */
public final class ClientAuthSupport {

  public static final String ADMIN_USER = "admin";
  public static final String ADMIN_PASSWORD = "admin";

  private static final String SYSPROP_BOOTSTRAP_MODE = "urlshortener.security.bootstrap.mode";

  private ClientAuthSupport() {
  }

  /** Must be called before constructing/starting {@code ShortenerServer}. */
  public static void enableTestBootstrap() {
    System.setProperty(SYSPROP_BOOTSTRAP_MODE, "DISABLED");
  }

  public static void disableTestBootstrap() {
    System.clearProperty(SYSPROP_BOOTSTRAP_MODE);
  }

  /** Logs in as the seed admin and returns a fresh bearer token. */
  public static String loginAdmin() throws IOException {
    return loginAdmin(ADMIN_SERVER_URL);
  }

  public static String loginAdmin(String adminBaseUrl) throws IOException {
    return new LoginClient(adminBaseUrl).login(ADMIN_USER, ADMIN_PASSWORD).token();
  }
}

package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.LoginClient;
import com.svenruppert.urlshortener.core.DefaultValues;
import com.svenruppert.vaadin.security.authentication.AuthenticationService;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/**
 * REST-backed authentication service: delegates to {@link LoginClient} for
 * a real round-trip against {@code POST /api/login}. The successful result
 * is cached briefly via a {@link ThreadLocal} so the immediately following
 * {@link #loadSubject(AppCredentials)} call can reuse it without a second
 * server call.
 */
public class AppAuthenticationService
    implements AuthenticationService<AppCredentials, AppUser>, HasLogger {

  private final LoginClient loginClient;
  private final ThreadLocal<LoginClient.AuthSession> lastSuccess = new ThreadLocal<>();

  public AppAuthenticationService() {
    this(new LoginClient(DefaultValues.ADMIN_SERVER_URL));
  }

  public AppAuthenticationService(LoginClient loginClient) {
    this.loginClient = loginClient;
  }

  @Override
  public boolean checkCredentials(AppCredentials credentials) {
    lastSuccess.remove();
    if (credentials == null
        || credentials.username() == null || credentials.username().isBlank()
        || credentials.password() == null) {
      return false;
    }
    try {
      LoginClient.AuthSession session = loginClient.login(
          credentials.username(), credentials.password());
      lastSuccess.set(session);
      return true;
    } catch (LoginClient.AuthenticationException auth) {
      logger().info("login rejected ({})", auth.getMessage());
      return false;
    } catch (IOException ioe) {
      logger().warn("login failed: {}", ioe.getMessage());
      return false;
    }
  }

  @Override
  public AppUser loadSubject(AppCredentials credentials) {
    LoginClient.AuthSession session = lastSuccess.get();
    try {
      if (session == null) {
        // No cached result — fall back to a fresh login.
        session = loginClient.login(credentials.username(), credentials.password());
      }
      Set<AppRole> roles = mapRoles(session.roles());
      return new AppUser(
          session.username(),
          roles,
          session.token(),
          Set.copyOf(session.permissions()));
    } catch (IOException ioe) {
      throw new IllegalStateException("loadSubject failed: " + ioe.getMessage(), ioe);
    } finally {
      lastSuccess.remove();
    }
  }

  @Override
  public Class<AppUser> subjectType() {
    return AppUser.class;
  }

  private static Set<AppRole> mapRoles(Iterable<String> roleNames) {
    EnumSet<AppRole> set = EnumSet.noneOf(AppRole.class);
    for (String name : roleNames) {
      if (name == null) continue;
      try {
        set.add(AppRole.valueOf(name));
      } catch (IllegalArgumentException ignored) {
        // unknown role from server — skip silently
      }
    }
    return set;
  }
}

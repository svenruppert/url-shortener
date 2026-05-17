package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.adapter.SecurityHttpHandler;
import com.svenruppert.urlshortener.api.security.auth.ShortenerAuthenticationService;
import com.svenruppert.urlshortener.api.security.auth.ShortenerAuthorizationService;
import com.svenruppert.urlshortener.api.security.auth.ShortenerRestSubjectResolver;
import com.svenruppert.urlshortener.api.security.bootstrap.ShortenerAdministratorAccountStore;
import com.svenruppert.urlshortener.api.security.handler.BootstrapAdminHandler;
import com.svenruppert.urlshortener.api.security.handler.BootstrapStatusHandler;
import com.svenruppert.urlshortener.api.security.handler.LoginHandler;
import com.svenruppert.urlshortener.api.security.handler.LogoutHandler;
import com.svenruppert.urlshortener.api.security.handler.MeHandler;
import com.svenruppert.urlshortener.api.security.handler.OperationsHandler;
import com.svenruppert.urlshortener.api.security.permissions.ShortenerRolePermissionMapping;
import com.svenruppert.urlshortener.api.security.token.InMemoryTokenStore;
import com.svenruppert.urlshortener.api.security.user.InMemoryUserStore;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.vaadin.security.authentication.PasswordHasher;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;
import com.svenruppert.vaadin.security.bootstrap.BootstrapConfiguration;
import com.svenruppert.vaadin.security.bootstrap.BootstrapMode;
import com.svenruppert.vaadin.security.bootstrap.BootstrapStartup;
import com.svenruppert.vaadin.security.bootstrap.BootstrapStateService;
import com.svenruppert.vaadin.security.bootstrap.BootstrapTokenGenerator;
import com.svenruppert.vaadin.security.bootstrap.BootstrapTokenOutput;
import com.svenruppert.vaadin.security.bootstrap.BootstrapTokenStore;
import com.svenruppert.vaadin.security.bootstrap.ConsoleBootstrapTokenOutput;
import com.svenruppert.vaadin.security.bootstrap.FileBootstrapTokenOutput;
import com.svenruppert.vaadin.security.bootstrap.FileBootstrapTokenStore;
import com.svenruppert.vaadin.security.bootstrap.InMemoryBootstrapTokenStore;
import com.svenruppert.vaadin.security.bootstrap.InitialAdminBootstrapService;
import com.svenruppert.vaadin.security.bootstrap.MinimumLengthPasswordPolicy;
import com.svenruppert.vaadin.security.bruteforce.InMemoryLoginAttemptPolicy;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptConfiguration;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptPolicy;
import com.svenruppert.vaadin.security.rest.RestAuthorizationFilter;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/**
 * Wires the complete authoritative security stack for the shortener REST
 * server: user store, token store, role/permission mapping, subject resolver,
 * authentication/authorization filters, login attempt throttling, audit
 * pipeline (via SPI default), and the initial-administrator bootstrap.
 */
public final class ShortenerSecurityModule implements HasLogger {

  public static final String PATH_API_LOGIN = "/api/login";
  public static final String PATH_API_LOGOUT = "/api/logout";
  public static final String PATH_API_ME = "/api/me";
  public static final String PATH_API_OPERATIONS = "/api/operations";
  public static final String PATH_API_BOOTSTRAP_STATUS = "/api/bootstrap/status";
  public static final String PATH_API_BOOTSTRAP_ADMIN = "/api/bootstrap/admin";

  private final UserStore userStore;
  private final InMemoryTokenStore tokenStore;
  private final ShortenerRolePermissionMapping mapping;
  private final ShortenerRestSubjectResolver resolver;
  private final RestAuthorizationFilter authorizationFilter;
  private final LoginHandler loginHandler;
  private final LogoutHandler logoutHandler;
  private final MeHandler meHandler;
  private final OperationsHandler operationsHandler;
  private final BootstrapStatusHandler bootstrapStatusHandler;
  private final BootstrapAdminHandler bootstrapAdminHandler;
  private LoginAttemptPolicy loginAttemptPolicy;

  public ShortenerSecurityModule(Path storageRoot) {
    this(storageRoot, null);
  }

  /**
   * Constructs the security module with an injected {@link UserStore}. When
   * {@code userStore} is {@code null} a fresh {@link InMemoryUserStore} is
   * created and — outside bootstrap mode — seeded with dev/test accounts.
   * Persistent deployments should pass an {@code EclipseUserStore} so that
   * users survive restarts; in that case no automatic seeding happens.
   */
  public ShortenerSecurityModule(Path storageRoot, UserStore injectedUserStore) {
    PasswordHasher hasher = SecurityServiceResolver.passwordHashingService();
    BootstrapConfiguration bootstrapConfig = defaultBootstrapConfig(storageRoot);
    boolean bootstrapMode = bootstrapConfig.mode() != BootstrapMode.DISABLED;

    if (injectedUserStore != null) {
      this.userStore = injectedUserStore;
    } else {
      InMemoryUserStore inMemory = new InMemoryUserStore(hasher);
      if (!bootstrapMode) {
        // Dev/test fallback only — convenient seeds for non-bootstrap runs.
        inMemory.create("admin", "admin", "Admin User",
            com.svenruppert.urlshortener.api.security.permissions.ShortenerRole.ROLE_ADMIN);
        inMemory.create("user", "user", "Standard User",
            com.svenruppert.urlshortener.api.security.permissions.ShortenerRole.ROLE_USER);
        inMemory.create("viewer", "viewer", "Read-only Viewer",
            com.svenruppert.urlshortener.api.security.permissions.ShortenerRole.ROLE_VIEWER);
      }
      this.userStore = inMemory;
    }

    this.tokenStore = new InMemoryTokenStore();
    this.mapping = new ShortenerRolePermissionMapping();
    this.resolver = new ShortenerRestSubjectResolver(tokenStore, mapping);
    this.authorizationFilter = new RestAuthorizationFilter(resolver);

    LoginAttemptPolicy loginAttempts = new UnlockableLoginAttemptPolicy(
        new InMemoryLoginAttemptPolicy());
    this.loginAttemptPolicy = loginAttempts;
    LoginAttemptPolicy bootstrapAttempts = new InMemoryLoginAttemptPolicy(
        LoginAttemptConfiguration.strictBootstrap(),
        Clock.systemUTC(),
        SecurityServiceResolver.securityAuditService());

    this.loginHandler = new LoginHandler(userStore, tokenStore, loginAttempts);
    this.logoutHandler = new LogoutHandler(tokenStore);
    this.meHandler = new MeHandler(resolver);
    this.operationsHandler = new OperationsHandler(resolver);

    // Bootstrap pipeline
    ShortenerAdministratorAccountStore adminStore = new ShortenerAdministratorAccountStore(userStore);
    BootstrapStateService stateService = new BootstrapStateService(adminStore, bootstrapConfig.mode());
    BootstrapTokenStore tokenStoreBoot = bootstrapTokenStore(bootstrapConfig);
    BootstrapTokenOutput tokenOutput = bootstrapTokenOutput(bootstrapConfig);
    InitialAdminBootstrapService bootstrapService = new InitialAdminBootstrapService(
        stateService, tokenStoreBoot, adminStore, hasher,
        new MinimumLengthPasswordPolicy(8),
        bootstrapConfig.tokenValidity(), Clock.systemUTC());
    this.bootstrapStatusHandler = new BootstrapStatusHandler(stateService);
    this.bootstrapAdminHandler = new BootstrapAdminHandler(bootstrapService, bootstrapAttempts);

    BootstrapStartup.initializeIfRequired(
        stateService, tokenStoreBoot, new BootstrapTokenGenerator(), tokenOutput, bootstrapConfig);

    // Publish SPI-like singletons for ShortenerAuthenticationService /
    // ShortenerAuthorizationService consumers if needed in-process.
    ShortenerSecurityServices.set(
        new ShortenerAuthenticationService(userStore),
        new ShortenerAuthorizationService(mapping));

    logger().info("Security module initialised — bootstrap mode={}, admin present={}",
        bootstrapConfig.mode(), userStore.hasAnyAdministrator());
  }

  public HttpHandler wrap(HttpHandler delegate, String operationName) {
    return new SecurityHttpHandler(
        authorizationFilter, resolver, delegate, ShortenerSecuredOperations.method(operationName));
  }

  public HttpHandler loginHandler() {
    return loginHandler;
  }

  public HttpHandler logoutHandler() {
    return logoutHandler;
  }

  public HttpHandler meHandler() {
    return meHandler;
  }

  public HttpHandler operationsHandler() {
    return operationsHandler;
  }

  public HttpHandler bootstrapStatusHandler() {
    return bootstrapStatusHandler;
  }

  public HttpHandler bootstrapAdminHandler() {
    return bootstrapAdminHandler;
  }

  public UserStore userStore() {
    return userStore;
  }

  public InMemoryTokenStore tokenStore() {
    return tokenStore;
  }

  public LoginAttemptPolicy loginAttemptPolicy() {
    return loginAttemptPolicy;
  }

  public static final String SYSPROP_BOOTSTRAP_MODE = "urlshortener.security.bootstrap.mode";

  private static BootstrapConfiguration defaultBootstrapConfig(Path storageRoot) {
    Path tokenFile = storageRoot.resolve(".bootstrap-token");
    BootstrapMode mode = resolveMode();
    return new BootstrapConfiguration(mode, tokenFile, Duration.ofHours(1));
  }

  private static BootstrapMode resolveMode() {
    String value = System.getProperty(SYSPROP_BOOTSTRAP_MODE, "PERSISTENT_FILE");
    try {
      return BootstrapMode.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return BootstrapMode.PERSISTENT_FILE;
    }
  }

  private static BootstrapTokenStore bootstrapTokenStore(BootstrapConfiguration cfg) {
    return cfg.mode() == BootstrapMode.PERSISTENT_FILE
        ? new FileBootstrapTokenStore(cfg.tokenFilePath())
        : new InMemoryBootstrapTokenStore();
  }

  private static BootstrapTokenOutput bootstrapTokenOutput(BootstrapConfiguration cfg) {
    return switch (cfg.mode()) {
      case PERSISTENT_FILE -> new FileBootstrapTokenOutput();
      case TRANSIENT_CONSOLE -> new ConsoleBootstrapTokenOutput(
          "POST the bootstrap token to " + PATH_API_BOOTSTRAP_ADMIN + " to create the initial administrator.");
      case DISABLED -> (token, configuration) -> {
      };
    };
  }
}

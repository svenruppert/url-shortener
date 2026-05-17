package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.security.LastAdminGuard;
import com.svenruppert.urlshortener.api.security.UnlockableLoginAttemptPolicy;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.api.security.permissions.ShortenerRole;
import com.svenruppert.urlshortener.api.security.token.InMemoryTokenStore;
import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.users.AdminResetPasswordRequest;
import com.svenruppert.urlshortener.core.users.CreateUserRequest;
import com.svenruppert.urlshortener.core.users.UpdateUserRequest;
import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptContext;
import com.svenruppert.vaadin.security.bruteforce.LoginAttemptPolicy;
import com.svenruppert.vaadin.security.logout.SubjectId;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS_PASSWORD_SUFFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS_UNLOCK_SUFFIX;

/**
 * Single dispatcher for the {@code /api/users} surface. The outer
 * {@code RestAuthorizationFilter} pre-checks {@code user:read}; every mutating
 * branch verifies an additional, finer-grained permission via
 * {@link CurrentSubject#hasPermission(String)}.
 * <p>
 * Self-protection rules:
 * <ul>
 *   <li>An admin cannot delete or disable themselves.</li>
 *   <li>The last enabled administrator cannot be deleted, disabled, or demoted.</li>
 * </ul>
 */
public final class UserManagementHandler implements HttpHandler, HasLogger {

  private static final int MIN_PASSWORD_LENGTH = 8;

  private final UserStore userStore;
  private final InMemoryTokenStore tokenStore;
  private final LoginAttemptPolicy loginAttemptPolicy;

  public UserManagementHandler(UserStore userStore, InMemoryTokenStore tokenStore) {
    this(userStore, tokenStore, null);
  }

  public UserManagementHandler(UserStore userStore,
                               InMemoryTokenStore tokenStore,
                               LoginAttemptPolicy loginAttemptPolicy) {
    this.userStore = Objects.requireNonNull(userStore, "userStore");
    this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore");
    this.loginAttemptPolicy = loginAttemptPolicy;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    try {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();
      String remainder = path.startsWith(PATH_API_USERS)
          ? path.substring(PATH_API_USERS.length())
          : "";
      if (remainder.startsWith("/")) remainder = remainder.substring(1);

      if (remainder.isEmpty()) {
        switch (method) {
          case "GET" -> listUsers(response);
          case "POST" -> createUser(exchange, response);
          default -> methodNotAllowed(response);
        }
      } else if (remainder.endsWith(PATH_API_USERS_PASSWORD_SUFFIX.substring(1))) {
        // {username}/password
        String username = remainder.substring(0, remainder.length()
            - PATH_API_USERS_PASSWORD_SUFFIX.substring(1).length() - 1);
        if (!"POST".equals(method)) {
          methodNotAllowed(response);
        } else if (username.isBlank()) {
          badRequest(response, "username_required");
        } else {
          resetPassword(exchange, response, username);
        }
      } else if (remainder.endsWith(PATH_API_USERS_UNLOCK_SUFFIX.substring(1))) {
        // {username}/unlock
        String username = remainder.substring(0, remainder.length()
            - PATH_API_USERS_UNLOCK_SUFFIX.substring(1).length() - 1);
        if (!"POST".equals(method)) {
          methodNotAllowed(response);
        } else if (username.isBlank()) {
          badRequest(response, "username_required");
        } else {
          unlockUser(response, username);
        }
      } else if (!remainder.contains("/")) {
        // {username}
        switch (method) {
          case "PUT" -> updateUser(exchange, response, remainder);
          case "DELETE" -> deleteUser(response, remainder);
          default -> methodNotAllowed(response);
        }
      } else {
        notFound(response);
      }
    } catch (RuntimeException e) {
      logger().warn("UserManagementHandler failed: {}", e.getMessage());
      response.status(500);
      response.body("{\"error\":\"internal_error\"}");
    }
    response.writeTo(exchange);
  }

  // ---------- operations ----------

  private void listUsers(HttpExchangeRestResponse response) {
    List<UserSummary> summaries = userStore.listAll().stream()
        .map(UserManagementHandler::toSummary)
        .toList();
    writeJson(response, 200, summaries);
  }

  private void createUser(HttpExchange exchange, HttpExchangeRestResponse response) throws IOException {
    if (!CurrentSubject.hasPermission("user:create")) {
      forbidden(response);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    CreateUserRequest body = parse(request.bodyBytes(), CreateUserRequest.class);
    if (body == null
        || body.username() == null || body.username().isBlank()
        || body.password() == null) {
      badRequest(response, "bad_request");
      return;
    }
    if (body.password().length() < MIN_PASSWORD_LENGTH) {
      badRequest(response, "password_too_short");
      return;
    }
    ShortenerRole role = parseRole(body.role());
    if (role == null) {
      badRequest(response, "unknown_role");
      return;
    }
    if (userStore.findByUsername(body.username()).isPresent()) {
      writeJson(response, 409, Map.of("error", "username_exists"));
      return;
    }
    ShortenerUser created = userStore.create(
        body.username(), body.password(), body.displayName(), role);
    writeJson(response, 201, toSummary(created));
  }

  private void updateUser(HttpExchange exchange, HttpExchangeRestResponse response, String username) throws IOException {
    if (!CurrentSubject.hasPermission("user:update")) {
      forbidden(response);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    UpdateUserRequest body = parse(request.bodyBytes(), UpdateUserRequest.class);
    if (body == null) {
      badRequest(response, "bad_request");
      return;
    }
    if (userStore.findByUsername(username).isEmpty()) {
      writeJson(response, 404, Map.of("error", "user_not_found"));
      return;
    }
    String currentUser = CurrentSubject.username().orElse(null);

    if (body.role() != null) {
      if (!CurrentSubject.hasPermission("user:role:assign")) {
        forbidden(response);
        return;
      }
      ShortenerRole newRole = parseRole(body.role());
      if (newRole == null) {
        badRequest(response, "unknown_role");
        return;
      }
      if (newRole == ShortenerRole.ROLE_USER
          && LastAdminGuard.isOnlyEnabledAdmin(userStore, username)) {
        writeJson(response, 409, Map.of("error", "last_admin_protected"));
        return;
      }
      userStore.setRole(username, newRole);
    }

    if (body.enabled() != null) {
      if (!body.enabled() && username.equals(currentUser)) {
        writeJson(response, 409, Map.of("error", "self_disable_forbidden"));
        return;
      }
      if (!body.enabled() && LastAdminGuard.isOnlyEnabledAdmin(userStore, username)) {
        writeJson(response, 409, Map.of("error", "last_admin_protected"));
        return;
      }
      userStore.setEnabled(username, body.enabled());
    }

    if (body.displayName() != null) {
      userStore.setDisplayName(username, body.displayName());
    }

    ShortenerUser refreshed = userStore.findByUsername(username).orElseThrow();
    writeJson(response, 200, toSummary(refreshed));
  }

  private void deleteUser(HttpExchangeRestResponse response, String username) {
    if (!CurrentSubject.hasPermission("user:delete")) {
      forbidden(response);
      return;
    }
    String currentUser = CurrentSubject.username().orElse(null);
    if (username.equals(currentUser)) {
      writeJson(response, 409, Map.of("error", "self_delete_forbidden"));
      return;
    }
    if (LastAdminGuard.isOnlyEnabledAdmin(userStore, username)) {
      writeJson(response, 409, Map.of("error", "last_admin_protected"));
      return;
    }
    if (!userStore.deleteUser(username)) {
      writeJson(response, 404, Map.of("error", "user_not_found"));
      return;
    }
    tokenStore.clearAll(SubjectId.of(username));
    response.status(204);
    response.body("");
  }

  private void unlockUser(HttpExchangeRestResponse response, String username) {
    if (!CurrentSubject.hasPermission("user:update")) {
      forbidden(response);
      return;
    }
    if (userStore.findByUsername(username).isEmpty()) {
      writeJson(response, 404, Map.of("error", "user_not_found"));
      return;
    }
    if (loginAttemptPolicy instanceof UnlockableLoginAttemptPolicy unlockable) {
      unlockable.unlock(username);
    } else if (loginAttemptPolicy != null) {
      // Fallback for non-unlockable policies — clears only the username-only key.
      loginAttemptPolicy.recordSuccess(LoginAttemptContext.now(username, null, null));
    }
    response.status(204);
    response.body("");
  }

  private void resetPassword(HttpExchange exchange, HttpExchangeRestResponse response, String username) throws IOException {
    if (!CurrentSubject.hasPermission("user:update")) {
      forbidden(response);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    AdminResetPasswordRequest body = parse(request.bodyBytes(), AdminResetPasswordRequest.class);
    if (body == null || body.newPassword() == null) {
      badRequest(response, "bad_request");
      return;
    }
    if (body.newPassword().length() < MIN_PASSWORD_LENGTH) {
      badRequest(response, "password_too_short");
      return;
    }
    if (!userStore.resetPassword(username, body.newPassword())) {
      writeJson(response, 404, Map.of("error", "user_not_found"));
      return;
    }
    tokenStore.clearAll(SubjectId.of(username));
    response.status(204);
    response.body("");
  }

  // ---------- helpers ----------

  private static UserSummary toSummary(ShortenerUser u) {
    return new UserSummary(u.username(), u.displayName(), u.role().name(), u.enabled());
  }

  private static ShortenerRole parseRole(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return ShortenerRole.valueOf(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static <T> T parse(byte[] body, Class<T> type) {
    try {
      if (body == null || body.length == 0) return null;
      return JacksonJson.mapper().readValue(body, type);
    } catch (Exception e) {
      return null;
    }
  }

  private static void writeJson(HttpExchangeRestResponse response, int status, Object payload) {
    try {
      response.status(status);
      response.body(JacksonJson.mapper().writeValueAsString(payload));
    } catch (Exception e) {
      response.status(500);
      response.body("{\"error\":\"serialization\"}");
    }
  }

  private static void methodNotAllowed(HttpExchangeRestResponse response) {
    writeJson(response, 405, Map.of("error", "method_not_allowed"));
  }

  private static void notFound(HttpExchangeRestResponse response) {
    writeJson(response, 404, Map.of("error", "not_found"));
  }

  private static void badRequest(HttpExchangeRestResponse response, String code) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("error", code);
    writeJson(response, 400, payload);
  }

  private static void forbidden(HttpExchangeRestResponse response) {
    writeJson(response, 403, Map.of("error", "forbidden"));
  }
}

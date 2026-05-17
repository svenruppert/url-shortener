package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JacksonJson;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;

/**
 * Minimal client for the {@code POST /api/login} endpoint of the URL-Shortener
 * admin server. Exchanges username/password for a bearer token plus the
 * subject metadata (username, displayName, role, permissions) returned by the
 * server.
 */
public final class LoginClient implements HasLogger {

  public static final String PATH_API_LOGIN = "/api/login";
  public static final String PATH_API_LOGOUT = "/api/logout";

  private static final int CONNECT_TIMEOUT = 5_000;
  private static final int READ_TIMEOUT = 5_000;

  private final URI serverBaseAdmin;

  public LoginClient() {
    this(ADMIN_SERVER_URL);
  }

  public LoginClient(String serverBaseUrlAdmin) {
    Objects.requireNonNull(serverBaseUrlAdmin, "serverBaseUrlAdmin");
    String normalized = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";
    this.serverBaseAdmin = URI.create(normalized);
  }

  /**
   * Performs a login round-trip. Returns the parsed result on HTTP 200,
   * throws {@link AuthenticationException} on HTTP 401 / 429, and a generic
   * {@link IOException} on any other failure.
   */
  public AuthSession login(String username, String password) throws IOException {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(password, "password");

    URI uri = serverBaseAdmin.resolve(PATH_API_LOGIN.startsWith("/") ? PATH_API_LOGIN.substring(1) : PATH_API_LOGIN);
    HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
    try {
      con.setRequestMethod("POST");
      con.setConnectTimeout(CONNECT_TIMEOUT);
      con.setReadTimeout(READ_TIMEOUT);
      con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      con.setRequestProperty("Accept", "application/json");
      con.setDoOutput(true);
      String body = "{\"username\":" + jsonString(username) + ",\"password\":" + jsonString(password) + "}";
      try (OutputStream out = con.getOutputStream()) {
        out.write(body.getBytes(StandardCharsets.UTF_8));
      }
      int code = con.getResponseCode();
      String response = readBody(con, code);
      if (code == 200) {
        return parse(response);
      }
      if (code == 401) {
        throw new AuthenticationException("invalid_credentials");
      }
      if (code == 429) {
        throw new AuthenticationException("too_many_attempts");
      }
      throw new IOException("login failed: HTTP " + code + " body=" + response);
    } finally {
      con.disconnect();
    }
  }

  /**
   * Invalidates the given bearer token on the server. Best-effort; failures
   * are logged and swallowed because the client is logging out anyway.
   */
  public void logout(String token) {
    if (token == null || token.isBlank()) return;
    try {
      URI uri = serverBaseAdmin.resolve(PATH_API_LOGOUT.startsWith("/") ? PATH_API_LOGOUT.substring(1) : PATH_API_LOGOUT);
      HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
      try {
        con.setRequestMethod("POST");
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setRequestProperty("Authorization", "Bearer " + token);
        con.setDoOutput(false);
        con.getResponseCode();
      } finally {
        con.disconnect();
      }
    } catch (IOException e) {
      logger().warn("logout failed: {}", e.getMessage());
    }
  }

  private static AuthSession parse(String body) throws IOException {
    JsonNode node = JacksonJson.mapper().readTree(body);
    if (node == null || !node.isObject()) {
      throw new IOException("login response is not a JSON object: " + body);
    }
    String token = text(node, "token");
    String username = text(node, "username");
    String displayName = text(node, "displayName");
    String role = text(node, "role");
    if (token == null || username == null) {
      throw new IOException("login response missing required fields: " + body);
    }
    List<String> roles = new ArrayList<>();
    if (role != null) roles.add(role);
    return new AuthSession(token, username, displayName, List.copyOf(roles), List.of());
  }

  private static String readBody(HttpURLConnection con, int code) throws IOException {
    var stream = code >= 400 ? con.getErrorStream() : con.getInputStream();
    if (stream == null) return "";
    try (stream) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String text(JsonNode node, String key) {
    JsonNode child = node.get(key);
    return child != null && child.isTextual() ? child.asText() : null;
  }

  private static String jsonString(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /**
   * Server response for a successful login. {@code permissions} is empty in the
   * current server implementation; permissions are discovered via
   * {@code GET /api/me} or {@code GET /api/operations}.
   */
  public record AuthSession(
      String token,
      String username,
      String displayName,
      List<String> roles,
      List<String> permissions
  ) {
  }

  /** Thrown on HTTP 401 and HTTP 429 from the login endpoint. */
  public static final class AuthenticationException extends IOException {
    public AuthenticationException(String reason) {
      super(reason);
    }
  }
}

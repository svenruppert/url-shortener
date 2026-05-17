package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.users.AdminResetPasswordRequest;
import com.svenruppert.urlshortener.core.users.CreateUserRequest;
import com.svenruppert.urlshortener.core.users.SelfChangePasswordRequest;
import com.svenruppert.urlshortener.core.users.UpdateUserRequest;
import com.svenruppert.urlshortener.core.users.UserSummary;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.JSON_CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_ME_PASSWORD;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_USERS_PASSWORD_SUFFIX;

/**
 * REST client for the user-management surface and the self-service password
 * endpoint. All mutating calls invoke {@link AuthFailureRegistry} on 401 so
 * the host (e.g. the Vaadin UI) can clear local session state and redirect to
 * login.
 */
public class UserManagementClient implements HasLogger {

  private final URI base;
  private volatile String authToken;

  public UserManagementClient() {
    this(ADMIN_SERVER_URL);
  }

  public UserManagementClient(String baseUrl) {
    var trimmed = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.base = URI.create(trimmed);
  }

  public void setAuthToken(String token) {
    this.authToken = (token == null || token.isBlank()) ? null : token;
  }

  // ---------- admin ops ----------

  public List<UserSummary> listUsers() throws IOException {
    HttpURLConnection con = open(PATH_API_USERS, "GET", null);
    try {
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code != 200) {
        throw new IOException("listUsers failed: HTTP " + code + " body=" + readBody(con));
      }
      try (InputStream in = con.getInputStream()) {
        return JacksonJson.mapper().readValue(in, new TypeReference<List<UserSummary>>() { });
      }
    } finally {
      con.disconnect();
    }
  }

  public UserSummary createUser(CreateUserRequest request) throws IOException {
    HttpURLConnection con = open(PATH_API_USERS, "POST", JSON_CONTENT_TYPE);
    try {
      writeJson(con, request);
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code == 201) {
        try (InputStream in = con.getInputStream()) {
          return JacksonJson.mapper().readValue(in, UserSummary.class);
        }
      }
      throw new IOException("createUser failed: HTTP " + code + " body=" + readBody(con));
    } finally {
      con.disconnect();
    }
  }

  public UserSummary updateUser(String username, UpdateUserRequest request) throws IOException {
    HttpURLConnection con = open(PATH_API_USERS + "/" + encode(username), "PUT", JSON_CONTENT_TYPE);
    try {
      writeJson(con, request);
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code == 200) {
        try (InputStream in = con.getInputStream()) {
          return JacksonJson.mapper().readValue(in, UserSummary.class);
        }
      }
      throw new IOException("updateUser failed: HTTP " + code + " body=" + readBody(con));
    } finally {
      con.disconnect();
    }
  }

  /** Returns {@code true} on successful delete (204), {@code false} on 404. */
  public boolean deleteUser(String username) throws IOException {
    HttpURLConnection con = open(PATH_API_USERS + "/" + encode(username), "DELETE", null);
    try {
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code == 204) return true;
      if (code == 404) return false;
      throw new IOException("deleteUser failed: HTTP " + code + " body=" + readBody(con));
    } finally {
      con.disconnect();
    }
  }

  /** Admin-only force reset. Returns {@code true} on 204, {@code false} on 404. */
  public boolean resetPassword(String username, String newPassword) throws IOException {
    HttpURLConnection con = open(
        PATH_API_USERS + "/" + encode(username) + PATH_API_USERS_PASSWORD_SUFFIX,
        "POST", JSON_CONTENT_TYPE);
    try {
      writeJson(con, new AdminResetPasswordRequest(newPassword));
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code == 204) return true;
      if (code == 404) return false;
      throw new IOException("resetPassword failed: HTTP " + code + " body=" + readBody(con));
    } finally {
      con.disconnect();
    }
  }

  // ---------- self-service ----------

  /**
   * Returns {@code true} on success (204). Returns {@code false} on 401,
   * which on this endpoint means "old password incorrect" — a domain
   * outcome, not a session failure. {@link AuthFailureRegistry} is NOT
   * invoked so that an incorrect old password does not silently log the
   * user out of the UI.
   */
  public boolean changeOwnPassword(String oldPassword, String newPassword) throws IOException {
    HttpURLConnection con = open(PATH_API_ME_PASSWORD, "POST", JSON_CONTENT_TYPE);
    try {
      writeJson(con, new SelfChangePasswordRequest(oldPassword, newPassword));
      int code = con.getResponseCode();
      if (code == 204) return true;
      if (code == 401) return false;
      throw new IOException("changeOwnPassword failed: HTTP " + code + " body=" + readBody(con));
    } finally {
      con.disconnect();
    }
  }

  // ---------- helpers ----------

  private HttpURLConnection open(String path, String method, String contentType) throws IOException {
    HttpURLConnection con = (HttpURLConnection) base.resolve(path.startsWith("/") ? path.substring(1) : path)
        .toURL().openConnection();
    con.setRequestMethod(method);
    con.setConnectTimeout(3000);
    con.setReadTimeout(5000);
    con.setRequestProperty("Accept", "application/json");
    if (contentType != null) {
      con.setRequestProperty("Content-Type", contentType);
      con.setDoOutput(true);
    }
    String token = authToken;
    if (token != null) {
      con.setRequestProperty("Authorization", "Bearer " + token);
    }
    return con;
  }

  private static void writeJson(HttpURLConnection con, Object body) throws IOException {
    byte[] payload = JacksonJson.mapper().writeValueAsBytes(body);
    try (var os = con.getOutputStream()) {
      os.write(payload);
    }
  }

  private static String readBody(HttpURLConnection con) {
    try {
      InputStream in = con.getErrorStream() != null ? con.getErrorStream() : con.getInputStream();
      if (in == null) return "";
      try (in) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      return "";
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}

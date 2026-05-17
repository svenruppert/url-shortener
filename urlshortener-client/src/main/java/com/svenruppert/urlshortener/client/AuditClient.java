package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.audit.AuditEventView;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_API_AUDIT;

/**
 * Read-only client for the admin audit endpoint. Returns the flattened
 * {@link AuditEventView} projection produced by the server.
 */
public class AuditClient implements HasLogger {

  private final URI base;
  private volatile String authToken;

  public AuditClient() {
    this(ADMIN_SERVER_URL);
  }

  public AuditClient(String baseUrl) {
    var trimmed = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.base = URI.create(trimmed);
  }

  public void setAuthToken(String token) {
    this.authToken = (token == null || token.isBlank()) ? null : token;
  }

  /**
   * Returns the matching audit events. {@code type} is a comma-separated
   * list of event simple-names (e.g. {@code "LoginSucceeded,LoginFailed"});
   * pass {@code null} for "all types".
   */
  public List<AuditEventView> fetch(String type, String subject, Integer limit) throws IOException {
    StringBuilder qs = new StringBuilder();
    if (type != null && !type.isBlank()) appendParam(qs, "type", type);
    if (subject != null && !subject.isBlank()) appendParam(qs, "subject", subject);
    if (limit != null) appendParam(qs, "limit", String.valueOf(limit));

    String path = PATH_API_AUDIT + (qs.length() == 0 ? "" : "?" + qs);
    HttpURLConnection con = open(path);
    try {
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code != 200) {
        throw new IOException("fetch audit failed: HTTP " + code);
      }
      try (InputStream in = con.getInputStream()) {
        JsonNode root = JacksonJson.mapper().readTree(in);
        JsonNode events = root.get("events");
        if (events == null || !events.isArray()) return List.of();
        return JacksonJson.mapper().treeToValue(events, new TypeReference<List<AuditEventView>>() { });
      }
    } finally {
      con.disconnect();
    }
  }

  private HttpURLConnection open(String path) throws IOException {
    HttpURLConnection con = (HttpURLConnection) base.resolve(
        path.startsWith("/") ? path.substring(1) : path).toURL().openConnection();
    con.setRequestMethod("GET");
    con.setConnectTimeout(3000);
    con.setReadTimeout(5000);
    con.setRequestProperty("Accept", "application/json");
    String token = authToken;
    if (token != null) {
      con.setRequestProperty("Authorization", "Bearer " + token);
    }
    return con;
  }

  private static void appendParam(StringBuilder qs, String key, String value) {
    if (qs.length() > 0) qs.append('&');
    qs.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
  }
}

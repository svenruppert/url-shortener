package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JacksonJson;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;

/**
 * Reads the list of REST operations the current subject may invoke from
 * {@code GET /api/operations}. The Vaadin UI uses the result to decide
 * which buttons/menu entries to render — the authoritative authorization
 * remains on the server (per-endpoint {@code @RequiresPermission}).
 */
public final class OperationsClient implements HasLogger {

  public static final String PATH_API_OPERATIONS = "/api/operations";

  private static final int CONNECT_TIMEOUT = 3_000;
  private static final int READ_TIMEOUT = 5_000;

  private final URI serverBaseAdmin;
  private volatile String authToken;

  public OperationsClient() {
    this(ADMIN_SERVER_URL);
  }

  public OperationsClient(String serverBaseUrlAdmin) {
    Objects.requireNonNull(serverBaseUrlAdmin, "serverBaseUrlAdmin");
    String normalized = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";
    this.serverBaseAdmin = URI.create(normalized);
  }

  public void setAuthToken(String token) {
    this.authToken = (token == null || token.isBlank()) ? null : token;
  }

  /** Fetches the operations the current bearer-token holder may invoke. */
  public List<Operation> fetch() throws IOException {
    URI uri = serverBaseAdmin.resolve(PATH_API_OPERATIONS.startsWith("/")
        ? PATH_API_OPERATIONS.substring(1)
        : PATH_API_OPERATIONS);
    HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
    try {
      con.setRequestMethod("GET");
      con.setConnectTimeout(CONNECT_TIMEOUT);
      con.setReadTimeout(READ_TIMEOUT);
      con.setRequestProperty("Accept", "application/json");
      String token = authToken;
      if (token != null) {
        con.setRequestProperty("Authorization", "Bearer " + token);
      }
      int code = con.getResponseCode();
      AuthFailureRegistry.notifyIfUnauthorized(code);
      if (code != 200) {
        throw new IOException("GET /api/operations failed: HTTP " + code);
      }
      try (var in = con.getInputStream()) {
        byte[] body = in.readAllBytes();
        return parse(body);
      }
    } finally {
      con.disconnect();
    }
  }

  private static List<Operation> parse(byte[] body) throws IOException {
    JsonNode node = JacksonJson.mapper().readTree(body);
    JsonNode ops = node == null ? null : node.get("operations");
    if (ops == null || !ops.isArray()) {
      throw new IOException("invalid /api/operations response: " +
          new String(body, StandardCharsets.UTF_8));
    }
    List<Operation> result = new ArrayList<>(ops.size());
    for (JsonNode item : ops) {
      String id = text(item, "id");
      String label = text(item, "label");
      String permission = text(item, "permission");
      if (id != null) {
        result.add(new Operation(id, label, permission));
      }
    }
    return List.copyOf(result);
  }

  private static String text(JsonNode node, String key) {
    JsonNode child = node.get(key);
    return child != null && child.isTextual() ? child.asText() : null;
  }

  /** A single operation entry returned by {@code /api/operations}. */
  public record Operation(String id, String label, String permission) {
  }
}

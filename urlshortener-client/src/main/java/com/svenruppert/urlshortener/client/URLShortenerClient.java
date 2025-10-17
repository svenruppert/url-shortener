package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.ShortUrlMapping;
import com.svenruppert.urlshortener.core.ShortenRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class URLShortenerClient
    implements HasLogger {

  private final URI serverBaseAdmin;
  private final URI serverBaseRedirect;

  public URLShortenerClient(String serverBaseUrlAdmin,
                            String serverBaseUrlRedirect) {
    var urlToServerAdmin = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";

    var urlToServerRedirect = serverBaseUrlRedirect.endsWith("/")
        ? serverBaseUrlRedirect
        : serverBaseUrlRedirect + "/";

    logger().info("urlToServer ADMIN - {}", urlToServerAdmin);
    logger().info("urlToServer REDIRECT - {}", urlToServerRedirect);

    this.serverBaseAdmin = URI.create(urlToServerAdmin);
    this.serverBaseRedirect = URI.create(urlToServerRedirect);
  }

  public URLShortenerClient() {
    this(ADMIN_SERVER_URL, DEFAULT_SERVER_URL);
  }


  private static String readAllAsString(InputStream is)
      throws IOException {
    if (is == null) return "";
    return new String(is.readAllBytes(), UTF_8);
  }

  /**
   * Cuts the part between "items":[ and the corresponding closing bracket ].
   */
  private static String sliceItemsArray(String json) {
    final String key = "\"items\"";
    int keyPos = json.indexOf(key);
    if (keyPos < 0) return "[]";
    int colon = json.indexOf(':', keyPos + key.length());
    if (colon < 0) return "[]";
    int startArr = json.indexOf('[', colon + 1);
    if (startArr < 0) return "[]";
    int endArr = findMatchingBracket(json, startArr, '[', ']');
    if (endArr < 0) return "[]";
    return json.substring(startArr + 1, endArr); // Inhalt ohne umschließende []
  }

  /**
   * Finds the corresponding closing bracket, respecting strings and escapes.
   */
  private static int findMatchingBracket(String s, int from, char open, char close) {
    int depth = 0;
    boolean inString = false;
    boolean escape = false;
    for (int i = from; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inString) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      } else {
        if (c == '"') {
          inString = true;
          continue;
        }
        if (c == open) depth++;
        if (c == close) {
          depth--;
          if (depth == 0) return i;
        }
      }
    }
    return -1;
  }

  /**
   * Splits a comma-separated list of top-level JSON objects into individual strings.
   */
  private static List<String> splitTopLevelObjects(String itemsInner) {
    List<String> out = new ArrayList<>();
    int i = 0;
    while (i < itemsInner.length()) {
      // Überspringe Leerraum und Kommas
      while (i < itemsInner.length() && Character.isWhitespace(itemsInner.charAt(i))) i++;
      if (i < itemsInner.length() && itemsInner.charAt(i) == ',') {
        i++;
        continue;
      }
      while (i < itemsInner.length() && Character.isWhitespace(itemsInner.charAt(i))) i++;
      if (i >= itemsInner.length()) break;
      if (itemsInner.charAt(i) != '{') {
        // Unerwartetes Zeichen, breche ab
        break;
      }
      int start = i;
      int end = findMatchingBracket(itemsInner, start, '{', '}');
      if (end < 0) break;
      out.add(itemsInner.substring(start, end + 1));
      i = end + 1;
    }
    return out;
  }

  /**
   * Parses a single mapping object. Expected fields are strings or null.
   */
  private static ShortUrlMapping parseOneMapping(String objJson) {
    String shortCode = extractString(objJson, "shortCode");
    String originalUrl = extractString(objJson, "originalUrl");
    String createdAtIso = extractString(objJson, "createdAt");
    String expiresAtIso = extractNullableString(objJson, "expiresAt");

    Instant createdAt = createdAtIso != null ? Instant.parse(createdAtIso) : Instant.EPOCH;
    Optional<Instant> expiresAt = expiresAtIso != null && !expiresAtIso.isEmpty()
        ? Optional.of(Instant.parse(expiresAtIso))
        : Optional.empty();

    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt);
  }

  /**
   * Reads a string field "key":"value" without keeping the quotes.
   */
  private static String extractString(String json, String key) {
    String v = extractNullableString(json, key);
    return v == null || "null".equals(v) ? null : v;
  }

  /**
   * Reads a field and returns the raw string contents or null if the field is missing or null.
   */
  private static String extractNullableString(String json, String key) {
    final String pattern = "\"" + key + "\"";
    int p = json.indexOf(pattern);
    if (p < 0) return null;
    int colon = json.indexOf(':', p + pattern.length());
    if (colon < 0) return null;

    // jump over Whitespace
    int i = colon + 1;
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
    if (i >= json.length()) return null;

    char c = json.charAt(i);
    if (c == 'n') { // null
      // expected "null"
      return "null";
    }
    if (c != '"') return null;

    // Read string, respect escapes
    StringBuilder sb = new StringBuilder();
    i++;
    boolean escape = false;
    for (; i < json.length(); i++) {
      char ch = json.charAt(i);
      if (escape) {
        // For our fields, the standard escapes are sufficient, we take raw
        sb.append(ch);
        escape = false;
      } else {
        if (ch == '\\') {
          escape = true;
          continue;
        }
        if (ch == '"') break;
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private static void drainQuietly(InputStream is) {
    if (is == null) return;
    try (is) {
      while (is.read() != -1) { /* discard */ }
    } catch (IOException ignored) {
    }
  }


  // ————————————————————————————————————————————————————————————————————————————
  // HTTP-Hilfen
  // ————————————————————————————————————————————————————————————————————————————

  /**
   * String originalUrl = "https://svenruppert.com";
   *
   * @param originalUrl
   * @return
   * @throws IOException
   */
  private String shortenURL(String originalUrl)
      throws IOException {
    URL shortenUrl = serverBaseAdmin.resolve(PATH_ADMIN_SHORTEN).toURL();
    logger().info("connecting to .. shortenUrl {}", shortenUrl);
    HttpURLConnection connection = (HttpURLConnection) shortenUrl.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");

    String body = "{\"url\":\"" + originalUrl + "\"}";
    logger().info("body - '{}'", body);
    try (OutputStream os = connection.getOutputStream()) {
      os.write(body.getBytes());
    }

    int status = connection.getResponseCode();
    logger().info("Response Code from Server - {}", status);
    if (status == 200 || status == 201) {
      try (InputStream is = connection.getInputStream()) {
        String jsonResponse = new String(is.readAllBytes(), UTF_8);
        String extractedShortCode = JsonUtils.extractShortCode(jsonResponse);
        logger().info("extractedShortCode .. {}", extractedShortCode);
        return extractedShortCode;
      }
    } else {
      throw new IOException("Server returned status " + status);
    }
  }

  public String resolveShortcode(String shortCode)
      throws IOException {
    logger().info("Resolving shortCode: {}", shortCode);
    var resolveURI = serverBaseRedirect.resolve(PATH_REDIRECT);
    URL url = URI.create(resolveURI + shortCode).toURL();
    logger().info("resolveShortcode - url .. {}", url);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setInstanceFollowRedirects(false);
    int responseCode = connection.getResponseCode();
    logger().info("resolveShortcode - responseCode .. {}", responseCode);

    if (responseCode == 302 || responseCode == 301) {
      var location = connection.getHeaderField("Location");
      logger().info("location .. {}", location);
      return location;
    } else if (responseCode == 404) {
      return null;
    } else {
      throw new IOException("Unexpected response: " + responseCode);
    }
  }

  // ————————————————————————————————————————————————————————————————————————————
// Minimal parser for the known response structure
// Expected format: {"mode":"...","count":N,"items":[{...},{...}]}
// We only extract items[], the fields: shortCode, originalUrl, createdAt, expiresAt
  // ————————————————————————————————————————————————————————————————————————————

  /**
   * Returns all mappings.
   */
  public List<ShortUrlMapping> listAll()
      throws IOException {
    final String json = fetchJson(PATH_ADMIN_LIST_ALL);
    return parseItemsAsMappings(json);
  }

  /**
   * Returns expired mappings.
   */
  public List<ShortUrlMapping> listExpired()
      throws IOException {
    final String json = fetchJson(PATH_ADMIN_LIST_EXPIRED);
    return parseItemsAsMappings(json);
  }

  /**
   * Returns active mappings.
   */
  public List<ShortUrlMapping> listActive()
      throws IOException {
    final String json = fetchJson(PATH_ADMIN_LIST_ACTIVE);
    return parseItemsAsMappings(json);
  }

  /**
   * Optional: Raw JSON if a client needs metadata like count/mode.
   */
  public String listAllJson()
      throws IOException {
    return fetchJson(PATH_ADMIN_LIST_ALL);
  }

  public String listExpiredJson()
      throws IOException {
    return fetchJson(PATH_ADMIN_LIST_EXPIRED);
  }

  public String listActiveJson()
      throws IOException {
    return fetchJson(PATH_ADMIN_LIST_ACTIVE);
  }

  private String fetchJson(String relativePath)
      throws IOException {
    final URI uri = serverBaseAdmin.resolve(relativePath);
    final URL url = uri.toURL();
    logger().info("fetchJson - url {}", url);
    final HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    con.setRequestProperty("Accept", "application/json");
    con.setConnectTimeout(10_000);
    con.setReadTimeout(15_000);

    final int code = con.getResponseCode();
    if (code != 200) {
      final String err = readAllAsString(
          con.getErrorStream() != null ? con.getErrorStream() : con.getInputStream());
      throw new IOException("Unexpected HTTP " + code + " for " + url + " body=" + err);
    }
    try (InputStream is = con.getInputStream()) {
      return readAllAsString(is);
    } finally {
      con.disconnect();
    }
  }

  private List<ShortUrlMapping> parseItemsAsMappings(String json) {
    final String items = sliceItemsArray(json);
    final List<String> objects = splitTopLevelObjects(items);
    final List<ShortUrlMapping> result = new ArrayList<>(objects.size());
    for (String obj : objects) {
      result.add(parseOneMapping(obj));
    }
    return result;
  }

  public ShortUrlMapping createMapping(String url)
      throws IOException {
    logger().info("Shorten the following url {}", url);
    var shortenURL = shortenURL(url);
    return new ShortUrlMapping(shortenURL, url, Instant.now(), Optional.empty());
  }

  public ShortUrlMapping createCustomMapping(String alias, String url)
      throws IOException {
    logger().info("Create custom mapping alias='{}' url='{}'", alias, url);

    if (alias == null || alias.isBlank()) {
      return createMapping(url);
    }

    var validate = AliasPolicy.validate(alias);
    if (!validate.valid()) {
      var reason = validate.reason();
      throw new IllegalArgumentException(reason.defaultMessage);
    }

    URL shortenUrl = serverBaseAdmin.resolve(PATH_ADMIN_SHORTEN).toURL();
    logger().info("connecting to .. shortenUrl {} (custom)", shortenUrl);
    HttpURLConnection connection = (HttpURLConnection) shortenUrl.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");

    String body = new ShortenRequest(url, alias).toJson();
    logger().info("body - '{}'", body);
    try (OutputStream os = connection.getOutputStream()) {
      os.write(body.getBytes(UTF_8));
    }

    int status = connection.getResponseCode();
    logger().info("Response Code from Server - {}", status);
    if (status == 200 || status == 201) {
      try (InputStream is = connection.getInputStream()) {
        String jsonResponse = new String(is.readAllBytes(), UTF_8);
        String extractedShortCode = JsonUtils.extractShortCode(jsonResponse);
        logger().info("extractedShortCode .. {}", extractedShortCode);
        return new ShortUrlMapping(extractedShortCode, url, Instant.now(), Optional.empty());
      }
    }
    if (status == 409) {
      final String err = readAllAsString(connection.getErrorStream());
      throw new IllegalArgumentException("Alias already in use: '" + alias + "'. " + err);
    }
    if (status == 400) {
      final String err = readAllAsString(connection.getErrorStream());
      throw new IllegalArgumentException("Bad request: " + err);
    }
    throw new IOException("Server returned status " + status);
  }

  /**
   * Removes an existing mapping. Equivalent to DELETE /mapping/{shortCode}.
   *
   * @return true if deleted (HTTP 204); false if not found (HTTP 404).
   * @throws IOException for unexpected HTTP codes or I/O problems.
   */
  public boolean delete(String shortCode)
      throws IOException {
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode must not be null/blank");
    }
    final URI uri = serverBaseAdmin.resolve("delete/" + shortCode);
    final URL url = uri.toURL();
    logger().info("delete - {}", url);

    final HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("DELETE");
    con.setRequestProperty("Accept", "application/json");
    con.setConnectTimeout(10_000);
    con.setReadTimeout(15_000);

    final int code = con.getResponseCode();
    try {
      if (code == 204) {
        return true;
      }
      if (code == 404) {
        // optional: read error body if the server returns something
        drainQuietly(con.getErrorStream());
        return false;
      }
      final String body = readAllAsString(con.getErrorStream() != null ? con.getErrorStream() : con.getInputStream());
      throw new IOException("Unexpected HTTP " + code + " for DELETE " + url + " body=" + body);
    } finally {
      con.disconnect();
    }
  }

  /**
   * Variant that throws an IOException if not found.
   * Useful in workflows that require strict consistency checks.
   */
  public void deleteOrThrow(String shortCode)
      throws IOException {
    boolean removed = delete(shortCode);
    if (!removed) {
      throw new IOException("shortCode not found: " + shortCode);
    }
  }
}
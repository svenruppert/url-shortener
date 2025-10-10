package junit.com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.handler.ShortenHandler;
import com.svenruppert.urlshortener.core.ShortUrlMapping;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedirectHandlerTest
    implements HasLogger {

  protected static final String SHORT_CODE = "abc123";
  protected static final String ORIGINAL_URL = "https://heise.de";
  private InMemoryStore store;
  private ShortenHandler handler;

  @BeforeEach
  void setup() {
    store = new InMemoryStore();
    handler = new ShortenHandler(store);
  }

  @Test
  void handleValidShortenRequest()
      throws IOException {
    String inputJson = "{\"url\":\"https://heise.de\"}";
    MockHttpExchange exchange = new MockHttpExchange("POST", inputJson);

    handler.handle(exchange);

    assertEquals(200, exchange.getResponseCode());
    String response = exchange.getResponseBodyAsString();
    logger().info("response body .. " + response);
    assertTrue(response.contains("\"shortCode\":\""+SHORT_CODE+"\""));
    assertEquals(1, store.mappingCount());
  }

  @Test
  void handleInvalidJsonReturns400()
      throws IOException {
    MockHttpExchange exchange = new MockHttpExchange("POST", "{this is not json}");
    handler.handle(exchange);
    assertEquals(400, exchange.getResponseCode());
  }

  @Test
  void handleMissingUrlKeyReturns400()
      throws IOException {
    MockHttpExchange exchange = new MockHttpExchange("POST", "{\"link\":\"http://foo\"}");

    handler.handle(exchange);

    assertEquals(400, exchange.getResponseCode());
  }

  // In-Memory-Implementierung für Tests
  static class InMemoryStore
      implements UrlMappingStore, HasLogger {
    private final Map<String, ShortUrlMapping> mappings = new HashMap<>();
    private final Map<String, String> reverseLookup = new HashMap<>();

    @Override
    public ShortUrlMapping createMapping(String originalUrl) {
      logger().info("Creating mapping for {}", originalUrl);
      if (reverseLookup.containsKey(originalUrl)) {
        return mappings.get(reverseLookup.get(originalUrl));
      }
      String shortCode = generateShortCode(originalUrl);
      var shortUrlMapping = new ShortUrlMapping(
          SHORT_CODE,
          ORIGINAL_URL,
          Instant.now(),
          Optional.empty()
      );
      mappings.put(SHORT_CODE, shortUrlMapping);
      reverseLookup.put(originalUrl, shortCode);
      return shortUrlMapping;
    }

    @Override
    public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
      return Optional.ofNullable(mappings.get(shortCode));
    }

    @Override
    public boolean exists(String shortCode) {
      return mappings.containsKey(shortCode);
    }

    @Override
    public List<ShortUrlMapping> findAll() {
      return new ArrayList<>(mappings.values());
    }

    @Override
    public boolean delete(String shortCode) {
      if (mappings.containsKey(shortCode)) {
        var url = mappings.remove(shortCode).originalUrl();
        reverseLookup.remove(url);
        return true;
      }
      return false;
    }

    @Override
    public int mappingCount() {
      return mappings.size();
    }

    @Override
    public ShortUrlMapping createCustomMapping(String alias, String url) {
      return null;
    }

    private String generateShortCode(String url) {
      return Integer.toHexString(url.hashCode()).substring(0, 6);
    }
  }

  // Simulierter HttpExchange
  static class MockHttpExchange
      extends HttpExchange {
    private final ByteArrayInputStream request;
    private final ByteArrayOutputStream response = new ByteArrayOutputStream();
    private final com.sun.net.httpserver.Headers responseHeaders = new com.sun.net.httpserver.Headers();
    private final String method;
    private int responseCode;

    public MockHttpExchange(String method, String body) {
      this.method = method;
      this.request = new ByteArrayInputStream(body.getBytes());
    }

    @Override
    public InputStream getRequestBody() { return request; }

    @Override
    public OutputStream getResponseBody() { return response; }

    @Override
    public void sendResponseHeaders(int code, long len) { this.responseCode = code; }

    @Override
    public URI getRequestURI() { return URI.create("/shorten"); }

    @Override
    public com.sun.net.httpserver.Headers getRequestHeaders() { return new com.sun.net.httpserver.Headers(); }

    @Override
    public com.sun.net.httpserver.Headers getResponseHeaders() { return responseHeaders; }

    @Override
    public String getRequestMethod() { return method; }

    @Override
    public void close() { }

    @Override
    public InetSocketAddress getRemoteAddress() { return null; }

    @Override
    public InetSocketAddress getLocalAddress() { return null; }

    @Override
    public String getProtocol() { return "HTTP/1.1"; }

    @Override
    public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }

    @Override
    public void setStreams(InputStream input, OutputStream output) { }

    @Override
    public HttpPrincipal getPrincipal() {
      return null;
    }

    @Override
    public Object getAttribute(String name) { return null; }

    @Override
    public void setAttribute(String name, Object value) { }

    public int getResponseCode() {
      return responseCode;
    }

    public String getResponseBodyAsString() {
      return response.toString();
    }
  }
}
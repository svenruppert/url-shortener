package junit.com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.*;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.handler.ShortenHandler;
import com.svenruppert.urlshortener.api.store.InMemoryUrlMappingStore;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShortenHandlerTest implements HasLogger {

  private UrlMappingStore store;
  private ShortenHandler handler;

  @BeforeEach
  void setup() {
    store = new InMemoryUrlMappingStore();
    handler = new ShortenHandler(store);
  }

  @Test
  void handleValidShortenRequest()
      throws IOException {
    String originalUrl = "https://heise.de";
    String jsonBody = "{\"url\":\"" + originalUrl + "\"}";
    MockHttpExchange exchange = new MockHttpExchange("POST", jsonBody);

    handler.handle(exchange);

    assertEquals(200, exchange.getResponseCode());
    String body = exchange.getResponseBodyAsString();
    logger().info("response body .. " + body);
//    assertTrue(body.contains("\"url\":\"" + originalUrl + "\""));
    assertTrue(body.contains("\"shortCode\":\""));
    assertEquals(1, store.mappingCount());
  }

  @Test
  void handleSameUrlReturnsSameCode()
      throws IOException {
    String url = "https://example.org";
    MockHttpExchange first = new MockHttpExchange("POST", "{\"url\":\"" + url + "\"}");
    handler.handle(first);

    String firstCode = extractCode(first.getResponseBodyAsString());

    MockHttpExchange second = new MockHttpExchange("POST", "{\"url\":\"" + url + "\"}");
    handler.handle(second);
    String secondCode = extractCode(second.getResponseBodyAsString());

    assertEquals(firstCode, secondCode);
    assertEquals(2, store.mappingCount()); // kein zweiter Eintrag
  }

  @Test
  void handleMissingUrlKeyReturnsError()
      throws IOException {
    MockHttpExchange exchange = new MockHttpExchange("POST", "{\"wrong\":\"value\"}");
    handler.handle(exchange);
    assertEquals(400, exchange.getResponseCode());
  }

  @Test
  void handleEmptyBodyReturnsError()
      throws IOException {
    MockHttpExchange exchange = new MockHttpExchange("POST", "");
//    assertThrows(IOException.class, () -> handler.handle(exchange));
    handler.handle(exchange);
    assertEquals(400, exchange.getResponseCode());
  }

  private String extractCode(String json) {
    int start = json.indexOf("\"code\":\"") + 8;
    int end = json.indexOf("\"", start);
    return json.substring(start, end);
  }

  static class MockHttpExchange
      extends HttpExchange {
    private final ByteArrayOutputStream response = new ByteArrayOutputStream();
    private final ByteArrayInputStream request;
    private int responseCode;

    public MockHttpExchange(String method, String body) {
      this.request = new ByteArrayInputStream(body.getBytes());
    }

    @Override
    public InputStream getRequestBody() { return request; }

    @Override
    public OutputStream getResponseBody() { return response; }

    @Override
    public void sendResponseHeaders(int code, long length) { this.responseCode = code; }

    public int getResponseCode() { return responseCode; }

    public String getResponseBodyAsString() { return response.toString(); }

    // Dummy-Implementierungen
    @Override
    public URI getRequestURI() { return URI.create("http://localhost/shorten"); }

    @Override
    public Headers getRequestHeaders() { return new Headers(); }

    @Override
    public Request with(String headerName, List<String> headerValues) {
      return super.with(headerName, headerValues);
    }

    @Override
    public Headers getResponseHeaders() { return new Headers(); }

    @Override
    public String getRequestMethod() { return "POST"; }

    @Override
    public void close() { }

    @Override
    public InetSocketAddress getRemoteAddress() { return null; }

    @Override
    public InetSocketAddress getLocalAddress() { return null; }

    @Override
    public String getProtocol() { return null; }

    @Override
    public HttpContext getHttpContext() { return null; }

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
  }
}
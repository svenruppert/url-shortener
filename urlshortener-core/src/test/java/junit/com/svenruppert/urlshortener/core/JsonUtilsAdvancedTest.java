package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.ShortenRequest;
import com.svenruppert.urlshortener.core.ShortenResponse;
import com.svenruppert.urlshortener.core.ShortUrlMapping;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsAdvancedTest {

  @Nested
  class EscapeUnescape {
    @Test
    void roundTrip_ascii_and_quotes() {
      String original = "simple \"quoted\" \\ text";
      String escaped = JsonUtils.escape(original);
      assertEquals("simple \\\"quoted\\\" \\\\ text", escaped);
      String unescaped = JsonUtils.unescape(escaped);
      assertEquals(original, unescaped);
    }

    @Test
    void escape_controls_and_unicode() {
      String s = "\b\f\n\r\t\u20ac and snowman: \u2603"; // note: already escaped literals
      // Build raw containing those control chars and unicode
      String raw = "\b\f\n\r\t" + '€' + " and snowman: " + '\u2603';
      String escaped = JsonUtils.escape(raw);
      assertTrue(escaped.contains("\\b"));
      assertTrue(escaped.contains("\\f"));
      assertTrue(escaped.contains("\\n"));
      assertTrue(escaped.contains("\\r"));
      assertTrue(escaped.contains("\\t"));
      // non-ASCII should be encoded as \\uXXXX
      assertTrue(escaped.contains("\\u20ac"));
      assertTrue(escaped.contains("\\u2603"));
      assertEquals(raw, JsonUtils.unescape(escaped));
    }
  }

  @Test
  void toJsonArrayOfObjects_handles_nulls_and_order() {
    List<Map<String, String>> items = new ArrayList<>();
    Map<String, String> m1 = new LinkedHashMap<>();
    m1.put("a", "1");
    m1.put("b", null);
    items.add(m1);
    Map<String, String> m2 = new LinkedHashMap<>();
    m2.put("c", "3");
    items.add(m2);

    String json = JsonUtils.toJsonArrayOfObjects(items);
    assertEquals("[{\"a\":\"1\",\"b\":null},{\"c\":\"3\"}]", json);
  }

  @Test
  void toJsonListing_wraps_array_and_metadata() {
    List<Map<String, String>> items = List.of(Map.of("x", "y"));
    String json = JsonUtils.toJsonListing("test", 1, items);
    assertTrue(json.startsWith("{"));
    assertTrue(json.contains("\"mode\":\"test\""));
    assertTrue(json.contains("\"count\":1"));
    assertTrue(json.contains("\"items\":[{"));
    assertTrue(json.endsWith("}"));
  }

  @Nested
  class DTOJson {
    @Test
    void toJson_ShortenRequest_and_fromJson_roundtrip() {
      ShortenRequest req = new ShortenRequest("https://example.com?q=1", "alias-01");
      String json = JsonUtils.toJson(req);
      assertTrue(json.contains("\"url\":\"https://example.com?q=1\""));
      assertTrue(json.contains("\"alias\":\"alias-01\""));

      ShortenRequest parsed = JsonUtils.fromJson(json, ShortenRequest.class);
      assertEquals(req.getUrl(), parsed.getUrl());
      assertEquals(req.getShortURL(), parsed.getShortURL());
    }

    @Test
    void fromJson_invalid_missing_url_throws() {
      String invalid = "{\"alias\":\"x\"}";
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> JsonUtils.fromJson(invalid, ShortenRequest.class));
      assertTrue(ex.getMessage().toLowerCase().contains("url"));
    }

    @Test
    void toJson_ShortenResponse_and_ShortUrlMapping() {
      ShortenResponse res = new ShortenResponse("sc", "https://e");
      String resJson = JsonUtils.toJson(res);
      assertEquals("{\"shortCode\":\"sc\",\"originalUrl\":\"https://e\"}", resJson);

      ShortUrlMapping map = new ShortUrlMapping("sc", "https://e", Instant.parse("2020-01-01T00:00:00Z"), java.util.Optional.empty());
      String mapJson = JsonUtils.toJson(map);
      assertTrue(mapJson.contains("\"shortCode\":\"sc\""));
      assertTrue(mapJson.contains("\"originalUrl\":\"https://e\""));
      assertTrue(mapJson.contains("\"alias\":\"sc\""));
      assertTrue(mapJson.contains("\"createdAt\":\"2020-01-01T00:00:00Z\""));
    }
  }
}

package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.ShortenRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortenRequestJsonTest {

  @Test
  void toJson_with_null_alias_writes_null() {
    ShortenRequest req = new ShortenRequest("https://example.com/path?q=\"x\"", null);
    String json = req.toJson();
    assertTrue(json.contains("\"url\": \"https://example.com/path?q=\\\"x\\\"\""));
    assertTrue(json.contains("\"alias\": \"null\""));
  }

  @Test
  void toJson_with_alias_writes_string_and_escapes() {
    ShortenRequest req = new ShortenRequest("https://example.com", "a\tb\nc\"d");
    String json = req.toJson();
    assertTrue(json.contains("\"url\": \"https://example.com\""));
    // alias should be quoted and contain escapes
    assertTrue(json.contains("\"alias\": \"a\\tb\\nc\\\"d\""));
  }

  @Test
  void fromJson_accepts_null_alias_and_reads_url() {
    String json = "{\n  \"url\": \"https://e\",\n  \"alias\": null\n}";
    ShortenRequest req = JsonUtils.fromJson(json, ShortenRequest.class);
    assertEquals("https://e", req.getUrl());
    assertFalse(req.hasAlias());
    assertNull(req.getShortURL());
  }

  @Test
  void fromJson_rejects_non_object() {
    String json = "[]";
    assertThrows(IllegalArgumentException.class, () -> JsonUtils.fromJson(json, ShortenRequest.class));
  }
}

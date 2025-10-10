package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.urlshortener.core.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {

  @Test
  void parseValidJson() throws IOException {
    String json = "{\"key\":\"value\",\"number\":\"42\"}";
    ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    Map<String, String> result = JsonUtils.parseJson(input);
    assertEquals(2, result.size());
    assertEquals("value", result.get("key"));
    assertEquals("42", result.get("number"));
  }

  @Test
  void parseEmptyJsonReturnsEmptyMap() throws IOException {
    String json = "{}";
    ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    Map<String, String> result = JsonUtils.parseJson(input);
    assertTrue(result.isEmpty());
  }

  @Test
  void parseInvalidJsonThrowsException() {
    String json = "\"not a json object\"";
    ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    assertThrows(IOException.class, () -> JsonUtils.parseJson(input));
  }

  @Test
  void toJsonProducesValidOutput() {
    Map<String, String> map = Map.of("foo", "bar", "num", "123");
    String json = JsonUtils.toJson(map);
    assertTrue(json.contains("\"foo\":\"bar\""));
    assertTrue(json.contains("\"num\":\"123\""));
    assertTrue(json.startsWith("{") && json.endsWith("}"));
  }

  @Test
  void toJsonHandlesEscaping() {
    Map<String, String> map = Map.of("a", "b\"", "c\"", "d");
    String json = JsonUtils.toJson(map);
    assertTrue(json.contains("\\\""));
  }
}
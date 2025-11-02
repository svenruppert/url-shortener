package com.svenruppert.urlshortener.core;

import java.time.Instant;

public class ShortenRequest {
  private String url;
  private String shortURL;
  private Instant expiresAt;

  public ShortenRequest() {
  }

  public ShortenRequest(String url, String shortURL) {
    this.url = url;
    this.shortURL = shortURL;
    this.expiresAt = null;
  }

  public ShortenRequest(String url, String shortURL, Instant expiresAt) {
    this.url = url;
    this.shortURL = shortURL;
    this.expiresAt = expiresAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getShortURL() {
    return shortURL;
  }

  public void setShortURL(String shortURL) {
    this.shortURL = shortURL;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public boolean hasAlias() {
    return shortURL != null && !shortURL.isBlank();
  }

  @Override
  public String toString() {
    return "ShortenRequest{" +
        "url='" + url + '\'' +
        ", shortURL='" + shortURL + '\'' +
        ", expiresAt='" + expiresAt + '\'' +
        '}';
  }

  /**
   * Returns the JSON representation of this object.
   * No external libraries required, with correct escaping for simple cases.
   */
  //TODO Design error due to dependency on Utils class
  public String toJson() {
    var a = shortURL == null ? "\"null\"" : "\"" + JsonUtils.escape(shortURL) + "\"";
    var b = expiresAt == null ? "\"null\"" : "\"" + JsonUtils.escape(expiresAt.toString()) + "\"";
    return """
        {
          "url": "%s",
          "alias": %s,
          "expiresAt": %s
        }
        """.formatted(JsonUtils.escape(url), a, b
    );
  }
}
package com.svenruppert.urlshortener.core;

public class ShortenRequest {
  private String url;
  private String shortURL;

  public ShortenRequest() {
  }

  public ShortenRequest(String url, String shortURL) {
    this.url = url;
    this.shortURL = shortURL;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setShortURL(String shortURL) {
    this.shortURL = shortURL;
  }

  public String getShortURL() {
    return shortURL;
  }

  public String getUrl() {
    return url;
  }

  public boolean hasAlias() {
    return shortURL != null && !shortURL.isBlank();
  }

  @Override
  public String toString() {
    return "ShortenRequest{" +
        "url='" + url + '\'' +
        ", alias='" + shortURL + '\'' +
        '}';
  }

  /**
   * Returns the JSON representation of this object.
   * No external libraries required, with correct escaping for simple cases.
   */
  //TODO Design error due to dependency on Utils class
  public String toJson() {
    return """
        {
          "url": "%s",
          "alias": %s
        }
        """.formatted(
        JsonUtils.escape(url),
        shortURL == null ? "null" : "\"" + JsonUtils.escape(shortURL) + "\""
    );
  }
}
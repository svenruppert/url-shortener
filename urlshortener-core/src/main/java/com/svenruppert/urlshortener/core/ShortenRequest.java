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
   * Gibt die JSON-Repräsentation dieses Objekts zurück.
   * Ohne externe Libraries, mit korrektem Escaping für einfache Fälle.
   */
  //TODO Design Fehler, da Abhaengigkeit zu UtilsKlasse
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
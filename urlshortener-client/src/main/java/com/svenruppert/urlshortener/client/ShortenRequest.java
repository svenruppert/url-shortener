package com.svenruppert.urlshortener.client;

public class ShortenRequest {
  private String url;
  private String alias;

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public String toString() {
    return "ShortenRequest{" +
           "url='" + url + '\'' +
           ", alias='" + alias + '\'' +
           '}';
  }
}
package com.svenruppert.urlshortener.core.urlmapping;

import java.util.List;

public final class BulkShortenRequest {

  private List<String> urls;

  public BulkShortenRequest() {
  }

  public BulkShortenRequest(List<String> urls) {
    this.urls = urls;
  }

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(List<String> urls) {
    this.urls = urls;
  }
}

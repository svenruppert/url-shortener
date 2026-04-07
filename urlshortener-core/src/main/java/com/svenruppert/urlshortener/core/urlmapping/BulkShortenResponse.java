package com.svenruppert.urlshortener.core.urlmapping;

import java.util.List;

public final class BulkShortenResponse {

  private List<BulkShortenItemResult> results;
  private int total;
  private int succeeded;
  private int failed;

  public BulkShortenResponse() {
  }

  public BulkShortenResponse(List<BulkShortenItemResult> results) {
    this.results = results;
    this.total = results.size();
    this.succeeded = (int) results.stream().filter(BulkShortenItemResult::isSuccess).count();
    this.failed = this.total - this.succeeded;
  }

  public List<BulkShortenItemResult> getResults() {
    return results;
  }

  public void setResults(List<BulkShortenItemResult> results) {
    this.results = results;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getSucceeded() {
    return succeeded;
  }

  public void setSucceeded(int succeeded) {
    this.succeeded = succeeded;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(int failed) {
    this.failed = failed;
  }

  public static final class BulkShortenItemResult {

    private String originalUrl;
    private String shortCode;
    private boolean success;
    private String errorMessage;

    public BulkShortenItemResult() {
    }

    public static BulkShortenItemResult success(String originalUrl, String shortCode) {
      var r = new BulkShortenItemResult();
      r.originalUrl = originalUrl;
      r.shortCode = shortCode;
      r.success = true;
      return r;
    }

    public static BulkShortenItemResult error(String originalUrl, String errorMessage) {
      var r = new BulkShortenItemResult();
      r.originalUrl = originalUrl;
      r.success = false;
      r.errorMessage = errorMessage;
      return r;
    }

    public String getOriginalUrl() {
      return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
      this.originalUrl = originalUrl;
    }

    public String getShortCode() {
      return shortCode;
    }

    public void setShortCode(String shortCode) {
      this.shortCode = shortCode;
    }

    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(boolean success) {
      this.success = success;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }
  }
}

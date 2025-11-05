package com.svenruppert.urlshortener.core.urlmapping;


import java.net.URLEncoder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class UrlMappingListRequest {
  private String codePart;
  private boolean codeCaseSensitive;
  private String urlPart;
  private boolean urlCaseSensitive;
  private Instant from;
  private Instant to;
  private Integer page;
  private Integer size;
  private String sort; // createdAt|shortCode|originalUrl|expiresAt
  private String dir; // asc|desc

  private UrlMappingListRequest() {
  }

  public static Builder builder() {
    return new Builder();
  }

  private static void add(Map<String, String> m, String k, Object v) {
    if (v == null) return;
    String s = v.toString();
    if (!s.isBlank()) m.put(k, s);
  }

  private static void addTrue(Map<String, String> m, String k, boolean flag) {
    if (flag) m.put(k, "true");
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, UTF_8);
  }

  private static String toQuery(Map<String, String> params) {
    return params
        .entrySet()
        .stream()
        .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private Map<String, String> baseParams() {
    var q = new LinkedHashMap<String, String>();
    add(q, "code", codePart);
    addTrue(q, "codeCase", codeCaseSensitive);
    add(q, "url", urlPart);
    addTrue(q, "urlCase", urlCaseSensitive);
    add(q, "from", from);
    add(q, "to", to);
    return q;
  }

  public String toQueryStringForCount() {
    return toQuery(baseParams());
  }

  public String toQueryString() {
    var q = baseParams();
    if (page != null && page > 0) add(q, "page", page);
    if (size != null && size > 0) add(q, "size", size);
    add(q, "sort", sort);
    add(q, "dir", dir);
    return toQuery(q);
  }

  public String getCodePart() {
    return codePart;
  }

  public boolean isCodeCaseSensitive() {
    return codeCaseSensitive;
  }

  public String getUrlPart() {
    return urlPart;
  }

  public boolean isUrlCaseSensitive() {
    return urlCaseSensitive;
  }

  public Instant getFrom() {
    return from;
  }

  public Instant getTo() {
    return to;
  }

  public Integer getPage() {
    return page;
  }

  public Integer getSize() {
    return size;
  }

  public String getSort() {
    return sort;
  }

  public String getDir() {
    return dir;
  }

  @Override
  public String toString() {
    return "UrlMappingListRequest{"
        + "codePart='" + codePart + '\''
        + ", codeCaseSensitive=" + codeCaseSensitive
        + ", urlPart='" + urlPart + '\''
        + ", urlCaseSensitive=" + urlCaseSensitive
        + ", from=" + from
        + ", to=" + to
        + ", page=" + page
        + ", size=" + size
        + ", sort='" + sort + '\''
        + ", dir='" + dir + '\''
        + '}';
  }

  public static final class Builder {
    private final UrlMappingListRequest r = new UrlMappingListRequest();

    public Builder codePart(String v) {
      r.codePart = v;
      return this;
    }

    public Builder codeCaseSensitive(boolean b) {
      r.codeCaseSensitive = b;
      return this;
    }

    public Builder urlPart(String v) {
      r.urlPart = v;
      return this;
    }

    public Builder urlCaseSensitive(boolean b) {
      r.urlCaseSensitive = b;
      return this;
    }

    public Builder from(Instant v) {
      r.from = v;
      return this;
    }

    public Builder to(Instant v) {
      r.to = v;
      return this;
    }

    public Builder page(Integer v) {
      r.page = v;
      return this;
    }

    public Builder size(Integer v) {
      r.size = v;
      return this;
    }

    public Builder sort(String v) {
      r.sort = v;
      return this;
    } // createdAt|shortCode|originalUrl|expiresAt

    public Builder dir(String v) {
      r.dir = v;
      return this;
    } // asc|desc

    public UrlMappingListRequest build() {
      return r;
    }
  }
}
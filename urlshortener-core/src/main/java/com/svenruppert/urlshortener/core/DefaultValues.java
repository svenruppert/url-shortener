package com.svenruppert.urlshortener.core;

import java.util.regex.Pattern;

public final class DefaultValues {
  public static final int DEFAULT_SERVER_PORT = 9090;
  public static final String DEFAULT_SERVER_HOST = "localhost";
  public static final String DEFAULT_SERVER_PROTOCOL = "http";
  public static final String DEFAULT_SERVER_URL = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + DEFAULT_SERVER_PORT;

  public static final String PATH_REDIRECT = "/r";

  public static final String PATH_SHORTEN = "/shorten";

  public static final String PATH_DELETE = "/delete";

  public static final String PATH_LIST = "/list";
  public static final String PATH_LIST_ALL = "/list/all";
  public static final String PATH_LIST_EXPIRED = "/list/expired";
  public static final String PATH_LIST_ACTIVE = "/list/active";

  public static final Pattern ALLOWED_SHORTCODES = Pattern.compile("([-A-Za-z0-9]+)$");


  private DefaultValues() {
  }
}

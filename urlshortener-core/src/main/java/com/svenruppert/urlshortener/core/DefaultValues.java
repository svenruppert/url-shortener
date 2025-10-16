package com.svenruppert.urlshortener.core;

public final class DefaultValues {
  public static final int ADMIN_SERVER_PORT = 9090;
  public static final String ADMIN_SERVER_HOST = "localhost";
  public static final String ADMIN_SERVER_PROTOCOL = "http";
  public static final String ADMIN_SERVER_URL = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + ADMIN_SERVER_PORT;

  public static final int DEFAULT_SERVER_PORT = 8081;
  public static final String DEFAULT_SERVER_HOST = "localhost";
  public static final String DEFAULT_SERVER_PROTOCOL = "http";
  public static final String DEFAULT_SERVER_URL = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + DEFAULT_SERVER_PORT;

  public static final String PATH_REDIRECT = "/";

  public static final String PATH_ADMIN_SHORTEN = "/shorten";
  public static final String PATH_ADMIN_DELETE = "/delete";
  public static final String PATH_ADMIN_LIST = "/list";
  public static final String PATH_ADMIN_LIST_ALL = "/list/all";
  public static final String PATH_ADMIN_LIST_EXPIRED = "/list/expired";
  public static final String PATH_ADMIN_LIST_ACTIVE = "/list/active";

  private DefaultValues() {
  }
}

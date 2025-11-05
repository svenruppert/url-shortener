package com.svenruppert.urlshortener.core;

public final class DefaultValues {
  //TODO - must be editable by user
  public static final String SHORTCODE_BASE_URL = "https://3g3.eu/";

  public static final int ADMIN_SERVER_PORT = 9090;
  public static final String ADMIN_SERVER_HOST = "localhost";
  public static final String ADMIN_SERVER_PROTOCOL = "http";
  public static final String ADMIN_SERVER_URL = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + ADMIN_SERVER_PORT;

  public static final int DEFAULT_SERVER_PORT = 8081;
  public static final String DEFAULT_SERVER_HOST = "localhost";
  public static final String DEFAULT_SERVER_PROTOCOL = "http";
  public static final String DEFAULT_SERVER_URL = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + DEFAULT_SERVER_PORT;

  public static final String CONTENT_TYPE = "Content-Type";
  public static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  public static final String APPLICATION_JSON = "application/json";
  public static final String ACCEPT = "Accept";


  public static final String PATH_REDIRECT = "/";

  public static final String PATH_ADMIN_SHORTEN = "/shorten";
  public static final String PATH_ADMIN_DELETE = "/delete";
  public static final String PATH_ADMIN_LIST = "/list";
  public static final String PATH_ADMIN_LIST_ALL = "/list/all";
  public static final String PATH_ADMIN_LIST_EXPIRED = "/list/expired";
  public static final String PATH_ADMIN_LIST_ACTIVE = "/list/active";
  public static final String PATH_ADMIN_LIST_COUNT = "/list/count";

  public static final String PATH_ADMIN_STORE_INFO = "/store/info";

  public static final String PATH_ADMIN_PREFERENCES_COLUMNS        = "/admin/preferences/columns";
  public static final String PATH_ADMIN_PREFERENCES_COLUMNS_EDIT   = "/admin/preferences/columns/edit";
  public static final String PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE = "/admin/preferences/columns/single";



  public static final String PATTERN_DATE_TIME = "yyyy.MM.dd HH:mm";

  public static final String STORAGE_DATA_PATH = "data";

  private DefaultValues() {
  }
}

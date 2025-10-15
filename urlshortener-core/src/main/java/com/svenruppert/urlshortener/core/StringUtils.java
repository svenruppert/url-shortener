package com.svenruppert.urlshortener.core;

public class StringUtils {

  private StringUtils() {
  }

  public static boolean isNullOrBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}

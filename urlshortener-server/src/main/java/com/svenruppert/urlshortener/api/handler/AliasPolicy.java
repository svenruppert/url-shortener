package com.svenruppert.urlshortener.api.handler;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class AliasPolicy {
  private AliasPolicy() {
  }

  private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
  private static final Set<String> RESERVED = Set.of("r", "list", "delete");

  public static String normalize(String alias) {
    return alias.toLowerCase(Locale.ROOT);
  }

  public static boolean isValid(String alias) {
    return alias != null
        && ALIAS.matcher(alias).matches()
        && RESERVED.stream().noneMatch(alias::equalsIgnoreCase);
  }
}

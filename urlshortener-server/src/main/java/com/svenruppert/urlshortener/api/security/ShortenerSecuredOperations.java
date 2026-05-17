package com.svenruppert.urlshortener.api.security;

import com.svenruppert.vaadin.security.authorization.annotations.RequiresPermission;

import java.lang.reflect.Method;

/**
 * Marker class whose annotated methods carry the {@link RequiresPermission}
 * declarations for every protected REST endpoint. The {@link Method} objects
 * are used as {@code AnnotatedElement} inputs for
 * {@code RestAuthorizationFilter.authorizeAndHandle(...)}.
 * <p>
 * The methods do nothing — they exist solely to attach the permission metadata
 * without modifying the existing HTTP handler classes.
 */
public final class ShortenerSecuredOperations {

  private ShortenerSecuredOperations() {
  }

  @RequiresPermission("link:create")
  public static void createLink() {
  }

  @RequiresPermission("link:create")
  public static void bulkCreateLinks() {
  }

  @RequiresPermission("link:create")
  public static void bulkValidateLinks() {
  }

  @RequiresPermission("link:read:own")
  public static void listLinks() {
  }

  @RequiresPermission("link:read:own")
  public static void listLinksCount() {
  }

  @RequiresPermission("link:update:own")
  public static void editLink() {
  }

  @RequiresPermission("link:delete:own")
  public static void deleteLink() {
  }

  @RequiresPermission("link:update:own")
  public static void toggleActive() {
  }

  @RequiresPermission("admin:access")
  public static void storeInfo() {
  }

  @RequiresPermission("link:create")
  public static void importValidate() {
  }

  @RequiresPermission("link:create")
  public static void importApply() {
  }

  @RequiresPermission("link:read:own")
  public static void importConflicts() {
  }

  @RequiresPermission("link:read:own")
  public static void importInvalid() {
  }

  @RequiresPermission("link:read:own")
  public static void preferencesColumns() {
  }

  @RequiresPermission("link:read:own")
  public static void preferencesColumnsBulk() {
  }

  @RequiresPermission("link:read:own")
  public static void preferencesColumnsSingle() {
  }

  @RequiresPermission("link:stats:own")
  public static void statisticsCount() {
  }

  @RequiresPermission("link:stats:own")
  public static void statisticsHourly() {
  }

  @RequiresPermission("link:stats:own")
  public static void statisticsDaily() {
  }

  @RequiresPermission("link:stats:own")
  public static void statisticsTimeline() {
  }

  @RequiresPermission("link:stats:own")
  public static void statisticsConfig() {
  }

  @RequiresPermission("link:stats:all")
  public static void statisticsDebug() {
  }

  @RequiresPermission("link:stats:own")
  public static void statisticsExport() {
  }

  @RequiresPermission("link:stats:all")
  public static void statisticsImport() {
  }

  @RequiresPermission("user:read")
  public static void listUsers() {
  }

  @RequiresPermission("user:create")
  public static void createUser() {
  }

  @RequiresPermission("user:update")
  public static void updateUser() {
  }

  @RequiresPermission("user:delete")
  public static void deleteUser() {
  }

  @RequiresPermission("user:update")
  public static void resetUserPassword() {
  }

  @RequiresPermission("user:update")
  public static void unlockUser() {
  }

  @RequiresPermission("admin:access")
  public static void listAudit() {
  }

  public static Method method(String name) {
    try {
      return ShortenerSecuredOperations.class.getDeclaredMethod(name);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Unknown secured operation: " + name, e);
    }
  }
}

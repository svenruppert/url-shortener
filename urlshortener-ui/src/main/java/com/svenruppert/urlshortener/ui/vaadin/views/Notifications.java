package com.svenruppert.urlshortener.ui.vaadin.views;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

public final class Notifications {

  private static final int DEFAULT_DURATION = 3000;
  private static final Notification.Position DEFAULT_POSITION =
      Notification.Position.BOTTOM_START;

  private Notifications() {
  }

  // =========================
  // Core helpers
  // =========================

  private static void show(String message,
                           NotificationVariant variant,
                           int duration,
                           Notification.Position position) {

    Notification n = new Notification(message, duration, position);

    if (variant != null) {
      n.addThemeVariants(variant);
    }

    n.open();
  }

  private static void success(String message) {
    show(message, NotificationVariant.LUMO_SUCCESS,
         DEFAULT_DURATION, DEFAULT_POSITION);
  }

  private static void error(String message) {
    show(message, NotificationVariant.LUMO_ERROR,
         DEFAULT_DURATION, DEFAULT_POSITION);
  }

  private static void warning(String message) {
    show(message, NotificationVariant.LUMO_WARNING,
         DEFAULT_DURATION, DEFAULT_POSITION);
  }

  private static void info(String message) {
    show(message, null,
         DEFAULT_DURATION, DEFAULT_POSITION);
  }

  // =========================
  // Public API
  // =========================

  public static void saved() {
    success("Saved successfully");
  }

  public static void savedAndNotSaved(long success, long failed) {
    success("Saved: " + success + " | Failed: " + failed);
  }

  public static void updatedAndNotUpdated(long success, long failed) {
    success("Updated: " + success + " | Failed: " + failed);
  }

  public static void deletedAndNotDeleted(long success, long failed) {
    success("Deleted: " + success + " | Failed: " + failed);
  }

  public static void noChanges() {
    info("No changes detected");
  }

  public static void noValidShortCode() {
    warning("No valid short code");
  }

  public static void noDateSelected() {
    warning("No date selected");
  }

  public static void noSelection() {
    warning("No entries selected");
  }

  public static void loadingFailed() {
    error("Loading failed");
  }

  public static void shortCodeDeleted() {
    success("Short code deleted");
  }

  public static void shortCodeNotFound() {
    warning("Short code not found");
  }

  public static void shortCodeCopied() {
    info("Short code copied");
  }

  public static void urlCopied() {
    info("URL copied");
  }

  public static void statusUpdatedOK() {
    success("Status updated");
  }

  public static void statusUpdatedFailed(Exception ex) {
    error("Error updating status: " + safeMessage(ex));
  }

  public static void operationFailed(Exception ex) {
    error("Operation failed: " + safeMessage(ex));
  }

  public static void loginCurrentlyDisabled() {
    warning("Login is currently disabled. Check server configuration.");
  }

  public static void loginCurrentlyNotConfigured() {
    warning("Login is not configured. Verify configuration file.");
  }

  // =========================
  // Safety helper
  // =========================

  private static String safeMessage(Exception ex) {
    return (ex == null || ex.getMessage() == null)
        ? "Unknown error"
        : ex.getMessage();
  }
}

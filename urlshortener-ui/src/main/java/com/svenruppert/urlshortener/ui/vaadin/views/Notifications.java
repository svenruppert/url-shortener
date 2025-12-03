package com.svenruppert.urlshortener.ui.vaadin.views;

import com.vaadin.flow.component.notification.Notification;

public class Notifications {

  private Notifications() {
  }

  public static void saved() {
    Notification.show("Saved..");
  }

  public static void savedAndNotSaved(long success, long failed) {
    Notification.show("Saved: success:" + success + " | failed: " + failed,
                      3500, Notification.Position.TOP_CENTER);
  }

  public static void updatedAndNotUpdated(long success, long failed) {
    Notification.show("Updated: success:" + success + " | failed: " + failed,
                      3500, Notification.Position.TOP_CENTER);
  }

  public static void deletedAndNotDeleted(long success, long failed) {
    Notification.show("Deleted: success:" + success + " | failed: " + failed,
                      3500, Notification.Position.TOP_CENTER);
  }

  public static void noChanges() {
    Notification.show("No Changes..");
  }

  public static void noValidShortCode() {
    Notification.show("No valid Short Code..");
  }

  public static void noDateSelected() {
    Notification.show("No Date selected");
  }

  public static void noSelection() {
    Notification.show("No entries selected");
  }

  public static void loadingFailed() {
    Notification.show("Loading failed");
  }

  public static void shortCodeDeleted() {
    Notification.show("ShortCode deleted.");
  }

  public static void shortCodeDNotFound() {
    Notification.show("ShortCode not found.");
  }

  public static void shortCodeCopied() {
    Notification.show("Shortcode copied");
  }

  public static void urlCopied() {
    Notification.show("URL copied");
  }

  public static void statusUpdatedOK() {
    Notification.show("Status updated", 2000, Notification.Position.TOP_CENTER);
  }

  public static void statusUpdatedFailed(Exception ex) {
    Notification.show("Error updating active status: " + ex.getMessage(),
                      3000, Notification.Position.TOP_CENTER);
  }

  public static void operationFailed(Exception ex) {
    Notification.show("Operation Failed: " + ex.getMessage(),
                      3000, Notification.Position.TOP_CENTER);
  }

  public static void loginCurrentlyDisabled() {
    Notification.show(
        "Login is currently disabled. Please check the server configuration.",
        3000,
        Notification.Position.MIDDLE
    );
  }

  public static void loginCurrentlyNotConfigured() {
    Notification.show(
        "Login is not configured. Please verify that the configuration file has been loaded.",
        3000,
        Notification.Position.MIDDLE
    );
  }


}

package com.svenruppert.urlshortener.ui.vaadin.components;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class ExpiryBadgeFactory {

  private ExpiryBadgeFactory() {
  }

  public record Status(String text, String theme) { }

  /**
   * Computes the expiry status label and its theme colour.
   */
  public static  Status computeStatusText(Optional<Instant> expiresAt) {
    return expiresAt.map(ts -> {
      long d = Duration.between(Instant.now(), ts).toDays();
      if (d < 0) return new Status("Expired", "error");
      if (d == 0) return new Status("Expires today", "warning");
      if (d <= 1) return new Status("Expires in " + d + " day", "warning");
      if (d <= 2) return new Status("Expires in " + d + " days", "warning");
      return new Status("Valid (" + d + " days left)", "success");
    }).orElse(new Status("No expiry", "contrast"));
  }
}

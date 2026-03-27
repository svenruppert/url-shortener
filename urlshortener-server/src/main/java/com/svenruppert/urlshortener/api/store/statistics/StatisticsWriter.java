package com.svenruppert.urlshortener.api.store.statistics;

import com.svenruppert.urlshortener.core.statistics.RedirectEvent;

/**
 * Interface for recording redirect events.
 * Implementations should be optimized for high-throughput writes.
 */
public interface StatisticsWriter {

  /**
   * Records a redirect event.
   * This method should be non-blocking and return quickly.
   *
   * @param event the redirect event to record
   */
  void recordEvent(RedirectEvent event);

  /**
   * Flushes any buffered events to persistent storage.
   * Called during shutdown or when immediate persistence is required.
   */
  void flush();
}

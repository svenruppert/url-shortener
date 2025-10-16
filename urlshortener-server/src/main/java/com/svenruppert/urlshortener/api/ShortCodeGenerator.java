package com.svenruppert.urlshortener.api;

import java.util.concurrent.atomic.AtomicLong;

public final class ShortCodeGenerator {

  private final AtomicLong counter;

  public ShortCodeGenerator(long initialValue) {
    if (initialValue <= 99) {
      this.counter = new AtomicLong(100);
    } else {
      this.counter = new AtomicLong(initialValue);
    }
  }

  public String nextCode() {
    long id = counter.getAndIncrement();
    return String.valueOf(id);
  }

  public long currentId() {
    return counter.get();
  }
}
package com.svenruppert.urlshortener.api;

import com.svenruppert.urlshortener.core.Base62Encoder;

import java.util.concurrent.atomic.AtomicLong;

public final class ShortCodeGenerator {

  private final AtomicLong counter;

  public ShortCodeGenerator(long initialValue) {
    this.counter = new AtomicLong(initialValue);
  }

  public String nextCode() {
    long id = counter.getAndIncrement();
    return Base62Encoder.encode(id);
  }

  public long currentId() {
    return counter.get();
  }
}
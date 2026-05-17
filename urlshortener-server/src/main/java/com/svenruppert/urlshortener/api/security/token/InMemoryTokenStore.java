package com.svenruppert.urlshortener.api.security.token;

import com.svenruppert.urlshortener.api.security.user.ShortenerUser;
import com.svenruppert.vaadin.security.logout.SubjectId;
import com.svenruppert.vaadin.security.logout.SubjectSessionRegistry;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryTokenStore implements SubjectSessionRegistry {

  public record Metadata(ShortenerUser user, Instant createdAt, Instant lastActivityAt) {
  }

  private final ConcurrentMap<String, Metadata> tokens = new ConcurrentHashMap<>();
  private final ConcurrentMap<SubjectId, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final Clock clock;

  public InMemoryTokenStore() {
    this(Clock.systemUTC());
  }

  public InMemoryTokenStore(Clock clock) {
    this.clock = clock;
  }

  public String issue(ShortenerUser user) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = HexFormat.of().formatHex(bytes);
    Instant now = Instant.now(clock);
    tokens.put(token, new Metadata(user, now, now));
    sessionsByUser
        .computeIfAbsent(SubjectId.of(user.username()), k -> ConcurrentHashMap.newKeySet())
        .add(token);
    return token;
  }

  public Optional<ShortenerUser> resolve(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    Metadata metadata = tokens.get(token);
    return metadata == null ? Optional.empty() : Optional.of(metadata.user());
  }

  public Optional<Metadata> resolveMetadata(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(tokens.get(token));
  }

  public Optional<Instant> markActivity(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    Instant now = Instant.now(clock);
    final Instant[] previous = new Instant[1];
    Metadata updated = tokens.computeIfPresent(token, (k, current) -> {
      previous[0] = current.lastActivityAt();
      return new Metadata(current.user(), current.createdAt(), now);
    });
    return updated == null ? Optional.empty() : Optional.ofNullable(previous[0]);
  }

  public void revoke(String token) {
    if (token == null) {
      return;
    }
    Metadata removed = tokens.remove(token);
    if (removed != null) {
      Set<String> entries = sessionsByUser.get(SubjectId.of(removed.user().username()));
      if (entries != null) {
        entries.remove(token);
      }
    }
  }

  @Override
  public void register(SubjectId subjectId, String sessionId) {
    sessionsByUser
        .computeIfAbsent(subjectId, k -> ConcurrentHashMap.newKeySet())
        .add(sessionId);
  }

  @Override
  public void unregister(SubjectId subjectId, String sessionId) {
    Set<String> entries = sessionsByUser.get(subjectId);
    if (entries != null) {
      entries.remove(sessionId);
    }
  }

  @Override
  public Collection<String> sessionsOf(SubjectId subjectId) {
    Set<String> entries = sessionsByUser.get(subjectId);
    return entries == null ? List.of() : List.copyOf(entries);
  }

  @Override
  public Collection<String> clearAll(SubjectId subjectId) {
    Set<String> entries = sessionsByUser.remove(subjectId);
    if (entries == null) {
      return List.of();
    }
    Collection<String> copy = List.copyOf(entries);
    copy.forEach(tokens::remove);
    return copy;
  }
}

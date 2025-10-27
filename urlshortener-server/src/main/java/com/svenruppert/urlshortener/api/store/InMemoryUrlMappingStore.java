package com.svenruppert.urlshortener.api.store;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.svenruppert.urlshortener.api.store.UrlMappingFilter.*;
import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Comparator.*;

public class InMemoryUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final Map<String, ShortUrlMapping> store = new ConcurrentHashMap<>();
  private final ShortCodeGenerator generator;

  public InMemoryUrlMappingStore(ShortCodeGenerator generator) {
    this.generator = generator;
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String originalUrl) {
    logger().info("originalUrl: {} ->", originalUrl);
    return createMapping(null, originalUrl);
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    logger().info("findByShortCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("findByShortCode normalized for search '{}'", shortCode);
    return Optional.ofNullable(store.get(normalized));
  }

  @Override
  public boolean exists(String shortCode) {
    logger().info("exists '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("exists - normalized for search '{}'", shortCode);
    return store.containsKey(normalized);
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    return new ArrayList<>(store.values());
  }

  @Override
  public List<ShortUrlMapping> find(UrlMappingFilter filter) {
    Objects.requireNonNull(filter, "filter");

    // 1) Raw filtering
    List<ShortUrlMapping> tmp = new ArrayList<>();
    for (ShortUrlMapping mapping : store.values()) {
      if (matches(filter, mapping)) tmp.add(mapping);
    }

    // 2) Sorting
    Comparator<ShortUrlMapping> cmp = buildComparator(filter);
    if (cmp != null) {
      tmp.sort(cmp);
      if (filter.direction().orElse(Direction.ASC) == Direction.DESC) {
        Collections.reverse(tmp);
      }
    }

    // 3) Paging
    int from = Math.max(0, filter.offset().orElse(0));
    int lim  = filter.limit().orElse(MAX_VALUE);
    if (from >= tmp.size()) return List.of();
    int to = Math.min(tmp.size(), from + Math.max(0, lim));
    return tmp.subList(from, to);
  }

  @Override
  public int count(UrlMappingFilter filter) {
    int c = 0;
    for (ShortUrlMapping m : store.values()) {
      if (matches(filter, m)) c++;
    }
    return c;
  }
  private boolean matches(UrlMappingFilter f, ShortUrlMapping m) {
    return matchCode(f, m) && matchUrl(f, m) && matchCreated(f, m);
  }

  private boolean matchCode(UrlMappingFilter f, ShortUrlMapping m) {
    return f.codePart()
        .map(part -> {
          String code = normalize(m.shortCode());
          if (f.codeCaseSensitive()) {
            return code.contains(part);
          } else {
            return code.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
          }
        })
        .orElse(true);
  }

  private boolean matchUrl(UrlMappingFilter f, ShortUrlMapping m) {
    return f.urlPart()
        .map(part -> {
          String url = m.originalUrl();
          if (url == null) return false;
          return f.urlCaseSensitive()
              ? url.contains(part)
              : url.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
        })
        .orElse(true);
  }

  private boolean matchCreated(UrlMappingFilter f, ShortUrlMapping m) {
    var created = m.createdAt();
    if (f.createdFrom().isPresent() && created.isBefore(f.createdFrom().get())) return false;
    if (f.createdTo().isPresent()   && created.isAfter(f.createdTo().get()))   return false;
    return true;
  }

  private Comparator<ShortUrlMapping> buildComparator(UrlMappingFilter f) {
    return f.sortBy().map(sb -> switch (sb) {
      case CREATED_AT -> Comparator.comparing(ShortUrlMapping::createdAt);
      case SHORT_CODE -> Comparator.comparing(
          ShortUrlMapping::shortCode,
          nullsFirst(naturalOrder())
      );
      case ORIGINAL_URL -> Comparator.comparing(
          ShortUrlMapping::originalUrl,
          nullsFirst(naturalOrder())
      );
      case EXPIRES_AT -> Comparator.comparing(
          (ShortUrlMapping m) -> m.expiresAt().orElse(null),
          nullsLast(naturalOrder())
      );
    }).orElse(null);
  }


  @Override
  public boolean delete(String shortCode) {
    logger().info("delete '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("delete - normalized for deletion '{}'", normalized);
    return store.remove(normalized) != null;
  }

  @Override
  public int mappingCount() {
    return store.size();
  }

  @Override
  public Result<ShortUrlMapping> createMapping(String alias, String url) {
    String shortCode;
    logger().info("createMapping - alias - '{}' / url - '{}' ", alias, url);
    if (!isNullOrBlank(alias)) {
      logger().info("Alias is set to '{}'", alias);
      var aliasCheck = AliasPolicy.validate(alias);
      if (!aliasCheck.valid()) {
        // optional: maschinenlesbarer Code zusätzlich
        String code = switch (aliasCheck.reason()) {
          case NULL_OR_BLANK -> "ALIAS_EMPTY";
          case TOO_SHORT     -> "ALIAS_TOO_SHORT";
          case TOO_LONG      -> "ALIAS_TOO_LONG";
          case INVALID_CHARS -> "ALIAS_INVALID_CHARS";
          case RESERVED      -> "ALIAS_RESERVED";
        };
        var errorMessage = toJson("400", aliasCheck.reason().defaultMessage, code);
        logger().warn("createMapping - {}", errorMessage);
        return Result.failure(errorMessage);
      }
      logger().info("Alias is valid");
      final String normalizedAlias = normalize(alias);
      logger().info("normalizedAlias Alias is'{}'", alias);
      if (store.containsKey(normalizedAlias)) {
        var errorMessage = toJson("409", "normalizedAlias already in use");
        logger().warn("createMapping - {}", errorMessage);
        return Result.failure(errorMessage);
      } else {
        logger().info("normalizedAlias is not in store.. ");
      }
      shortCode = normalizedAlias;
    } else {
      logger().info("Alias is NOT set");
      String generated = generator.nextCode();
      generated = normalize(generated);
      logger().info("generated shortCode '{}'", generated);
      while (existsByCode(generated)) {
        generated = generator.nextCode();
        generated = normalize(generated);
        logger().info("generated shortCode (loop) '{}'", generated);
      }
      shortCode = generated;
    }
    logger().info("final shortCode '{}'", shortCode);
    ShortUrlMapping shortMapping = new ShortUrlMapping(
        shortCode,
        url,
        Instant.now(),
        Optional.empty()
    );
    store.put(shortCode, shortMapping);
    return Result.success(shortMapping);
  }

  @Override
  public boolean existsByCode(String shortCode) {
    logger().info("existsByCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("existsByCode - normalized for search '{}'", normalized);
    return store.containsKey(normalized);
  }
}
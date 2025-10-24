package com.svenruppert.urlshortener.api.store;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.ShortUrlMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

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
    String normalized = AliasPolicy.normalize(shortCode);
    logger().info("findByShortCode normalized for search '{}'", shortCode);
    return Optional.ofNullable(store.get(normalized));
  }

  @Override
  public boolean exists(String shortCode) {
    logger().info("exists '{}'", shortCode);
    String normalized = AliasPolicy.normalize(shortCode);
    logger().info("exists - normalized for search '{}'", shortCode);
    return store.containsKey(normalized);
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    return new ArrayList<>(store.values());
  }

  @Override
  public boolean delete(String shortCode) {
    logger().info("delete '{}'", shortCode);
    String normalized = AliasPolicy.normalize(shortCode);
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
        return Result.failure(errorMessage); // führt zu HTTP 400 mit diesem Body
      }
      logger().info("Alias is valid");
      final String normalizedAlias = AliasPolicy.normalize(alias);
      logger().info("normalizedAlias Alias is'{}'", alias);
      if (store.containsKey(normalizedAlias)) {
        var errorMessage = toJson("409", "normalizedAlias already in use");
        logger().warn("createMapping - {}", errorMessage);
        return Result.failure(errorMessage);
      } else {
        logger().info("normalizedAlias is not in store.. ");
      }
      shortCode = normalizedAlias; // Alias wird verwendet
    } else {
      logger().info("Alias is NOT set");
      String generated = generator.nextCode();
      generated = AliasPolicy.normalize(generated);
      logger().info("generated shortCode '{}'", generated);
      while (existsByCode(generated)) {
        generated = generator.nextCode();
        generated = AliasPolicy.normalize(generated);
        logger().info("generated shortCode '{}'", generated);
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
    String normalized = AliasPolicy.normalize(shortCode);
    logger().info("existsByCode - normalized for search '{}'", normalized);
    return store.containsKey(normalized);
  }
}
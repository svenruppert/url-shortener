package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.model.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * Reusable, memory-agnostic generation of a ShortUrlMapping.
 */
public final class MappingCreator
    implements HasLogger {

  private final ShortCodeGenerator generator;
  private final ExistsByCode exists;
  private final PutMapping store;
  private final Clock clock;
  private final Function<ErrorInfo, String> errorMapper; // maps ErrorInfo → e.g. your toJson(...)
  public MappingCreator(ShortCodeGenerator generator,
                        ExistsByCode exists,
                        PutMapping store,
                        Clock clock,
                        Function<ErrorInfo, String> errorMapper) {
    this.generator = Objects.requireNonNull(generator);
    this.exists = Objects.requireNonNull(exists);
    this.store = Objects.requireNonNull(store);
    this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
    this.errorMapper = Objects.requireNonNull(errorMapper);
  }

  /**
   * Main method: creates mapping with optional alias.
   */
  public Result<ShortUrlMapping> create(String alias, String url, Instant expiredAt) {
    logger().info("createMapping - alias='{}' / url='{}' / expiredAt='{}'", alias, url, expiredAt);

    final String shortCode;
    if (!isNullOrBlank(alias)) {
      var aliasCheck = AliasPolicy.validate(alias);
      if (aliasCheck.failed()) {
        var reason = aliasCheck.reason();
        var reasonCode = switch (reason) {
          case NULL_OR_BLANK -> "ALIAS_EMPTY";
          case TOO_SHORT -> "ALIAS_TOO_SHORT";
          case TOO_LONG -> "ALIAS_TOO_LONG";
          case INVALID_CHARS -> "ALIAS_INVALID_CHARS";
          case RESERVED -> "ALIAS_RESERVED";
        };
        var errorJson = errorMapper.apply(new ErrorInfo("400", reason.defaultMessage, reasonCode));
        logger().warn("createMapping - {}", errorJson);
        return Result.failure(errorJson);
      }
      var normalized = normalize(alias);
      if (exists.test(normalized)) {
        var errorJson = errorMapper.apply(new ErrorInfo("409", "normalizedAlias already in use", "ALIAS_CONFLICT"));
        logger().warn("createMapping - {}", errorJson);
        return Result.failure(errorJson);
      }
      shortCode = normalized;
    } else {
      logger().info("alias is null or blank");
      String gen = normalize(generator.nextCode());
      logger().info("next normalized alias .. {} " , gen);
      while (exists.test(gen)) {
        gen = normalize(generator.nextCode());
        logger().info("next normalized alias .. {} " , gen);
      }
      shortCode = gen;
    }
    var mapping = new ShortUrlMapping(shortCode, url, Instant.now(clock), Optional.ofNullable(expiredAt));
    logger().info("mapping to store .. {}", mapping);
    store.accept(mapping);
    logger().info("mapping stored .. {}", mapping);
    return Result.success(mapping);
  }
  @FunctionalInterface
  public interface ExistsByCode {
    boolean test(String code);
  }

  @FunctionalInterface
  public interface PutMapping {
    void accept(ShortUrlMapping m);
  }

  /**
   * Error information that the caller (e.g. HTTP layer) can freely map into JSON/logs.
   */
  public record ErrorInfo(String httpStatus, String message, String reasonCode) { }

}

package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.api.utils.QueryUtils;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.audit.AuditEventView;
import com.svenruppert.vaadin.security.audit.AccessDenied;
import com.svenruppert.vaadin.security.audit.AccessGranted;
import com.svenruppert.vaadin.security.audit.ActionDenied;
import com.svenruppert.vaadin.security.audit.AuditEvent;
import com.svenruppert.vaadin.security.audit.AuditQuery;
import com.svenruppert.vaadin.security.audit.BootstrapAdminCreated;
import com.svenruppert.vaadin.security.audit.BootstrapTokenRejected;
import com.svenruppert.vaadin.security.audit.BruteForceLimitReached;
import com.svenruppert.vaadin.security.audit.LoginFailed;
import com.svenruppert.vaadin.security.audit.LoginSucceeded;
import com.svenruppert.vaadin.security.audit.LogoutPerformed;
import com.svenruppert.vaadin.security.audit.RoleAssigned;
import com.svenruppert.vaadin.security.audit.RoleRevoked;
import com.svenruppert.vaadin.security.audit.SecurityAuditService;
import com.svenruppert.vaadin.security.audit.SessionCreated;
import com.svenruppert.vaadin.security.audit.SessionExpired;
import com.svenruppert.vaadin.security.audit.SessionInvalidated;
import com.svenruppert.vaadin.security.audit.UserCreated;
import com.svenruppert.vaadin.security.audit.UserDeleted;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only view onto the in-memory security audit ring buffer. Wraps the
 * underlying typed {@link AuditEvent}s into flat {@link AuditEventView}
 * records so the UI grid can render any variant with five columns.
 * <p>
 * Filter via query parameters:
 * <ul>
 *   <li>{@code type} — comma-separated event simple-names (e.g.
 *       {@code LoginFailed,LoginSucceeded})</li>
 *   <li>{@code subject} — username/subjectId substring match</li>
 *   <li>{@code from} / {@code to} — ISO-8601 instants</li>
 *   <li>{@code limit} — max rows (default {@value #DEFAULT_LIMIT})</li>
 * </ul>
 */
public final class AuditHandler implements HttpHandler, HasLogger {

  private static final int DEFAULT_LIMIT = 200;
  private static final int MAX_LIMIT = 1000;

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    try {
      if (!"GET".equals(exchange.getRequestMethod())) {
        writeJson(response, 405, Map.of("error", "method_not_allowed"));
        response.writeTo(exchange);
        return;
      }
      var query = QueryUtils.parseQueryParams(
          Optional.ofNullable(exchange.getRequestURI().getRawQuery()).orElse(""));

      Set<Class<? extends AuditEvent>> types = parseTypes(QueryUtils.first(query, "type"));
      String subject = blankToNull(QueryUtils.first(query, "subject"));
      Instant from = parseInstant(QueryUtils.first(query, "from"));
      Instant to = parseInstant(QueryUtils.first(query, "to"));
      int limit = parseLimit(QueryUtils.first(query, "limit"));

      AuditQuery q = new AuditQuery(types, subject, from, to, limit);
      SecurityAuditService audit = SecurityServiceResolver.securityAuditService();
      List<AuditEvent> events = audit.query(q);

      List<AuditEventView> rows = events.stream()
          .map(AuditHandler::toView)
          .collect(Collectors.toList());
      writeJson(response, 200, Map.of("events", rows));
    } catch (DateTimeParseException e) {
      writeJson(response, 400, Map.of("error", "invalid_date"));
    } catch (RuntimeException e) {
      logger().warn("audit query failed: {}", e.getMessage());
      writeJson(response, 500, Map.of("error", "internal_error"));
    }
    response.writeTo(exchange);
  }

  // ---------- projection ----------

  private static AuditEventView toView(AuditEvent event) {
    return switch (event) {
      case LoginSucceeded e -> view(e.timestamp(), "LoginSucceeded",
          e.username(), e.clientAddress(), e.sessionId());
      case LoginFailed e -> view(e.timestamp(), "LoginFailed",
          e.username(), e.clientAddress(), e.reason());
      case LogoutPerformed e -> view(e.timestamp(), "LogoutPerformed",
          e.subjectId(), null, e.scope() == null ? null : e.scope().name());
      case AccessGranted e -> view(e.timestamp(), "AccessGranted",
          e.subjectId(), e.route(), null);
      case AccessDenied e -> view(e.timestamp(), "AccessDenied",
          e.subjectId(), e.route(), e.reason());
      case ActionDenied e -> view(e.timestamp(), "ActionDenied",
          e.subjectId(), e.action(), null);
      case BruteForceLimitReached e -> view(e.timestamp(), "BruteForceLimitReached",
          e.username(), e.clientAddress(),
          "failed=" + e.failedAttempts() + " lockout=" + e.lockoutDuration());
      case SessionCreated e -> view(e.timestamp(), "SessionCreated",
          e.subjectId(), e.sessionId(), null);
      case SessionExpired e -> view(e.timestamp(), "SessionExpired",
          e.subjectId(), e.sessionId(), e.reason());
      case SessionInvalidated e -> view(e.timestamp(), "SessionInvalidated",
          e.subjectId(), e.sessionId(), e.reason());
      case RoleAssigned e -> view(e.timestamp(), "RoleAssigned",
          e.subjectId(), e.role(), e.assignedBy());
      case RoleRevoked e -> view(e.timestamp(), "RoleRevoked",
          e.subjectId(), e.role(), e.revokedBy());
      case UserCreated e -> view(e.timestamp(), "UserCreated",
          e.username(), e.role(), e.createdBy());
      case UserDeleted e -> view(e.timestamp(), "UserDeleted",
          e.username(), null, e.deletedBy());
      case BootstrapAdminCreated e -> view(e.timestamp(), "BootstrapAdminCreated",
          e.username(), e.clientAddress(), null);
      case BootstrapTokenRejected e -> view(e.timestamp(), "BootstrapTokenRejected",
          null, e.clientAddress(), e.reason());
    };
  }

  private static AuditEventView view(Instant ts, String type, String subject, String target, String detail) {
    return new AuditEventView(
        ts == null ? null : ts.toString(),
        type,
        nullToEmpty(subject),
        nullToEmpty(target),
        nullToEmpty(detail));
  }

  // ---------- query parsing ----------

  private static Set<Class<? extends AuditEvent>> parseTypes(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(AuditHandler::resolveType)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static Class<? extends AuditEvent> resolveType(String simpleName) {
    return switch (simpleName) {
      case "LoginSucceeded" -> LoginSucceeded.class;
      case "LoginFailed" -> LoginFailed.class;
      case "LogoutPerformed" -> LogoutPerformed.class;
      case "AccessGranted" -> AccessGranted.class;
      case "AccessDenied" -> AccessDenied.class;
      case "ActionDenied" -> ActionDenied.class;
      case "BruteForceLimitReached" -> BruteForceLimitReached.class;
      case "SessionCreated" -> SessionCreated.class;
      case "SessionExpired" -> SessionExpired.class;
      case "SessionInvalidated" -> SessionInvalidated.class;
      case "RoleAssigned" -> RoleAssigned.class;
      case "RoleRevoked" -> RoleRevoked.class;
      case "UserCreated" -> UserCreated.class;
      case "UserDeleted" -> UserDeleted.class;
      case "BootstrapAdminCreated" -> BootstrapAdminCreated.class;
      case "BootstrapTokenRejected" -> BootstrapTokenRejected.class;
      default -> null;
    };
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return Instant.parse(raw.trim());
  }

  private static int parseLimit(String raw) {
    if (raw == null || raw.isBlank()) return DEFAULT_LIMIT;
    try {
      int v = Integer.parseInt(raw.trim());
      if (v < 0) return DEFAULT_LIMIT;
      return Math.min(v, MAX_LIMIT);
    } catch (NumberFormatException e) {
      return DEFAULT_LIMIT;
    }
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static void writeJson(HttpExchangeRestResponse response, int status, Object payload) {
    try {
      response.status(status);
      response.body(JacksonJson.mapper().writeValueAsString(payload));
    } catch (Exception e) {
      response.status(500);
      response.body("{\"error\":\"serialization\"}");
    }
  }
}

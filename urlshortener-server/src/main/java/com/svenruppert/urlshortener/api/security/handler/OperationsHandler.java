package com.svenruppert.urlshortener.api.security.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestRequest;
import com.svenruppert.urlshortener.api.security.adapter.HttpExchangeRestResponse;
import com.svenruppert.urlshortener.api.security.permissions.ShortenerPermission;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.vaadin.security.authorization.api.SecuritySubject;
import com.svenruppert.vaadin.security.authorization.api.permissions.PermissionName;
import com.svenruppert.vaadin.security.rest.RestSubjectResolver;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class OperationsHandler implements HttpHandler {

  private record Op(String id, String label, ShortenerPermission required) {
  }

  private static final List<Op> OPERATIONS = List.of(
      new Op("link.create", "Create link", ShortenerPermission.LINK_CREATE),
      new Op("link.list-own", "List own links", ShortenerPermission.LINK_READ_OWN),
      new Op("link.list-all", "List all links", ShortenerPermission.LINK_READ_ALL),
      new Op("link.update-own", "Update own link", ShortenerPermission.LINK_UPDATE_OWN),
      new Op("link.update-all", "Update any link", ShortenerPermission.LINK_UPDATE_ALL),
      new Op("link.delete-own", "Delete own link", ShortenerPermission.LINK_DELETE_OWN),
      new Op("link.delete-all", "Delete any link", ShortenerPermission.LINK_DELETE_ALL),
      new Op("link.stats-own", "Read own statistics", ShortenerPermission.LINK_STATS_OWN),
      new Op("link.stats-all", "Read all statistics", ShortenerPermission.LINK_STATS_ALL),
      new Op("user.list", "List users", ShortenerPermission.USER_READ),
      new Op("user.create", "Create user", ShortenerPermission.USER_CREATE),
      new Op("user.update", "Update user", ShortenerPermission.USER_UPDATE),
      new Op("user.delete", "Delete user", ShortenerPermission.USER_DELETE),
      new Op("user.role-assign", "Assign role to user", ShortenerPermission.USER_ROLE_ASSIGN),
      new Op("user.unlock", "Unlock locked-out user", ShortenerPermission.USER_UPDATE),
      new Op("audit.list", "View security audit log", ShortenerPermission.ADMIN_ACCESS),
      new Op("admin.dashboard", "Admin dashboard", ShortenerPermission.ADMIN_ACCESS)
  );

  private final RestSubjectResolver subjectResolver;

  public OperationsHandler(RestSubjectResolver subjectResolver) {
    this.subjectResolver = subjectResolver;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    if (!"GET".equals(exchange.getRequestMethod())) {
      response.status(405);
      response.body("{\"error\":\"method_not_allowed\"}");
      response.writeTo(exchange);
      return;
    }
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    Optional<SecuritySubject> subject = subjectResolver.resolveSubject(request);
    if (subject.isEmpty()) {
      response.status(401);
      response.body("{\"error\":\"unauthenticated\"}");
      response.writeTo(exchange);
      return;
    }
    Set<PermissionName> granted = subject.get().permissions();
    List<Map<String, Object>> ops = OPERATIONS.stream()
        .filter(op -> granted.contains(op.required().permissionName()))
        .map(op -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", op.id());
          m.put("label", op.label());
          m.put("permission", op.required().permissionName().permissionName());
          return m;
        })
        .collect(Collectors.toList());
    try {
      response.status(200);
      response.body(JacksonJson.mapper().writeValueAsString(Map.of("operations", ops)));
    } catch (Exception e) {
      response.status(500);
      response.body("{\"error\":\"serialization\"}");
    }
    response.writeTo(exchange);
  }
}

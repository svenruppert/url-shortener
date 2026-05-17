package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.urlshortener.client.OperationsClient;

import java.util.List;
import java.util.Set;

/**
 * UI-side session subject. Wraps the data returned by {@code POST /api/login}
 * on the REST server: the bearer token to attach to subsequent requests,
 * the role set, the permission strings carried by the role, and the list of
 * operations the user may invoke (loaded from {@code GET /api/operations}).
 */
public record AppUser(
    String name,
    Set<AppRole> roles,
    String accessToken,
    Set<String> permissions,
    List<OperationsClient.Operation> operations
) {
  public AppUser(String name, Set<AppRole> roles) {
    this(name, roles, null, Set.of(), List.of());
  }

  public AppUser(String name, Set<AppRole> roles, String accessToken, Set<String> permissions) {
    this(name, roles, accessToken, permissions, List.of());
  }

  public AppUser withOperations(List<OperationsClient.Operation> ops) {
    return new AppUser(name, roles, accessToken, permissions, List.copyOf(ops));
  }

  /** {@code true} if the current user is allowed to perform the operation with id {@code opId}. */
  public boolean canInvoke(String opId) {
    if (opId == null) return false;
    for (OperationsClient.Operation op : operations) {
      if (opId.equals(op.id())) return true;
    }
    return false;
  }
}

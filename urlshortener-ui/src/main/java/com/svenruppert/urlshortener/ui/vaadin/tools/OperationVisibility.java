package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.flow.component.Component;

/**
 * Convenience wrapper around the operations list cached in the current
 * {@link AppUser}. View code uses {@link #canInvoke(String)} or the
 * {@link #applyTo(Component, String)} helper to drive button/menu
 * visibility based on what the server actually exposes via
 * {@code /api/operations}.
 * <p>
 * Permission checks here are advisory: hiding a button is convenience for
 * the user, it is not a security boundary. The authoritative check stays
 * on the REST server.
 */
public final class OperationVisibility {

  private OperationVisibility() {
  }

  /**
   * Returns {@code true} if the operation with the given id is in the
   * subject's allowed-operations list. Returns {@code false} for unknown
   * operations, missing sessions, or anonymous access.
   */
  public static boolean canInvoke(String operationId) {
    return SubjectStores.subjectStore().currentSubject(AppUser.class)
        .map(u -> u.canInvoke(operationId))
        .orElse(false);
  }

  /**
   * Sets {@code component.setVisible(canInvoke(operationId))}. Returns the
   * same component so the call chains.
   */
  public static <T extends Component> T applyTo(T component, String operationId) {
    if (component != null) {
      component.setVisible(canInvoke(operationId));
    }
    return component;
  }
}

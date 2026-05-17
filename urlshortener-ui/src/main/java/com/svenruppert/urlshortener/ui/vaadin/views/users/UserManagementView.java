package com.svenruppert.urlshortener.ui.vaadin.views.users;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.UserManagementClientFactory;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.List;

/**
 * Admin-only user management view: list / create / edit / delete / reset
 * password. The view is gated by {@link VisibleFor} for navigation; the
 * authoritative permission check happens server-side on every REST call.
 */
@Route(value = UserManagementView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.ROLE_ADMIN)
public class UserManagementView extends Composite<VerticalLayout> implements HasLogger {

  public static final String PATH = "users";

  private final Grid<UserSummary> grid = new Grid<>(UserSummary.class, false);

  public UserManagementView() {
    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setPadding(true);
    root.setSpacing(true);

    H2 title = new H2("Users");

    Button createBtn = new Button("New user", new Icon(VaadinIcon.PLUS));
    createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createBtn.addClickListener(e -> openCreateDialog());

    Button refreshBtn = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    refreshBtn.addClickListener(e -> reload());

    HorizontalLayout toolbar = new HorizontalLayout(title, createBtn, refreshBtn);
    toolbar.setWidthFull();
    toolbar.setAlignItems(HorizontalLayout.Alignment.CENTER);
    toolbar.expand(title);

    grid.addColumn(UserSummary::username).setHeader("Username").setAutoWidth(true);
    grid.addColumn(UserSummary::displayName).setHeader("Display name").setAutoWidth(true);
    grid.addColumn(UserSummary::role).setHeader("Role").setAutoWidth(true);
    grid.addComponentColumn(u -> {
      Span badge = new Span(u.enabled() ? "enabled" : "disabled");
      badge.getElement().setAttribute("theme", "badge " + (u.enabled() ? "success" : "error"));
      return badge;
    }).setHeader("Status").setAutoWidth(true);
    grid.addComponentColumn(this::rowActions).setHeader("Actions").setAutoWidth(true);
    grid.setSizeFull();

    root.add(toolbar, grid);
    reload();
  }

  private HorizontalLayout rowActions(UserSummary user) {
    Button edit = new Button(new Icon(VaadinIcon.EDIT));
    edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    edit.getElement().setAttribute("title", "Edit user");
    edit.addClickListener(e -> openEditDialog(user));

    Button reset = new Button(new Icon(VaadinIcon.KEY));
    reset.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    reset.getElement().setAttribute("title", "Reset password");
    reset.addClickListener(e -> openResetDialog(user));

    Button delete = new Button(new Icon(VaadinIcon.TRASH));
    delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
    delete.getElement().setAttribute("title", "Delete user");
    delete.addClickListener(e -> confirmAndDelete(user));

    HorizontalLayout row = new HorizontalLayout(edit, reset, delete);
    row.setSpacing(true);
    return row;
  }

  private void reload() {
    try {
      UserManagementClient client = UserManagementClientFactory.newInstance();
      List<UserSummary> users = client.listUsers();
      grid.setItems(users);
    } catch (IOException ex) {
      error("Failed to load users: " + ex.getMessage());
    }
  }

  private void openCreateDialog() {
    new CreateUserDialog(this::reload).open();
  }

  private void openEditDialog(UserSummary user) {
    new EditUserDialog(user, this::reload).open();
  }

  private void openResetDialog(UserSummary user) {
    new AdminResetPasswordDialog(user).open();
  }

  private void confirmAndDelete(UserSummary user) {
    try {
      UserManagementClient client = UserManagementClientFactory.newInstance();
      if (client.deleteUser(user.username())) {
        info("Deleted '" + user.username() + "'");
        reload();
      } else {
        error("User '" + user.username() + "' not found.");
      }
    } catch (IOException ex) {
      error("Delete failed: " + extractMessage(ex));
    }
  }

  static void info(String msg) {
    Notification n = Notification.show(msg, 2500, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  static void error(String msg) {
    Notification n = Notification.show(msg, 5000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }

  static String extractMessage(IOException ex) {
    String msg = ex.getMessage();
    if (msg == null) return "error";
    if (msg.contains("last_admin_protected")) return "Cannot demote/disable/delete the last administrator.";
    if (msg.contains("self_delete_forbidden")) return "You cannot delete your own account.";
    if (msg.contains("self_disable_forbidden")) return "You cannot disable your own account.";
    if (msg.contains("username_exists")) return "Username already exists.";
    if (msg.contains("password_too_short")) return "Password must be at least 8 characters.";
    if (msg.contains("unknown_role")) return "Unknown role.";
    if (msg.contains("forbidden")) return "Forbidden — your account lacks the required permission.";
    return msg;
  }
}

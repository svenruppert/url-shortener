package com.svenruppert.urlshortener.ui.vaadin.views.profile;

import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.UserManagementClientFactory;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Profile view for every authenticated user: shows username/role triple,
 * lets the user edit their display name (PUT /api/me/profile) and offers
 * the self-service password change flow.
 */
@Route(value = ProfileView.PATH, layout = MainLayout.class)
@VisibleFor({AppRole.ROLE_VIEWER, AppRole.ROLE_USER, AppRole.ROLE_ADMIN})
public class ProfileView extends Composite<VerticalLayout> {

  public static final String PATH = "profile";

  public ProfileView() {
    VerticalLayout root = getContent();
    root.setPadding(true);
    root.setSpacing(true);

    AppUser user = SubjectStores.subjectStore()
        .currentSubject(AppUser.class)
        .orElse(null);

    root.add(new H2("Profile"));
    if (user == null) {
      root.add(new Span("Not signed in."));
      return;
    }

    root.add(new Span("Username: " + user.name()));
    root.add(new Span("Roles: " + user.roles().stream()
        .map(Enum::name).collect(Collectors.joining(", "))));

    UserSummary current = fetchCurrent();

    TextField displayName = new TextField("Display name");
    if (current != null && current.displayName() != null) {
      displayName.setValue(current.displayName());
    }

    Button save = new Button("Save", new Icon(VaadinIcon.CHECK));
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      try {
        UserSummary updated = UserManagementClientFactory.newInstance()
            .updateOwnProfile(displayName.getValue());
        displayName.setValue(updated.displayName() == null ? "" : updated.displayName());
        success("Display name updated.");
      } catch (IOException ex) {
        error("Update failed: " + ex.getMessage());
      }
    });

    HorizontalLayout profileRow = new HorizontalLayout(displayName, save);
    profileRow.setAlignItems(HorizontalLayout.Alignment.BASELINE);
    profileRow.setSpacing(true);
    root.add(profileRow);

    Button changePw = new Button("Change password", new Icon(VaadinIcon.KEY));
    changePw.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    changePw.addClickListener(e -> new ChangePasswordDialog().open());

    HorizontalLayout actions = new HorizontalLayout(changePw);
    actions.setSpacing(true);
    root.add(actions);
  }

  private static UserSummary fetchCurrent() {
    try {
      return UserManagementClientFactory.newInstance().fetchOwnProfile();
    } catch (IOException ignored) {
      return null;
    }
  }

  private static void success(String msg) {
    Notification n = Notification.show(msg, 2500, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private static void error(String msg) {
    Notification n = Notification.show(msg, 4500, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }
}

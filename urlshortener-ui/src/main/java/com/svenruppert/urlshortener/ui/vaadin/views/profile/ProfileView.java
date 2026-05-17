package com.svenruppert.urlshortener.ui.vaadin.views.profile;

import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.util.stream.Collectors;

/**
 * Profile view available to every authenticated user. Currently shows the
 * username/display name/role triple and offers a self-service password
 * change.
 */
@Route(value = ProfileView.PATH, layout = MainLayout.class)
@VisibleFor({AppRole.ROLE_USER, AppRole.ROLE_ADMIN})
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

    Button changePw = new Button("Change password", new Icon(VaadinIcon.KEY));
    changePw.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    changePw.addClickListener(e -> new ChangePasswordDialog().open());

    HorizontalLayout actions = new HorizontalLayout(changePw);
    actions.setSpacing(true);
    root.add(actions);
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views.login;

import com.svenruppert.urlshortener.ui.vaadin.security.LoginConfig;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(LoginView.PATH)
@PageTitle("Admin Login | URL Shortener")
@CssImport("./styles/login-view.css")
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

  public static final String PATH = "login";

  private static final String C_ROOT = "login-view";
  private static final String C_FORM = "login-view__form";
  private static final String C_FIELD = "login-view__field";
  private static final String C_BUTTON = "login-view__button";

  private final PasswordField passwordField = new PasswordField("Password");
  private final Button loginButton = new Button("Login");

  public LoginView() {
    addClassName(C_ROOT);

    setSizeFull();
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.CENTER);

    configureForm();
    buildLayout();
  }

  private void configureForm() {
    passwordField.addClassName(C_FIELD);
    passwordField.setAutofocus(true);
    passwordField.setClearButtonVisible(true);
    passwordField.setRevealButtonVisible(false);
    passwordField.setInvalid(false);

    loginButton.addClassName(C_BUTTON);
    loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    loginButton.addClickListener(_ -> attemptLogin());

    passwordField.addKeyDownListener(event -> {
      if ("Enter".equalsIgnoreCase(event.getKey().getKeys().getFirst())) {
        attemptLogin();
      }
    });

    passwordField.addValueChangeListener(_ -> passwordField.setInvalid(false));
  }

  private void buildLayout() {
    H2 title = new H2("Admin Login");
    Paragraph subtitle = new Paragraph(
        "Please enter the administrator password to access the management interface."
    );

    VerticalLayout formLayout =
        new VerticalLayout(title, subtitle, passwordField, loginButton);

    formLayout.addClassName(C_FORM);
    formLayout.setSpacing(true);
    formLayout.setPadding(true);
    formLayout.setAlignItems(Alignment.CENTER);

    add(formLayout);
  }

  private void attemptLogin() {
    if (!LoginConfig.isLoginEnabled()) {
      Notifications.loginCurrentlyDisabled();
      UI.getCurrent().navigate(OverviewView.PATH);
      return;
    }

    char[] input = passwordField.getValue() != null
        ? passwordField.getValue().toCharArray()
        : new char[0];

    if (!LoginConfig.isLoginConfigured()) {
      Notifications.loginCurrentlyNotConfigured();
      return;
    }

    boolean authenticated = LoginConfig.matches(input);

    if (authenticated) {
      SessionAuth.markAuthenticated();
      UI.getCurrent().navigate(OverviewView.PATH);
    } else {
      passwordField.setErrorMessage("Incorrect password");
      passwordField.setInvalid(true);
    }
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    if (!LoginConfig.isLoginEnabled()) {
      event.forwardTo(OverviewView.PATH);
      return;
    }

    if (SessionAuth.isAuthenticated()) {
      event.forwardTo(OverviewView.PATH);
    }
  }
}

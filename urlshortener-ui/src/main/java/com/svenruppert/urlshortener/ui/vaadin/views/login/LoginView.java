package com.svenruppert.urlshortener.ui.vaadin.views.login;

import com.svenruppert.urlshortener.ui.vaadin.security.LoginConfig;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(LoginView.PATH) // No layout = no navigation or drawer visible
@PageTitle("Admin Login | URL Shortener")
public class LoginView
    extends VerticalLayout
    implements BeforeEnterObserver {

  public static final String PATH = "login";

  private final PasswordField passwordField = new PasswordField("Password");
  private final Button loginButton = new Button("Login");

  public LoginView() {
    setSizeFull();
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.CENTER);

    configureForm();
    buildLayout();
  }

  /**
   * Configures the password field and login button.
   * This avoids Chrome’s autofill overlay issues by disabling autocomplete.
   */
  private void configureForm() {
    //passwordField.getElement().setAttribute("autocomplete", "new-password");
    passwordField.setAutofocus(true);
    passwordField.setWidth("300px");
    passwordField.setClearButtonVisible(true);
    passwordField.setRevealButtonVisible(false);
    passwordField.setInvalid(false);

    loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    loginButton.setWidth("300px");
    loginButton.addClickListener(_ -> attemptLogin());

    passwordField.addKeyDownListener(event -> {
      if ("Enter".equalsIgnoreCase(event.getKey().getKeys().getFirst())) {
        attemptLogin();
      }
    });

    passwordField.addValueChangeListener(_ -> passwordField.setInvalid(false));
  }


  /**
   * Builds the layout structure (title, instructions and form components).
   */
  private void buildLayout() {
    H2 title = new H2("Admin Login");
    Paragraph subtitle = new Paragraph(
        "Please enter the administrator password to access the management interface."
    );

    VerticalLayout formLayout = new VerticalLayout(title, subtitle, passwordField, loginButton);
    formLayout.setSpacing(true);
    formLayout.setPadding(true);
    formLayout.setAlignItems(Alignment.CENTER);

    add(formLayout);
  }

  /**
   * Attempts to authenticate the user based on the entered password.
   * If successful, the session is marked as authenticated and the user is redirected.
   */
  private void attemptLogin() {
    if (!LoginConfig.isLoginEnabled()) {
      Notification.show(
          "Login is currently disabled. Please check the server configuration.",
          3000,
          Notification.Position.MIDDLE
      );
      UI.getCurrent().navigate(OverviewView.PATH);
      return;
    }


    char[] input = passwordField.getValue() != null
        ? passwordField.getValue().toCharArray()
        : new char[0];

    if (!LoginConfig.isLoginConfigured()) {
      Notification.show(
          "Login is not configured. Please verify that the configuration file has been loaded.",
          3000,
          Notification.Position.MIDDLE
      );
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

  /**
   * Redirect authenticated users away from the login page.
   */
  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    // If login is disabled, skip the login page entirely
    if (!LoginConfig.isLoginEnabled()) {
      event.forwardTo(OverviewView.PATH);
      return;
    }

    // If already authenticated, also skip the login page
    if (SessionAuth.isAuthenticated()) {
      event.forwardTo(OverviewView.PATH);
    }
  }

}

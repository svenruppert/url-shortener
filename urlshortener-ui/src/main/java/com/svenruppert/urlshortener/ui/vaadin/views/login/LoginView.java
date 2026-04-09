package com.svenruppert.urlshortener.ui.vaadin.views.login;

import com.svenruppert.urlshortener.ui.vaadin.security.LoginConfig;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
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
import com.vaadin.flow.router.*;

@Route(LoginView.PATH)
@CssImport("./styles/login-view.css")
public class LoginView
    extends VerticalLayout
    implements BeforeEnterObserver, I18nSupport, HasDynamicTitle {

  private static final String K_PAGE_TITLE = "login.pageTitle";

  public static final String PATH = "login";

  private static final String C_ROOT = "login-view";
  private static final String C_FORM = "login-view__form";
  private static final String C_FIELD = "login-view__field";
  private static final String C_BUTTON = "login-view__button";

  // i18n keys
  private static final String K_TITLE = "login.title";
  private static final String K_SUBTITLE = "login.subtitle";
  private static final String K_PASSWORD = "login.field.password";
  private static final String K_LOGIN = "login.btn.login";
  private static final String K_ERR_INCORRECT_PW = "login.error.incorrectPassword";

  private final PasswordField passwordField = new PasswordField();
  private final Button loginButton = new Button();

  public LoginView() {
    addClassName(C_ROOT);

    setSizeFull();
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.CENTER);

    applyI18n();
    configureForm();
    buildLayout();
  }

  private void applyI18n() {
    passwordField.setLabel(tr(K_PASSWORD, "Password"));
    loginButton.setText(tr(K_LOGIN, "Login"));
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
    H2 title = new H2(tr(K_TITLE, "Admin Login"));
    Paragraph subtitle = new Paragraph(tr(
        K_SUBTITLE,
        "Please enter the administrator password to access the management interface."
    ));

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
      passwordField.setErrorMessage(tr(K_ERR_INCORRECT_PW, "Incorrect password"));
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

  @Override
  public String getPageTitle() {
    return tr(K_PAGE_TITLE, "Admin Login | URL Shortener");
  }

}

package com.svenruppert.urlshortener.ui.vaadin.views.login;



import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.OperationsClient;
import com.svenruppert.urlshortener.core.DefaultValues;
import com.svenruppert.urlshortener.ui.vaadin.security.AppCredentials;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;
import com.svenruppert.vaadin.security.authentication.AuthenticationService;
import com.svenruppert.vaadin.security.authorization.api.SecurityServiceResolver;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;

@Route(LoginView.PATH)
public class LoginView
    extends com.svenruppert.vaadin.security.authorization.LoginView
    implements I18nSupport, HasDynamicTitle, HasLogger {

  public static final String PATH = "login";

  private static final String K_PAGE_TITLE = "login.pageTitle";
  private static final String K_ERR_INCORRECT_PW = "login.error.incorrectPassword";

  private final AuthenticationService<AppCredentials, AppUser> authenticationService =
      SecurityServiceResolver.authenticationService();

  @Override
  public boolean checkCredentials() {
    AppCredentials credentials = new AppCredentials(username(), password());
    boolean permitted = authenticationService.checkCredentials(credentials);

    if (permitted) {
      AppUser user = authenticationService.loadSubject(credentials);
      user = enrichWithOperations(user);
      SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);
    }

    return permitted;
  }

  private AppUser enrichWithOperations(AppUser user) {
    if (user.accessToken() == null) return user;
    OperationsClient ops = new OperationsClient(DefaultValues.ADMIN_SERVER_URL);
    ops.setAuthToken(user.accessToken());
    try {
      return user.withOperations(ops.fetch());
    } catch (IOException ioe) {
      logger().warn("operations fetch failed: {}", ioe.getMessage());
      return user;
    }
  }

  @Override
  public void navigateToApp() {
    UI.getCurrent().navigate(OverviewView.class);
  }

  @Override
  public void reactOnFailedLogin() {
    Notifications.errorKey(K_ERR_INCORRECT_PW, "Incorrect password");
  }

  @Override
  public String getPageTitle() {
    return tr(K_PAGE_TITLE, "Admin Login | URL Shortener");
  }
}
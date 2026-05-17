package junit.com.svenruppert.urlshortener.ui.browserless;

import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonTester;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless integration test for the URL shortener {@link LoginView}.
 * Runs against the real {@link com.svenruppert.urlshortener.api.ShortenerServer}
 * (started by {@link AbstractBrowserlessTest}), so credentials are validated
 * end-to-end and the resulting {@link AppUser} subject carries a real bearer
 * token and the actual server-resolved operation set.
 */
@ViewPackages(classes = {LoginView.class})
class LoginViewBrowserlessTest extends AbstractBrowserlessTest {

  @Test
  @DisplayName("Valid admin credentials populate the subject store with role + token + ops")
  void admin_login_populates_subject() {
    LoginView view = navigate(LoginView.class);
    typeUsername("admin");
    typePassword("admin");

    assertTrue(view.checkCredentials(),
        "admin/admin must authenticate against the in-process REST server");

    Optional<AppUser> stored = SubjectStores.subjectStore().currentSubject(AppUser.class);
    assertTrue(stored.isPresent(), "successful login must store an AppUser");
    AppUser user = stored.get();
    assertEquals("admin", user.name());
    assertTrue(user.roles().contains(AppRole.ROLE_ADMIN),
        "admin must carry ROLE_ADMIN, but had: " + user.roles());
    assertNotNull(user.accessToken(), "bearer token must be set");
    assertFalse(user.accessToken().isBlank(), "bearer token must not be blank");
    assertFalse(user.operations().isEmpty(),
        "operations list must be populated via /api/operations");
    assertTrue(user.canInvoke("user.create"),
        "admin must see the createUser operation");
  }

  @Test
  @DisplayName("ROLE_USER login does not see admin-only operations")
  void user_login_no_admin_ops() {
    navigate(LoginView.class);
    typeUsername("user");
    typePassword("user");
    LoginView view = (LoginView) getCurrentView();

    assertTrue(view.checkCredentials());

    AppUser user = SubjectStores.subjectStore().currentSubject(AppUser.class).orElseThrow();
    assertTrue(user.roles().contains(AppRole.ROLE_USER));
    assertFalse(user.roles().contains(AppRole.ROLE_ADMIN),
        "ROLE_USER login must not promote to ROLE_ADMIN");
    assertFalse(user.canInvoke("user.create"),
        "ROLE_USER must not see the user-management operations");
  }

  @Test
  @DisplayName("Wrong password keeps the subject store empty")
  void wrong_password_keeps_subject_empty() {
    LoginView view = navigate(LoginView.class);
    typeUsername("admin");
    typePassword("totally-wrong-pw");

    assertFalse(view.checkCredentials(),
        "wrong password must return false from checkCredentials");
    assertTrue(SubjectStores.subjectStore().currentSubject(AppUser.class).isEmpty(),
        "failed login must NOT populate the subject store");
  }

  @Test
  @DisplayName("Unknown user is rejected without leaking which check failed")
  void unknown_user_rejected() {
    LoginView view = navigate(LoginView.class);
    typeUsername("ghost-user-that-does-not-exist");
    typePassword("anything");

    assertFalse(view.checkCredentials());
    assertTrue(SubjectStores.subjectStore().currentSubject(AppUser.class).isEmpty());
  }

  @Test
  @DisplayName("Clicking the login button on a failed attempt invokes reactOnFailedLogin()")
  void failed_button_click_triggers_react() {
    LoginView view = navigate(LoginView.class);
    typeUsername("admin");
    typePassword("totally-wrong-pw");

    ButtonTester<Button> button = test($view(Button.class).id(
        com.svenruppert.vaadin.security.authorization.LoginView.BTN_LOGIN_ID));
    button.click();

    // After click the framework clears the fields; assert observable
    // post-condition: still unauthenticated, no subject.
    assertTrue(SubjectStores.subjectStore().currentSubject(AppUser.class).isEmpty(),
        "failed click must not persist a subject");
    assertEquals("", $view(TextField.class).id(
            com.svenruppert.vaadin.security.authorization.LoginView.TF_USERNAME_ID).getValue(),
        "fields must be cleared after the click");
  }

  // ---- helpers ----

  private void typeUsername(String value) {
    TextField field = $view(TextField.class).id(
        com.svenruppert.vaadin.security.authorization.LoginView.TF_USERNAME_ID);
    field.setValue(value);
  }

  private void typePassword(String value) {
    PasswordField field = $view(PasswordField.class).id(
        com.svenruppert.vaadin.security.authorization.LoginView.PF_PASSWORD_ID);
    field.setValue(value);
  }
}

package junit.com.svenruppert.urlshortener.ui.browserless;

import com.svenruppert.urlshortener.client.LoginClient;
import com.svenruppert.urlshortener.client.OperationsClient;
import com.svenruppert.urlshortener.core.DefaultValues;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.views.profile.ProfileView;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonTester;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless integration test for {@link ProfileView} and the embedded
 * self-service password-change dialog. The flow exercises a real REST
 * round-trip against the in-process server and asserts on observable
 * side-effects: server-side credential change, subject store cleared,
 * subsequent re-login with the new password succeeds.
 */
@ViewPackages(packages = {"com.svenruppert.urlshortener.ui.vaadin"})
class ProfileViewBrowserlessTest extends AbstractBrowserlessTest {

  private String createdUsername;

  @AfterEach
  void cleanup() throws IOException {
    if (createdUsername != null) {
      String adminToken = new LoginClient(DefaultValues.ADMIN_SERVER_URL)
          .login("admin", "admin").token();
      com.svenruppert.urlshortener.client.UserManagementClient mgmt =
          new com.svenruppert.urlshortener.client.UserManagementClient(
              DefaultValues.ADMIN_SERVER_URL);
      mgmt.setAuthToken(adminToken);
      mgmt.deleteUser(createdUsername);
      createdUsername = null;
    }
  }

  @Test
  @DisplayName("Profile view shows the signed-in user's display name")
  void profile_shows_username() throws IOException {
    AppUser user = signInFreshUser("profile-show-name", "profile-pw-1");

    navigate(ProfileView.class);

    boolean found = $view(Span.class).withTextContaining(user.name()).exists();
    assertTrue(found, "profile must display the username '" + user.name() + "'");
  }

  @Test
  @DisplayName("Save updates the display name via PUT /api/me/profile")
  void edit_display_name_persists() throws IOException {
    String username = "profile-rename";
    signInFreshUser(username, "profile-pw-1");

    navigate(ProfileView.class);

    com.vaadin.flow.component.textfield.TextField field =
        $view(com.vaadin.flow.component.textfield.TextField.class)
            .withCaption("Display name").single();
    field.setValue("New Display Name");
    test($view(com.vaadin.flow.component.button.Button.class).withText("Save").single()).click();

    // Server-side: a fresh listUsers call must now show the renamed user
    String adminToken = new com.svenruppert.urlshortener.client.LoginClient(
        com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL)
        .login("admin", "admin").token();
    com.svenruppert.urlshortener.client.UserManagementClient adminClient =
        new com.svenruppert.urlshortener.client.UserManagementClient(
            com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL);
    adminClient.setAuthToken(adminToken);
    com.svenruppert.urlshortener.core.users.UserSummary updated =
        adminClient.listUsers().stream()
            .filter(u -> username.equals(u.username()))
            .findFirst().orElseThrow();
    assertEquals("New Display Name", updated.displayName());
  }

  @Test
  @DisplayName("Change-password dialog: correct old PW clears subject and changes credentials")
  void change_password_happy_path() throws IOException {
    String username = "profile-pw-happy";
    String oldPw = "profile-pw-old-1";
    String newPw = "profile-pw-new-2";
    signInFreshUser(username, oldPw);

    navigate(ProfileView.class);

    // Open the dialog
    ButtonTester<Button> changeBtn = test($view(Button.class).withText("Change password").single());
    changeBtn.click();

    Dialog dialog = $(Dialog.class).single();
    List<PasswordField> fields = $(PasswordField.class, dialog).all();
    assertEquals(3, fields.size(), "dialog must have 3 password fields");
    fields.get(0).setValue(oldPw);
    fields.get(1).setValue(newPw);
    fields.get(2).setValue(newPw);

    test($(Button.class, dialog).withText("Save").single()).click();

    // Subject is cleared after success
    assertTrue(SubjectStores.subjectStore().currentSubject(AppUser.class).isEmpty(),
        "successful self-service change must clear the local subject");

    // Server-side: old PW rejected, new PW works
    assertThrowsAuth(() -> new LoginClient(DefaultValues.ADMIN_SERVER_URL).login(username, oldPw));
    assertNotNull(new LoginClient(DefaultValues.ADMIN_SERVER_URL).login(username, newPw).token(),
        "new password must authenticate after self-service change");
  }

  @Test
  @DisplayName("Change-password dialog: wrong old PW keeps subject intact and credentials unchanged")
  void change_password_wrong_old() throws IOException {
    String username = "profile-pw-wrong";
    String oldPw = "profile-pw-old-1";
    signInFreshUser(username, oldPw);

    navigate(ProfileView.class);
    test($view(Button.class).withText("Change password").single()).click();

    Dialog dialog = $(Dialog.class).single();
    List<PasswordField> fields = $(PasswordField.class, dialog).all();
    fields.get(0).setValue("not-the-real-old-pw");
    fields.get(1).setValue("brand-new-pw-2");
    fields.get(2).setValue("brand-new-pw-2");

    test($(Button.class, dialog).withText("Save").single()).click();

    // Subject untouched
    assertTrue(SubjectStores.subjectStore().currentSubject(AppUser.class).isPresent(),
        "wrong old PW must leave the subject in place");
    // Old PW still works on server
    assertNotNull(new LoginClient(DefaultValues.ADMIN_SERVER_URL).login(username, oldPw).token(),
        "old password must still authenticate after a failed self-service attempt");
  }

  @Test
  @DisplayName("Change-password dialog: new ≠ confirmation -> no server call, subject intact")
  void change_password_mismatch_confirmation() throws IOException {
    String username = "profile-pw-mismatch";
    String oldPw = "profile-pw-old-1";
    signInFreshUser(username, oldPw);

    navigate(ProfileView.class);
    test($view(Button.class).withText("Change password").single()).click();

    Dialog dialog = $(Dialog.class).single();
    List<PasswordField> fields = $(PasswordField.class, dialog).all();
    fields.get(0).setValue(oldPw);
    fields.get(1).setValue("first-typed-new-pw-1");
    fields.get(2).setValue("different-confirmation-pw-2");

    test($(Button.class, dialog).withText("Save").single()).click();

    // Mismatch is caught client-side: subject still here, old PW still works
    assertTrue(SubjectStores.subjectStore().currentSubject(AppUser.class).isPresent(),
        "mismatch must NOT clear the subject");
    assertNotNull(new LoginClient(DefaultValues.ADMIN_SERVER_URL).login(username, oldPw).token());
  }

  // ---- helpers ----

  private AppUser signInFreshUser(String username, String password) throws IOException {
    String adminToken = new LoginClient(DefaultValues.ADMIN_SERVER_URL)
        .login("admin", "admin").token();
    com.svenruppert.urlshortener.client.UserManagementClient mgmt =
        new com.svenruppert.urlshortener.client.UserManagementClient(
            DefaultValues.ADMIN_SERVER_URL);
    mgmt.setAuthToken(adminToken);
    // Best-effort cleanup in case a prior run left it
    try {
      mgmt.deleteUser(username);
    } catch (IOException ignored) {
      // not present — fine
    }
    mgmt.createUser(new com.svenruppert.urlshortener.core.users.CreateUserRequest(
        username, password, username, "ROLE_USER"));
    createdUsername = username;

    LoginClient.AuthSession session =
        new LoginClient(DefaultValues.ADMIN_SERVER_URL).login(username, password);
    OperationsClient ops = new OperationsClient(DefaultValues.ADMIN_SERVER_URL);
    ops.setAuthToken(session.token());
    List<OperationsClient.Operation> opList = ops.fetch();
    AppUser user = new AppUser(username, Set.of(AppRole.ROLE_USER),
        session.token(), Set.of(), opList);
    signIn(user);
    return user;
  }

  private static void assertThrowsAuth(IOAction action) {
    try {
      action.run();
      throw new AssertionError("expected authentication failure");
    } catch (LoginClient.AuthenticationException expected) {
      // OK
    } catch (IOException io) {
      throw new AssertionError("unexpected IOException: " + io.getMessage(), io);
    }
  }

  @FunctionalInterface
  private interface IOAction {
    void run() throws IOException;
  }
}

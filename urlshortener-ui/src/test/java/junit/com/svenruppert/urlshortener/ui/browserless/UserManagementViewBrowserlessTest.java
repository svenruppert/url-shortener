package junit.com.svenruppert.urlshortener.ui.browserless;

import com.svenruppert.urlshortener.client.LoginClient;
import com.svenruppert.urlshortener.client.OperationsClient;
import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.DefaultValues;
import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.views.users.UserManagementView;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonTester;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxTester;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridTester;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldTester;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless integration test for {@link UserManagementView}. Drives the
 * grid + dialogs through the typed testers and asserts on observable
 * REST/state side-effects (the user actually exists on the server after a
 * create round-trip).
 */
@ViewPackages(packages = {"com.svenruppert.urlshortener.ui.vaadin"})
class UserManagementViewBrowserlessTest extends AbstractBrowserlessTest {

  private static String adminToken;
  private static List<OperationsClient.Operation> adminOps;

  private void signInAsAdmin() throws IOException {
    if (adminToken == null) {
      LoginClient.AuthSession session =
          new LoginClient(DefaultValues.ADMIN_SERVER_URL).login("admin", "admin");
      adminToken = session.token();
      OperationsClient opsClient = new OperationsClient(DefaultValues.ADMIN_SERVER_URL);
      opsClient.setAuthToken(adminToken);
      adminOps = opsClient.fetch();
    }
    signIn(new AppUser("admin", Set.of(AppRole.ROLE_ADMIN), adminToken,
        Set.of(), adminOps));
  }

  private static UserManagementClient adminApi() {
    UserManagementClient c = new UserManagementClient(DefaultValues.ADMIN_SERVER_URL);
    c.setAuthToken(adminToken);
    return c;
  }

  @Test
  @DisplayName("Grid lists the seed users when an admin opens the view")
  void grid_lists_seed_users() throws IOException {
    signInAsAdmin();
    navigate(UserManagementView.class);

    Grid<UserSummary> grid = $view(Grid.class).single();
    GridTester<Grid<UserSummary>, UserSummary> tester = test(grid);
    int count = tester.size();
    assertTrue(count >= 2, "expected at least admin + user seed rows, got " + count);

    boolean adminFound = false;
    boolean userFound = false;
    for (int i = 0; i < count; i++) {
      UserSummary row = tester.getRow(i);
      if ("admin".equals(row.username())) adminFound = true;
      if ("user".equals(row.username())) userFound = true;
    }
    assertTrue(adminFound, "admin row must be present");
    assertTrue(userFound, "user row must be present");
  }

  @Test
  @DisplayName("Create-user dialog round-trip ends with the new user persisted on the server")
  void create_user_dialog_creates_user() throws IOException {
    signInAsAdmin();
    String victim = "browserless-created-1";
    // Pre-clean in case of a previous failed run.
    try {
      adminApi().deleteUser(victim);
    } catch (IOException ignored) {
      // nothing to clean — that's fine
    }

    navigate(UserManagementView.class);

    // Toolbar create button.
    ButtonTester<Button> newBtn = test($view(Button.class).withText("New user").single());
    newBtn.click();

    // Dialog is open: fill and submit.
    Dialog dialog = $(Dialog.class).single();
    fillDialogField(dialog, TextField.class, "Username", victim);
    fillDialogPassword(dialog, "browserless-pw-1");
    fillDialogField(dialog, TextField.class, "Display name", "Browserless Created");
    // role ComboBox already defaults to ROLE_USER

    test($(Button.class, dialog).withText("Create").single()).click();

    // Server-side state — the new user must now exist.
    UserSummary created = adminApi().listUsers().stream()
        .filter(u -> victim.equals(u.username()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("user '" + victim + "' was not persisted"));
    assertEquals("Browserless Created", created.displayName());
    assertEquals("ROLE_USER", created.role());
    assertTrue(created.enabled());

    // Cleanup
    adminApi().deleteUser(victim);
  }

  @Test
  @DisplayName("Refresh button does not throw and keeps the grid populated")
  void refresh_button_works() throws IOException {
    signInAsAdmin();
    navigate(UserManagementView.class);

    ButtonTester<Button> refresh = test($view(Button.class).withText("Refresh").single());
    refresh.click();

    int rows = test(($view(Grid.class).single())).size();
    assertTrue(rows >= 2, "refresh must leave the grid populated, got " + rows);
  }

  @Test
  @DisplayName("Edit dialog: changing display name + role persists via REST")
  void edit_dialog_persists_changes() throws IOException {
    signInAsAdmin();
    String victim = "browserless-edit-1";
    try {
      adminApi().deleteUser(victim);
    } catch (IOException ignored) {
      // not present — fine
    }
    adminApi().createUser(new com.svenruppert.urlshortener.core.users.CreateUserRequest(
        victim, "browserless-pw-1", "Old Display", "ROLE_USER"));

    navigate(UserManagementView.class);

    UserSummary current = adminApi().listUsers().stream()
        .filter(u -> victim.equals(u.username())).findFirst().orElseThrow();
    new TestEditDialogOpener(current).run();

    Dialog dialog = $(Dialog.class).single();
    TextField display = (TextField) $(TextField.class, dialog)
        .withCaption("Display name").single();
    test(display).setValue("New Display");
    @SuppressWarnings("unchecked")
    ComboBox<String> role = (ComboBox<String>) $(ComboBox.class, dialog).single();
    test(role).selectItem("ROLE_ADMIN");
    test($(Button.class, dialog).withText("Save").single()).click();

    UserSummary after = adminApi().listUsers().stream()
        .filter(u -> victim.equals(u.username())).findFirst().orElseThrow();
    assertEquals("New Display", after.displayName());
    assertEquals("ROLE_ADMIN", after.role());

    adminApi().deleteUser(victim);
  }

  @Test
  @DisplayName("Admin reset dialog persists the new password + invalidates existing tokens")
  void reset_dialog_persists() throws IOException {
    signInAsAdmin();
    String victim = "browserless-reset-1";
    try {
      adminApi().deleteUser(victim);
    } catch (IOException ignored) {
      // not present
    }
    adminApi().createUser(new com.svenruppert.urlshortener.core.users.CreateUserRequest(
        victim, "browserless-pw-1", "Reset Victim", "ROLE_USER"));
    String victimToken = new LoginClient(DefaultValues.ADMIN_SERVER_URL)
        .login(victim, "browserless-pw-1").token();

    navigate(UserManagementView.class);

    UserSummary current = adminApi().listUsers().stream()
        .filter(u -> victim.equals(u.username())).findFirst().orElseThrow();
    new TestResetDialogOpener(current).run();

    Dialog dialog = $(Dialog.class).single();
    List<PasswordField> pws = $(PasswordField.class, dialog).all();
    assertEquals(2, pws.size());
    pws.get(0).setValue("brand-new-pw-2");
    pws.get(1).setValue("brand-new-pw-2");

    test($(Button.class, dialog).withText("Reset password").single()).click();

    // Old token revoked, old password no longer authenticates, new one does
    assertThrows(LoginClient.AuthenticationException.class,
        () -> new LoginClient(DefaultValues.ADMIN_SERVER_URL)
            .login(victim, "browserless-pw-1"));
    assertNotNull(new LoginClient(DefaultValues.ADMIN_SERVER_URL)
        .login(victim, "brand-new-pw-2").token());

    adminApi().deleteUser(victim);
  }

  @Test
  @DisplayName("Reset dialog rejects mismatching confirmation client-side, no server call")
  void reset_dialog_rejects_mismatch() throws IOException {
    signInAsAdmin();
    String victim = "browserless-reset-mismatch";
    try {
      adminApi().deleteUser(victim);
    } catch (IOException ignored) {
      // not present
    }
    adminApi().createUser(new com.svenruppert.urlshortener.core.users.CreateUserRequest(
        victim, "untouched-pw-1", "MM", "ROLE_USER"));

    navigate(UserManagementView.class);
    UserSummary current = adminApi().listUsers().stream()
        .filter(u -> victim.equals(u.username())).findFirst().orElseThrow();
    new TestResetDialogOpener(current).run();

    Dialog dialog = $(Dialog.class).single();
    List<PasswordField> pws = $(PasswordField.class, dialog).all();
    pws.get(0).setValue("brand-new-pw-2");
    pws.get(1).setValue("different-confirm-2");
    test($(Button.class, dialog).withText("Reset password").single()).click();

    // Old password still works -> reset never reached server
    assertNotNull(new LoginClient(DefaultValues.ADMIN_SERVER_URL)
        .login(victim, "untouched-pw-1").token(),
        "client-side mismatch must abort BEFORE the REST call");

    adminApi().deleteUser(victim);
  }

  @Test
  @DisplayName("Row delete button deletes the underlying user (admin self-protect still applies)")
  void row_delete_button_deletes_user() throws IOException {
    signInAsAdmin();
    String victim = "browserless-row-delete";
    try {
      adminApi().deleteUser(victim);
    } catch (IOException ignored) {
      // not present
    }
    adminApi().createUser(new com.svenruppert.urlshortener.core.users.CreateUserRequest(
        victim, "browserless-pw-1", "Row Delete", "ROLE_USER"));

    navigate(UserManagementView.class);

    @SuppressWarnings("unchecked")
    Grid<UserSummary> grid = $view(Grid.class).single();
    GridTester<Grid<UserSummary>, UserSummary> gridTester = test(grid);

    int targetRow = -1;
    for (int i = 0; i < gridTester.size(); i++) {
      if (victim.equals(gridTester.getRow(i).username())) {
        targetRow = i;
        break;
      }
    }
    assertTrue(targetRow >= 0, "freshly created user must appear in the grid");

    // 5 columns: Username, Display name, Role, Status, Actions -> index 4.
    com.vaadin.flow.component.Component cell =
        gridTester.getCellComponent(targetRow, 4);
    // The cell renders a HorizontalLayout with 3 icon buttons in order
    // edit / reset / delete. Walk the layout and click the delete one.
    Button deleteButton = null;
    for (int i = 0; i < cell.getElement().getChildCount(); i++) {
      com.vaadin.flow.dom.Element child = cell.getElement().getChild(i);
      java.util.Optional<com.vaadin.flow.component.Component> opt =
          child.getComponent();
      if (opt.isPresent() && opt.get() instanceof Button btn
          && "Delete user".equals(btn.getElement().getAttribute("title"))) {
        deleteButton = btn;
        break;
      }
    }
    assertNotNull(deleteButton, "delete button must exist in the actions cell");
    test(deleteButton).click();

    // Server confirms the row is gone
    boolean stillThere = adminApi().listUsers().stream()
        .anyMatch(u -> victim.equals(u.username()));
    assertFalse(stillThere, "row-action delete must remove the user");
  }

  // Wrapper that opens the EditUserDialog without exposing the dialog
  // class outside its package-private visibility. The dialog is final
  // package-private — instantiate via fully-qualified name.
  private static class TestEditDialogOpener implements Runnable {
    private final UserSummary user;

    TestEditDialogOpener(UserSummary user) {
      this.user = user;
    }

    @Override
    public void run() {
      try {
        var cls = Class.forName(
            "com.svenruppert.urlshortener.ui.vaadin.views.users.EditUserDialog");
        var ctor = cls.getDeclaredConstructor(UserSummary.class, Runnable.class);
        ctor.setAccessible(true);
        var dialog = (Dialog) ctor.newInstance(user, (Runnable) () -> { });
        dialog.open();
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  private static class TestResetDialogOpener implements Runnable {
    private final UserSummary user;

    TestResetDialogOpener(UserSummary user) {
      this.user = user;
    }

    @Override
    public void run() {
      try {
        var cls = Class.forName(
            "com.svenruppert.urlshortener.ui.vaadin.views.users.AdminResetPasswordDialog");
        var ctor = cls.getDeclaredConstructor(UserSummary.class);
        ctor.setAccessible(true);
        var dialog = (Dialog) ctor.newInstance(user);
        dialog.open();
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Test
  @DisplayName("ROLE_USER attempting to open /users is blocked by the navigation guard")
  void non_admin_blocked_by_visibility_annotation() throws IOException {
    LoginClient.AuthSession userSession =
        new LoginClient(DefaultValues.ADMIN_SERVER_URL).login("user", "user");
    OperationsClient userOpsClient = new OperationsClient(DefaultValues.ADMIN_SERVER_URL);
    userOpsClient.setAuthToken(userSession.token());
    List<OperationsClient.Operation> userOps = userOpsClient.fetch();
    signIn(new AppUser("user", Set.of(AppRole.ROLE_USER),
        userSession.token(), Set.of(), userOps));

    // The @VisibleFor(ROLE_ADMIN) navigation guard rejects the ROLE_USER
    // session and redirects somewhere else — anywhere but UserManagementView.
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> navigate(UserManagementView.class),
        "ROLE_USER must not reach UserManagementView");
    assertTrue(ex.getMessage().contains("unexpected class"),
        "guard should redirect away, was: " + ex.getMessage());
    assertTrue(!getCurrentView().getClass().equals(UserManagementView.class),
        "current view must NOT be UserManagementView");
  }

  // ---- helpers ----

  private <T extends com.vaadin.flow.component.Component>
  void fillDialogField(Dialog dialog, Class<T> type, String label, String value) {
    @SuppressWarnings("unchecked")
    TextField field = (TextField) $(type, dialog).withCaption(label).single();
    TextFieldTester tester = test((TextField) field);
    tester.setValue(value);
  }

  private void fillDialogPassword(Dialog dialog, String value) {
    PasswordField pw = $(PasswordField.class, dialog).single();
    pw.setValue(value);
  }
}

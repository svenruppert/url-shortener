package com.svenruppert.urlshortener.ui.vaadin.views.profile;

import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.tools.UserManagementClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.svenruppert.vaadin.security.authorization.api.SubjectStores;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.PasswordField;

import java.io.IOException;

/**
 * Self-service password change. On success the server revokes the current
 * token, so we clear the local subject and bounce the user to {@link LoginView}.
 */
final class ChangePasswordDialog extends Dialog {

  ChangePasswordDialog() {
    setHeaderTitle("Change password");

    Paragraph info = new Paragraph(
        "After saving, all your active sessions will be signed out.");

    PasswordField oldPw = new PasswordField("Current password");
    oldPw.setRequiredIndicatorVisible(true);

    PasswordField newPw = new PasswordField("New password");
    newPw.setHelperText("At least 8 characters.");
    newPw.setRequiredIndicatorVisible(true);

    PasswordField confirmPw = new PasswordField("Confirm new password");
    confirmPw.setRequiredIndicatorVisible(true);

    FormLayout form = new FormLayout(oldPw, newPw, confirmPw);
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
    add(info, form);

    Button cancel = new Button("Cancel", e -> close());
    Button save = new Button("Save");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      if (oldPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
        error("All fields are required.");
        return;
      }
      if (newPw.getValue().length() < 8) {
        error("New password must be at least 8 characters.");
        return;
      }
      if (!newPw.getValue().equals(confirmPw.getValue())) {
        error("Confirmation does not match.");
        return;
      }
      try {
        UserManagementClient client = UserManagementClientFactory.newInstance();
        boolean ok = client.changeOwnPassword(oldPw.getValue(), newPw.getValue());
        if (!ok) {
          error("Current password is incorrect.");
          return;
        }
        info("Password changed. Please sign in again.");
        SubjectStores.subjectStore().deleteCurrentSubject(AppUser.class);
        close();
        UI.getCurrent().navigate(LoginView.class);
      } catch (IOException ex) {
        error("Password change failed: " + ex.getMessage());
      }
    });

    getFooter().add(cancel, save);
  }

  private static void info(String msg) {
    Notification n = Notification.show(msg, 3500, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private static void error(String msg) {
    Notification n = Notification.show(msg, 5000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views.users;

import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.urlshortener.ui.vaadin.tools.UserManagementClientFactory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;

import java.io.IOException;

/** Admin force-reset dialog. The target user's tokens are revoked on success. */
final class AdminResetPasswordDialog extends Dialog {

  AdminResetPasswordDialog(UserSummary user) {
    setHeaderTitle("Reset password for '" + user.username() + "'");

    Paragraph info = new Paragraph(
        "The user will have to log in again with the new password.");

    PasswordField newPw = new PasswordField("New password");
    newPw.setHelperText("At least 8 characters.");
    newPw.setRequiredIndicatorVisible(true);

    PasswordField confirmPw = new PasswordField("Confirm new password");
    confirmPw.setRequiredIndicatorVisible(true);

    FormLayout form = new FormLayout(newPw, confirmPw);
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
    add(info, form);

    Button cancel = new Button("Cancel", e -> close());
    Button save = new Button("Reset password");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      if (newPw.isEmpty() || confirmPw.isEmpty()) {
        UserManagementView.error("Both fields are required.");
        return;
      }
      if (newPw.getValue().length() < 8) {
        UserManagementView.error("Password must be at least 8 characters.");
        return;
      }
      if (!newPw.getValue().equals(confirmPw.getValue())) {
        UserManagementView.error("Password confirmation does not match.");
        return;
      }
      try {
        UserManagementClient client = UserManagementClientFactory.newInstance();
        if (!client.resetPassword(user.username(), newPw.getValue())) {
          UserManagementView.error("User no longer exists.");
          return;
        }
        UserManagementView.info("Password reset; existing sessions revoked.");
        close();
      } catch (IOException ex) {
        UserManagementView.error(UserManagementView.extractMessage(ex));
      }
    });

    getFooter().add(cancel, save);
  }
}

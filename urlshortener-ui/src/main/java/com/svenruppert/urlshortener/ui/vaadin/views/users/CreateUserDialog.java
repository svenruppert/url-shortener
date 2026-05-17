package com.svenruppert.urlshortener.ui.vaadin.views.users;

import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.users.CreateUserRequest;
import com.svenruppert.urlshortener.ui.vaadin.tools.UserManagementClientFactory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import java.io.IOException;

/** Admin-side "create user" dialog. */
final class CreateUserDialog extends Dialog {

  CreateUserDialog(Runnable onSuccess) {
    setHeaderTitle("Create user");

    TextField username = new TextField("Username");
    username.setRequiredIndicatorVisible(true);
    PasswordField password = new PasswordField("Password");
    password.setHelperText("At least 8 characters.");
    password.setRequiredIndicatorVisible(true);
    TextField displayName = new TextField("Display name");
    ComboBox<String> role = new ComboBox<>("Role");
    role.setItems("ROLE_USER", "ROLE_ADMIN");
    role.setValue("ROLE_USER");
    role.setRequiredIndicatorVisible(true);

    FormLayout form = new FormLayout(username, password, displayName, role);
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
    add(form);

    Button cancel = new Button("Cancel", e -> close());
    Button save = new Button("Create");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
        UserManagementView.error("Username, password and role are required.");
        return;
      }
      if (password.getValue().length() < 8) {
        UserManagementView.error("Password must be at least 8 characters.");
        return;
      }
      try {
        UserManagementClient client = UserManagementClientFactory.newInstance();
        client.createUser(new CreateUserRequest(
            username.getValue().trim(),
            password.getValue(),
            displayName.isEmpty() ? null : displayName.getValue().trim(),
            role.getValue()));
        UserManagementView.info("Created user '" + username.getValue().trim() + "'");
        close();
        onSuccess.run();
      } catch (IOException ex) {
        UserManagementView.error(UserManagementView.extractMessage(ex));
      }
    });

    getFooter().add(cancel, save);
  }
}

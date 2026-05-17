package com.svenruppert.urlshortener.ui.vaadin.views.users;

import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.users.UpdateUserRequest;
import com.svenruppert.urlshortener.core.users.UserSummary;
import com.svenruppert.urlshortener.ui.vaadin.tools.UserManagementClientFactory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import java.io.IOException;
import java.util.Objects;

/**
 * Edit dialog for an existing user. Submits a partial update — fields whose
 * value did not change are sent as {@code null} so the server keeps them.
 */
final class EditUserDialog extends Dialog {

  EditUserDialog(UserSummary user, Runnable onSuccess) {
    setHeaderTitle("Edit user '" + user.username() + "'");

    TextField displayName = new TextField("Display name");
    displayName.setValue(user.displayName() == null ? "" : user.displayName());

    ComboBox<String> role = new ComboBox<>("Role");
    role.setItems("ROLE_USER", "ROLE_ADMIN");
    role.setValue(user.role());

    Checkbox enabled = new Checkbox("Enabled", user.enabled());

    FormLayout form = new FormLayout(
        new Span("Username: " + user.username()),
        displayName, role, enabled);
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
    add(form);

    Button cancel = new Button("Cancel", e -> close());
    Button save = new Button("Save");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      String newDisplay = displayName.getValue().trim();
      String roleValue = role.getValue();
      Boolean enabledValue = enabled.getValue();

      String displayPatch = Objects.equals(newDisplay, user.displayName())
          ? null
          : newDisplay;
      String rolePatch = Objects.equals(roleValue, user.role()) ? null : roleValue;
      Boolean enabledPatch = enabledValue == user.enabled() ? null : enabledValue;

      if (displayPatch == null && rolePatch == null && enabledPatch == null) {
        UserManagementView.info("No changes.");
        close();
        return;
      }

      try {
        UserManagementClient client = UserManagementClientFactory.newInstance();
        client.updateUser(user.username(),
            new UpdateUserRequest(displayPatch, rolePatch, enabledPatch));
        UserManagementView.info("Updated '" + user.username() + "'");
        close();
        onSuccess.run();
      } catch (IOException ex) {
        UserManagementView.error(UserManagementView.extractMessage(ex));
      }
    });

    getFooter().add(cancel, save);
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views.audit;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.AuditClient;
import com.svenruppert.urlshortener.core.audit.AuditEventView;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.AuditClientFactory;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.List;

/**
 * Admin-only viewer for the security audit ring buffer. Pulls events from
 * {@code GET /api/audit} with optional type/subject filters and renders
 * them in a typed grid.
 */
@Route(value = AuditView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.ROLE_ADMIN)
public class AuditView extends Composite<VerticalLayout> implements HasLogger {

  public static final String PATH = "audit";

  private static final List<String> TYPES = List.of(
      "LoginSucceeded", "LoginFailed", "LogoutPerformed",
      "AccessGranted", "AccessDenied", "ActionDenied",
      "BruteForceLimitReached",
      "SessionCreated", "SessionExpired", "SessionInvalidated",
      "RoleAssigned", "RoleRevoked",
      "UserCreated", "UserDeleted",
      "BootstrapAdminCreated", "BootstrapTokenRejected");

  private final Grid<AuditEventView> grid = new Grid<>(AuditEventView.class, false);
  private final ComboBox<String> typeFilter = new ComboBox<>("Type");
  private final TextField subjectFilter = new TextField("Subject");

  public AuditView() {
    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setPadding(true);
    root.setSpacing(true);

    H2 title = new H2("Security audit");

    typeFilter.setItems(TYPES);
    typeFilter.setClearButtonVisible(true);

    subjectFilter.setClearButtonVisible(true);

    Button reload = new Button("Reload", new Icon(VaadinIcon.REFRESH));
    reload.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    reload.addClickListener(e -> reload());

    HorizontalLayout filters = new HorizontalLayout(typeFilter, subjectFilter, reload);
    filters.setAlignItems(HorizontalLayout.Alignment.BASELINE);
    filters.setSpacing(true);

    grid.addColumn(AuditEventView::timestamp).setHeader("Timestamp").setAutoWidth(true);
    grid.addColumn(AuditEventView::type).setHeader("Type").setAutoWidth(true);
    grid.addColumn(AuditEventView::subject).setHeader("Subject").setAutoWidth(true);
    grid.addColumn(AuditEventView::target).setHeader("Target").setAutoWidth(true);
    grid.addColumn(AuditEventView::detail).setHeader("Detail").setAutoWidth(true);
    grid.setSizeFull();

    root.add(title, filters, grid);
    reload();
  }

  private void reload() {
    try {
      AuditClient client = AuditClientFactory.newInstance();
      List<AuditEventView> events = client.fetch(
          typeFilter.getValue(),
          subjectFilter.getValue(),
          500);
      grid.setItems(events);
    } catch (IOException ex) {
      Notification n = Notification.show("Audit load failed: " + ex.getMessage(),
          4500, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import static com.vaadin.flow.component.button.ButtonVariant.LUMO_ERROR;

@CssImport("./styles/bulk-actions-bar.css")
public class BulkActionsBar
    extends Composite<HorizontalLayout>
    implements HasLogger {

  private static final String C_ROOT = "bulk-actions-bar";
  private static final String C_INFO = "bulk-actions-bar__info";
  private static final String C_BTN  = "bulk-actions-bar__btn";
  private static final String C_ICON = "bulk-actions-bar__icon";

  private final URLShortenerClient urlShortenerClient;
  private final Grid<ShortUrlMapping> grid;
  private final OverviewView holdingComponent;

  private final Button bulkDeleteBtn = new Button();
  private final Button bulkSetExpiryBtn = new Button();
  private final Button bulkClearExpiryBtn = new Button();
  private final Button bulkActivateBtn = new Button();
  private final Button bulkDeactivateBtn = new Button();

  private final Span selectionInfo = new Span();

  public BulkActionsBar(URLShortenerClient urlShortenerClient,
                        Grid<ShortUrlMapping> grid,
                        OverviewView holdingComponent) {
    this.urlShortenerClient = urlShortenerClient;
    this.grid = grid;
    this.holdingComponent = holdingComponent;

    buildBulkBar();
    addListeners();
  }

  private HorizontalLayout bulkBar() {
    return this.getContent();
  }

  private void buildBulkBar() {
    bulkBar().addClassName(C_ROOT);

    selectionInfo.addClassName(C_INFO);

    setupIconButton(bulkDeleteBtn,
                    VaadinIcon.TRASH,
                    "Delete selected links",
                    "danger");

    setupIconButton(bulkSetExpiryBtn,
                    VaadinIcon.CALENDAR_CLOCK,
                    "Set expiry for selected",
                    "primary");

    setupIconButton(bulkClearExpiryBtn,
                    VaadinIcon.CALENDAR_CLOCK,
                    "Clear expiry for selected",
                    "neutral");

    setupIconButton(bulkActivateBtn,
                    VaadinIcon.CHECK_CIRCLE,
                    "Activate selected",
                    "success");

    setupIconButton(bulkDeactivateBtn,
                    VaadinIcon.CLOSE_CIRCLE,
                    "Deactivate selected",
                    "danger");

    bulkBar().removeAll();
    bulkBar().add(
        selectionInfo,
        bulkDeleteBtn,
        bulkSetExpiryBtn,
        bulkClearExpiryBtn,
        bulkActivateBtn,
        bulkDeactivateBtn
    );

    bulkBar().setWidthFull();
    bulkBar().setSpacing(true);
    bulkBar().setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
    bulkBar().setVisible(false);
  }

  private void setupIconButton(Button btn,
                               VaadinIcon icon,
                               String tooltip,
                               String role) {

    btn.addClassName(C_BTN);
    btn.addClassName(C_BTN + "--" + role);

    Icon ic = icon.create();
    ic.addClassName(C_ICON);

    btn.setIcon(ic);
    btn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
    btn.getElement().setProperty("title", tooltip);
  }

  private void addListeners() {
    bulkDeleteBtn.addClickListener(_ -> confirmBulkDeleteSelected());
    bulkSetExpiryBtn.addClickListener(_ -> openBulkSetExpiryDialog());
    bulkClearExpiryBtn.addClickListener(_ -> confirmBulkClearExpirySelected());
    bulkActivateBtn.addClickListener(_ -> confirmBulkActivateSelected());
    bulkDeactivateBtn.addClickListener(_ -> confirmBulkDeactivateSelected());
  }

  public void confirmBulkDeleteSelected() {
    var selected = grid.getSelectedItems();
    if (selected.isEmpty()) {
      Notifications.noSelection();
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Delete " + selected.size() + " short links?");

    var exampleCodes = selected.stream()
        .map(ShortUrlMapping::shortCode)
        .sorted()
        .limit(5)
        .toList();

    if (!exampleCodes.isEmpty()) {
      String preview = String.join(", ", exampleCodes);
      if (selected.size() > 5) preview += ", …";
      dialog.add(new Text("Examples: " + preview));
    } else {
      dialog.add(new Text("Delete selected short links?"));
    }

    Button confirm = new Button("Delete", _ -> {
      int success = 0;
      int failed = 0;

      for (var m : selected) {
        try {
          boolean ok = urlShortenerClient.delete(m.shortCode());
          if (ok) success++; else failed++;
        } catch (IOException ex) {
          logger().error("Bulk delete failed for {}", m.shortCode(), ex);
          failed++;
        }
      }

      dialog.close();
      grid.deselectAll();
      holdingComponent.safeRefresh();
      Notifications.deletedAndNotDeleted(success, failed);
    });
    confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, LUMO_ERROR);

    Button cancel = new Button("Cancel", _ -> dialog.close());
    dialog.getFooter().add(new HorizontalLayout(confirm, cancel));
    dialog.open();
  }

  private void openBulkSetExpiryDialog() {
    var selected = grid.getSelectedItems();
    if (selected.isEmpty()) {
      Notifications.noSelection();
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Set expiry for " + selected.size() + " short links");

    DatePicker date = new DatePicker("Date");
    TimePicker time = new TimePicker("Time");
    time.setStep(Duration.ofMinutes(15));

    HorizontalLayout body = new HorizontalLayout(date, time);
    body.setAlignItems(FlexComponent.Alignment.END);
    dialog.add(body);

    Button cancel = new Button("Cancel", _ -> dialog.close());
    Button apply = new Button("Apply", _ -> {
      if (date.getValue() == null) {
        Notifications.noDateSelected();
        return;
      }

      var localTime = Optional.ofNullable(time.getValue())
          .orElse(java.time.LocalTime.of(0, 0));
      var zdt = ZonedDateTime.of(date.getValue(), localTime, ZoneId.systemDefault());
      Instant expiresAt = zdt.toInstant();

      int success = 0;
      int failed = 0;

      for (ShortUrlMapping m : selected) {
        try {
          boolean ok = urlShortenerClient.edit(
              m.shortCode(),
              m.originalUrl(),
              expiresAt,
              m.active()
          );
          if (ok) success++; else failed++;
        } catch (IOException ex) {
          logger().error("Bulk set expiry failed for {}", m.shortCode(), ex);
          failed++;
        }
      }

      dialog.close();
      grid.deselectAll();
      holdingComponent.safeRefresh();
      Notifications.updatedAndNotUpdated(success, failed);
    });
    apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    dialog.getFooter().add(new HorizontalLayout(cancel, apply));
    dialog.open();
  }

  private void confirmBulkClearExpirySelected() {
    var selected = grid.getSelectedItems();
    if (selected.isEmpty()) {
      Notifications.noSelection();
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Remove expiry for " + selected.size() + " short links?");

    dialog.add(new Text(
        "This will remove the expiry date from all selected short links. "
            + "They will no longer expire automatically."
    ));

    Button cancel = new Button("Cancel", _ -> dialog.close());
    Button confirm = new Button("Remove expiry", _ -> {
      dialog.close();
      bulkClearExpiry(selected);
    });
    confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    dialog.getFooter().add(new HorizontalLayout(cancel, confirm));
    dialog.open();
  }

  private void bulkClearExpiry(Set<ShortUrlMapping> selected) {
    int success = 0;
    int failed = 0;

    for (var m : selected) {
      try {
        boolean ok = urlShortenerClient.edit(
            m.shortCode(),
            m.originalUrl(),
            null,
            m.active()
        );
        if (ok) success++; else failed++;
      } catch (IOException ex) {
        logger().error("Bulk clear expiry failed for {}", m.shortCode(), ex);
        failed++;
      }
    }

    grid.deselectAll();
    holdingComponent.safeRefresh();
    Notifications.updatedAndNotUpdated(success, failed);
  }

  private void bulkSetActive(Set<ShortUrlMapping> selected, boolean activate) {
    int success = 0;
    int failed = 0;

    for (var m : selected) {
      try {
        var ok = urlShortenerClient.toggleActive(m.shortCode(), activate);
        if (ok) success++; else failed++;
      } catch (IOException ex) {
        logger().error("Toggle active state failed for {}", m.shortCode(), ex);
        failed++;
      }
    }

    grid.deselectAll();
    holdingComponent.safeRefresh();
    Notifications.updatedAndNotUpdated(success, failed);
  }

  private void confirmBulkSetActiveSelected(boolean activate) {
    var selected = grid.getSelectedItems();
    if (selected.isEmpty()) {
      Notifications.noSelection();
      return;
    }

    var verb = activate ? "activate" : "deactivate";
    var verbCap = activate ? "Activate" : "Deactivate";

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(verbCap + " all " + selected.size() + " short links?");

    dialog.add(new Text(
        "This will " + verb + " all selected short links. "
            + "They will be " + (activate ? "active" : "inactive") + " afterwards."
    ));

    Button cancel = new Button("Cancel", _ -> dialog.close());
    Button confirm = new Button(verbCap + " All", _ -> {
      dialog.close();
      bulkSetActive(Set.copyOf(selected), activate);
    });
    confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    dialog.getFooter().add(new HorizontalLayout(cancel, confirm));
    dialog.open();
  }

  private void confirmBulkActivateSelected() {
    confirmBulkSetActiveSelected(true);
  }

  private void confirmBulkDeactivateSelected() {
    confirmBulkSetActiveSelected(false);
  }

  public void setButtonsEnabled(boolean hasSelection) {
    bulkDeleteBtn.setEnabled(hasSelection);
    bulkSetExpiryBtn.setEnabled(hasSelection);
    bulkClearExpiryBtn.setEnabled(hasSelection);
    bulkActivateBtn.setEnabled(hasSelection);
    bulkDeactivateBtn.setEnabled(hasSelection);
  }

  public void selectionInfoText(String txt) {
    selectionInfo.setText(txt);
    selectionInfo.setVisible(!txt.isBlank());
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views.overview.imports;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.imports.ImportResult;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.svenruppert.urlshortener.core.DefaultValues.APPLICATION_ZIP;
import static com.svenruppert.urlshortener.core.DefaultValues.IMPORT_MAX_ZIP_BYTES;
import static com.svenruppert.urlshortener.core.urlmapping.imports.JsonFieldExtractor.extractJsonInt;
import static com.svenruppert.urlshortener.core.urlmapping.imports.JsonFieldExtractor.extractJsonString;

public final class ImportDialog
    extends Dialog
    implements HasLogger {

  private final URLShortenerClient client;

  private final Upload upload = new Upload();
  private byte[] zipBytes;

  private final Button btnValidate = new Button("Validate");
  private final Button btnApply = new Button("Apply Import");
  private final Button btnClose = new Button("Close");

  private final Span lblStaging = new Span("-");
  private final Span lblNew = new Span("0");
  private final Span lblConflicts = new Span("0");
  private final Span lblInvalid = new Span("0");
  private final Tab tabConflicts = new Tab("Conflicts");
  private final Tab tabInvalid = new Tab("Invalid");
  private final Tabs tabs = new Tabs(tabConflicts, tabInvalid);
  private final Grid<ConflictRow> gridConflicts = new Grid<>(ConflictRow.class, false);
  private final Grid<InvalidRow> gridInvalid = new Grid<>(InvalidRow.class, false);
  private final PagingBar pagingConflicts = new PagingBar(50);
  private final PagingBar pagingInvalid = new PagingBar(50);
  private final Runnable onImportApplied;
  private final Checkbox chkSkipConflicts = new Checkbox("Skip conflicts on apply");
  private final Div applyHint = new Div();
  private String stagingId;


  public ImportDialog(URLShortenerClient client, Runnable onImportApplied) {
    this.client = client;
    this.onImportApplied = onImportApplied != null ? onImportApplied : () -> { };


    setHeaderTitle("Import");
    setModal(true);
    setResizable(true);
    setDraggable(true);
    setWidth("1100px");
    setHeight("750px");

    chkSkipConflicts.setEnabled(false);
    chkSkipConflicts.setValue(false);
    chkSkipConflicts.addValueChangeListener(_ -> updateApplyState());

    applyHint.getStyle().set("font-size", "var(--lumo-font-size-s)");
    applyHint.getStyle().set("color", "var(--lumo-secondary-text-color)");
    applyHint.setText("Upload a ZIP and validate to continue.");

    upload.setAcceptedFileTypes(".zip", APPLICATION_ZIP);
    upload.setMaxFiles(1);
    upload.setMaxFileSize(IMPORT_MAX_ZIP_BYTES);
    upload.addAllFinishedListener(listener -> {
      logger().info("Upload finished..");
      btnApply.setEnabled(false);
    });
    upload.addFileRejectedListener(event -> {
      String errorMessage = event.getErrorMessage();
      Notification notification = Notification.show(errorMessage, 5000,
                                                    Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      logger().warn("upload.addFileRejectedListener - {}", errorMessage);
    });
    UploadHandler inMemoryUploadHandler = UploadHandler
        .inMemory((metadata, bytes) -> {
          String fileName = metadata.fileName();
          //String mimeType = metadata.contentType();
          long contentLength = metadata.contentLength();
          logger().info("uploaded file: fileName: {} , contentLength {}", fileName, contentLength);
          zipBytes = bytes;
          logger().info("setting zipBytes..");
          btnValidate.setEnabled(true);
        });
    upload.setUploadHandler(inMemoryUploadHandler);

    btnValidate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    btnValidate.setEnabled(false);

    btnApply.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
    btnApply.setEnabled(false);

    btnClose.addClickListener(_ -> close());

    btnValidate.addClickListener(_ -> validate());
    btnApply.addClickListener(_ -> applyImport());

    initGrids();
    add(buildContent());
    getFooter().add(btnClose, btnValidate, btnApply);
  }

  private static Component labelPair(String label, Span value) {
    var l = new Span(label + ": ");
    l.getStyle().set("font-weight", "600");
    var row = new HorizontalLayout(l, value);
    row.setSpacing(false);
    row.getStyle().set("gap", "6px");
    return row;
  }

  private static int parseInt(String s, int def) {
    try {
      return Integer.parseInt(s);
    } catch (Exception e) {
      return def;
    }
  }

  private Component buildContent() {
    var summary = new HorizontalLayout(
        labelPair("stagingId", lblStaging),
        labelPair("new", lblNew),
        labelPair("conflicts", lblConflicts),
        labelPair("invalid", lblInvalid)
    );
    summary.setWidthFull();

    var applyRow = new HorizontalLayout(chkSkipConflicts, applyHint);
    applyRow.setWidthFull();
    applyRow.setDefaultVerticalComponentAlignment(HorizontalLayout.Alignment.CENTER);
    applyRow.expand(applyHint);


    var tabContent = new VerticalLayout();
    tabContent.setPadding(false);
    tabContent.setSpacing(false);
    tabContent.setSizeFull();

    tabs.addSelectedChangeListener(e -> renderTab(tabContent));
    tabs.setSelectedTab(tabConflicts);

    var root = new VerticalLayout(
        new H3("Upload ZIP"),
        upload,
        new H3("Preview"),
        summary,
        applyRow,
        tabs,
        tabContent
    );

    root.setSizeFull();
    root.setPadding(false);

    renderTab(tabContent);
    return root;
  }

  private void initGrids() {
    gridConflicts.addColumn(ConflictRow::shortCode).setHeader("shortCode").setAutoWidth(true).setFlexGrow(0);
    gridConflicts.addColumn(ConflictRow::diff).setHeader("diff").setAutoWidth(true).setFlexGrow(0);
    gridConflicts.addColumn(ConflictRow::existingUrl).setHeader("existingUrl").setAutoWidth(true);
    gridConflicts.addColumn(ConflictRow::incomingUrl).setHeader("incomingUrl").setAutoWidth(true);
    gridConflicts.addColumn(ConflictRow::existingActive).setHeader("existingActive").setAutoWidth(true).setFlexGrow(0);
    gridConflicts.addColumn(ConflictRow::incomingActive).setHeader("incomingActive").setAutoWidth(true).setFlexGrow(0);
    gridConflicts.addColumn(ConflictRow::existingExpiresAt).setHeader("existingExpiresAt").setAutoWidth(true).setFlexGrow(0);
    gridConflicts.addColumn(ConflictRow::incomingExpiresAt).setHeader("incomingExpiresAt").setAutoWidth(true).setFlexGrow(0);
    gridConflicts.setSizeFull();

    gridInvalid.addColumn(InvalidRow::shortCode).setHeader("shortCode").setAutoWidth(true).setFlexGrow(0);
    gridInvalid.addColumn(InvalidRow::reason).setHeader("reason").setAutoWidth(true);
    gridInvalid.setSizeFull();
  }



  private void renderTab(VerticalLayout container) {
    container.removeAll();
    container.setSizeFull();

    if (tabs.getSelectedTab() == tabInvalid) {
      container.add(pagingInvalid, gridInvalid);
      container.expand(gridInvalid);
    } else {
      container.add(pagingConflicts, gridConflicts);
      container.expand(gridConflicts);
    }
  }

  private void validate() {
    try {
      if (zipBytes == null || zipBytes.length == 0) {
        Notification.show("No ZIP uploaded.", 2500, Notification.Position.TOP_CENTER);
        return;
      }

      // B3: raw preview JSON (mit conflictItems[] + invalidItems[])
      String previewJson = client.importValidateRaw(zipBytes);
      logger().info("importValidateRaw response ->|{}|<-", previewJson);
      this.stagingId = extractJsonString(previewJson, "stagingId");
      int newItems = extractJsonInt(previewJson, "newItems", 0);
      int conflicts = extractJsonInt(previewJson, "conflicts", 0);
      int invalid = extractJsonInt(previewJson, "invalid", 0);

      lblStaging.setText(stagingId == null ? "-" : stagingId);
      lblNew.setText(String.valueOf(newItems));
      lblConflicts.setText(String.valueOf(conflicts));
      lblInvalid.setText(String.valueOf(invalid));

      // B3: Arrays direkt aus dem Validate-Response lesen
      List<ConflictRow> conflictRows = new ArrayList<>();
      try (StringReader r = new StringReader(previewJson)) {
        for (String obj : new ItemsArrayIterator(r, "conflictItems")) {
          Map<String, String> m = JsonUtils.parseJson(obj);
          conflictRows.add(ConflictRow.from(m));
        }
      }

      List<InvalidRow> invalidRows = new ArrayList<>();
      try (StringReader r = new StringReader(previewJson)) {
        for (String obj : new ItemsArrayIterator(r, "invalidItems")) {
          Map<String, String> m = JsonUtils.parseJson(obj);
          invalidRows.add(InvalidRow.from(m));
        }
      }

      gridConflicts.setItems(conflictRows);
      gridInvalid.setItems(invalidRows);

      // Paging ist bei B3 nur noch "Anzeige", keine Server-Calls mehr
      pagingConflicts.setTotal(conflictRows.size());
      pagingConflicts.setPage(1);
      pagingInvalid.setTotal(invalidRows.size());
      pagingInvalid.setPage(1);

      chkSkipConflicts.setEnabled(conflicts > 0);
      chkSkipConflicts.setValue(false); // immer reset

      updateApplyState();

      Notification.show("Import validated.", 2500, Notification.Position.TOP_CENTER);

    } catch (Exception ex) {
      Notifications.operationFailed(ex);
      logger().warn("validate {}", ex.getMessage());
    }
  }


  /**
   * Updates the state of the "Apply Import" button and its associated hint text based on the current
   * conditions, such as the presence of a valid staging ID, the number of invalid items, and
   * conflicts with the option to skip conflicts. The following logic governs the button's state:
   *
   * - If there is no valid staging ID, the "Apply Import" button is disabled and a hint is shown
   *   indicating the need to validate an import archive.
   * - If there are invalid items, the "Apply Import" button remains disabled, and a hint provides
   *   guidance on resolving or removing invalid items before proceeding.
   * - If there are conflicts, but the "Skip conflicts on apply" option is selected, the button's
   *   visual style is updated to indicate the skipped conflicts.
   * - If conflicts exist and the "Skip conflicts on apply" option is not selected, the "Apply Import"
   *   button remains disabled with a hint directing the user to enable skipping conflicts.
   * - If no invalid items or blocking conflicts exist, the button is enabled, and appropriate text
   *   is displayed based on the conflict status.
   *
   * This method relies on parsing integer values for invalid items and conflicts and toggling
   * component states accordingly.
   */
  private void updateApplyState() {
    // Default: disabled
    btnApply.setEnabled(false);
    btnApply.setText("Apply Import");

    if (stagingId == null || stagingId.isBlank()) {
      applyHint.setText("Validate an import archive first.");
      return;
    }

    int invalid = parseInt(lblInvalid.getText(), 0);
    int conflicts = parseInt(lblConflicts.getText(), 0);

    if (invalid > 0) {
      applyHint.setText("Apply disabled: " + invalid + " invalid item(s). Fix/remove them before applying.");
      btnApply.setText("Apply Import");
      return;
    }

    if (conflicts > 0 && chkSkipConflicts.getValue()) {
      btnApply.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    } else {
      btnApply.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
    }

    if (conflicts > 0 && !chkSkipConflicts.getValue()) {
      applyHint.setText("Apply disabled: " + conflicts + " conflict(s). Tick “Skip conflicts on apply” to proceed.");
      btnApply.setText("Apply Import");
      return;
    }

    btnApply.setEnabled(true);
    btnApply.setText(conflicts > 0 ? "Apply (skip conflicts)" : "Apply Import");
    applyHint.setText("Ready to apply import.");
  }

  private void applyImport() {
    if (stagingId == null || stagingId.isBlank()) return;

    try {
      ImportResult res = client.importApply(stagingId);
      onImportApplied.run();
      close(); // optional: wenn du ihn offen lassen willst, entferne diese Zeile

      Notification.show("Import applied: created=" + res.created()
                            + ", skippedConflicts=" + res.skippedConflicts()
                            + ", invalid=" + res.invalid(),
                        3500, Notification.Position.TOP_CENTER);

      btnApply.setEnabled(false);
      btnValidate.setEnabled(false);
      chkSkipConflicts.setEnabled(false);
      chkSkipConflicts.setValue(false);
      applyHint.setText("Import applied. Upload a new ZIP to continue.");

    } catch (Exception ex) {
      Notifications.operationFailed(ex);
      logger().warn("applyImport {}", ex.getMessage());
    }
  }

  public record ConflictRow(
      String shortCode,
      String diff,
      String existingUrl,
      String incomingUrl,
      String existingActive,
      String incomingActive,
      String existingExpiresAt,
      String incomingExpiresAt
  ) {
    static ConflictRow from(Map<String, String> m) {
      return new ConflictRow(
          m.get("shortCode"),
          m.get("diff"),
          m.get("existingUrl"),
          m.get("incomingUrl"),
          m.get("existingActive"),
          m.get("incomingActive"),
          m.get("existingExpiresAt"),
          m.get("incomingExpiresAt")
      );
    }
  }

  public record InvalidRow(String shortCode, String reason) {
    static InvalidRow from(Map<String, String> m) {
      return new InvalidRow(m.get("shortCode"), m.get("reason"));
    }
  }
}

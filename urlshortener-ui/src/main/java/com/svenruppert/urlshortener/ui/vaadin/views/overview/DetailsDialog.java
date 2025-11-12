package com.svenruppert.urlshortener.ui.vaadin.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.validation.UrlValidator;
import com.svenruppert.urlshortener.ui.vaadin.components.MultiAliasEditorStrict;
import com.svenruppert.urlshortener.ui.vaadin.events.MappingCreatedOrChanged;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.shared.Registration;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;

/**
 * Displays detailed information for a ShortUrlMapping.
 * Independent of any specific view; communicates through component events.
 */
public class DetailsDialog
    extends Dialog
    implements HasLogger {

  public static final ZoneId ZONE = ZoneId.systemDefault();
  private static final DateTimeFormatter DATE_TIME_FMT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE);

  private final String shortCode;
  private final String originalUrl;
  private final Instant createdAt;
  private final Optional<Instant> expiresAt;

  // UI components
  private final TextField tfShort = new TextField("Shortcode");
  private final TextField tfUrl = new TextField("Original URL");
  private final TextField tfCreated = new TextField("Created on");
  private final TextField tfExpires = new TextField("Expires");
  private final Span statusPill = new Span();

  private final Button openBtn = new Button("Open", new Icon(VaadinIcon.EXTERNAL_LINK));
  private final Button copyShortBtn = new Button("Copy ShortURL", new Icon(VaadinIcon.COPY));
  private final Button copyUrlBtn = new Button("Copy URL", new Icon(VaadinIcon.COPY));
  private final Button deleteBtn = new Button("Delete…", new Icon(VaadinIcon.TRASH));
  private final Button closeBtn = new Button("Close");

  private final Button editBtn = new Button("Edit", new Icon(VaadinIcon.EDIT));
  private final Button saveBtn = new Button(new Icon(VaadinIcon.CHECK));
  private final Button cancelBtn = new Button(new Icon(VaadinIcon.CLOSE));
  private final DateTimePicker expiresField = new DateTimePicker("Expires");
  private final URLShortenerClient client;
  private final ShortUrlMapping item;
  private final Binder<ShortUrlMapping> binder = new Binder<>(ShortUrlMapping.class);


  /**
   * @param mapping concrete ShortUrlMapping instance
   */
  public DetailsDialog(URLShortenerClient client, ShortUrlMapping mapping) {
    Objects.requireNonNull(client, "URLShortenerClient");
    Objects.requireNonNull(mapping, "ShortUrlMapping");

    this.client = client;
    this.item = mapping;

    this.shortCode = mapping.shortCode();
    this.originalUrl = mapping.originalUrl();
    this.createdAt = mapping.createdAt();
    this.expiresAt = mapping.expiresAt();

    setHeaderTitle("Details: " + shortCode);
    setModal(true);
    setDraggable(true);
    setResizable(true);
    setWidth("820px");

    openBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    editBtn.addThemeVariants(ButtonVariant.LUMO_WARNING);
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    saveBtn.setVisible(false);
    cancelBtn.setVisible(false);

    binder.forField(tfUrl)
        .asRequired("URL must not be blank")
        .withValidator((String url, ValueContext ctx) -> {
          var res = UrlValidator.validate(url);
          if (res.valid()) return ValidationResult.ok();
          else return ValidationResult.error(res.message());
        })
        .bind(ShortUrlMapping::originalUrl, (_, _) -> { });

    binder.forField(expiresField)
        .bind(
            m -> m.expiresAt().map(i -> LocalDateTime.ofInstant(i, ZONE)).orElse(null),
            (_, _) -> { }
        );
    binder.readBean(item);

    HorizontalLayout rightHeader = new HorizontalLayout(editBtn, saveBtn, cancelBtn);
    rightHeader.setSpacing(true);
    rightHeader.setPadding(false);
    rightHeader.setAlignItems(FlexComponent.Alignment.CENTER);

    var leftHeader = new HorizontalLayout(openBtn, copyShortBtn, copyUrlBtn, deleteBtn);
    leftHeader.setSpacing(true);
    leftHeader.setPadding(false);

    getHeader().add(leftHeader, rightHeader);

    configureFields();

    var form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("600px", 2)
    );
    form.add(tfShort, tfUrl, tfCreated, tfExpires, buildExpiresRow(), statusPill);
    form.setColspan(tfUrl, 2);
    add(form);

    Button addAliasesBtn = new Button("Add aliases…", _ -> openAddAliasesDialog(mapping));
    closeBtn.addClickListener(_ -> close());
    getFooter().add(closeBtn, addAliasesBtn);


    wireActions();
  }

  /**
   * Configures and populates all field components with the mapping values.
   */
  private void configureFields() {
    tfShort.setValue(shortCode);
    tfShort.setReadOnly(true);

    tfUrl.setValue(originalUrl);
    tfUrl.setReadOnly(true);
    tfUrl.getElement().setProperty("title", originalUrl);
    tfUrl.getStyle().set("white-space", "nowrap")
        .set("overflow", "hidden")
        .set("text-overflow", "ellipsis");

    tfCreated.setValue(DATE_TIME_FMT.format(createdAt));
    tfCreated.setReadOnly(true);

    tfExpires.setValue(expiresAt.map(DATE_TIME_FMT::format).orElse("No expiry date"));
    tfExpires.setReadOnly(true);

    statusPill.getElement().getThemeList().add("badge");
    statusPill.getElement().getThemeList().add("pill");
    statusPill.getElement().getThemeList().add("small");
    var statusText = computeStatusText();
    statusPill.setText(statusText.text());
    statusPill.getElement().getThemeList().add(statusText.theme());

    editBtn.getElement().setAttribute("title", "Edit");
    saveBtn.getElement().setAttribute("title", "Save");
    cancelBtn.getElement().setAttribute("title", "Cancel");
  }


  private Component buildExpiresRow() {
    expiresField.setLabel("Expires");
    expiresField.setStep(Duration.ofMinutes(1));
    expiresField.setWidthFull();
    expiresField.setVisible(false);
    expiresField.setWeekNumbersVisible(true);
    expiresField.setDatePickerI18n(
        new DatePicker.DatePickerI18n().setFirstDayOfWeek(1));
    expiresField.setStep(Duration.ofMinutes(1));
    expiresField.setWidthFull();
    Button clearBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL), _ -> expiresField.clear());
    clearBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    clearBtn.getElement().setAttribute("title", "Clear expiry");

    HorizontalLayout row = new HorizontalLayout(expiresField, clearBtn);
    row.setSpacing(true);
    row.setPadding(false);
    row.setWidthFull();
    row.setFlexGrow(1, expiresField);
    return row;
  }

  /**
   * Computes the expiry status label and its theme colour.
   */
  private Status computeStatusText() {
    return expiresAt.map(ts -> {
      long d = Duration.between(Instant.now(), ts).toDays();
      if (d < 0) return new Status("Expired", "error");
      if (d == 0) return new Status("Expires today", "warning");
      if (d <= 3) return new Status("Expires in " + d + " days", "warning");
      return new Status("Valid (" + d + " days left)", "success");
    }).orElse(new Status("No expiry", "contrast"));
  }

  /**
   * Sets up button actions and event propagation.
   */
  private void wireActions() {
    openBtn.addClickListener(_ -> {
      fireEvent(new OpenEvent(this, shortCode, originalUrl));
      getUI().ifPresent(ui -> ui.getPage().open(originalUrl, "_blank"));
    });

    copyShortBtn.addClickListener(_ -> {
      var shortURL = SHORTCODE_BASE_URL + shortCode;
      copyToClipboard(shortURL);
      fireEvent(new CopyShortcodeEvent(this, shortURL));
      Notification.show("Shortcode copied");
    });

    copyUrlBtn.addClickListener(_ -> {
      copyToClipboard(originalUrl);
      fireEvent(new CopyUrlEvent(this, originalUrl));
      Notification.show("URL copied");
    });

    deleteBtn.addClickListener(_ -> fireEvent(new DeleteEvent(this, shortCode)));

    editBtn.addClickListener(_ -> switchToEdit(true));
    saveBtn.addClickListener(_ -> onSave());

    cancelBtn.addClickListener(_ -> {
      binder.readBean(item);
      switchToEdit(false);
    });

  }

  private void copyToClipboard(String value) {
    logger().info("copyToClipboard {}", value);
    getUI().map(UI::getPage)
        .ifPresent(page -> page.executeJs("navigator.clipboard.writeText($0)", value));
  }

  private void switchToEdit(boolean enable) {
    tfUrl.setReadOnly(!enable);
    tfExpires.setVisible(!enable);

    expiresField.setVisible(enable);
    expiresField.setReadOnly(!enable);

    editBtn.setVisible(!enable);
    saveBtn.setVisible(enable);
    cancelBtn.setVisible(enable);

    if (enable) {
      tfUrl.focus();
      tfUrl.getElement().executeJs("this.select()");
    }
  }

  private void onSave() {
    try {
      String newUrl = tfUrl.getValue();
      var validate = UrlValidator.validate(newUrl);
      if (!validate.valid()) throw new RuntimeException(validate.message());

      LocalDateTime ldt = expiresField.getValue();
      Instant expires = (ldt == null) ? null : ldt.atZone(ZoneId.systemDefault()).toInstant();

      boolean ok = client.edit(item.shortCode(), newUrl, expires);
      if (ok) {
        Notification.show("Saved.");
        fireEvent(new SavedEvent(this, item.shortCode()));
        switchToEdit(false);
        fireEvent(new SavedEvent(this, item.shortCode()));
        close();
      } else {
        Notification.show("No changes.");
      }
    } catch (Exception ex) {
      logger().error("Save failed", ex);
      Notification.show("Save failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
    }
  }

  public String getShortCode() {
    return shortCode;
  }

  private void openAddAliasesDialog(ShortUrlMapping currentMapping) {
    var client = UrlShortenerClientFactory.newInstance();

    Dialog dlg = new Dialog();
    dlg.setHeaderTitle("new alias for: " + currentMapping.shortCode());
    dlg.setModal(true);
    dlg.setCloseOnEsc(true);
    dlg.setCloseOnOutsideClick(false);

    dlg.setWidth("70vw");
    dlg.getElement().getStyle().set("max-width", "1100px");

    var editor = new MultiAliasEditorStrict(
        SHORTCODE_BASE_URL,
        alias -> {
          try {
            return client.resolveShortcode(alias) == null;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
    );
    editor.setWidthFull();
    Button saveBtn = new Button("Save", _ -> {
      editor.validateAll();
      var validAliases = editor.getValidAliases();
      if (validAliases.isEmpty()) {
        Notification.show("no valid alias.", 2500, Notification.Position.TOP_CENTER);
        return;
      }

      int ok = 0;
      for (String alias : validAliases) {
        try {
          var expires = currentMapping.expiresAt().orElse(null);
          client.createCustomMapping(alias, currentMapping.originalUrl(), expires);
          editor.markSaved(alias);
          ok++;
        } catch (Exception ex) {
          editor.markError(alias, String.valueOf(ex.getMessage()));
        }
      }
      Notification.show("Saved: " + ok + " | Open: " + editor.countOpen(),
                        3500, Notification.Position.TOP_CENTER);
      refreshAfterAliasAdd();
    });

    Button closeBtn = new Button("Close", _ -> dlg.close());

    dlg.addOpenedChangeListener(e -> {
      if (!e.isOpened()) {
        var ui = UI.getCurrent();
        if (ui != null) {
          ComponentUtil.fireEvent(ui, new MappingCreatedOrChanged(this));
        }
      }
    });

    dlg.add(editor);
    dlg.getFooter().add(saveBtn, closeBtn);
    dlg.open();
  }

  private void refreshAfterAliasAdd() {
    var ui = UI.getCurrent();
    if (ui != null) {
      ComponentUtil.fireEvent(ui, new MappingCreatedOrChanged(this));
    }
  }

  // ---------- Public API

  public String getOriginalUrl() {
    return originalUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Optional<Instant> getExpiresAt() {
    return expiresAt;
  }

  public Registration addOpenListener(ComponentEventListener<OpenEvent> l) {
    return addListener(OpenEvent.class, l);
  }

  public Registration addCopyShortListener(ComponentEventListener<CopyShortcodeEvent> l) {
    return addListener(CopyShortcodeEvent.class, l);
  }

  public Registration addCopyUrlListener(ComponentEventListener<CopyUrlEvent> l) {
    return addListener(CopyUrlEvent.class, l);
  }

  public Registration addDeleteListener(ComponentEventListener<DeleteEvent> l) {
    return addListener(DeleteEvent.class, l);
  }

  public Registration addSavedListener(ComponentEventListener<SavedEvent> listener) {
    return addListener(SavedEvent.class, listener);
  }

  // ---- Event API ----
  public static class SavedEvent
      extends ComponentEvent<DetailsDialog> {
    private final String shortCode;

    public SavedEvent(DetailsDialog src, String shortCode) {
      super(src, false);
      this.shortCode = shortCode;
    }

    public String getShortCode() {
      return shortCode;
    }
  }

  private record Status(String text, String theme) { }

  // ---------- Event classes

  public static class DetailsEvent
      extends ComponentEvent<DetailsDialog> {
    public DetailsEvent(DetailsDialog source) {
      super(source, false);
    }
  }

  public static class OpenEvent
      extends DetailsEvent {
    public final String shortCode;
    public final String originalUrl;

    public OpenEvent(DetailsDialog src, String sc, String url) {
      super(src);
      this.shortCode = sc;
      this.originalUrl = url;
    }
  }

  public static class CopyShortcodeEvent
      extends DetailsEvent {
    public final String shortCode;

    public CopyShortcodeEvent(DetailsDialog src, String sc) {
      super(src);
      this.shortCode = sc;
    }
  }

  public static class CopyUrlEvent
      extends DetailsEvent {
    public final String url;

    public CopyUrlEvent(DetailsDialog src, String url) {
      super(src);
      this.url = url;
    }
  }

  public static class DeleteEvent
      extends DetailsEvent {
    public final String shortCode;

    public DeleteEvent(DetailsDialog src, String sc) {
      super(src);
      this.shortCode = sc;
    }
  }
}

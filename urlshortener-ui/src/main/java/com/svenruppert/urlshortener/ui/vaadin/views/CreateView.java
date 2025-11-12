package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import com.svenruppert.urlshortener.core.validation.UrlValidator;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.components.MultiAliasEditorStrict;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;

@Route(value = CreateView.PATH, layout = MainLayout.class)
public class CreateView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "create";
  private static final ZoneId ZONE = ZoneId.systemDefault();

  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();

  private final TextField urlField = new TextField("Target URL");
  private final Button saveAllButton = new Button("Save");
  private final Button resetButton = new Button("Reset");

  private final DatePicker expiresDate = new DatePicker("Expires (date)");
  private final TimePicker expiresTime = new TimePicker("Expires (time)");
  private final Checkbox noExpiry = new Checkbox("No expiry");

  private final FormLayout form = new FormLayout();

  public CreateView() {
    setSpacing(true);
    setPadding(true);
    setSizeFull();

    urlField.setWidthFull();
    saveAllButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    configureExpiryFields();

    form.add(urlField, noExpiry, expiresDate, expiresTime);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("900px", 2)
    );
    form.setColspan(urlField, 2);

    HorizontalLayout actions = new HorizontalLayout(saveAllButton, resetButton);
    actions.setWidthFull();
    actions.setJustifyContentMode(JustifyContentMode.START);

    Binder<ShortenRequest> binder = new Binder<>(ShortenRequest.class);

    binder.forField(urlField)
        .asRequired("URL must not be empty")
        .withValidator((String url, ValueContext ctx) -> {
          var res = UrlValidator.validate(url);
          return res.valid() ? ValidationResult.ok() : ValidationResult.error(res.message());
        })
        .bind(ShortenRequest::getUrl, ShortenRequest::setUrl);

    var editor = new MultiAliasEditorStrict(
        SHORTCODE_BASE_URL,
        alias -> {
          try {
            return urlShortenerClient.resolveShortcode(alias) == null;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
    );
    editor.setSizeFull();
    editor.getStyle().set("padding", "var(--lumo-space-m)");

    saveAllButton.addClickListener(_ -> {
      var validated = binder.validate();
      if (validated.hasErrors()) return;
      if (!validateExpiryInFuture()) return;

      if (urlField.getValue() == null || urlField.getValue().isBlank()) {
        Notification.show("Target URL is empty", 2500, Notification.Position.TOP_CENTER);
        return;
      }
      editor.validateAll();

      List<String> validAliases = editor.getValidAliases();
      if (validAliases.isEmpty()) {
        Notification.show("No valid aliases to save", 2000, Notification.Position.TOP_CENTER);
        return;
      }

      Optional<Instant> expiresAt = computeExpiresAt();

      int ok = 0;
      for (String alias : validAliases) {
        try {
          logger().info("try to save mapping {} / {} ", urlField.getValue(), alias);
          var customMapping = urlShortenerClient.createCustomMapping(alias, urlField.getValue(), expiresAt.orElse(null));
          logger().info("created customMapping is {}", customMapping);
          if (customMapping != null)
            logger().info("saved - {}", customMapping);
          else logger().info("save failed for target {} with alias {}", urlField.getValue(), alias);
          editor.markSaved(alias);
          ok++;
        } catch (Exception ex) {
          editor.markError(alias, String.valueOf(ex.getMessage()));
          logger().info("failed to save url with alias {}", alias);
        }
      }
      Notification.show("Saved: " + ok + " | Open: " + editor.countOpen(), 3500, Notification.Position.TOP_CENTER);
    });

    resetButton.addClickListener(_ -> {
      clearFormAll(binder);
      editor.clearAllRows();
    });

    // — SplitLayout
    var leftCol = new VerticalLayout(new H2("Create new short links"), form, actions);
    leftCol.setPadding(false);
    leftCol.setSpacing(true);
    leftCol.setSizeFull();

    var rightCol = new VerticalLayout(new H2("Aliases"), editor);
    rightCol.setPadding(false);
    rightCol.setSpacing(true);
    rightCol.setSizeFull();

    SplitLayout split = new SplitLayout(leftCol, rightCol);
    split.setSizeFull();
    split.setSplitterPosition(40);

    add(split);
  }

  private void configureExpiryFields() {
    expiresDate.setClearButtonVisible(true);
    expiresDate.setPlaceholder("dd.MM.yyyy");
    expiresTime.setStep(Duration.ofMinutes(1));
    expiresTime.setPlaceholder("HH:mm");

    expiresTime.setEnabled(false);
    expiresDate.addValueChangeListener(ev -> {
      boolean hasDate = ev.getValue() != null;
      expiresTime.setEnabled(hasDate && !noExpiry.getValue());
    });

    noExpiry.addValueChangeListener(ev -> {
      boolean disabled = ev.getValue();
      expiresDate.setEnabled(!disabled);
      expiresTime.setEnabled(!disabled && expiresDate.getValue() != null);
      if (disabled) {
        expiresDate.clear();
        expiresTime.clear();
      }
    });
  }

  private Optional<Instant> computeExpiresAt() {
    if (Boolean.TRUE.equals(noExpiry.getValue())) return Optional.empty();
    LocalDate d = expiresDate.getValue();
    LocalTime t = expiresTime.getValue();
    if (d == null || t == null) return Optional.empty();
    return Optional.of(ZonedDateTime.of(d, t, ZONE).toInstant());
  }

  private boolean validateExpiryInFuture() {
    var exp = computeExpiresAt();
    if (exp.isPresent() && exp.get().isBefore(Instant.now())) {
      Notification.show("Expiry must be in the future");
      return false;
    }
    return true;
  }

  private void clearFormAll(Binder<ShortenRequest> binder) {
    urlField.clear();
    noExpiry.clear();
    expiresDate.clear();
    expiresTime.clear();
    binder.setBean(new ShortenRequest());
    urlField.setInvalid(false);
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.ShortenRequest;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.time.*;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.AliasPolicy.REGEX_ALLOWED;

@Route(value = CreateView.PATH, layout = MainLayout.class)
public class CreateView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "create";
  private static final ZoneId ZONE = ZoneId.systemDefault();

  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();

  private final TextField urlField = new TextField("Target URL");
  private final TextField aliasField = new TextField("Alias (optional)");
  private final Button shortenButton = new Button("Shorten");

  private final DatePicker expiresDate = new DatePicker("Expires (date)");
  private final TimePicker expiresTime = new TimePicker("Expires (time)");
  private final Checkbox noExpiry = new Checkbox("No expiry");

  private final FormLayout form = new FormLayout();

  public CreateView() {
    setSpacing(true);
    setPadding(true);

    urlField.setWidthFull();
    aliasField.setWidth("300px");
    shortenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    // Build form
    form.add(urlField, aliasField);
    configureExpiryFields();

    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("600px", 2)
    );
    form.setColspan(urlField, 2);

    HorizontalLayout actions = new HorizontalLayout(shortenButton);
    actions.setAlignItems(Alignment.END);

    // Binder
    Binder<ShortenRequest> binder = new Binder<>(ShortenRequest.class);
    ShortenRequest request = new ShortenRequest();

    binder.forField(urlField)
        .asRequired("URL must not be empty")
        .withValidator(url -> url.startsWith("http://") || url.startsWith("https://"),
                       "Only HTTP(S) URLs allowed")
        .bind(ShortenRequest::getUrl, ShortenRequest::setUrl);

    binder.forField(aliasField)
        .withValidator(a -> a == null || a.isBlank() || a.length() >= AliasPolicy.MIN,
                       "Alias is too short (min " + AliasPolicy.MIN + ")")
        .withValidator(a -> a == null || a.isBlank() || a.length() <= AliasPolicy.MAX,
                       "Alias is too long (max " + AliasPolicy.MAX + ")")
        .withValidator(a -> a == null || a.isBlank() || a.matches(REGEX_ALLOWED),
                       "Only [A-Za-z0-9_-] allowed")
        .bind(ShortenRequest::getShortURL, ShortenRequest::setShortURL);

    shortenButton.addClickListener(_ -> {
      var validated = binder.validate();
      if (validated.hasErrors()) return;
      if (!validateExpiryInFuture()) return;

      if (binder.writeBeanIfValid(request)) {
        // If ShortenRequest has a setExpiresAt(Instant), set it. Otherwise ignore silently.
        computeExpiresAt().ifPresent(request::setExpiresAt);

        Optional<String> code = createShortCode(request, computeExpiresAt());
        code.ifPresentOrElse(c -> {
          Notification.show("Short link created: " + c);
          clearForm(binder);
        }, () -> Notification.show("Alias already assigned or error saving",
                                   3000, Notification.Position.MIDDLE));
      }
    });

    add(new H2("Create new short link"), form, actions);
  }

  private void configureExpiryFields() {
    expiresDate.setClearButtonVisible(true);
    expiresDate.setPlaceholder("dd.MM.yyyy");
    expiresTime.setStep(Duration.ofMinutes(1));
    expiresTime.setPlaceholder("HH:mm");

    // Disable time until a date is chosen
    expiresTime.setEnabled(false);
    expiresDate.addValueChangeListener(ev -> {
      boolean hasDate = ev.getValue() != null;
      expiresTime.setEnabled(hasDate && !noExpiry.getValue());
    });

    noExpiry.addValueChangeListener(ev -> {
      boolean disabled = ev.getValue();
      expiresDate.setEnabled(!disabled);
      expiresTime.setEnabled(!disabled && expiresDate.getValue() != null);
    });

    form.add(noExpiry, expiresDate, expiresTime);
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

  private void clearForm(Binder<ShortenRequest> binder) {
    urlField.clear();
    aliasField.clear();
    noExpiry.clear();
    expiresDate.clear();
    expiresTime.clear();
    binder.setBean(new ShortenRequest());
    urlField.setInvalid(false);
    aliasField.setInvalid(false);
  }

  /**
   * Creates the short code. Supports multiple backends:
   * A) URLShortenerClient.createMapping(String url, Instant expiresAt)
   * B) URLShortenerClient.createMapping(ShortenRequest)
   * C) Fallback to existing methods without expiry.
   */
  private Optional<String> createShortCode(ShortenRequest req, Optional<Instant> expiresAt) {
    logger().info("createShortCode with ShortenRequest '{}'", req);
    try {
      var customMapping = urlShortenerClient.createCustomMapping(req.getShortURL(), req.getUrl(), expiresAt.orElse(null));
      return Optional.ofNullable(customMapping.shortCode());
    } catch (IllegalArgumentException | IOException e) {
      logger().error("Error saving", e);
      return Optional.empty();
    }
  }


}

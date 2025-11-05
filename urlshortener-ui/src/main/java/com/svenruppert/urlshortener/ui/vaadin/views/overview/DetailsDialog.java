package com.svenruppert.urlshortener.ui.vaadin.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.shared.Registration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;

/**
 * Displays detailed information for a ShortUrlMapping.
 * Independent of any specific view; communicates through component events.
 */
public class DetailsDialog extends Dialog implements HasLogger {

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

  /**
   * @param mapping concrete ShortUrlMapping instance
   */
  public DetailsDialog(ShortUrlMapping mapping) {
    Objects.requireNonNull(mapping, "mapping");

    this.shortCode   = mapping.shortCode();
    this.originalUrl = mapping.originalUrl();
    this.createdAt   = mapping.createdAt();
    this.expiresAt   = mapping.expiresAt();

    setHeaderTitle("Details: " + shortCode);
    setModal(true);
    setDraggable(true);
    setResizable(true);
    setWidth("720px");

    // Header actions
    openBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

    var headerActions = new HorizontalLayout(openBtn, copyShortBtn, copyUrlBtn, deleteBtn);
    headerActions.setSpacing(true);
    headerActions.setPadding(false);
    getHeader().add(headerActions);

    // Content
    configureFields();
    var form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("600px", 2)
    );
    form.add(tfShort, tfUrl, tfCreated, tfExpires, statusPill);
    form.setColspan(tfUrl, 2);

    add(form);

    // Footer
    closeBtn.addClickListener(e -> close());
    getFooter().add(closeBtn);

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
  }

  private void copyToClipboard(String value) {
    logger().info("copyToClipboard {}", value);
    getUI().map(UI::getPage)
        .ifPresent(page -> page.executeJs("navigator.clipboard.writeText($0)", value));
  }

  // ---------- Public API

  public String getShortCode() {
    return shortCode;
  }

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

  private record Status(String text, String theme) { }

  // ---------- Event classes

  public static class DetailsEvent extends ComponentEvent<DetailsDialog> {
    public DetailsEvent(DetailsDialog source) {
      super(source, false);
    }
  }

  public static class OpenEvent extends DetailsEvent {
    public final String shortCode;
    public final String originalUrl;

    public OpenEvent(DetailsDialog src, String sc, String url) {
      super(src);
      this.shortCode = sc;
      this.originalUrl = url;
    }
  }

  public static class CopyShortcodeEvent extends DetailsEvent {
    public final String shortCode;

    public CopyShortcodeEvent(DetailsDialog src, String sc) {
      super(src);
      this.shortCode = sc;
    }
  }

  public static class CopyUrlEvent extends DetailsEvent {
    public final String url;

    public CopyUrlEvent(DetailsDialog src, String url) {
      super(src);
      this.url = url;
    }
  }

  public static class DeleteEvent extends DetailsEvent {
    public final String shortCode;

    public DeleteEvent(DetailsDialog src, String sc) {
      super(src);
      this.shortCode = sc;
    }
  }
}

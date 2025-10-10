package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.ShortenRequest;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.Optional;

@Route(value = CreateView.PATH, layout = MainLayout.class)
public class CreateView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "create";
  private final URLShortenerClient urlShortenerClient = new URLShortenerClient();

  public CreateView() {
    setSpacing(true);
    setPadding(true);

    TextField urlField = new TextField("Ziel-URL");
    urlField.setWidthFull();

    TextField aliasField = new TextField("Alias (optional)");
    aliasField.setWidth("300px");

    Button shortenButton = new Button("Verkürzen");
    shortenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout actions = new HorizontalLayout(shortenButton);
    actions.setAlignItems(Alignment.END);

    Binder<ShortenRequest> binder = new Binder<>(ShortenRequest.class);
    ShortenRequest request = new ShortenRequest();

    binder.forField(urlField)
        .asRequired("URL darf nicht leer sein")
        .withValidator(url -> url.startsWith("http://") || url.startsWith("https://"),
                       "Nur HTTP(S)-URLs erlaubt")
        .bind(ShortenRequest::getUrl, ShortenRequest::setUrl);

    binder.forField(aliasField)
        .bind(ShortenRequest::getAlias, ShortenRequest::setAlias);

    shortenButton.addClickListener(click -> {
      if (binder.writeBeanIfValid(request)) {
        Optional<String> code = createShortCode(request);
        code.ifPresentOrElse(c -> {
          Notification.show("Kurzlink erstellt: " + c);
          urlField.clear();
          aliasField.clear();
        }, () -> Notification.show("Alias bereits vergeben oder Fehler beim Speichern", 3000, Notification.Position.MIDDLE));
      }
    });

    add(new H2("Neuen Kurzlink erstellen"), urlField, aliasField, actions);
  }

  private Optional<String> createShortCode(ShortenRequest req) {
    try {
      String value = req.getAlias() == null || req.getAlias().isBlank()
          ? urlShortenerClient.createMapping(req.getUrl()).shortCode()
          : urlShortenerClient.createCustomMapping(req.getAlias(), req.getUrl()).shortCode();
      logger().info("UrlMapping : {}", value);
      return Optional.ofNullable(value);
    } catch (IllegalArgumentException | IOException e) {
      logger().error("Fehler beim Speichern", e);
      return Optional.empty();
    }
  }

}
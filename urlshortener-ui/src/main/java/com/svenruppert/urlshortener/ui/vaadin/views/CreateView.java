package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.ShortenRequest;
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

import static com.svenruppert.urlshortener.core.AliasPolicy.REGEX_ALLOWED;

@Route(value = CreateView.PATH, layout = MainLayout.class)
public class CreateView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "create";
  private final URLShortenerClient urlShortenerClient = new URLShortenerClient();

  public CreateView() {
    setSpacing(true);
    setPadding(true);

    TextField urlField = new TextField("Target URL");
    urlField.setWidthFull();

    TextField aliasField = new TextField("Alias (optional)");
    aliasField.setWidth("300px");

    Button shortenButton = new Button("Shorten");
    shortenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout actions = new HorizontalLayout(shortenButton);
    actions.setAlignItems(Alignment.END);

    Binder<ShortenRequest> binder = new Binder<>(ShortenRequest.class);
    ShortenRequest request = new ShortenRequest();

    binder.forField(urlField)
        .asRequired("URL must not be empty")
        //TODO - use better validator
        .withValidator(url -> url.startsWith("http://") || url.startsWith("https://"),
                       "Only HTTP(S) URLs allowed")
        .bind(ShortenRequest::getUrl, ShortenRequest::setUrl);

    binder.forField(aliasField)
        .withValidator(a -> a == null || a.isBlank() || a.length() >= AliasPolicy.MIN,
                       "Alias is too short (min " + AliasPolicy.MIN + ")")
        .withValidator(a -> a == null || a.isBlank() || a.length() <= AliasPolicy.MAX,
                       "Alias is too long (max " + AliasPolicy.MAX + ")")
        .withValidator(a -> a == null || a.isBlank() || a.matches(REGEX_ALLOWED),
                       "only [A-Za-z0-9_-] allowed")
        .bind(ShortenRequest::getShortURL, ShortenRequest::setShortURL);


    shortenButton.addClickListener(click -> {
      var validated = binder.validate();
      if (validated.hasErrors()) return;

      if (binder.writeBeanIfValid(request)) {
        Optional<String> code = createShortCode(request);
        code.ifPresentOrElse(c -> {
          Notification.show("Short link created:" + c);
          urlField.clear();
          aliasField.clear();
          binder.setBean(new ShortenRequest());
          urlField.setInvalid(false);
          aliasField.setInvalid(false);
        }, () -> Notification.show("Alias already assigned or error saving", 3000, Notification.Position.MIDDLE));
      }
    });

    add(new H2("Create new short link"), urlField, aliasField, actions);
  }

  private Optional<String> createShortCode(ShortenRequest req) {
    logger().info("createShortCode with ShortenRequest '{}'", req);
    try {
      var shortUrlIsNull = req.getShortURL() == null;
      var shortUrlIsBlank = req.getShortURL().isBlank();
      logger().info("shortUrlIsNull '{}' / shortUrlIsBlank '{}' ", shortUrlIsNull, shortUrlIsBlank);
      String value;
      if (shortUrlIsNull || shortUrlIsBlank) {
        var mapping = urlShortenerClient.createMapping(req.getUrl());
        logger().info("mapping - {}", mapping);
        value = mapping.shortCode();
      } else {
        var customMapping = urlShortenerClient.createCustomMapping(req.getShortURL(), req.getUrl());
        logger().info("customMapping - {}", customMapping);
        value = customMapping.shortCode();
      }
      logger().info("UrlMapping : {}", value);
      return Optional.ofNullable(value);
    } catch (IllegalArgumentException | IOException e) {
      logger().error("Error saving", e);
      return Optional.empty();
    }
  }

}
package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.urlshortener.api.store.InMemoryUrlMappingStore;
import com.svenruppert.urlshortener.api.store.UrlMappingStore;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;


@Route(value = ShortenerView.PATH, layout = MainLayout.class)
public class ShortenerView
    extends VerticalLayout {

  public static final String PATH = "shortener";
  private final UrlMappingStore store = new InMemoryUrlMappingStore();

  public ShortenerView() {
    TextField input = new TextField("Lange URL");
    input.setWidthFull();

    Button shorten = new Button("Verkürzen");
    Div result = new Div();

    shorten.addClickListener(click -> {
      String longUrl = input.getValue();
      if (longUrl == null || longUrl.isBlank()) {
        Notification.show("Bitte eine gültige URL eingeben.");
        return;
      }

      var mapping = store.createMapping(longUrl);
      var link = new Anchor("/" + mapping.shortCode(), mapping.shortCode());
      result.removeAll();
      result.add(link);
    });

    add(input, shorten, result);
    setWidth("600px");
    setPadding(true);
    setSpacing(true);
  }
}
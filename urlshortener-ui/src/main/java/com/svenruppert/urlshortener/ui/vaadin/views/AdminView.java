package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.ShortUrlMapping;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.List;

@Route(value = AdminView.PATH, layout = MainLayout.class)
public class AdminView
    extends VerticalLayout {

  public static final String PATH = "admin";

  private final URLShortenerClient urlShortenerClient = new URLShortenerClient();
  private final Grid<ShortUrlMapping> grid = new Grid<>(ShortUrlMapping.class, false);

  public AdminView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    configureGrid();
    updateGrid();

    add(grid);
  }

  private void configureGrid() {
    grid.setItems(List.of()); // initial leer
    grid.addColumn(ShortUrlMapping::shortCode).setHeader("Shortcode");
    grid.addColumn(mapping -> formatAnchor(mapping.shortCode())).setHeader("Link").setAutoWidth(true);
    grid.addColumn(ShortUrlMapping::originalUrl).setHeader("Original-URL").setFlexGrow(2);
    grid.addColumn(mapping -> mapping.createdAt().toString()).setHeader("Erstellt am");
    grid.addComponentColumn(this::createDeleteButton).setHeader("Aktion");

    grid.setAllRowsVisible(true);
  }

  private void updateGrid() {
    List<ShortUrlMapping> mappings = null;
    try {
      mappings = urlShortenerClient.listAll();
      grid.setItems(mappings);
    } catch (IOException e) {
      Notification.show("Data Update Failed.. ");
      throw new RuntimeException(e);
    }
  }

  private Anchor formatAnchor(String shortCode) {
    String href = "/" + shortCode;
    return new Anchor(href, href);
  }

  private Button createDeleteButton(ShortUrlMapping mapping) {
    Button delete = new Button("Löschen");
    delete.addClickListener(e -> {
      try {
        boolean removed = removeMapping(mapping.shortCode());
        if (removed) {
          Notification.show("Eintrag gelöscht: " + mapping.shortCode());
          updateGrid();
        } else {
          Notification.show("Löschen fehlgeschlagen.", 3000, Notification.Position.MIDDLE);
        }
      } catch (Exception exception) {
        Notification.show("Delete Failed.. ");
        throw new RuntimeException(exception);
      }

    });
    return delete;
  }

  private boolean removeMapping(String shortCode)
      throws IOException {
    return urlShortenerClient.delete(shortCode);
  }
}
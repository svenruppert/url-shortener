package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.ShortUrlMapping;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.io.IOException;

@Route(value = OverviewView.PATH, layout = MainLayout.class)
public class OverviewView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "";
  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();
  private final Grid<ShortUrlMapping> grid = new Grid<>(ShortUrlMapping.class, false);

  public OverviewView()
      throws IOException {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    grid.addColumn(ShortUrlMapping::shortCode).setHeader("Short Code");
    grid.addColumn(ShortUrlMapping::originalUrl).setHeader("Original URL").setFlexGrow(1);
    grid.addColumn(m -> m.createdAt().toString()).setHeader("Created on");
    grid.addComponentColumn(this::buildActionButtons).setHeader("Actions");

    var all = urlShortenerClient.listAll();
    logger().info("findAll - {}", all);
    grid.setItems(all);
    grid.setSizeFull();

    add(new H2("All short links"), grid);
  }

  private Component buildActionButtons(ShortUrlMapping mapping) {
    Button delete = new Button("Delete", e -> openConfirmDialog(mapping));
    delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
    return delete;
  }

  private void openConfirmDialog(ShortUrlMapping mapping) {
    Dialog dialog = new Dialog();
    dialog.add(new Text("Really delete short link? (" + mapping.shortCode() + ")"));

    Button confirm = new Button("Ja", e -> {
      try {
        urlShortenerClient.delete(mapping.shortCode());
        grid.setItems(urlShortenerClient.listAll());
        dialog.close();
        Notification.show("Short link deleted.");
      } catch (IOException ex) {
        Notification.show("Operation failed");
        throw new RuntimeException(ex);
      }

    });
    confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancel = new Button("Cancel", e -> dialog.close());

    dialog.add(new HorizontalLayout(confirm, cancel));
    dialog.open();
  }
}
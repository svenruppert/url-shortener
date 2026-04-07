package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse.BulkShortenItemResult;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;

import java.util.Arrays;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;

@Route(value = BulkCreateView.PATH, layout = MainLayout.class)
public class BulkCreateView extends VerticalLayout implements HasLogger, I18nSupport {

  public static final String PATH = "bulk-create";

  private static final String K_TITLE          = "bulkCreate.title";
  private static final String K_LABEL_URLS      = "bulkCreate.field.urls";
  private static final String K_PH_URLS         = "bulkCreate.field.urls.placeholder";
  private static final String K_BTN_CREATE      = "bulkCreate.btn.create";
  private static final String K_BTN_RESET       = "common.reset";
  private static final String K_COL_URL         = "bulkCreate.col.url";
  private static final String K_COL_SHORTCODE   = "bulkCreate.col.shortcode";
  private static final String K_COL_STATUS      = "bulkCreate.col.status";
  private static final String K_COL_ERROR       = "bulkCreate.col.error";
  private static final String K_SUMMARY         = "bulkCreate.summary";
  private static final String K_TOAST_EMPTY     = "bulkCreate.toast.empty";
  private static final String K_TOAST_DONE      = "bulkCreate.toast.done";
  private static final String K_STATUS_OK       = "bulkCreate.status.ok";
  private static final String K_STATUS_ERROR    = "bulkCreate.status.error";

  private final URLShortenerClient client = UrlShortenerClientFactory.newInstance();

  private final TextArea urlsArea = new TextArea();
  private final Button createButton = new Button();
  private final Button resetButton = new Button();
  private final Span summaryLabel = new Span();
  private final Grid<BulkShortenItemResult> resultsGrid = new Grid<>();

  public BulkCreateView() {
    setSizeFull();
    setSpacing(true);
    setPadding(true);

    add(new H2(tr(K_TITLE, "Bulk Create Short Links")));

    urlsArea.setLabel(tr(K_LABEL_URLS, "Target URLs (one per line)"));
    urlsArea.setPlaceholder(tr(K_PH_URLS, "https://example.com\nhttps://other.org"));
    urlsArea.setWidthFull();
    urlsArea.setMinHeight("180px");

    createButton.setText(tr(K_BTN_CREATE, "Create Links"));
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    resetButton.setText(tr(K_BTN_RESET, "Reset"));
    resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    createButton.addClickListener(_ -> processUrls());
    resetButton.addClickListener(_ -> reset());

    HorizontalLayout actions = new HorizontalLayout(createButton, resetButton);
    actions.setSpacing(true);

    configureGrid();

    add(urlsArea, actions, summaryLabel, resultsGrid);
    resultsGrid.setVisible(false);
    summaryLabel.setVisible(false);
  }

  private void configureGrid() {
    resultsGrid.addColumn(BulkShortenItemResult::getOriginalUrl)
        .setHeader(tr(K_COL_URL, "Original URL"))
        .setFlexGrow(3)
        .setResizable(true);

    resultsGrid.addComponentColumn(item -> {
      if (item.isSuccess()) {
        var link = new com.vaadin.flow.component.html.Anchor(
            SHORTCODE_BASE_URL + item.getShortCode(),
            item.getShortCode()
        );
        link.setTarget("_blank");
        return link;
      }
      return new Span("-");
    })
        .setHeader(tr(K_COL_SHORTCODE, "Short Code"))
        .setFlexGrow(1)
        .setResizable(true);

    resultsGrid.addComponentColumn(item -> {
      var badge = new Span(item.isSuccess()
          ? tr(K_STATUS_OK, "OK")
          : tr(K_STATUS_ERROR, "Error"));
      badge.getElement().getThemeList().add(item.isSuccess() ? "badge success" : "badge error");
      return badge;
    })
        .setHeader(tr(K_COL_STATUS, "Status"))
        .setFlexGrow(0)
        .setWidth("100px");

    resultsGrid.addColumn(item -> item.getErrorMessage() != null ? item.getErrorMessage() : "")
        .setHeader(tr(K_COL_ERROR, "Error"))
        .setFlexGrow(2)
        .setResizable(true);

    resultsGrid.setWidthFull();
    resultsGrid.setHeight("400px");
  }

  private void processUrls() {
    final String raw = urlsArea.getValue();
    if (raw == null || raw.isBlank()) {
      Notification.show(tr(K_TOAST_EMPTY, "Please enter at least one URL"),
                        2500, Notification.Position.TOP_CENTER);
      return;
    }

    final List<String> urls = Arrays.stream(raw.split("\\R"))
        .map(String::strip)
        .filter(s -> !s.isBlank())
        .toList();

    if (urls.isEmpty()) {
      Notification.show(tr(K_TOAST_EMPTY, "Please enter at least one URL"),
                        2500, Notification.Position.TOP_CENTER);
      return;
    }

    try {
      final BulkShortenResponse response = client.bulkShorten(urls);

      resultsGrid.setItems(response.getResults());
      resultsGrid.setVisible(true);

      summaryLabel.setText(tr(K_SUMMARY,
                               "Total: {0} | Created: {1} | Failed: {2}",
                               response.getTotal(),
                               response.getSucceeded(),
                               response.getFailed()));
      summaryLabel.setVisible(true);

      Notification.show(
          tr(K_TOAST_DONE, "Done: {0} created, {1} failed", response.getSucceeded(), response.getFailed()),
          3000, Notification.Position.TOP_CENTER);

    } catch (Exception e) {
      logger().error("Bulk create failed", e);
      Notification.show("Error: " + e.getMessage(), 4000, Notification.Position.TOP_CENTER);
    }
  }

  private void reset() {
    urlsArea.clear();
    resultsGrid.setItems(List.of());
    resultsGrid.setVisible(false);
    summaryLabel.setVisible(false);
  }
}

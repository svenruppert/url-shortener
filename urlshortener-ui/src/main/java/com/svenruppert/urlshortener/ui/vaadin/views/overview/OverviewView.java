package com.svenruppert.urlshortener.ui.vaadin.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.ColumnVisibilityClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.components.ColumnVisibilityDialog;
import com.svenruppert.urlshortener.ui.vaadin.events.MappingCreatedOrChanged;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreEvents;
import com.svenruppert.urlshortener.ui.vaadin.tools.ColumnVisibilityClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.ColumnVisibilityService;
import com.svenruppert.urlshortener.ui.vaadin.tools.HasRefreshGuard;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.svenruppert.urlshortener.core.DefaultValues.PATTERN_DATE_TIME;
import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;
import static com.vaadin.flow.component.button.ButtonVariant.LUMO_ERROR;
import static com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY_INLINE;
import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@PageTitle("Overview")
@Route(value = OverviewView.PATH, layout = MainLayout.class)
public class OverviewView
    extends VerticalLayout
    implements HasLogger, HasRefreshGuard {

  public static final String PATH = "";
  protected static final int VALUE_CHANGE_TIMEOUT = 400;
  private static final DateTimeFormatter DATE_TIME_FMT =
      DateTimeFormatter.ofPattern(PATTERN_DATE_TIME)
          .withLocale(Locale.GERMANY)
          .withZone(ZoneId.systemDefault());
  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();
  private final ColumnVisibilityClient columnVisibilityClient = ColumnVisibilityClientFactory.newInstance();
  //TODO  Initialize Column Visibility Client/Service (User ID later from security context)
  private final ColumnVisibilityService columnVisibilityService = new ColumnVisibilityService(columnVisibilityClient, "admin", "overview");
  private final Grid<ShortUrlMapping> grid = new Grid<>(ShortUrlMapping.class, false);
  private final BulkActionsBar bulkBar = new BulkActionsBar(urlShortenerClient, grid, this);
  private final Button btnSettings = new Button(new Icon(VaadinIcon.COG));
  private final SearchBar searchBar = new SearchBar(this);
  private final Button prevBtn = new Button("‹ Prev");
  private final Button nextBtn = new Button("Next ›");
  private final Text pageInfo = new Text("");
  private Integer currentPage = 1;
  private Integer totalCount = 0;
  private CallbackDataProvider<ShortUrlMapping, Void> dataProvider;
  private AutoCloseable subscription;
  private volatile boolean suppressRefresh = false;

  public OverviewView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);
    add(new H2("URL Shortener – Overview"));
    initDataProvider();

    var pagingBar = new HorizontalLayout(prevBtn, nextBtn, pageInfo, btnSettings);
    pagingBar.setDefaultVerticalComponentAlignment(CENTER);
    HorizontalLayout bottomBar = new HorizontalLayout(new Span(), pagingBar);
    bottomBar.setWidthFull();
    bottomBar.expand(bottomBar.getComponentAt(0));
    bottomBar.setAlignItems(CENTER);
    VerticalLayout container = new VerticalLayout(searchBar, bottomBar);
    container.setPadding(false);
    container.setSpacing(true);
    container.setWidthFull();
    add(container);

    add(bulkBar);
    add(grid);
    configureGrid();
    addListeners();
    addShortCuts();

    try (var _ = withRefreshGuard(false)) {
      searchBar.setPageSize(25);
      searchBar.setSortBy("createdAt");
      searchBar.setDirValue("desc");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void addListeners() {
    ComponentUtil.addListener(UI.getCurrent(),
                              MappingCreatedOrChanged.class,
                              _ -> {
                                logger().info("Received MappingCreatedOrChanged -> refreshing overview");
                                refreshPageInfo();
                                safeRefresh();
                              });

    grid.addSelectionListener(event -> {
      var all = event.getAllSelectedItems();
      boolean hasSelection = !all.isEmpty();

      bulkBar.setVisible(hasSelection);

      if (hasSelection) {
        int count = all.size();
        String label = count == 1 ? "link selected" : "links selected";
        bulkBar.selectionInfoText(count + " " + label + " on page " + currentPage);
      } else {
        bulkBar.selectionInfoText("");
      }

      bulkBar.setButtonsEnabled(hasSelection);
    });

    btnSettings.addClickListener(_ -> new ColumnVisibilityDialog<>(grid, columnVisibilityService).open());
    prevBtn.addClickListener(_ -> {
      if (currentPage > 1) {
        currentPage--;
        refreshPageInfo();
        safeRefresh();
      }
    });

    nextBtn.addClickListener(_ -> {
      int size = Optional.ofNullable(searchBar.getPageSize()).orElse(25);
      int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / size));
      if (currentPage < maxPage) {
        currentPage++;
        refreshPageInfo();
        safeRefresh();
      }
    });
  }

  private void addShortCuts() {
    var current = UI.getCurrent();

    current.addShortcutListener(_ -> {
                                  if (!grid.getSelectedItems().isEmpty()) {
                                    bulkBar.confirmBulkDeleteSelected();
                                  }
                                },
                                Key.DELETE);
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    try (var _ = withRefreshGuard(false)) {
      var keys = grid
          .getColumns()
          .stream()
          .map(Grid.Column::getKey)
          .filter(Objects::nonNull)
          .toList();

      var vis = columnVisibilityService.mergeWithDefaults(keys);
      grid.getColumns()
          .forEach(c -> {
            var k = c.getKey();
            if (k != null) c.setVisible(vis.getOrDefault(k, true));
          });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    subscription = StoreEvents.subscribe(_ -> getUI()
        .ifPresent(ui -> ui.access(this::safeRefresh)));
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    if (subscription != null) {
      try {
        subscription.close();
      } catch (Exception ignored) {
      }
      subscription = null;
    }
  }


  private void configureGrid() {
    logger().info("configureGrid..");
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
    grid.setHeight("70vh");
    grid.setSelectionMode(Grid.SelectionMode.MULTI);

    configureColumShortCode();
    configureColumUrl();
    configureColumCreated();
    configureColumActive();
    configureColumExpires();
    configureColumActions();

    grid.addItemDoubleClickListener(ev -> openDetailsDialog(ev.getItem()));
    grid.addItemClickListener(ev -> {
      if (ev.getClickCount() == 2) openDetailsDialog(ev.getItem());
    });
    grid.getElement().addEventListener("keydown", _ -> {
      grid.getSelectedItems().stream().findFirst().ifPresent(this::openDetailsDialog);
    }).setFilter("event.key === 'Enter'");

    GridContextMenu<ShortUrlMapping> menu = new GridContextMenu<>(grid);
    menu.addItem("Show details", e -> e.getItem()
        .ifPresent(this::openDetailsDialog));
    menu.addItem("Open URL", e -> e.getItem()
        .ifPresent(m ->
                       UI.getCurrent().getPage().open(m.originalUrl(), "_blank")));
    menu.addItem("Copy shortcode", e -> e.getItem()
        .ifPresent(m ->
                       UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", m.shortCode())));
    menu.addItem("Delete…", e -> e.getItem()
        .ifPresent(m -> confirmDelete(m.shortCode())));
  }

  private void configureColumActions() {
    grid.addComponentColumn(this::buildGridRowActions)
        .setHeader("Actions")
        .setKey("actions")
        .setAutoWidth(true)
        .setFlexGrow(0)
        .setResizable(true);
  }

  private void configureColumExpires() {
    grid.addComponentColumn(m -> {
          var pill = new Span(m.expiresAt()
                                  .map(ts -> {
                                    var days = Duration.between(Instant.now(), ts).toDays();
                                    if (days < 0) return "Expired";
                                    if (days == 0) return "Today";
                                    return "in " + days + " days";
                                  })
                                  .orElse("No expiry"));
          pill.getElement().getThemeList().add("badge pill small");

          m.expiresAt()
              .ifPresent(ts -> {
                long d = Duration.between(Instant.now(), ts).toDays();
                if (d < 0) pill.getElement().getThemeList().add("error");
                else if (d <= 3) pill.getElement().getThemeList().add("warning");
                else pill.getElement().getThemeList().add("success");
              });
          return pill;
        })
        .setHeader("Expires")
        .setKey("expires")
        .setAutoWidth(true)
        .setResizable(true)
        .setFlexGrow(0);
  }

  private void configureColumActive() {
    grid.addComponentColumn(m -> {
          Icon icon = m.active()
              ? VaadinIcon.CHECK_CIRCLE.create()
              : VaadinIcon.CLOSE_CIRCLE.create();

          icon.setColor(m.active()
                            ? "var(--lumo-success-color)"
                            : "var(--lumo-error-color)");
          icon.getStyle().set("cursor", "pointer");
          icon.getElement().setProperty("title",
                                        m.active() ? "Deactivate" : "Activate");

          icon.addClickListener(_ -> {
            boolean newValue = !m.active();

            try {
              urlShortenerClient.toggleActive(m.shortCode(), newValue);
              Notifications.statusUpdatedOK();
              safeRefresh();
            } catch (Exception ex) {
              Notifications.statusUpdatedFailed(ex);
            }
          });
          return icon;
        })
        .setHeader("Active")
        .setKey("active")
        .setAutoWidth(true)
        .setResizable(true)
        .setSortable(true)
        .setFlexGrow(0);
  }

  private void configureColumCreated() {
    grid.addColumn(m -> DATE_TIME_FMT.format(m.createdAt()))
        .setHeader("Created")
        .setKey("created")
        .setAutoWidth(true)
        .setResizable(true)
        .setSortable(true)
        .setFlexGrow(0);
  }

  private void configureColumUrl() {
    grid.addComponentColumn(m -> {
          var a = new Anchor(m.originalUrl(), m.originalUrl());
          a.setTarget("_blank");
          a.getStyle()
              .set("white-space", "nowrap")
              .set("overflow", "hidden")
              .set("text-overflow", "ellipsis")
              .set("display", "inline-block")
              .set("max-width", "100%");
          a.getElement().setProperty("title", m.originalUrl());
          return a;
        })
        .setHeader("URL")
        .setKey("url")
        .setFlexGrow(1)
        .setResizable(true);
  }

  private void configureColumShortCode() {
    grid.addComponentColumn(m -> {
          var code = new Span(m.shortCode());
          code.getStyle().set("font-family", "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace");

          var copy = new Button(new Icon(VaadinIcon.COPY));
          copy.addThemeVariants(LUMO_TERTIARY_INLINE);
          copy.getElement().setProperty("title", "Copy ShortUrl");
          copy.addClickListener(_ -> {
            UI.getCurrent()
                .getPage()
                .executeJs("navigator.clipboard.writeText($0)", SHORTCODE_BASE_URL + m.shortCode());
            Notifications.shortCodeCopied();
          });
          var wrap = new HorizontalLayout(code, copy);
          wrap.setSpacing(true);
          wrap.setPadding(false);
          return wrap;
        })
        .setHeader("Shortcode")
        .setKey("shortcode")
        .setAutoWidth(true)
        .setFrozen(true)
        .setResizable(true)
        .setFlexGrow(0);
  }

  private void openDetailsDialog(ShortUrlMapping item) {
    var dlg = new DetailsDialog(urlShortenerClient, item);
    dlg.addDeleteListener(ev -> confirmDelete(ev.shortCode));
    dlg.addOpenListener(ev -> logger().info("Open URL {}", ev.originalUrl));
    dlg.addCopyShortListener(ev -> logger().info("Copied shortcode {}", ev.shortCode));
    dlg.addCopyUrlListener(ev -> logger().info("Copied URL {}", ev.url));
    dlg.addSavedListener(_ -> safeRefresh());
    dlg.open();
  }

  private Component buildGridRowActions(ShortUrlMapping m) {
    logger().info("buildGridRowActions..");
    Button delete = new Button(new Icon(VaadinIcon.TRASH));
    delete.addThemeVariants(LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    delete.addClickListener(_ -> confirmDelete(m.shortCode()));

    var details = new Button(new Icon(VaadinIcon.SEARCH));
    details.addThemeVariants(LUMO_TERTIARY_INLINE);
    details.addClickListener(_ -> openDetailsDialog(m));
    var row = new HorizontalLayout(details, delete);
    row.setSpacing(true);
    return row;
  }


  private void initDataProvider() {
    dataProvider = new CallbackDataProvider<>(
        q -> {
          final int uiSize = Optional.ofNullable(searchBar.getPageSize()).orElse(25);
          final int pageStart = (currentPage - 1) * uiSize;

          final int vLimit = q.getLimit();
          final int vOffset = q.getOffset();

          final int effectiveLimit = (vLimit > 0) ? vLimit : uiSize;
          final int effectiveOffset = pageStart + vOffset;

          final int page = (effectiveLimit > 0) ? (effectiveOffset / effectiveLimit) + 1 : 1;
          final int size = (effectiveLimit > 0) ? effectiveLimit : uiSize;

          final UrlMappingListRequest req = searchBar.buildFilter(page, size);
          try {
            final List<ShortUrlMapping> items = urlShortenerClient.list(req);
            return items.stream();
          } catch (IOException ex) {
            logger().error("Error fetching (page={}, size={})", page, size, ex);
            Notifications.loadingFailed();
            return Stream.empty();
          }
        },

        _ -> {
          try {
            final UrlMappingListRequest base = searchBar.buildFilter(null, null);
            totalCount = urlShortenerClient.listCount(base);

            final int uiSize = Optional.ofNullable(searchBar.getPageSize()).orElse(25);
            final int pageStart = (currentPage - 1) * uiSize;
            final int remaining = Math.max(0, totalCount - pageStart);
            final int pageCount = Math.min(uiSize, remaining);

            refreshPageInfo();
            return pageCount;
          } catch (IOException ex) {
            logger().error("Error counting", ex);
            totalCount = 0;
            refreshPageInfo();
            return 0;
          }
        }
    );

    grid.setPageSize(Optional.ofNullable(searchBar.getPageSize()).orElse(25));
    grid.setDataProvider(dataProvider);
  }

  private void refreshPageInfo() {
    logger().info("refreshPageInfo");
    int size = Optional.ofNullable(searchBar.getPageSize()).orElse(25);
    int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / size));
    currentPage = Math.min(Math.max(1, currentPage), maxPage);
    pageInfo.setText("Page " + currentPage + " / " + maxPage + "   •   " + totalCount + " total");

    prevBtn.setEnabled(currentPage > 1);
    nextBtn.setEnabled(currentPage < maxPage);
  }

  @Override
  public void safeRefresh() {
    logger().info("safeRefresh");
    if (!suppressRefresh) {
      logger().info("refresh");
      dataProvider.refreshAll();
    }
  }

  private void confirmDelete(String shortCode) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Confirm deletion");
    dialog.add(new Text("Delete short link “" + shortCode + "”?"));

    Button confirm = new Button("Delete", _ -> {
      try {
        boolean ok = urlShortenerClient.delete(shortCode);
        dialog.close();
        if (ok) {
          Notifications.shortCodeDeleted();
          safeRefresh();
        } else {
          Notifications.shortCodeDNotFound();
        }
      } catch (IOException ex) {
        Notifications.operationFailed(ex);
      }
    });
    confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, LUMO_ERROR);

    Button cancel = new Button("Cancel", _ -> dialog.close());

    dialog.add(new HorizontalLayout(confirm, cancel));
    dialog.open();
  }

  public void setGridPageSize(Integer gridPageSize) {
    grid.setPageSize(Optional.ofNullable(gridPageSize).orElse(25)); // optional
  }

  public void setCurrentPage(int currentPage) {
    this.currentPage = currentPage;
  }

  @Override
  public boolean isRefreshSuppressed() {
    return suppressRefresh;
  }

  @Override
  public void setRefreshSuppressed(boolean suppressed) {
    this.suppressRefresh = suppressed;
  }

}
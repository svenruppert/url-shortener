package com.svenruppert.urlshortener.ui.vaadin.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.ColumnVisibilityClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.components.ColumnVisibilityDialog;
import com.svenruppert.urlshortener.ui.vaadin.events.StoreEvents;
import com.svenruppert.urlshortener.ui.vaadin.tools.ColumnVisibilityClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.ColumnVisibilityService;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.svenruppert.urlshortener.core.DefaultValues.PATTERN_DATE_TIME;
import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;

@PageTitle("Overview")
@Route(value = OverviewView.PATH, layout = MainLayout.class)
public class OverviewView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "";
  private static final DateTimeFormatter DATE_TIME_FMT =
      DateTimeFormatter.ofPattern(PATTERN_DATE_TIME)
          .withLocale(Locale.GERMANY)
          .withZone(ZoneId.systemDefault());
  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();
  private final ColumnVisibilityClient columnVisibilityClient = ColumnVisibilityClientFactory.newInstance();

  // Filter controls
  private final TextField codePart = new TextField("Shortcode contains");
  private final Checkbox codeCase = new Checkbox("Case-sensitive");
  private final TextField urlPart = new TextField("Original URL contains");
  private final Checkbox urlCase = new Checkbox("Case-sensitive");
  private final DatePicker fromDate = new DatePicker("From (local)");
  private final TimePicker fromTime = new TimePicker("Time");
  private final DatePicker toDate = new DatePicker("To (local)");
  private final TimePicker toTime = new TimePicker("Time");
  private final ComboBox<String> sortBy = new ComboBox<>("Sort by");
  private final ComboBox<String> dir = new ComboBox<>("Direction");
  private final IntegerField pageSize = new IntegerField("Page size");
  private final Button searchBtn = new Button("Search", new Icon(VaadinIcon.SEARCH));
  private final Button resetBtn = new Button("Reset", new Icon(VaadinIcon.ROTATE_LEFT));
  private final Button prevBtn = new Button("‹ Prev");
  private final Button nextBtn = new Button("Next ›");
  private final Button btnSettings = new Button(new Icon(VaadinIcon.COG));

  private final Text pageInfo = new Text("");
  private final Grid<ShortUrlMapping> grid = new Grid<>(ShortUrlMapping.class, false);

  private int currentPage = 1;
  private int totalCount = 0;
  private CallbackDataProvider<ShortUrlMapping, Void> dataProvider;
  private AutoCloseable subscription;
  private ColumnVisibilityService columnVisibilityService;

  public OverviewView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(new H2("URL Shortener – Overview"));

    initDataProvider();

    add(buildSearchBar());
    configureGrid();
    add(grid);

    pageSize.setValue(25);
    sortBy.setValue("createdAt");
    dir.setValue("desc");

  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    //TODO  Initialize Column Visibility Client/Service (User ID later from security context)
    this.columnVisibilityService = new ColumnVisibilityService(columnVisibilityClient, "admin", "overview");

    var keys = grid.getColumns().stream()
        .map(Grid.Column::getKey)
        .filter(Objects::nonNull)
        .toList();

    var vis = columnVisibilityService.mergeWithDefaults(keys);
    grid.getColumns().forEach(c -> {
      var k = c.getKey();
      if (k != null) c.setVisible(vis.getOrDefault(k, true));
    });

    refresh();
    subscription = StoreEvents.subscribe(_ -> getUI()
        .ifPresent(ui -> ui.access(this::refresh)));
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

  private Component buildSearchBar() {
    codePart.setPlaceholder("e.g. ex-");
    codePart.setValueChangeMode(ValueChangeMode.LAZY);
    codePart.setValueChangeTimeout(400);
    codePart.addValueChangeListener(e -> refresh());

    urlPart.setPlaceholder("e.g. docs");
    urlPart.setValueChangeMode(ValueChangeMode.LAZY);
    urlPart.setValueChangeTimeout(400);
    urlPart.addValueChangeListener(e -> refresh());

    sortBy.setItems("createdAt", "shortCode", "originalUrl", "expiresAt");
    dir.setItems("asc", "desc");

    pageSize.setMin(1);
    pageSize.setMax(500);
    pageSize.setStepButtonsVisible(true);
    pageSize.setWidth("140px");

    prevBtn.addClickListener(e -> {
      if (currentPage > 1) {
        currentPage--;
        refresh();
      }
    });
    nextBtn.addClickListener(_ -> {
      int size = Optional.ofNullable(pageSize.getValue()).orElse(25);
      int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / size));
      if (currentPage < maxPage) {
        currentPage++;
        refresh();
      }
    });

    pageSize.addValueChangeListener(e -> {
      currentPage = 1;
      grid.setPageSize(Optional.ofNullable(e.getValue()).orElse(25)); // optional
      refresh();
    });

    fromDate.setClearButtonVisible(true);
    toDate.setClearButtonVisible(true);

    fromTime.setStep(Duration.ofMinutes(15));
    toTime.setStep(Duration.ofMinutes(15));

    fromTime.setPlaceholder("hh:mm");
    toTime.setPlaceholder("hh:mm");

    searchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchBtn.addClickListener(_ -> {
      currentPage = 1;
      refresh();
    });

    resetBtn.addClickListener(_ -> {
      codePart.clear();
      codeCase.clear();
      urlPart.clear();
      urlCase.clear();
      fromDate.clear();
      fromTime.clear();
      toDate.clear();
      toTime.clear();
      sortBy.clear();
      dir.clear();
      pageSize.setValue(25);
      sortBy.setValue("createdAt");
      dir.setValue("desc");
      currentPage = 1;
      refresh();
    });

    btnSettings.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    btnSettings.getElement().setProperty("title", "Column visibility");
    btnSettings.addClickListener(_ -> new ColumnVisibilityDialog<>(grid, columnVisibilityService).open());

    var fromGroup = new HorizontalLayout(fromDate, fromTime);
    fromGroup.setDefaultVerticalComponentAlignment(Alignment.END);
    fromGroup.setSpacing(true);

    var toGroup = new HorizontalLayout(toDate, toTime);
    toGroup.setDefaultVerticalComponentAlignment(Alignment.END);
    toGroup.setSpacing(true);

    var pagingBar = new HorizontalLayout(prevBtn, nextBtn, pageInfo, btnSettings);
    pagingBar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    var line1 = new HorizontalLayout(
        codePart, codeCase,
        urlPart, urlCase,
        fromGroup, toGroup,
        sortBy, dir,
        pageSize,
        searchBtn, resetBtn
    );
    line1.add(pagingBar);

    line1.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    line1.setWidthFull();
    line1.setSpacing(true);
    line1.setWrap(true);

    return line1;
  }

  private void configureGrid() {
    logger().info("configureGrid..");
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
    grid.setHeight("70vh");
    grid.setSelectionMode(Grid.SelectionMode.SINGLE);

    grid.addComponentColumn(m -> {
          var code = new Span(m.shortCode());
          code.getStyle().set("font-family", "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace");

          var copy = new Button(new Icon(VaadinIcon.COPY));
          copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
          copy.getElement().setProperty("title", "Copy ShortUrl");
          copy.addClickListener(_ -> {
            UI.getCurrent()
                .getPage()
                .executeJs("navigator.clipboard.writeText($0)", SHORTCODE_BASE_URL + m.shortCode());
            Notification.show("Shortcode copied");
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

    grid.addColumn(m -> DATE_TIME_FMT.format(m.createdAt()))
        .setHeader("Created")
        .setKey("created")
        .setAutoWidth(true)
        .setResizable(true)
        .setSortable(true)
        .setFlexGrow(0);

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

          m.expiresAt().ifPresent(ts -> {
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

    grid.addComponentColumn(this::buildActions)
        .setHeader("Actions")
        .setKey("actions")
        .setAutoWidth(true)
        .setFlexGrow(0)
        .setResizable(true);

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

  private void openDetailsDialog(ShortUrlMapping item) {
    var dlg = new DetailsDialog(urlShortenerClient, item);
    dlg.addDeleteListener(ev -> confirmDelete(ev.shortCode));
    dlg.addOpenListener(ev -> logger().info("Open URL {}", ev.originalUrl));
    dlg.addCopyShortListener(ev -> logger().info("Copied shortcode {}", ev.shortCode));
    dlg.addCopyUrlListener(ev -> logger().info("Copied URL {}", ev.url));
    dlg.addSavedListener(_ -> refresh());
    dlg.open();
  }

  private Component buildActions(ShortUrlMapping m) {
    logger().info("buildActions..");
    Button delete = new Button(new Icon(VaadinIcon.TRASH));
    delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    delete.addClickListener(_ -> confirmDelete(m.shortCode()));

    var details = new Button(new Icon(VaadinIcon.SEARCH));
    details.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    details.addClickListener(_ -> openDetailsDialog(m));
    var row = new HorizontalLayout(details, delete);
    row.setSpacing(true);
    return row;
  }

  private void initDataProvider() {
    dataProvider = new CallbackDataProvider<>(
        q -> {
          final int uiSize = Optional.ofNullable(pageSize.getValue()).orElse(25);
          final int pageStart = (currentPage - 1) * uiSize;

          final int vLimit = q.getLimit();
          final int vOffset = q.getOffset();

          final int effectiveLimit = (vLimit > 0) ? vLimit : uiSize;
          final int effectiveOffset = pageStart + vOffset;

          final int page = (effectiveLimit > 0) ? (effectiveOffset / effectiveLimit) + 1 : 1;
          final int size = (effectiveLimit > 0) ? effectiveLimit : uiSize;

          final UrlMappingListRequest req = buildFilter(page, size);
          try {
            final List<ShortUrlMapping> items = urlShortenerClient.list(req);
            return items.stream();
          } catch (IOException ex) {
            logger().error("Error fetching (page={}, size={})", page, size, ex);
            Notification.show("Loading failed");
            return Stream.empty();
          }
        },

        q -> {
          try {
            final UrlMappingListRequest base = buildFilter(null, null);
            totalCount = urlShortenerClient.listCount(base);

            final int uiSize = Optional.ofNullable(pageSize.getValue()).orElse(25);
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

    grid.setPageSize(Optional.ofNullable(pageSize.getValue()).orElse(25));
    grid.setDataProvider(dataProvider);
  }

  private void refreshPageInfo() {
    logger().info("refreshPageInfo");
    int size = Optional.ofNullable(pageSize.getValue()).orElse(25);
    int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / size));
    currentPage = Math.min(Math.max(1, currentPage), maxPage);
    pageInfo.setText("Page " + currentPage + " / " + maxPage + "   •   " + totalCount + " total");

    prevBtn.setEnabled(currentPage > 1);
    nextBtn.setEnabled(currentPage < maxPage);
  }

  private void refresh() {
    logger().info("refresh");
    dataProvider.refreshAll();
  }

  private UrlMappingListRequest buildFilter(Integer page, Integer size) {
    UrlMappingListRequest.Builder b = UrlMappingListRequest.builder();

    if (codePart.getValue() != null && !codePart.getValue().isBlank()) {
      b.codePart(codePart.getValue());
    }
    b.codeCaseSensitive(Boolean.TRUE.equals(codeCase.getValue()));

    if (urlPart.getValue() != null && !urlPart.getValue().isBlank()) {
      b.urlPart(urlPart.getValue());
    }
    b.urlCaseSensitive(Boolean.TRUE.equals(urlCase.getValue()));

    if (fromDate.getValue() != null && fromTime.getValue() != null) {
      var zdt = ZonedDateTime.of(fromDate.getValue(), fromTime.getValue(), ZoneId.systemDefault());
      b.from(zdt.toInstant());
    } else if (fromDate.getValue() != null) {
      var zdt = fromDate.getValue().atStartOfDay(ZoneId.systemDefault());
      b.from(zdt.toInstant());
    }

    if (toDate.getValue() != null && toTime.getValue() != null) {
      var zdt = ZonedDateTime.of(toDate.getValue(), toTime.getValue(), ZoneId.systemDefault());
      b.to(zdt.toInstant());
    } else if (toDate.getValue() != null) {
      var zdt = toDate.getValue().atTime(23, 59).atZone(ZoneId.systemDefault());
      b.to(zdt.toInstant());
    }

    if (sortBy.getValue() != null && !sortBy.getValue().isBlank()) b.sort(sortBy.getValue());
    if (dir.getValue() != null && !dir.getValue().isBlank()) b.dir(dir.getValue());

    if (page != null && size != null) {
      b.page(page).size(size);
    }

    var filter = b.build();
    logger().info("buildFilter - {}", filter);
    return filter;
  }


  private void confirmDelete(String shortCode) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Confirm deletion");
    dialog.add(new Text("Delete short link “" + shortCode + "”?"));

    Button confirm = new Button("Delete", e -> {
      try {
        boolean ok = urlShortenerClient.delete(shortCode);
        dialog.close();
        if (ok) {
          Notification.show("Short link deleted.");
          refresh();
        } else {
          Notification.show("Short link not found.");
        }
      } catch (IOException ex) {
        Notification.show("Operation failed");
        throw new RuntimeException(ex);
      }
    });
    confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

    Button cancel = new Button("Cancel", e -> dialog.close());

    dialog.add(new HorizontalLayout(confirm, cancel));
    dialog.open();
  }


}
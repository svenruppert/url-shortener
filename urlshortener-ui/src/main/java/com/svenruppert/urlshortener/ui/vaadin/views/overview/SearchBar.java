package com.svenruppert.urlshortener.ui.vaadin.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.ui.vaadin.tools.HasRefreshGuard;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView.VALUE_CHANGE_TIMEOUT;
import static com.vaadin.flow.data.value.ValueChangeMode.LAZY;

public class SearchBar
    extends Composite<HorizontalLayout>
    implements HasLogger, HasRefreshGuard {

  private final TextField globalSearch = new TextField();
  private final ComboBox<String> searchScope = new ComboBox<>("Search in");
  private final TextField codePart = new TextField("Shortcode contains");
  private final TextField urlPart = new TextField("Original URL contains");
  private final IntegerField pageSize = new IntegerField("Page size");
  private final DatePicker fromDate = new DatePicker("From (local)");
  private final TimePicker fromTime = new TimePicker("Time");
  private final DatePicker toDate = new DatePicker("To (local)");
  private final TimePicker toTime = new TimePicker("Time");
  private final ComboBox<String> sortBy = new ComboBox<>("Sort by");
  private final ComboBox<String> dir = new ComboBox<>("Direction");
  private final Select<ActiveState> activeState = new Select<>();
  private final Button resetBtn = new Button("Reset", new Icon(VaadinIcon.ROTATE_LEFT));
  private final OverviewView holdingComponent;
  private boolean suppressRefresh = false;
  private Details advanced;

  public SearchBar(OverviewView holdingComponent) {
    this.holdingComponent = holdingComponent;
    container().add(buildSearchBar());
    addShortCuts();
    addListeners();
  }

  private HorizontalLayout container() {
    return this.getContent();
  }

  private Component buildSearchBar() {
    globalSearch.setPlaceholder("Search all…");
    globalSearch.setClearButtonVisible(true);
    globalSearch.setWidth("28rem");
    globalSearch.setValueChangeMode(LAZY);
    globalSearch.setValueChangeTimeout(VALUE_CHANGE_TIMEOUT);

    searchScope.setItems("URL", "Shortcode");
    searchScope.setValue("URL");
    searchScope.setWidth("11rem");

    pageSize.setMin(1);
    pageSize.setMax(500);
    pageSize.setStepButtonsVisible(true);
    pageSize.setWidth("140px");


    codePart.setPlaceholder("e.g. ex-");
    codePart.setValueChangeMode(LAZY);
    codePart.setValueChangeTimeout(VALUE_CHANGE_TIMEOUT);
    codePart.addValueChangeListener(_ -> holdingComponent.safeRefresh());

    urlPart.setPlaceholder("e.g. docs");
    urlPart.setValueChangeMode(LAZY);
    urlPart.setValueChangeTimeout(VALUE_CHANGE_TIMEOUT);
    urlPart.addValueChangeListener(_ -> holdingComponent.safeRefresh());

    sortBy.setItems("createdAt", "shortCode", "originalUrl", "expiresAt");
    dir.setItems("asc", "desc");

    fromDate.setClearButtonVisible(true);
    toDate.setClearButtonVisible(true);
    fromTime.setStep(Duration.ofMinutes(15));
    toTime.setStep(Duration.ofMinutes(15));
    fromTime.setPlaceholder("hh:mm");
    toTime.setPlaceholder("hh:mm");

    var fromGroup = new HorizontalLayout(fromDate, fromTime);
    fromGroup.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    var toGroup = new HorizontalLayout(toDate, toTime);
    toGroup.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

    FormLayout searchBlock = new FormLayout();
    searchBlock.setWidthFull();
    searchBlock.add(codePart, urlPart, new HorizontalLayout()); //TODO not nice!!
    searchBlock.add(fromGroup, toGroup);
    searchBlock.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("32rem", 2),
        new FormLayout.ResponsiveStep("56rem", 3)
    );

    sortBy.setLabel(null);
    sortBy.setPlaceholder("Sort by");
    sortBy.setWidth("12rem");

    dir.setLabel(null);
    dir.setPlaceholder("Direction");
    dir.setWidth("8rem");

    HorizontalLayout sortToolbar = new HorizontalLayout(sortBy, dir);
    sortToolbar.setAlignItems(FlexComponent.Alignment.END);

    HorizontalLayout advHeader = new HorizontalLayout(searchBlock, sortToolbar);
    advHeader.setWidthFull();
    advHeader.setSpacing(true);
    advHeader.setAlignItems(FlexComponent.Alignment.START);
    advHeader.expand(searchBlock);
    advHeader.getStyle().set("flex-wrap", "wrap");
    advHeader.setVerticalComponentAlignment(FlexComponent.Alignment.END, sortToolbar);

    advanced = new Details("Advanced filters", advHeader);
    advanced.setOpened(false);
    advanced.getElement().getThemeList().add("filled");

    setSimpleSearchEnabled(!advanced.isOpened());

    activeState.setLabel("Active state");
    activeState.setItems(ActiveState.values());
    activeState.setItemLabelGenerator(state -> switch (state) {
      case ACTIVE -> "Active";
      case INACTIVE -> "Inactive";
      case NOT_SET -> "Not set";
    });
    activeState.setEmptySelectionAllowed(false);
    activeState.setValue(ActiveState.NOT_SET);
    HorizontalLayout topBar = new HorizontalLayout(globalSearch, searchScope, pageSize, activeState, resetBtn);
    topBar.setWidthFull();
    topBar.setSpacing(true);
    topBar.setAlignItems(FlexComponent.Alignment.END);

    var searchBar = new VerticalLayout();
    searchBar.add(topBar, advanced);
    return searchBar;
  }

  private void addListeners() {
    activeState.addValueChangeListener(_ -> {
      holdingComponent.setCurrentPage(1);
      holdingComponent.safeRefresh();
    });

    globalSearch.addValueChangeListener(e -> {
      var v = Optional.ofNullable(e.getValue()).orElse("");
      if (searchScope.getValue().equals("Shortcode")) {
        codePart.setValue(v);
        urlPart.clear();
      } else {
        urlPart.setValue(v);
        codePart.clear();
      }
    });

    searchScope.addValueChangeListener(_ -> {
      var v = Optional.ofNullable(globalSearch.getValue()).orElse("");
      if ("Shortcode".equals(searchScope.getValue())) {
        codePart.setValue(v);
        urlPart.clear();
      } else {
        urlPart.setValue(v);
        codePart.clear();
      }
    });


    pageSize.addValueChangeListener(e -> {
      holdingComponent.setCurrentPage(1);
      holdingComponent.setGridPageSize(e.getValue());
      holdingComponent.safeRefresh();
    });

    advanced.addOpenedChangeListener(ev -> {
      boolean nowClosed = !ev.isOpened();
      if (nowClosed) {
        applyAdvancedToSimpleAndReset();
      } else {
        setSimpleSearchEnabled(false);
      }
    });

    resetBtn.addClickListener(_ -> {
      try (var _ = withRefreshGuard(true)) {
        resetElements();
        holdingComponent.setCurrentPage(1);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

  }

  private void addShortCuts() {
    var current = UI.getCurrent();
    current.addShortcutListener(_ -> {
                                  if (globalSearch.isEnabled()) globalSearch.focus();
                                },
                                Key.KEY_K, KeyModifier.CONTROL);
    current.addShortcutListener(_ -> {
                                  if (globalSearch.isEnabled()) globalSearch.focus();
                                },
                                Key.KEY_K, KeyModifier.META);


  }

  private void setSimpleSearchEnabled(boolean enabled) {
    globalSearch.setEnabled(enabled);
    searchScope.setEnabled(enabled);
    resetBtn.setEnabled(true);
  }

  private void applyAdvancedToSimpleAndReset() {
    String code = Optional.ofNullable(codePart.getValue()).orElse("").trim();
    String url = Optional.ofNullable(urlPart.getValue()).orElse("").trim();

    final boolean hasCode = !code.isBlank();
    final boolean hasUrl = !url.isBlank();
    final String winnerValue = hasCode ? code : (hasUrl ? url : "");
    final String winnerScope = hasCode ? "Shortcode" : "URL";

    try (var _ = withRefreshGuard(true)) {
      codePart.clear();
      urlPart.clear();
      fromDate.clear();
      fromTime.clear();
      toDate.clear();
      toTime.clear();

      sortBy.clear();
      dir.clear();
      sortBy.setValue("createdAt");
      dir.setValue("desc");

      searchScope.setValue(winnerScope);
      if (!winnerValue.isBlank()) {
        globalSearch.setValue(winnerValue);
      } else {
        globalSearch.clear();
      }

      setSimpleSearchEnabled(true);
      globalSearch.focus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public UrlMappingListRequest buildFilter(Integer page, Integer size) {
    UrlMappingListRequest.Builder b = UrlMappingListRequest.builder();

    ActiveState activeStateValue = activeState.getValue();
    logger().info("buildFilter - activeState == {}", activeStateValue);
    if (activeStateValue != null && activeStateValue.isSet()) {
      b.active(activeStateValue.toBoolean());
    }
    if (codePart.getValue() != null && !codePart.getValue().isBlank()) {
      b.codePart(codePart.getValue());
    }

    if (urlPart.getValue() != null && !urlPart.getValue().isBlank()) {
      b.urlPart(urlPart.getValue());
    }

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

  public void resetElements() {
    globalSearch.clear();
    codePart.clear();
    urlPart.clear();
    fromDate.clear();
    fromTime.clear();
    toDate.clear();
    toTime.clear();
    sortBy.clear();
    dir.clear();
    pageSize.setValue(25);
    sortBy.setValue("createdAt");
    dir.setValue("desc");

    searchScope.setValue("URL");
    advanced.setOpened(false);
    setSimpleSearchEnabled(true);
    globalSearch.focus();
    activeState.setValue(ActiveState.NOT_SET);
  }

  public Integer getPageSize() {
    return pageSize.getValue();
  }

  public void setPageSize(int value) {
    pageSize.setValue(value);
  }

  public void setSortBy(String value) {
    sortBy.setValue(value);
  }

  public void setDirValue(String value) {
    dir.setValue(value);
  }

  @Override
  public boolean isRefreshSuppressed() {
    return suppressRefresh;
  }

  @Override
  public void setRefreshSuppressed(boolean suppressed) {
    this.suppressRefresh = suppressed;
  }

  @Override
  public void safeRefresh() {

  }
}

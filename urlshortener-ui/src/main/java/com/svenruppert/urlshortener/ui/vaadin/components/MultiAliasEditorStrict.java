package com.svenruppert.urlshortener.ui.vaadin.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MultiAliasEditorStrict
    extends VerticalLayout {

  private static final String RX = "^[A-Za-z0-9_-]{3,64}$";
  private final Grid<Row> grid = new Grid<>(Row.class, false);
  private final TextArea bulk = new TextArea("Aliases (comma/space/newline)");
  private final Button insertBtn = new Button("Take over");
  private final Button validateBtn = new Button("Validate all");
  private final String baseUrl;
  private final Function<String, Boolean> isAliasFree;   // Server-Check (true = frei)

  public MultiAliasEditorStrict(String baseUrl,
                                Function<String, Boolean> isAliasFree) {
    this.baseUrl = baseUrl;
    this.isAliasFree = isAliasFree;
    build();
  }

  // ==== Public API for the parent view ====
  public void validateAll() {
    var items = new ArrayList<>(grid.getListDataView().getItems().toList());
    items.forEach(this::validateRow);
    grid.getDataProvider().refreshAll();
  }

  public List<String> getValidAliases() {
    return grid
        .getListDataView()
        .getItems()
        .filter(r -> r.getStatus() == Status.VALID)
        .map(Row::getAlias)
        .collect(Collectors.toList());
  }

  public void markSaved(String alias) {
    setStatus(alias, Status.SAVED, "saved");
  }

  public void markError(String alias, String message) {
    setStatus(alias, Status.ERROR, (message == null ? "error" : message));
  }

  public long countOpen() {
    return grid.getListDataView().getItems()
        .filter(r -> r.getStatus() != Status.SAVED).count();
  }

  public void clearAllRows() {
    grid.setItems(new ArrayList<>());
  }

  private void setStatus(String alias, Status s, String msg) {
    grid.getListDataView().getItems().forEach(r -> {
      if (Objects.equals(r.getAlias(), alias)) {
        r.setStatus(s);
        r.setMsg(msg);
      }
    });
    grid.getDataProvider().refreshAll();
  }

  private void build() {
    setPadding(false);
    setSpacing(true);

    bulk.setWidthFull();
    bulk.setMinHeight("120px");
    bulk.setValueChangeMode(ValueChangeMode.LAZY);
    bulk.setClearButtonVisible(true);
    bulk.setPlaceholder("z. B.\nnews-2025\npromo_x\nabc123");

    insertBtn.addClickListener(_ -> parseBulk());
    validateBtn.addClickListener(_ -> validateAll());

    var toolbar = new HorizontalLayout(insertBtn, validateBtn);
    toolbar.setSpacing(true);

    configureGrid();

    add(bulk, toolbar, grid);
  }

  private void configureGrid() {
    grid.addComponentColumn(row -> {
      var tf = new TextField();
      tf.setWidthFull();
      tf.setMaxLength(64);
      tf.setPattern(RX);
      tf.setValue(Objects.requireNonNullElse(row.getAlias(), ""));
      tf.setEnabled(row.getStatus() != Status.SAVED);
      tf.addValueChangeListener(ev -> {
        row.setAlias(ev.getValue());
        validateRow(row);
        grid.getDataProvider().refreshItem(row);
      });
      return tf;
    }).setHeader("Alias").setFlexGrow(1);

    grid.addColumn(r -> baseUrl + Objects.requireNonNullElse(r.getAlias(), ""))
        .setHeader("Preview").setAutoWidth(true);

    grid.addComponentColumn(row -> {
      var lbl = switch (row.getStatus()) {
        case NEW -> "New";
        case VALID -> "Valid";
        case INVALID_FORMAT -> "Format";
        case CONFLICT -> "Taken";
        case ERROR -> "Error";
        case SAVED -> "Saved";
      };
      var badge = new Span(lbl);
      var theme = switch (row.getStatus()) {
        case VALID, SAVED -> "badge success";
        case CONFLICT, INVALID_FORMAT, ERROR -> "badge error";
        default -> "badge";
      };
      badge.getElement().getThemeList().add(theme);
      if (row.getMsg() != null && !row.getMsg().isBlank()) badge.setTitle(row.getMsg());
      return badge;
    }).setHeader("Status").setAutoWidth(true);

    // Delete action per row
    grid.addComponentColumn(row -> {
      var del = new Button("✕", e -> {
        var items = new ArrayList<>(grid.getListDataView().getItems().toList());
        items.remove(row);
        grid.setItems(items);
      });
      del.getElement().setProperty("title", "Remove");
      return del;
    }).setHeader("").setAutoWidth(true);

    grid.setItems(new ArrayList<>());
    grid.setAllRowsVisible(false);
    grid.setHeight("320px");
  }
  // ========================================

  private void parseBulk() {
    var text = Objects.requireNonNullElse(bulk.getValue(), "");
    var tokens = Arrays.stream(text.split("[,;\\s]+"))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .toList();

    if (tokens.isEmpty()) {
      Notification.show("No aliases to insert", 2000, Notification.Position.TOP_CENTER);
      return;
    }

    Set<String> existing = grid.getListDataView().getItems()
        .map(Row::getAlias)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

    var view = grid.getListDataView();
    int added = 0;
    for (String tok : tokens) {
      if (existing.contains(tok)) continue;
      var r = new Row(tok);
      validateRow(r);
      view.addItem(r);
      existing.add(tok);
      added++;
    }
    grid.getDataProvider().refreshAll();
    bulk.clear();

    Notification.show("Inserted: " + added, 2000, Notification.Position.TOP_CENTER);
  }

  private void validateRow(Row r) {
    var a = Objects.requireNonNullElse(r.getAlias(), "");
    if (!a.matches(RX)) {
      r.setStatus(Status.INVALID_FORMAT);
      r.setMsg("3–64: A–Z a–z 0–9 - _");
      return;
    }

    long same = grid.getListDataView().getItems()
        .filter(x -> x != r && Objects.equals(a, x.getAlias()))
        .count();
    if (same > 0) {
      r.setStatus(Status.CONFLICT);
      r.setMsg("Duplicate in list");
      return;
    }

    if (isAliasFree != null) {
      try {
        if (!isAliasFree.apply(a)) {
          r.setStatus(Status.CONFLICT);
          r.setMsg("Alias taken");
          return;
        }
      } catch (Exception ex) {
        r.setStatus(Status.ERROR);
        r.setMsg("Check failed");
        return;
      }
    }
    r.setStatus(Status.VALID);
    r.setMsg("");
  }

  public enum Status { NEW, VALID, INVALID_FORMAT, CONFLICT, ERROR, SAVED }

  public static final class Row {
    private String alias;
    private Status status = Status.NEW;
    private String msg = "";

    Row(String a) {
      this.alias = a;
    }

    public String getAlias() {
      return alias;
    }

    public void setAlias(String a) {
      this.alias = a;
    }

    public Status getStatus() {
      return status;
    }

    public void setStatus(Status s) {
      this.status = s;
    }

    public String getMsg() {
      return msg;
    }

    public void setMsg(String m) {
      this.msg = m;
    }
  }
}

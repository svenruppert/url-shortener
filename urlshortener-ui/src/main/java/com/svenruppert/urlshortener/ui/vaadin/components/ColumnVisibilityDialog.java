package com.svenruppert.urlshortener.ui.vaadin.components;

import com.svenruppert.urlshortener.ui.vaadin.tools.ColumnVisibilityService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ColumnVisibilityDialog<T> extends Dialog {

  private final Grid<T> grid;
  private final ColumnVisibilityService service;

  public ColumnVisibilityDialog(Grid<T> grid, ColumnVisibilityService service) {
    this.grid = Objects.requireNonNull(grid);
    this.service = Objects.requireNonNull(service);
    setHeaderTitle("Columns");
    setModal(true);
    setDraggable(true);
    setResizable(true);

    var form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("480px", 2),
        new FormLayout.ResponsiveStep("900px", 3)
    );

    // Defaults: alle true, mit Serverwerten überlagert
    var knownKeys = grid.getColumns()
        .stream()
        .map(Grid.Column::getKey)
        .filter(Objects::nonNull)
        .toList();

    Map<String, Boolean> state = service.mergeWithDefaults(knownKeys);

    // Für Bulk-Apply (optional) sammeln wir Änderungen
    Map<String, Boolean> pending = new LinkedHashMap<>();

    grid.getColumns().forEach(col -> {
      final String key = col.getKey();
      if (key == null) return; // nur adressierbare Spalten

      boolean visible = state.getOrDefault(key, true);
      col.setVisible(visible);

      var cb = new Checkbox(col.getHeaderText() != null ? col.getHeaderText() : key, visible);
      cb.addValueChangeListener(ev -> {
        boolean v = Boolean.TRUE.equals(ev.getValue());
        col.setVisible(v);
        // Sofort speichern ODER sammeln:
        service.setSingle(key, v);
        // Falls du stattdessen bulk willst: pending.put(key, v);
      });
      form.add(cb);
    });

    var btnClose = new Button("Close", _ -> close());
    btnClose.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    var btnApply = new Button("Apply bulk", _ -> {
      if (!pending.isEmpty()) {
        service.setBulk(new LinkedHashMap<>(pending));
        pending.clear();
      }
      close();
    });
    btnApply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    getFooter().add(btnClose, btnApply);
    add(form);
  }
}

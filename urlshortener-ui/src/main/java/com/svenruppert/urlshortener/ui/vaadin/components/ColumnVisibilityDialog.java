package com.svenruppert.urlshortener.ui.vaadin.components;

import com.svenruppert.urlshortener.ui.vaadin.tools.ColumnVisibilityService;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@CssImport("./styles/column-visibility-dialog.css")
public final class ColumnVisibilityDialog<T>
    extends Dialog {

  private static final String CLASS_ROOT = "column-visibility-dialog";
  private static final String CLASS_FORM = "column-visibility-dialog__form";
  private static final String CLASS_CB = "column-visibility-dialog__checkbox";

  public ColumnVisibilityDialog(Grid<T> grid, ColumnVisibilityService service) {
    Objects.requireNonNull(service);

    addClassName(CLASS_ROOT);

    setHeaderTitle("Columns");
    setModality(ModalityMode.STRICT);
    setDraggable(true);
    setResizable(true);

    var form = new FormLayout();
    form.addClassName(CLASS_FORM);

    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("480px", 2),
        new FormLayout.ResponsiveStep("900px", 3)
    );

    var knownKeys = grid.getColumns()
        .stream()
        .map(Grid.Column::getKey)
        .filter(Objects::nonNull)
        .toList();

    Map<String, Boolean> state = service.mergeWithDefaults(knownKeys);
    Map<String, Boolean> pending = new LinkedHashMap<>();

    grid.getColumns().forEach(col -> {
      final String key = col.getKey();
      if (key == null) return;

      boolean visible = state.getOrDefault(key, true);
      col.setVisible(visible);

      var label = col.getHeaderText() != null ? col.getHeaderText() : key;
      var cb = new Checkbox(label, visible);
      cb.addClassName(CLASS_CB);

      cb.addValueChangeListener(ev -> {
        boolean v = Boolean.TRUE.equals(ev.getValue());
        col.setVisible(v);
        service.setSingle(key, v);
      });

      form.add(cb);
    });

    var btnClose = new Button("Close", _ -> close());
    btnClose.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    getFooter().add(btnClose);

    add(form);
  }
}

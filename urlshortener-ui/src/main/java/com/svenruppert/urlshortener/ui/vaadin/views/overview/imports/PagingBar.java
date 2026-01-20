package com.svenruppert.urlshortener.ui.vaadin.views.overview.imports;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

public final class PagingBar
    extends HorizontalLayout {

  private final int size;
  private final Button prev = new Button("‹ Prev");
  private final Button next = new Button("Next ›");
  private final Span info = new Span();
  private int page = 1;
  private int total = 0;
  private Runnable onPageChange = () -> { };

  public PagingBar(int size) {
    this.size = size;
    setDefaultVerticalComponentAlignment(CENTER);
    add(prev, next, info);
    updateInfo();

    prev.addClickListener(_ -> {
      if (page > 1) {
        page--;
        onPageChange.run();
      }
    });

    next.addClickListener(_ -> {
      if (page < maxPages()) {
        page++;
        onPageChange.run();
      }
    });
  }

  public int page() {
    return page;
  }

  public int size() {
    return size;
  }

  public void setPage(int page) {
    this.page = Math.max(1, page);
    updateInfo();
  }

  public void setTotal(int total) {
    this.total = Math.max(0, total);
    updateInfo();
  }

  private int maxPages() {
    if (total <= 0) return 1;
    return (int) Math.ceil(total / (double) size);
  }

  private void updateInfo() {
    info.setText("Page " + page + " / " + maxPages() + " (total " + total + ")");
    prev.setEnabled(page > 1);
    next.setEnabled(page < maxPages());
  }
}

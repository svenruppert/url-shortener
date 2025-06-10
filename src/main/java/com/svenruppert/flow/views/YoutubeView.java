package com.svenruppert.flow.views;

import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = "youtube", layout = MainLayout.class)
public class YoutubeView
    extends VerticalLayout {

  public YoutubeView() {
    IFrame youtubePlayer = new IFrame("https://www.youtube.com/embed/CxCMIc5Bx18");
    youtubePlayer.setWidth("800px");
    youtubePlayer.setHeight("450px");
    youtubePlayer.getElement().setAttribute("allowfullscreen", true);
    youtubePlayer.getElement().setAttribute("frameborder", "0");

    add(youtubePlayer);
    setSizeFull();
    setJustifyContentMode(JustifyContentMode.CENTER);
    setAlignItems(Alignment.CENTER);
  }
}
package com.svenruppert.urlshortener.ui.vaadin;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import jakarta.servlet.ServletException;

public class AppServlet
    extends VaadinServlet
    implements HasLogger {


  @Override
  protected void servletInitialized()
      throws ServletException {
    super.servletInitialized();
    logger().info("servletInitialized .. started");
    VaadinServletService service = getService();
    service.addSessionInitListener(e -> {
      e.getSession().setErrorHandler(err -> err.getThrowable().printStackTrace());
    });

    //service.addSessionInitListener();
  }

}

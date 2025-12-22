package com.svenruppert.urlshortener.ui.vaadin;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.ui.vaadin.components.StoreIndicator;
import com.svenruppert.urlshortener.ui.vaadin.security.LoginConfig;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.tools.AdminClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.AboutView;
import com.svenruppert.urlshortener.ui.vaadin.views.CreateView;
import com.svenruppert.urlshortener.ui.vaadin.views.YoutubeView;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.vaadin.flow.component.icon.VaadinIcon.*;

public class MainLayout
    extends AppLayout
    implements BeforeEnterObserver, HasLogger {

  public MainLayout() {
    createHeader();
  }

  private void createHeader() {
    H1 appTitle = new H1("URL Shortener");
    appTitle.getStyle()
        .set("font-size", "1.1rem")
        .set("margin", "0");

    SideNav views = getPrimaryNavigation();
    Scroller scroller = new Scroller(views);
    scroller.setClassName(LumoUtility.Padding.SMALL);

    DrawerToggle toggle = new DrawerToggle();

    var adminClient = AdminClientFactory.newInstance();
    var storeIndicator = new StoreIndicator(adminClient);
    storeIndicator.getStyle().set("margin-left", "auto"); // nach rechts schieben

    HorizontalLayout headerRow;
    if (LoginConfig.isLoginEnabled()) {
      var logoutButton = new Button("Logout", _ -> {
        SessionAuth.clearAuthentication();
        UI.getCurrent().getPage().setLocation("/" + LoginView.PATH);
      });
      logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
      headerRow = new HorizontalLayout(toggle, appTitle, new Span(), storeIndicator, logoutButton);
    } else {
      headerRow = new HorizontalLayout(toggle, appTitle, new Span(), storeIndicator);
    }

    headerRow.setWidthFull();
    headerRow.setAlignItems(FlexComponent.Alignment.CENTER);
    headerRow.setSpacing(true);
    headerRow.setPadding(true);
    headerRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
    headerRow.expand(headerRow.getComponentAt(2));

    VerticalLayout viewHeader = new VerticalLayout(headerRow);
    viewHeader.setPadding(false);
    viewHeader.setSpacing(false);

    addToDrawer(appTitle, scroller);
    addToNavbar(viewHeader);

    setPrimarySection(Section.DRAWER);
  }

  private SideNav getPrimaryNavigation() {
    SideNav sideNav = new SideNav();
    sideNav.addItem(
        new SideNavItem("Overview", "/" + OverviewView.PATH, DASHBOARD.create()),
        new SideNavItem("Create", "/" + CreateView.PATH, DASHBOARD.create()),
        new SideNavItem("Youtube", "/" + YoutubeView.PATH, CART.create()),
        new SideNavItem("About", "/" + AboutView.PATH, USER_HEART.create())
    );
    return sideNav;
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {

    // If login is globally disabled, do not protect any routes.
    if (!LoginConfig.isLoginEnabled()) {
      return;
    }

    logger().info("beforeEnter target={} authenticated={}",
                  event.getNavigationTarget().getSimpleName(),
                  SessionAuth.isAuthenticated());

    // The login view itself must never be protected, otherwise we create a loop
    if (event.getNavigationTarget().equals(LoginView.class)) {
      return;
    }

    if (!SessionAuth.isAuthenticated()) {
      logger().info("beforeEnter.. isAuthenticated()==false - reroute to LoginView");
      event.rerouteTo(LoginView.class);
    }
  }

}

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
import com.vaadin.flow.component.dependency.CssImport;
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

@CssImport("./styles/main-layout.css")
public class MainLayout
    extends AppLayout
    implements BeforeEnterObserver, HasLogger {

  private static final String C_APP_TITLE = "mainlayout__title";
  private static final String C_HEADER_ROW = "mainlayout__header";
  private static final String C_SPACER = "mainlayout__spacer";
  private static final String C_RIGHT = "mainlayout__right";

  public MainLayout() {
    createHeader();
  }

  private void createHeader() {
    H1 appTitle = new H1("URL Shortener");
    appTitle.addClassName(C_APP_TITLE);

    SideNav views = getPrimaryNavigation();
    Scroller scroller = new Scroller(views);
    scroller.setClassName(LumoUtility.Padding.SMALL);

    DrawerToggle toggle = new DrawerToggle();

    var adminClient = AdminClientFactory.newInstance();
    var storeIndicator = new StoreIndicator(adminClient);
    storeIndicator.addClassName(C_RIGHT); // push to right

    HorizontalLayout headerRow;

    if (LoginConfig.isLoginEnabled()) {
      var logoutButton = new Button("Logout", _ -> {
        SessionAuth.clearAuthentication();
        UI.getCurrent().getPage().setLocation("/" + LoginView.PATH);
      });
      logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

      Span spacer = new Span();
      spacer.addClassName(C_SPACER);

      headerRow = new HorizontalLayout(toggle, appTitle, spacer, storeIndicator, logoutButton);

    } else {
      Span spacer = new Span();
      spacer.addClassName(C_SPACER);

      headerRow = new HorizontalLayout(toggle, appTitle, spacer, storeIndicator);
    }

    headerRow.addClassName(C_HEADER_ROW);
    headerRow.setWidthFull();
    headerRow.setAlignItems(FlexComponent.Alignment.CENTER);
    headerRow.setSpacing(true);
    headerRow.setPadding(true);
    headerRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

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
    if (!LoginConfig.isLoginEnabled()) return;

    logger().info("beforeEnter target={} authenticated={}",
                  event.getNavigationTarget().getSimpleName(),
                  SessionAuth.isAuthenticated());

    if (event.getNavigationTarget().equals(LoginView.class)) return;

    if (!SessionAuth.isAuthenticated()) {
      logger().info("beforeEnter.. isAuthenticated()==false - reroute to LoginView");
      event.rerouteTo(LoginView.class);
    }
  }
}

package com.svenruppert.urlshortener.ui.vaadin.views;

import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = AboutView.PATH, layout = MainLayout.class)
public class AboutView
    extends Composite<Div> {

  public static final String PATH = "about";

  public AboutView() {
    Div root = getContent();
    root.addClassName("about-root");
    root.getStyle()
        .set("display", "flex")
        .set("justify-content", "center")
        .set("padding", "var(--lumo-space-l)");

    VerticalLayout container = new VerticalLayout();
    container.setWidthFull();
    container.setMaxWidth("980px");
    container.setSpacing(false);
    container.setPadding(false);
    container.getStyle()
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("gap", "var(--lumo-space-l)");

    VerticalLayout hero = new VerticalLayout();
    hero.setPadding(true);
    hero.setSpacing(false);
    hero.setAlignItems(Alignment.START);
    hero.addClassName("about-hero");
    hero.getStyle()
        .set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), transparent)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "var(--lumo-space-xl)");

    Span versionBadge = new Span("Version 1.0.0");
    versionBadge.getElement().setAttribute("theme", "badge primary pill");

    H1 title = new H1("About");
    title.getStyle()
        .set("margin", "0")
        .set("font-size", "var(--lumo-font-size-xxl)")
        .set("line-height", "1.1");

    Paragraph subtitle = new Paragraph("Vaadin Flow Demo Application");
    subtitle.getStyle().set("margin-top", "var(--lumo-space-xs)");

    HorizontalLayout heroHeader = new HorizontalLayout(title, versionBadge);
    heroHeader.setAlignItems(Alignment.CENTER);
    heroHeader.setSpacing(true);

    Paragraph description = new Paragraph(
        "This demo showcases polished UI patterns with Vaadin Flow — cards, badges, icons, and a responsive layout."
    );
    description.getStyle().set("margin-top", "var(--lumo-space-m)");

    hero.add(heroHeader, subtitle, description);

    VerticalLayout profileCard = card();
    profileCard.setWidthFull();

    HorizontalLayout header = new HorizontalLayout();
    header.setWidthFull();
    header.setAlignItems(Alignment.CENTER);
    header.setSpacing(true);

    Image vaadinLogo = new Image("images/portrait-sven.jpg", "neosecIT Logo");
    vaadinLogo.setWidth("72px");
    vaadinLogo.getStyle()
        .set("border-radius", "12px")
        .set("box-shadow", "var(--lumo-box-shadow-s)");

    VerticalLayout headerText = new VerticalLayout();
    headerText.setSpacing(false);
    headerText.setPadding(false);

    H2 authorName = new H2("Sven Ruppert");
    authorName.getStyle().set("margin", "0");

    Span role = new Span("Developer Advocate • Java • Secure Coding");
    role.getStyle().set("color", "var(--lumo-secondary-text-color)");

    headerText.add(authorName, role);
    header.add(vaadinLogo, headerText);

    Paragraph bio = new Paragraph(
        "Sven Ruppert has been involved in software development for more than 20 years. "
            + "As a developer advocate, he is constantly exploring innovations in software engineering. "
            + "He speaks internationally at conferences and has authored numerous technical articles and books."
    );
    bio.getStyle().set("margin-top", "var(--lumo-space-m)");

    HorizontalLayout badges = new HorizontalLayout();
    badges.setWrap(true);
    badges.setSpacing(true);
    badges.add(
        badge("Vaadin Flow"),
        badge("Java 21–24"),
        badge("Security"),
        badge("RAG/AI"),
        badge("EclipseStore"),
        badge("DevSecOps")
    );

    HorizontalLayout links = new HorizontalLayout();
    links.setSpacing(true);
    links.setWrap(true);

    Anchor website = themedLink(
        "https://www.svenruppert.com",
        "Website",
        new Icon(VaadinIcon.GLOBE_WIRE)
    );

    Anchor github = themedLink(
        "https://github.com/svenruppert",
        "GitHub",
        imgIcon("icons/github-mark.svg", "GitHub")
    );

    Anchor linkedin = themedLink(
        "https://www.linkedin.com/in/sven-ruppert",
        "LinkedIn",
        imgIcon("icons/linkedin-mark.png", "LinkedIn")
    );

    links.add(website, github, linkedin);

    profileCard.add(header, bio, badges, new Hr(), links);

    VerticalLayout aboutCard = card();
    aboutCard.setWidthFull();

    H3 projectTitle = new H3("About this Project");
    projectTitle.getStyle().set("margin-top", "0");

    Paragraph projectText = new Paragraph(
        "This application demonstrates clean UI composition with Flow components, "
            + "a soft visual hierarchy, and accessible defaults. The layout scales from mobile to desktop, "
            + "while subtle shadows and rounded corners keep it modern."
    );

    UnorderedList featureList = new UnorderedList(
        new ListItem(withIcon(VaadinIcon.CHECK, "Responsive layout & cards")),
        new ListItem(withIcon(VaadinIcon.CHECK, "Lumo-themed badges & icons")),
        new ListItem(withIcon(VaadinIcon.CHECK, "Clean typography & spacing")),
        new ListItem(withIcon(VaadinIcon.CHECK, "Accessible links and readable contrast"))
    );

    aboutCard.add(projectTitle, projectText, featureList);

    FlexLayout grid = new FlexLayout(profileCard, aboutCard);
    grid.setWidthFull();
    grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    grid.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
    grid.getStyle().set("gap", "var(--lumo-space-l)");
    profileCard.setFlexGrow(1.0, grid);
    aboutCard.setFlexGrow(1.0, grid);
    profileCard.setMinWidth("280px");
    aboutCard.setMinWidth("280px");

    // Footer / Meta
    Div footer = new Div();
    footer.getStyle()
        .set("display", "flex")
        .set("align-items", "center")
        .set("gap", "var(--lumo-space-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    footer.add(new Icon(VaadinIcon.INFO_CIRCLE_O), new Text("Demo UI built with Vaadin Flow & Lumo"));

    container.add(hero, grid, footer);
    root.add(container);
  }

  // Helpers

  private VerticalLayout card() {
    VerticalLayout card = new VerticalLayout();
    card.setPadding(true);
    card.setSpacing(true);
    card.setAlignItems(Alignment.START);
    card.getStyle()
        .set("background", "var(--lumo-base-color)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("box-shadow", "var(--lumo-box-shadow-s)")
        .set("padding", "var(--lumo-space-l)");
    return card;
  }

  private Span badge(String label) {
    Span s = new Span(label);
    s.getElement().setAttribute("theme", "badge contrast pill");
    return s;
  }

  private Component withIcon(VaadinIcon vaadinIcon, String text) {
    HorizontalLayout row = new HorizontalLayout();
    row.setSpacing(true);
    row.setAlignItems(Alignment.CENTER);
    Icon icon = new Icon(vaadinIcon);
    icon.getStyle().set("width", "var(--lumo-icon-size-s)").set("height", "var(--lumo-icon-size-s)");
    row.add(icon, new Text(text));
    return row;
  }

  private Anchor themedLink(String href, String label, VaadinIcon vaadinIcon) {
    Anchor a = new Anchor(href, "");
    a.setTarget("_blank");
    a.getElement().setAttribute("aria-label", label);
    a.getStyle()
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("gap", "0.4rem")
        .set("text-decoration", "none")
        .set("padding", "0.35rem 0.7rem")
        .set("border-radius", "999px")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("box-shadow", "var(--lumo-box-shadow-xs)");
    Icon icon = new Icon(vaadinIcon);
    Span text = new Span(label);
    a.add(icon, text);
    return a;
  }
  private Anchor themedLink(String href, String label, Component leadingIcon) {
    Anchor a = new Anchor(href, "");
    a.setTarget("_blank");
    a.getElement().setAttribute("aria-label", label);
    a.getStyle()
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("gap", "0.45rem")
        .set("text-decoration", "none")
        .set("padding", "0.35rem 0.7rem")
        .set("border-radius", "999px")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("box-shadow", "var(--lumo-box-shadow-xs)");
    Span text = new Span(label);
    a.add(leadingIcon, text);
    return a;
  }

  private Image imgIcon(String src, String alt) {
    Image img = new Image(src, alt);
    img.setWidth("18px");
    img.setHeight("18px");
    img.getStyle().set("display", "inline-block");
    return img;
  }

}

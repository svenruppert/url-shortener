package com.svenruppert.urlshortener.ui.vaadin.views;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.vaadin.barcodes.Barcode;

import java.net.URI;

@Route("/share/:urlCode")
public class ShareView extends VerticalLayout implements BeforeEnterObserver {

    private URI baseUrl;
    private String urlCode;

    public ShareView() {
        baseUrl = URI.create("https://3g3.eu/");
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        urlCode = event.getRouteParameters().get("urlCode").orElse("");
        var uri = baseUrl.resolve(urlCode);
        Barcode qrCode = new Barcode(uri.toString(), Barcode.Type.qrcode, "10rem", "10rem");
        qrCode.addClassNames(LumoUtility.Margin.Vertical.MEDIUM);
        add(qrCode);
        add(new Anchor(uri.toString(), uri.toString()));
    }
}
